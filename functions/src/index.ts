import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2";
import { defineSecret, defineString } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { GoogleGenAI } from "@google/genai";

admin.initializeApp();

// Set default region close to Malaysia.
setGlobalOptions({ region: "asia-southeast1" });

// ---- Config / params ----
const SAFE_BROWSING_API_KEY = defineSecret("SAFE_BROWSING_API_KEY");

// Google Gen AI SDK with Vertex AI
// Note: We'll use Vertex AI backend by setting vertexai: true and providing project+location.
// location "asia-southeast1" is supported by Gemini 2.5 Flash on Vertex AI.
const VERTEX_LOCATION = defineString("VERTEX_LOCATION", { default: "asia-southeast1" });
const GEMINI_MODEL = defineString("GEMINI_MODEL", { default: "gemini-2.5-flash" });

// ---- Helper: safe browsing lookup (manual scan use-case) ----
async function safeBrowsingLookup(urls: string[], apiKey: string) {
  // Google Safe Browsing Lookup API v4: threatMatches.find
  const endpoint = `https://safebrowsing.googleapis.com/v4/threatMatches:find?key=${apiKey}`;

  const body = {
    client: { clientId: "safex", clientVersion: "1.0.0" },
    threatInfo: {
      threatTypes: ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
      platformTypes: ["ALL_PLATFORMS"],
      threatEntryTypes: ["URL"],
      threatEntries: urls.slice(0, 500).map((u) => ({ url: u })),
    },
  };

  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`SafeBrowsing error ${res.status}: ${text}`);
  }

  return await res.json(); // either {} or { matches: [...] }
}

// ---- Helper: local heuristic check for typosquatting / brand impersonation ----
// ---- Helper: Levenshtein distance (for fuzzy brand matching) ----
function levenshtein(a: string, b: string): number {
  const m = a.length, n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, (_, i) => Array(n + 1).fill(0).map((_, j) => i === 0 ? j : j === 0 ? i : 0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
    }
  }
  return dp[m][n];
}

// ---- Helper: local heuristic check for typosquatting / brand impersonation ----
function checkUrlHeuristics(url: string): { suspicious: boolean; brand: string | null; reason: string | null } {
  try {
    const parsed = new URL(url.startsWith("http") ? url : `https://${url}`);
    const hostname = parsed.hostname.toLowerCase();
    // Use second-level domain (e.g. "whtasapp-a" from "whtasapp-a.com")
    const parts = hostname.split(".");
    const sld = parts.slice(0, -1).join("."); // everything before the last dot

    // Known brand names commonly impersonated in Malaysia
    const brands = [
      "whatsapp", "telegram", "maybank", "cimb", "rhb", "publicbank",
      "bnm", "banknegara", "pdrm", "lhdn", "mypay", "touchngo",
      "grabpay", "shopee", "lazada", "poslaju", "celcom", "maxis", "digi",
      "unifi", "tmnet", "facebook", "instagram", "paypal", "amazon", "apple", "microsoft",
      "google", "netflix", "boost", "mycash"
    ];

    // Official domain allowlist (exact hostname match)
    const officialDomains = new Set([
      "whatsapp.com", "web.whatsapp.com", "telegram.org", "t.me",
      "maybank2u.com.my", "maybank.com.my", "cimb.com.my", "www.rhbbank.com.my",
      "bnm.gov.my", "pdrm.gov.my", "lhdn.gov.my",
      "facebook.com", "instagram.com", "paypal.com", "amazon.com",
      "apple.com", "microsoft.com", "google.com", "netflix.com",
      "shopee.com.my", "lazada.com.my", "touchngo.com.my", "tngdigital.com.my"
    ]);

    if (officialDomains.has(hostname)) {
      return { suspicious: false, brand: null, reason: null };
    }

    // Suspicious TLDs
    const suspiciousTlds = [".xyz", ".top", ".club", ".online", ".site", ".info", ".biz", ".store", ".vip", ".pw", ".cc"];
    const hasSuspiciousTld = suspiciousTlds.some(tld => hostname.endsWith(tld));
    if (hasSuspiciousTld) {
      return { suspicious: true, brand: null, reason: `Domain uses a high-risk TLD (.${parts[parts.length - 1]}) commonly used in phishing.` };
    }

    // Fuzzy brand matching — catch typosquatting like whtasapp, telegr4m, maybonk etc.
    // Split SLD on hyphens/numbers and check each token
    const tokens = sld.split(/[-_0-9]/).filter(t => t.length >= 4);
    for (const token of tokens) {
      for (const brand of brands) {
        if (token.length < 4) continue;
        const dist = levenshtein(token, brand);
        // Allow 1 edit per 5 characters of the brand name (e.g. brand "whatsapp" len=8 → max dist 1)
        const maxDist = Math.max(1, Math.floor(brand.length / 5));
        if (dist <= maxDist && token !== brand) {
          return {
            suspicious: true,
            brand,
            reason: `Domain "${hostname}" appears to impersonate "${brand}" (typosquatting — very similar spelling).`
          };
        }
        // Also catch exact substring match for longer brand names
        if (brand.length >= 6 && sld.includes(brand)) {
          return {
            suspicious: true,
            brand,
            reason: `Domain "${hostname}" contains the brand name "${brand}" but is not the official website.`
          };
        }
      }
    }

    // Excessive hyphens in SLD
    if ((sld.match(/-/g) || []).length >= 2) {
      return { suspicious: true, brand: null, reason: "Domain has multiple hyphens, a common pattern in phishing URLs." };
    }

    return { suspicious: false, brand: null, reason: null };
  } catch {
    return { suspicious: false, brand: null, reason: null };
  }
}

// ---- Callable: explainAlert ----
// Called from Android only when user opens an alert detail screen.
export const explainAlert = onCall(
  {
    secrets: [SAFE_BROWSING_API_KEY],
    cors: true,
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request) => {
    // Security: require auth (anonymous auth is fine)
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Auth required.");
    }

    const data = request.data as any;

    // Minimal, privacy-first payload
    const alertType = String(data?.alertType ?? "");
    const category = String(data?.category ?? "unknown");
    const tactics = Array.isArray(data?.tactics) ? data.tactics.map(String) : [];
    const snippet = String(data?.snippet ?? "").slice(0, 500); // already redacted on-device
    const extractedUrl = data?.extractedUrl ? String(data.extractedUrl).slice(0, 500) : null;
    const heuristicScore = typeof data?.heuristicScore === 'number' ? data.heuristicScore : null;
    const tfliteScore = typeof data?.tfliteScore === 'number' ? data.tfliteScore : null;

    if (!alertType) {
      throw new HttpsError("invalid-argument", "alertType is required.");
    }

    // If this request includes a URL and user explicitly scanned it, we can optionally check Safe Browsing.
    // IMPORTANT: per your product decision, we do NOT auto-check links from notifications.
    let safeBrowsing = null;
    if (extractedUrl && data?.doSafeBrowsingCheck === true) {
      try {
        safeBrowsing = await safeBrowsingLookup([extractedUrl], SAFE_BROWSING_API_KEY.value());
      } catch (e: any) {
        logger.warn("SafeBrowsing failed", e);
        safeBrowsing = { error: String(e?.message ?? e) };
      }
    }

    const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
    if (!project) {
      throw new HttpsError("internal", "Missing project id in environment.");
    }

    const ai = new GoogleGenAI({
      vertexai: true,
      project,
      location: VERTEX_LOCATION.value(),
    });

    // System instruction: enforce safe, calm, elder-friendly language and JSON output
    const system = `
You are SafeX, an anti-scam safety assistant.
You must be calm, non-blaming, and actionable.
Return ONLY valid JSON. No markdown.

Determine the specific scam category from this list:
- Spam
- Phishing
- Investment Scam
- Love Scam
- Job Scam
- E-commerce Scam
- Impersonation Scam
- Loan Scam
- Giveaway Scam
- Tech Support Scam
- Deepfake
- KK Farm Scam
- Other

JSON schema:
{
  "category": "string (one of the above)",
  "riskLevel": "LOW" | "MEDIUM" | "HIGH",
  "headline": string,
  "whyFlagged": string[],
  "whatToDoNow": string[],
  "whatNotToDo": string[],
  "confidence": number, // 0 to 1
  "notes": string
}

Rules:
- Do not ask the user to click unknown links.
- If the message suggests urgency, impersonation, or money transfer, raise risk.
- FALSE POSITIVE PREVENTION: If the text appears to be a legitimate university poster, committee recruitment, event advertisement, or normal marketing material, and NOT a scam, you MUST set "riskLevel" to "LOW".
- Use simple language suitable for elders.
- Output language: You MUST return the ENTIRE JSON response in English. Do not translate it.
`;

    const userPrompt = {
      alertType,
      category,
      tactics,
      snippet,
      extractedUrl,
      safeBrowsing,
      heuristicScore,
      tfliteScore,
    };

    const response = await ai.models.generateContent({
      model: GEMINI_MODEL.value(),
      // Keep output short-ish for mobile UI.
      contents: [
        {
          role: "user",
          parts: [{ text: `Analyze this alert payload:\n${JSON.stringify(userPrompt)}` }],
        },
      ],
      config: {
        systemInstruction: system,
      },
    });

    const text = response.text ?? "";
    // Best-effort JSON parse
    try {
      const parsed = JSON.parse(text);
      return parsed;
    } catch (e) {
      logger.error("Gemini returned non-JSON", { text });
      // Fallback: return structured but generic
      return {
        category: "Spam", // Default fallback
        riskLevel: "MEDIUM",
        headline: "Suspicious message detected",
        whyFlagged: ["Message matched known scam manipulation patterns."],
        whatToDoNow: ["Do not respond yet.", "Use SafeX Scan to test any links.", "Ask a trusted person if unsure."],
        whatNotToDo: ["Do not share OTP or banking details.", "Do not send money."],
        confidence: 0.5,
        notes: "Fallback response (model output was not valid JSON).",
      };
    }
  }
);

// ---- Callable: reportAlert ----
// Called when user taps Report on alert detail screen.
// Stores only aggregated counters (no raw content).
export const reportAlert = onCall(
  {
    cors: true,
    timeoutSeconds: 15,
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Auth required.");
    }

    const data = request.data as any;
    const category = String(data?.category ?? "unknown");
    const tactics = Array.isArray(data?.tactics) ? data.tactics.map(String) : [];
    const domainPattern = data?.domainPattern ? String(data.domainPattern).slice(0, 120) : null;

    // Use a weekly doc ID, e.g. 2026-W05
    const now = new Date();
    const year = now.getUTCFullYear();
    const oneJan = new Date(Date.UTC(year, 0, 1));
    const week = Math.ceil((((now.getTime() - oneJan.getTime()) / 86400000) + oneJan.getUTCDay() + 1) / 7);
    const weekId = `${year}-W${String(week).padStart(2, "0")}`;

    const docRef = admin.firestore().collection("insightsWeekly").doc(weekId);

    const inc = admin.firestore.FieldValue.increment(1);

    const updates: any = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      totalReports: inc,
      [`categories.${category}`]: inc,
    };

    for (const t of tactics.slice(0, 8)) {
      updates[`tactics.${t}`] = inc;
    }

    if (domainPattern) {
      updates[`domainPatterns.${domainPattern}`] = inc;
    }

    await docRef.set(updates, { merge: true });

    return { ok: true, weekId };
  }
);

// ---- Callable: checkLink ----
// Called from Home -> Manual Link Scan.
export const checkLink = onCall(
  {
    secrets: [SAFE_BROWSING_API_KEY],
    cors: true,
    timeoutSeconds: 15,
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Auth required.");
    }

    const data = request.data as any;
    const url = String(data?.url ?? "").trim();
    const language = String(data?.language ?? "en"); // "en", "zh", "ms"

    if (!url) {
      throw new HttpsError("invalid-argument", "URL is required.");
    }

    // Language label for Gemini translation instruction
    const langLabel: Record<string, string> = {
      zh: "Simplified Chinese (中文)",
      ms: "Bahasa Melayu",
      en: "English",
    };
    const targetLang = langLabel[language] ?? "English";

    try {
      const result = await safeBrowsingLookup([url], SAFE_BROWSING_API_KEY.value());
      const matches = result.matches ?? [];

      // If Safe Browsing has a definitive hit, high confidence
      if (matches.length > 0) {
        return {
          safe: false,
          riskLevel: "HIGH",
          headline: "Dangerous URL Detected",
          reasons: [
            "Google Safe Browsing identified this URL as a known threat.",
            ...matches.map((m: any) => `Threat type: ${m.threatType}`)
          ],
          matches,
        };
      }

      // Safe Browsing clean — run local heuristic check
      // (many new phishing domains aren't in Google's DB yet)
      const heuristic = checkUrlHeuristics(url);
      if (heuristic.suspicious) {
        // Translate explanation via Gemini
        const proj = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
        let headline = "Suspicious URL — Possible Phishing";
        let reason1 = heuristic.reason ?? "URL matches known phishing patterns.";
        let reason2 = "Google Safe Browsing has not yet recorded this domain, but its pattern matches a known brand impersonation technique.";
        if (proj && language !== "en") {
          try {
            const ai2 = new GoogleGenAI({ vertexai: true, project: proj, location: VERTEX_LOCATION.value() });
            const translateResp = await ai2.models.generateContent({
              model: GEMINI_MODEL.value(),
              contents: [{
                role: "user", parts: [{
                  text:
                    `Translate the following strictly into ${targetLang}. Return ONLY valid JSON with keys: headline, reason1, reason2.\n` +
                    JSON.stringify({ headline, reason1, reason2 })
                }]
              }],
              config: { responseMimeType: "application/json" }
            });
            const parsed = JSON.parse(translateResp.text ?? "{}");
            headline = parsed.headline || headline;
            reason1 = parsed.reason1 || reason1;
            reason2 = parsed.reason2 || reason2;
          } catch { /* keep English on failure */ }
        }
        return {
          safe: false,
          riskLevel: "HIGH",
          headline,
          reasons: [reason1, reason2],
        };
      }

      // All checks passed — translate if needed
      let safeHeadline = "No threats found";
      let safeReason = "Google Safe Browsing found no known issues with this URL.";
      if (language !== "en") {
        try {
          const proj = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
          if (proj) {
            const ai2 = new GoogleGenAI({ vertexai: true, project: proj, location: VERTEX_LOCATION.value() });
            const r = await ai2.models.generateContent({
              model: GEMINI_MODEL.value(),
              contents: [{
                role: "user", parts: [{
                  text:
                    `Translate into ${targetLang}. Return ONLY JSON with keys: headline, reason.\n` +
                    JSON.stringify({ headline: safeHeadline, reason: safeReason })
                }]
              }],
              config: { responseMimeType: "application/json" }
            });
            const p = JSON.parse(r.text ?? "{}");
            safeHeadline = p.headline || safeHeadline;
            safeReason = p.reason || safeReason;
          }
        } catch { /* use English */ }
      }
      return {
        safe: true,
        riskLevel: "LOW",
        headline: safeHeadline,
        reasons: [safeReason]
      };
    } catch (e: any) {
      logger.error("checkLink failed", e);
      // Fail open but at MEDIUM, not LOW — something went wrong and user should be cautious
      return {
        safe: false,
        riskLevel: "MEDIUM",
        headline: "Could not fully verify URL",
        reasons: ["Safe Browsing check encountered an error. Treat with caution.", `Details: ${e?.message ?? String(e)}`]
      };
    }
  }
);

// ---- Shared Scraping Logic ----
async function scrapeAndProcessNews(region: string = "GLOBAL", customUrl?: string) {
  const db = admin.firestore();
  const newsColl = db.collection("scam_news");

  const query = buildNewsQuery(region);
  const searchUrl = customUrl || `https://news.google.com/rss/search?q=${encodeURIComponent(query)}+when:30d&hl=en-US&gl=US&ceid=US:en`;

  let articles: any[] = [];
  try {
    const res = await fetch(searchUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 SafeXBot/1.0",
        "Accept": "application/rss+xml, application/xml"
      },
      signal: AbortSignal.timeout(20000)
    });

    if (res.ok) {
      const xml = await res.text();
      const itemRegex = /<item>([\s\S]*?)<\/item>/g;
      let match;
      const limit = customUrl ? 250 : 100;

      while ((match = itemRegex.exec(xml)) !== null) {
        if (articles.length >= limit) break;
        const chunk = match[1];

        const titleMatch = chunk.match(/<title>(.*?)<\/title>/);
        const linkMatch = chunk.match(/<link>(.*?)<\/link>/);
        const dateMatch = chunk.match(/<pubDate>(.*?)<\/pubDate>/);
        const sourceMatch = chunk.match(/<source.*?url="([^"]*)">(.*?)<\/source>/);

        if (titleMatch && linkMatch) {
          articles.push({
            title: titleMatch[1].trim()
              .replace(/&apos;/g, "'")
              .replace(/&amp;/g, "&")
              .replace(/&quot;/g, '"')
              .replace(/&lt;/g, "<")
              .replace(/&gt;/g, ">"),
            url: linkMatch[1].trim(),
            seendate: dateMatch ? dateMatch[1].trim() : new Date().toUTCString(),
            domain: sourceMatch ? sourceMatch[1].trim() : "",
            sourceName: sourceMatch ? sourceMatch[2].trim() : "News Provider",
          });
        }
      }
    } else {
      logger.error(`News fetch returned ${res.status} ${res.statusText}`);
    }
  } catch (e) {
    logger.error("News fetch failed", e);
  }

  if (articles.length === 0) return;

  const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
  if (!project) {
    logger.error("Missing project id.");
    return;
  }

  const ai = new GoogleGenAI({ vertexai: true, project, location: VERTEX_LOCATION.value() });

  await Promise.all(articles.map(async (article: any) => {
    const url = article.url;
    if (!url) return;

    const id = Buffer.from(url).toString('base64').replace(/\//g, '_').slice(0, 200);
    const docRef = newsColl.doc(id);

    const docSnap = await docRef.get();
    if (docSnap.exists) return;

    const title = article.title;
    const domain = article.domain;
    const imageUrl = article.socialimage || "";
    const seenDate = article.seendate;

    const prompt = `
      Task: 
      1. Evaluate if this news article is about a scam targeting INDIVIDUALS through their PHONES, SMS, MESSENGING APPS (WhatsApp, Telegram, etc.), SOCIAL MEDIA, or PHONE CALLS.
      - Examples of ACCEPTED scams: fake apps, banking trojans on Android/iOS, SMS phishing (smishing), WhatsApp job scams, investment groups on Telegram, impersonation calls, romance scams via dating apps.
      - IF the article is about corporate fraud, company lawsuits, B2B issues, CEO crimes, political news, general cybersecurity (like server hacks), or scams that DO NOT involve mobile phones/apps, you MUST reject it.
      
      2. IF you accept it, generate IN ENGLISH:
      - A concise title.
      - A helpful 2-sentence anti-scam summary.
      - 2-3 bullet points of preventative warnings or tips specifically related to this type of scam.
      
      Input:
      Title: ${title}
      Source: ${domain}
      
      Output ONLY valid JSON:
      {
        "reject": boolean,
        "title": "string (or empty if rejected)",
        "summary": "string (or empty if rejected)",
        "warningsAndTips": "string (bullet points starting with •, or empty if rejected)"
      }
    `;

    try {
      const resp = await ai.models.generateContent({
        model: GEMINI_MODEL.value(),
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        config: { responseMimeType: "application/json" }
      });

      const result = JSON.parse(resp.text ?? "{}");

      if (result.reject === true) {
        logger.info(`Rejected irrelevant news: ${title}`);
        await docRef.set({ rejected: true, createdAt: Date.now() });
        return;
      }

      const finalObj = {
        url,
        title: result.title || title,
        summary: result.summary || "No summary available.",
        warningsAndTips: result.warningsAndTips || "• Be careful.\n• Do not share personal information.",
        domain,
        seenDate,
        imageUrl,
        region,
        createdAt: Date.now()
      };

      await docRef.set(finalObj);
      logger.info(`Saved new verified scam news: ${finalObj.title}`);
    } catch (e) {
      logger.warn(`AI processing failed for ${url}`, e);
    }
  }));
}

// ---- Background Worker: Runs continuously every 1 hour ----
export const periodicScamNewsScraper = onSchedule(
  {
    schedule: "every 1 hours",
    timeoutSeconds: 300, // Increased timeout given higher volume
    memory: "512MiB",
  },
  async (event) => {
    logger.info("Starting background periodic scam news scraper...");
    await scrapeAndProcessNews("GLOBAL");
    logger.info("Finished background periodic scam news scraper.");
  }
);

// ---- One-Time Backfill Trigger (via HTTP URL) ----
export const backfillHistoricalScams = onRequest(
  {
    timeoutSeconds: 540,
    memory: "1GiB",
  },
  async (req, res) => {
    logger.info("Starting massive historical backfill via Google News...");
    const query = buildNewsQuery("GLOBAL");

    // Grabs max records from the past 3 years.
    const searchUrl = `https://news.google.com/rss/search?q=${encodeURIComponent(query)}+when:3y&hl=en-US&gl=US&ceid=US:en`;

    await scrapeAndProcessNews("GLOBAL", searchUrl);

    logger.info("Historical backfill complete.");
    res.send({ success: true, message: "Historical 3y backfill complete via Google News!" });
  }
);

// ---- Callable: getScamNewsDigest ----
// Returns the incredibly fast, pre-processed Master English articles from Firestore.
export const getScamNewsDigest = onCall(
  {
    secrets: [],
    cors: true,
    timeoutSeconds: 20, // Much shorter timeout since it's just a DB read
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Auth required.");

    const data = request.data as any;
    const region = String(data?.region ?? "GLOBAL");

    const db = admin.firestore();
    const newsColl = db.collection("scam_news");

    try {
      let snapshot = await newsColl.orderBy("createdAt", "desc").limit(30).get();
      let validNews = snapshot.docs.map(d => d.data()).filter(d => d.rejected !== true);

      // If the database is completely empty (first run), force an emergency synchronous scrape
      if (validNews.length === 0) {
        logger.info("Database empty, performing emergency synchronous scrape.");
        await scrapeAndProcessNews(region);
        snapshot = await newsColl.orderBy("createdAt", "desc").limit(30).get();
        validNews = snapshot.docs.map(d => d.data()).filter(d => d.rejected !== true);
      }

      return { articles: validNews.slice(0, 3) };
    } catch (e) {
      logger.error("Failed to query scam_news", e);
      return { articles: [] };
    }
  }
);

function buildNewsQuery(region: string): string {
  const baseKeywords = '("sms scam" OR "whatsapp scam" OR "fake app" OR "android trojan" OR "scam call" OR "telegram scam" OR "smishing" OR "job scam" OR "love scam" OR "investment scam" OR "kk farm" OR "pig butchering" OR "impersonation scam") -"corporate" -"CEO" -"B2B" -"audit" -"lawsuit"';
  if (region === "ASIA") {
    return `${baseKeywords} (Malaysia OR Singapore OR Indonesia OR Thailand OR Philippines OR Asia OR sourcecountry:MY OR sourcecountry:SG)`;
  }
  return baseKeywords;
}
