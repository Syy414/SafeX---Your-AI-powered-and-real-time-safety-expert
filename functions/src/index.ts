import { onCall, HttpsError } from "firebase-functions/v2/https";
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
      // Keep these values aligned with Safe Browsing v4 docs examples.
      // If you later want to broaden, consult the "Safe Browsing Lists" page.
      threatTypes: ["MALWARE", "SOCIAL_ENGINEERING"],
      platformTypes: ["WINDOWS"],
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
    const language = String(data?.language ?? "unknown");
    const category = String(data?.category ?? "unknown");
    const tactics = Array.isArray(data?.tactics) ? data.tactics.map(String) : [];
    const snippet = String(data?.snippet ?? "").slice(0, 500); // already redacted on-device
    const extractedUrl = data?.extractedUrl ? String(data.extractedUrl).slice(0, 500) : null;

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
- Use simple language suitable for elders.
- If safeBrowsing indicates matches, include that in whyFlagged.
`;

    const userPrompt = {
      alertType,
      language,
      category,
      tactics,
      snippet,
      extractedUrl,
      safeBrowsing,
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

    if (!url) {
      throw new HttpsError("invalid-argument", "URL is required.");
    }

    try {
      const result = await safeBrowsingLookup([url], SAFE_BROWSING_API_KEY.value());
      const matches = result.matches ?? [];

      // If matches found, it's dangerous
      if (matches.length > 0) {
        return {
          safe: false,
          riskLevel: "HIGH",
          headline: "Dangerous URL Detected",
          reasons: ["Google Safe Browsing identified this URL as a known threat."],
          matches: matches
        };
      } else {
        return {
          safe: true,
          riskLevel: "LOW",
          headline: "No threats found",
          reasons: ["Google Safe Browsing found no known issues."]
        };
      }
    } catch (e: any) {
      logger.error("checkLink failed", e);
      // Fail open (don't block user if API fails, but warn)
      return {
        safe: true,
        riskLevel: "UNKNOWN",
        headline: "Could not verify URL",
        reasons: ["Safe Browsing check failed (network/server error)."]
      };
    }
  }
);

// ---- Callable: getScamNewsDigest ----
// Fetches top scam news, summarizes and translates them to user's language.
// Uses Firestore cache to avoid repetitive GDELT/Gemini calls.
export const getScamNewsDigest = onCall(
  {
    secrets: [],
    cors: true,
    timeoutSeconds: 60,
    memory: "512MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Auth required.");
    }

    const data = request.data as any;
    const region = String(data?.region ?? "GLOBAL");
    const targetLanguage = String(data?.language ?? "en"); // default English

    const db = admin.firestore();
    const cacheColl = db.collection("scam_articles");

    // 1. Check Cache (valid for 24 hours)
    const now = Date.now();
    const cutoff = now - (24 * 60 * 60 * 1000);

    // Composite index might be needed: region ASC, language ASC, createdAt DESC
    // If index missing, it will throw. We'll try user's query.
    try {
      const snapshot = await cacheColl
        .where("region", "==", region)
        .where("language", "==", targetLanguage)
        .where("createdAt", ">", cutoff)
        .orderBy("createdAt", "desc")
        .limit(10)
        .get();

      if (!snapshot.empty && snapshot.size >= 3) {
        // Cache hit! Return these.
        const cachedArticles = snapshot.docs.map(d => d.data());
        logger.info(`Serving ${snapshot.size} cached articles for ${region}/${targetLanguage}`);
        return { articles: cachedArticles };
      }
    } catch (e) {
      logger.warn("Cache query failed (missing index?)", e);
      // Continue to fetch fresh
    }

    const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
    if (!project) throw new HttpsError("internal", "Missing project id.");

    // 2. Fetch from GDELT
    const query = buildGdeltQuery(region);
    const gdeltUrl = `https://api.gdeltproject.org/api/v2/doc/doc?query=${encodeURIComponent(query)}&mode=ArtList&format=json&maxrecords=5&sort=DateDesc&timespan=24h`;

    let articles: any[] = [];
    try {
      const res = await fetch(gdeltUrl);
      if (res.ok) {
        const json = (await res.json()) as any;
        articles = json.articles || [];
      }
    } catch (e) {
      logger.error("GDELT fetch failed", e);
      return { articles: [] };
    }

    if (articles.length === 0) return { articles: [] };

    // 3. Process with Gemini
    const ai = new GoogleGenAI({
      vertexai: true,
      project,
      location: VERTEX_LOCATION.value(),
    });

    const processedArticles = await Promise.all(articles.map(async (article: any) => {
      const title = article.title;
      const url = article.url;
      const seenDate = article.seendate; // GDELT format
      const domain = article.domain;
      const imageUrl = article.socialimage || "";

      // Deduplication check: if we already have this URL for this lang in cache (even if old?), skip?
      // For now, simple.

      const prompt = `
        Task: 
        1. Summarize this news article title into a helpful 2-sentence anti-scam warning.
        2. Translate the title and the summary to: ${targetLanguage}.
        
        Input:
        Title: ${title}
        Source: ${domain}
        
        Output JSON:
        {
          "translatedTitle": "string",
          "summary": "string"
        }
      `;

      try {
        const resp = await ai.models.generateContent({
          model: GEMINI_MODEL.value(),
          contents: [{ role: "user", parts: [{ text: prompt }] }],
          config: { responseMimeType: "application/json" }
        });

        const jsonText = resp.text ?? "{}";
        const result = JSON.parse(jsonText);

        const finalObj = {
          url,
          title: result.translatedTitle || title,
          summary: result.summary || "No summary available.",
          domain,
          seenDate,
          imageUrl,
          region,
          language: targetLanguage,
          createdAt: Date.now() // store simple millis
        };

        // 4. Save to Cache
        // Use URL hash as ID to avoid dupes? Or just auto-id. 
        // Simple hash: base64 of url
        const id = Buffer.from(url).toString('base64').replace(/\//g, '_');
        // We accept that we create a new doc per language/region combination for the same URL 
        // (since summary is translated).
        // ID format: REGION_LANG_URLHASH
        const docId = `${region}_${targetLanguage}_${id}`.slice(0, 250);

        await cacheColl.doc(docId).set(finalObj);

        return finalObj;

      } catch (e) {
        logger.warn(`AI processing failed for ${url}`, e);
        return null;
      }
    }));

    const valid = processedArticles.filter(Boolean);
    return { articles: valid };
  }
);

function buildGdeltQuery(region: string): string {
  const baseKeywords = '(scam OR fraud OR phishing OR "online scam" OR "financial crime")';
  if (region === "ASIA") {
    return `${baseKeywords} (Malaysia OR Singapore OR Indonesia OR Thailand OR Philippines OR Vietnam OR "Hong Kong" OR Taiwan OR Asia OR sourcecountry:MY OR sourcecountry:SG)`;
  }
  return baseKeywords;
}
