# SafeX — Setup Guide (Manual steps BEFORE Antigravity “vibecoding”)

This guide is written for a **beginner** and is intentionally **step-by-step**.  
Follow it in order. If you skip anything, you’ll likely get build/deploy errors later.

> Scope: Android (Kotlin + Jetpack Compose) app + Firebase backend + ML Kit + on-device TFLite + Gemini via Vertex AI (through Cloud Functions).  
> Product scope: **4 tabs only** — Home, Alerts, Insights, Settings.

---

## 0) What you’re building (so your setup matches the design)

**Core behavior (your latest decisions):**
- SafeX detects **high-risk scam intent** from:
  1) **Notification previews** (Guardian mode, if Notification Access enabled)
  2) **New images in Gallery** (Guardian mode, if Gallery Access enabled)
- When a threat is detected, SafeX **immediately posts a system notification**.
- When the user taps that notification, SafeX opens the **Alerts tab**, showing the alert details.
- **No “hard link blocking” / no intercept-on-tap** in MVP.  
  If a message/image contains a link, SafeX warns and tells the user to **test the link manually** in the **Scan** feature from **Home**.

**Gemini usage (minimize calls):**
- Gemini is used for **clear explanation + advice** in the **Alert detail screen**.
- To minimize cost, the app **should call Gemini only when the user opens an alert** (lazy load), not at detection time.

**Insights data (privacy-first):**
- Insights are updated **only when user taps “Report” on an alert**.
- Reports store **only aggregated counts and masked patterns** (never raw chats / full URLs).

---

## 1) Accounts & prerequisites (do this first)

### 1.1 You need these accounts
1. **Google account** (for Firebase + Google Cloud)
2. **Firebase Console access** (same Google account)
3. **Google Cloud billing enabled** on the project (required for Cloud Functions deployments and Vertex AI usage)

> Cloud Functions setup/deploy guidance is in Firebase docs. See “Cloud Functions for Firebase” overview.  
> (You will be prompted to upgrade to Blaze / pay-as-you-go when deploying functions.)  

---

## 2) Install everything on your computer

### 2.1 Install Android Studio
1. Download and install **Android Studio** (latest stable).
2. Open Android Studio → let it install any suggested Android SDK components.

### 2.2 Install Node.js (for Firebase Functions)
You will use Cloud Functions + the Google Gen AI SDK. Use **Node.js 20+**.

1. Install **Node.js 20 LTS**.
2. Verify in Terminal:
```bash
node -v
npm -v
```

### 2.3 Install Firebase CLI
1. Install globally:
```bash
npm install -g firebase-tools
```
2. Verify:
```bash
firebase --version
```

### 2.4 Install Git (recommended)
You’ll want version control so Antigravity + agents can work cleanly.

---

## 3) Create your Android project (Jetpack Compose)

### 3.1 Create project
1. Open Android Studio
2. Click **New Project**
3. Choose **Empty Activity (Jetpack Compose / Material 3)** template
4. Set:
   - Name: `SafeX`
   - Package name (Application ID): **pick now and do not change later**  
     Example (recommended): `com.safex.app`
   - Minimum SDK: recommend **API 26** (Android 8.0) or higher (simplifies modern libs)

5. Click **Finish**
6. Wait for Gradle sync to finish.

> Why package name matters: Firebase app registration requires it, and it can’t be changed afterward.  

---

## 4) Create Firebase project & connect Android app

> This section follows the official “Add Firebase to your Android project” workflow (Firebase Console).  

### 4.1 Create Firebase Project
1. Go to Firebase Console
2. Click **Add project**
3. Enter project name: `SafeX`
4. Continue → choose Google Analytics (optional; you can skip for hackathon MVP)
5. Click **Create project**

### 4.2 Add Android app to Firebase
1. In Firebase project Overview page, click the **Android icon**
2. Enter:
   - Android package name: **exactly your app’s package** (e.g., `com.safex.app`)
   - App nickname: `SafeX Android` (optional)
3. Click **Register app**
4. Download **google-services.json**
5. Copy it into:
   - `SafeX/app/google-services.json`  
     (It must be inside the **app module**, not the project root.)

### 4.3 Add Firebase Gradle plugins (Android Studio)
Follow Firebase’s steps (Kotlin DSL version shown in docs). In short:

1. Open `build.gradle.kts` (Project-level)
2. Ensure Google Services plugin is declared:
```kotlin
plugins {
  // ...
  id("com.google.gms.google-services") version "4.4.4" apply false
}
```

3. Open `app/build.gradle.kts` (Module-level)
4. Apply plugin:
```kotlin
plugins {
  id("com.android.application")
  kotlin("android")
  id("com.google.gms.google-services")
}
```

5. Add Firebase BoM + libraries you will use:
```kotlin
dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.8.0"))

  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  implementation("com.google.firebase:firebase-functions")
}
```

6. Click **Sync Now** in Android Studio.

> Firebase BoM is updated regularly; the version above matches recent release notes.  
> If Gradle complains, keep the BoM but let Android Studio suggest the newest compatible one.

---

## 5) Enable Firebase products (Console switches)

### 5.1 Enable Authentication (Anonymous)
1. Firebase Console → **Build → Authentication**
2. Click **Get started**
3. Go to **Sign-in method**
4. Enable **Anonymous**
5. Click **Save**

Why: anonymous auth gives every device an identity token (no signup UI), and callable functions automatically attach auth tokens.

### 5.2 Enable Cloud Firestore
1. Firebase Console → **Build → Firestore Database**
2. Click **Create database**
3. Choose location:
   - Recommended: **asia-southeast1** (Singapore) for Malaysia proximity
4. Choose **Start in test mode** (for initial dev only)
5. Click **Enable**

### 5.3 Enable Cloud Functions
1. Firebase Console → **Build → Functions**
2. Click **Get started**
3. If prompted, upgrade to **Blaze** (pay-as-you-go). (Required to deploy.)

---

## 6) Enable Google Cloud APIs you need (GCP console)

Your Firebase project has an underlying Google Cloud project.

### 6.1 Open the Google Cloud project
1. Firebase Console → **Project settings (gear icon)**  
2. Under “Your project”, click the **Google Cloud Platform (GCP) Project** link

### 6.2 Enable Vertex AI API (Gemini via Vertex)
1. In Google Cloud Console, search: **Vertex AI API**
2. Click it
3. Click **Enable**

### 6.3 Enable Safe Browsing API
1. In Google Cloud Console, search: **Safe Browsing API**
2. Click it
3. Click **Enable**

---

## 7) Create API key for Safe Browsing (and lock it down)

Safe Browsing uses an API key for `threatMatches.find`.

### 7.1 Create API key
1. Google Cloud Console → **APIs & Services → Credentials**
2. Click **Create Credentials → API key**
3. Copy the key (you’ll store it as a **secret** in Cloud Functions)

### 7.2 Restrict the key (IMPORTANT)
Still in “Credentials”:
1. Click your newly created API key
2. Under **API restrictions**:
   - Select “Restrict key”
   - Choose **Safe Browsing API**
3. Under **Application restrictions** (recommended):
   - If you will ONLY call Safe Browsing from Cloud Functions:
     - Choose **None** or **IP addresses** (IP is hard for serverless)
     - In hackathon MVP, “None” + API restriction is acceptable, but treat key as secret.

---

## 8) Set up Firebase Cloud Functions (TypeScript + callable)

### 8.1 Initialize Functions in your repo
In a Terminal, inside your Android project folder (same level as `app/`):

1. Login:
```bash
firebase login
```

2. Initialize:
```bash
firebase init
```

3. Use arrow keys + spacebar to select:
   - **Functions**
   - **Firestore** (optional if you also want rules scaffolding)
4. Select your Firebase project (`SafeX`)
5. When asked language: choose **TypeScript**
6. When asked: “Use ESLint?” choose Yes (recommended)
7. When asked: “Install dependencies now?” choose Yes

You should now have a `/functions` folder.

### 8.2 Set Node runtime to 20
Open `functions/package.json` and ensure:
```json
"engines": {
  "node": "20"
}
```

### 8.3 Add Google Gen AI SDK

### 8.3.1 Ensure Firebase Functions dependencies are installed
Inside `functions/`, you should have `firebase-functions` and `firebase-admin` installed.  
To be safe (and avoid weird import/runtime issues), run:

```bash
cd functions
npm install firebase-functions firebase-admin
```


In `functions/`:
```bash
cd functions
npm install @google/genai
```

---

## 9) Store secrets & config for functions (NO hardcoding keys)

Firebase recommends parameterized config + Secret Manager. Avoid deprecated `functions.config()`.

### 9.1 Store Safe Browsing API key as a secret
From project root:
```bash
firebase functions:secrets:set SAFE_BROWSING_API_KEY
```
Paste your API key when prompted, press Enter.

### 9.2 (Optional) Store client metadata in non-secret params
In code you can define:
- `CLIENT_ID` = "safex"
- `CLIENT_VERSION` = app version

These are not sensitive.

---

## 10) Write Cloud Functions (2 callable functions)

You will deploy:
1. `explainAlert` — Gemini explanation + advice for an alert (called when user opens alert)
2. `reportAlert` — increments aggregated counters in Firestore for Insights

### 10.1 Create `functions/src/index.ts`
Replace its content with the following (copy-paste):

```ts
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

JSON schema:
{
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
        { role: "user", parts: [{ text: `Analyze this alert payload:\n${JSON.stringify(userPrompt)}` }] },
      ],
      systemInstruction: system,
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
```

### 10.2 Deploy functions
From project root:
```bash
firebase deploy --only functions
```

If deployment fails:
- Ensure billing is enabled (Blaze)
- Ensure Vertex AI API is enabled
- Ensure your Node runtime is supported
- Check logs:
```bash
firebase functions:log
```

---

## 11) Firestore data model for Insights

Create ONE collection:
- `insightsWeekly/{weekId}`

Example document fields:
```json
{
  "updatedAt": "...",
  "totalReports": 120,
  "categories": {
    "investment": 50,
    "impersonation": 30
  },
  "tactics": {
    "urgency": 80,
    "paymentPressure": 40
  },
  "domainPatterns": {
    "xxxxxx-xxxx.xxx": 12
  }
}
```

---

## 12) Firestore Security Rules (MVP-safe)

For hackathon, do NOT allow clients to write directly.  
Only callable functions update aggregates.

Rules idea:
- Allow read to all
- Deny writes from client

Example `firestore.rules`:
```
// Firestore rules (MVP)
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /insightsWeekly/{doc} {
      allow read: if true;
      allow write: if false; // only Cloud Functions (Admin SDK) can write
    }
  }
}
```

Deploy rules:
```bash
firebase deploy --only firestore:rules
```

---

## 13) Android permissions & services you must implement (high-level checklist)

**Required for local notifications:**
- Android 13+ request runtime permission: `POST_NOTIFICATIONS`

**If Guardian Mode + Notification scanning enabled:**
- Implement `NotificationListenerService`
- User must enable Notification Access in system settings

**If Guardian Mode + Gallery scanning enabled:**
- Android 13+ request `READ_MEDIA_IMAGES`
- Run a background Worker (WorkManager) to scan new images

**Manual Scan (Home tab) can avoid permissions:**
- Use Android Photo Picker for gallery selection (no `READ_MEDIA_IMAGES` needed)
- Use CameraX for camera scan (requires camera permission)

---

## 14) Add ML Kit dependencies (Android)

You will use:
- Text recognition (OCR)
- Barcode scanning (QR)
- Language ID
- Translation (on-device)

You add these dependencies in `app/build.gradle.kts`.

Example:
```kotlin
dependencies {
  // ML Kit (modern standalone libraries)
  implementation("com.google.mlkit:text-recognition:16.0.0")
  implementation("com.google.mlkit:barcode-scanning:17.2.0")
  implementation("com.google.mlkit:language-id:17.0.5")
  implementation("com.google.mlkit:translate:17.0.3")
}
```

> ML Kit versions change; if Gradle fails, use Android Studio’s suggested “latest compatible” versions.

---

## 14A) Add in-app language selection (English / Malay / Chinese) — step-by-step

This is required for your new requirement: **Settings → Language**, and also **first-run onboarding language choice**.

You will do 3 things:
1) Add translated string resources (`values-ms`, `values-zh`)
2) Enable per-app language support (Android 13+) via Gradle `generateLocaleConfig`
3) Implement a language picker that calls `AppCompatDelegate.setApplicationLocales(...)`

### 14A.1 — Add the translated resource folders
In Android Studio:
1. In the **Project** pane, switch to **Android** view.
2. Expand: `app > src > main > res`
3. Right click `res` → **New** → **Android Resource Directory**
4. In “Resource type”, pick **values**
5. In “Available qualifiers”, choose **Locale**
6. For Malay:
   - Language: `ms`
   - Region: leave blank
   - Click **OK**
7. Repeat for Chinese:
   - Language: `zh`
   - Region: leave blank (you can later change to `zh-rCN` or `zh-rTW`)

Now you should see:
- `res/values/strings.xml` (English default)
- `res/values-ms/strings.xml` (Malay)
- `res/values-zh/strings.xml` (Chinese)

### 14A.2 — Put your UI strings into strings.xml (so they can translate)
Open `res/values/strings.xml` and ensure ALL user-facing words are here:
- tab labels: Home / Alerts / Insights / Settings
- buttons: Scan, Report, Mark safe, Refresh, etc
- onboarding: Choose language, Choose mode
- disclaimers

(If you see hardcoded text inside Compose like `"Scan"`, replace it with `stringResource(R.string.scan)`.)

### 14A.3 — Add Malay + Chinese translations
Open `res/values-ms/strings.xml` and paste translations.
Open `res/values-zh/strings.xml` and paste translations.

**Starter translations (you can refine later):**
Malay (`values-ms/strings.xml`):
```xml
<resources>
  <string name="app_name">SafeX</string>
  <string name="tab_home">Laman Utama</string>
  <string name="tab_alerts">Amaran</string>
  <string name="tab_insights">Wawasan</string>
  <string name="tab_settings">Tetapan</string>

  <string name="scan">Imbas</string>
  <string name="paste_link">Tampal pautan</string>
  <string name="choose_image">Pilih imej</string>
  <string name="scan_qr">Imbas QR</string>

  <string name="report">Lapor</string>
  <string name="mark_safe">Tandakan Selamat</string>

  <string name="language">Bahasa</string>
  <string name="language_english">English</string>
  <string name="language_malay">Bahasa Melayu</string>
  <string name="language_chinese">中文</string>

  <string name="mode">Mod</string>
  <string name="mode_guardian">Penjaga</string>
  <string name="mode_companion">Teman</string>

  <string name="news_title">Berita Scam</string>
  <string name="news_disclaimer">Pautan berita dibuka dalam pelayar. Jangan masukkan kata laluan/OTP.</string>
  <string name="news_malaysia">Malaysia</string>
  <string name="news_global">Global</string>
  <string name="refresh">Muat Semula</string>
</resources>
```

Chinese (`values-zh/strings.xml`, Simplified):
```xml
<resources>
  <string name="app_name">SafeX</string>
  <string name="tab_home">首页</string>
  <string name="tab_alerts">警报</string>
  <string name="tab_insights">洞察</string>
  <string name="tab_settings">设置</string>

  <string name="scan">扫描</string>
  <string name="paste_link">粘贴链接</string>
  <string name="choose_image">选择图片</string>
  <string name="scan_qr">扫描二维码</string>

  <string name="report">举报</string>
  <string name="mark_safe">标记为安全</string>

  <string name="language">语言</string>
  <string name="language_english">English</string>
  <string name="language_malay">Bahasa Melayu</string>
  <string name="language_chinese">中文</string>

  <string name="mode">模式</string>
  <string name="mode_guardian">守护</string>
  <string name="mode_companion">陪伴</string>

  <string name="news_title">诈骗新闻</string>
  <string name="news_disclaimer">新闻链接将在浏览器中打开。请勿输入密码/OTP。</string>
  <string name="news_malaysia">马来西亚</string>
  <string name="news_global">全球</string>
  <string name="refresh">刷新</string>
</resources>
```

### 14A.4 — Enable per-app language support (Android 13+)
1) Open `app/build.gradle.kts`
2) Inside the `android { ... }` block, add:
```kotlin
androidResources {
  generateLocaleConfig = true
}
```

3) Create file: `app/src/main/res/resources.properties`
Add:
```
unqualifiedResLocale=en
```

### 14A.5 — Implement the in-app language picker (Settings + onboarding)
When user selects a language:
- Save language tag (`en`, `ms`, `zh`) in DataStore
- Call:
```kotlin
AppCompatDelegate.setApplicationLocales(
  LocaleListCompat.forLanguageTags(languageTag)
)
```

### 14A.6 — Also ask for language at first run
Onboarding:
1) Language (EN/MS/ZH)
2) Mode (Guardian/Companion)
Save both to DataStore.

## 15) Train + export a TFLite text classifier (Colab) — step-by-step

Goal: a tiny on-device “triage model” to score scam likelihood.

### 15.1 Create a new Colab notebook
1. Open Google Colab
2. Click **File → New notebook**
3. Click **Runtime → Change runtime type**
   - Runtime type: Python 3
   - Hardware accelerator: None (OK) or GPU (optional)
4. Click **Save**

### 15.2 Paste this FULL code into Colab (cell 1) and run
```python
# Install LiteRT / TFLite Model Maker for Text Classification
!pip -q install --upgrade tflite-model-maker
!pip -q install --upgrade ucimlrepo pandas

import pandas as pd
from ucimlrepo import fetch_ucirepo

# UCI SMS Spam Collection (classic spam dataset) as a starter triage model
# Dataset page: "SMS Spam Collection" (UCI Machine Learning Repository)
sms_spam = fetch_ucirepo(id=228)
df = sms_spam.data.original.copy()

# Inspect columns (usually "v1" label and "v2" text, depending on loader)
print(df.head())
print(df.columns)

# Try to normalize expected columns
# Many versions use columns: ['v1','v2'] where v1 is 'spam'/'ham', v2 is message text
label_col = df.columns[0]
text_col = df.columns[1]

df = df[[label_col, text_col]].rename(columns={label_col: "label", text_col: "text"})
df["label"] = df["label"].astype(str).str.lower().map({"ham": 0, "spam": 1}).fillna(0).astype(int)
df["text"] = df["text"].astype(str)

# Shuffle
df = df.sample(frac=1.0, random_state=42).reset_index(drop=True)

# Train / test split
split = int(len(df) * 0.8)
train_df = df.iloc[:split].copy()
test_df = df.iloc[split:].copy()

print("Train size:", len(train_df), "Test size:", len(test_df))
print(train_df["label"].value_counts())

# Save to CSV for Model Maker
train_path = "train.csv"
test_path = "test.csv"
train_df.to_csv(train_path, index=False)
test_df.to_csv(test_path, index=False)

print("Saved:", train_path, test_path)
```

### 15.3 Paste this FULL code into Colab (cell 2) and run
```python
from tflite_model_maker import model_spec
from tflite_model_maker import text_classifier
from tflite_model_maker.text_classifier import DataLoader

# Load CSVs
train_data = DataLoader.from_csv(
    filename="train.csv",
    text_column="text",
    label_column="label",
    label_names=["ham", "spam"],  # label 0/1 mapping
)

test_data = DataLoader.from_csv(
    filename="test.csv",
    text_column="text",
    label_column="label",
    label_names=["ham", "spam"],
)

# Choose a lightweight spec (average word vector)
spec = model_spec.get("average_word_vec")

# Train
model = text_classifier.create(
    train_data,
    model_spec=spec,
    epochs=5,       # increase later if you want
    batch_size=32,
)

# Evaluate
loss, acc = model.evaluate(test_data)
print("Test loss:", loss, "Test acc:", acc)

# Export
model.export(export_dir="export")
print("Exported to ./export")
```

### 15.4 Download the model from Colab
After running, in the left sidebar:
1. Click **Files**
2. Open folder `export/`
3. You should see a `.tflite` model (name depends on library)
4. Right-click → **Download**

Rename it on your computer to:
- `scam_triage.tflite`

---

## 16) Add TFLite model to Android app

1. In Android Studio, create folder:
   - `app/src/main/assets/`
2. Put `scam_triage.tflite` inside:
   - `app/src/main/assets/scam_triage.tflite`

### 16.1 Add TFLite Task Library dependency
Use latest stable (example version from Maven listings):
```kotlin
dependencies {
  implementation("org.tensorflow:tensorflow-lite-task-text:0.4.4")
}
```

---

## 17) Local-only alert storage (Room) — why you need it

You want:
- Alerts tab = history of detections user hasn’t reviewed
- Auto delete after review

So you need a local DB:
- **Room** (recommended)

Data table: `AlertEntity`
- id (UUID)
- createdAt
- sourceType: NOTIFICATION | IMAGE | MANUAL_SCAN
- riskLevel: LOW|MEDIUM|HIGH
- category
- tactics (list as JSON string)
- snippetRedacted
- extractedUrl (optional)
- reviewed: boolean

---

## 17A) Add Insights News feed (GDELT) — step-by-step (NO scraping)

Use **GDELT DOC 2.0 API** as a public news index (no API key needed).

### 17A.1 — Add Internet permission
`app/src/main/AndroidManifest.xml` (above `<application>`):
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 17A.2 — Add networking + JSON parsing dependencies
`app/build.gradle.kts`

Plugin:
```kotlin
plugins {
  id("org.jetbrains.kotlin.plugin.serialization") version "<YOUR_KOTLIN_VERSION>"
}
```

Dependencies:
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
implementation("io.coil-kt:coil-compose:2.6.0")
```

Sync:
Android Studio → File → Sync Project with Gradle Files

### 17A.3 — DTOs: GDELT JSONFeed
Create `news/GdeltJsonFeed.kt` and paste:
```kotlin
@Serializable
data class GdeltJsonFeed(
  val title: String? = null,
  @SerialName("home_page_url") val homePageUrl: String? = null,
  @SerialName("feed_url") val feedUrl: String? = null,
  val items: List<GdeltItem> = emptyList()
)

@Serializable
data class GdeltItem(
  val id: String,
  val url: String,
  val title: String,
  val image: String? = null,
  @SerialName("banner_image") val bannerImage: String? = null,
  @SerialName("content_text") val contentText: String? = null
)
```

### 17A.4 — NewsRepository (Malaysia + Global)
Create file:
`app/src/main/java/<your_package>/news/NewsRepository.kt`

Paste:
```kotlin
package <your_package>.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class NewsRepository(
  private val http: OkHttpClient = OkHttpClient(),
  private val json: Json = Json { ignoreUnknownKeys = true }
) {
  suspend fun fetchMalaysiaScamNews(): List<GdeltItem> =
    fetch(query = buildMalaysiaQuery())

  suspend fun fetchGlobalScamNews(): List<GdeltItem> =
    fetch(query = buildGlobalQuery())

  private fun buildMalaysiaQuery(): String =
    "(scam OR phishing OR fraud OR \"online scam\" OR \"investment scam\" OR \"love scam\") sourcecountry:malaysia"

  private fun buildGlobalQuery(): String =
    "(scam OR phishing OR fraud OR \"online scam\" OR \"investment scam\" OR \"love scam\")"

  private suspend fun fetch(query: String): List<GdeltItem> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = "https://api.gdeltproject.org/api/v2/doc/doc" +
      "?mode=ArtList" +
      "&format=jsonfeed" +
      "&maxrecords=25" +
      "&sort=datedesc" +
      "&timespan=7d" +
      "&query=$encoded"

    val req = Request.Builder().url(url).get().build()
    http.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return@withContext emptyList()
      val body = resp.body?.string() ?: return@withContext emptyList()
      val feed = json.decodeFromString(GdeltJsonFeed.serializer(), body)
      feed.items
        .filter { it.title.isNotBlank() && it.url.startsWith("http") }
        .distinctBy { it.url }
    }
  }
}
```


### 17A.5 — Cache + UI
- Cache results locally with TTL
- Two chips: Malaysia / Global
- Pull-to-refresh
- Open in browser / custom tabs
- Optional: translate headlines using ML Kit Translation

## 18) Antigravity pre-setup (so agents don’t hallucinate)

Before you ask agents to code, create a repo structure that matches your PRD.

### 18.1 Create these files at repo root
- `README.md`
- `productrequirementdocument.md`
- `agents.md`

(You will generate them from this ChatGPT response.)

### 18.2 Create a “truth folder” for prompts
- `docs/antigravity_agent_prompts.md`

So Antigravity agents always have the same instructions.

### 18.3 Agent workflow rule (critical)
Any agent that depends on cloud setup must:
1. STOP
2. Ask you to confirm:
   - Firebase project exists
   - google-services.json is added
   - Functions deployed successfully
   - Safe Browsing secret set
   - Vertex AI API enabled
3. Only then write code that calls the backend

---

## 19) Minimal “demo-ready” checklist (what must work for KitaHack demo)

✅ App opens; 4 tabs visible  
✅ Guardian mode: notification access toggle UI works (opens system settings)  
✅ A fake scam notification triggers local SafeX notification → tap opens alert detail  
✅ Alert detail calls Gemini (Cloud Function) and shows “Why flagged / What to do”  
✅ User can hit Report → Insights numbers update  
✅ Home Scan: paste URL → Safe Browsing check → show result  

---

## Useful links (copy/paste in browser)

```text
Firebase Android setup:
https://firebase.google.com/docs/android/setup

Callable functions (Android):
https://firebase.google.com/docs/functions/callable

Cloud Functions environment config + secrets:
https://firebase.google.com/docs/functions/config-env

Google Gen AI SDK (Vertex AI):
https://docs.cloud.google.com/vertex-ai/generative-ai/docs/sdks/overview

Safe Browsing Lookup API (v4):
https://developers.google.com/safe-browsing/v4/lookup-api

ML Kit Text Recognition v2:
https://developers.google.com/ml-kit/vision/text-recognition/v2/android
```
