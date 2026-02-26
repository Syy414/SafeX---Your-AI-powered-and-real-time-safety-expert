<p align="center">
  <img src="app/src/main/safeX_app_icon.png" width="120" alt="SafeX Logo"/>
</p>

<h1 align="center">SafeX — Your AI-Powered Real-Time Safety Expert</h1>

<p align="center">
  <b>KitaHack 2026 Submission</b><br/>
  An Android app that detects scam intent early, warns users instantly, and explains risks clearly — powered by Google's AI ecosystem.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Gemini_2.5_Flash-Vertex_AI-4285F4?logo=google&logoColor=white" alt="Gemini"/>
  <img src="https://img.shields.io/badge/Firebase-Cloud_Functions-FFCA28?logo=firebase&logoColor=black" alt="Firebase"/>
  <img src="https://img.shields.io/badge/ML_Kit-On--Device_AI-4285F4?logo=google&logoColor=white" alt="ML Kit"/>
  <img src="https://img.shields.io/badge/TFLite-Char--CNN-FF6F00?logo=tensorflow&logoColor=white" alt="TFLite"/>
</p>

---

## 👥 Team — KMPians

| Name | Role |
|------|------|
| **Ooi Fu Chuin** | Lead Developer & AI Integration |
| **Ng Hong Jon** | Developer |
| **Tan Sze Yung** | Developer |
| **Lai Joe Ming** | Developer |

---

## 📋 Table of Contents

1. [Project Overview](#-project-overview)
2. [Key Features](#-key-features)
3. [Google Technologies Used](#-google-technologies-used)
4. [Implementation Details & Innovation](#-implementation-details--innovation)
5. [Challenges Faced](#-challenges-faced)
6. [Installation & Setup](#-installation--setup)
7. [Future Roadmap](#-future-roadmap)

---

## 🔍 Project Overview

### Problem Statement

Scam losses in Malaysia reached **RM3.18 billion** between 2021–2023, with over **95,800 cases** reported in 2023 alone. Globally, consumers lost over **$10 billion** to fraud in 2023. Scammers don't succeed because victims are careless — they succeed because they exploit fundamental human psychology:

- **Urgency** — "Act now or your account will be locked"
- **Authority** — Impersonating banks, police, or delivery companies
- **Fear & Shame** — "Your account is compromised"
- **Greed** — "Guaranteed 300% investment returns"
- **Isolation** — "Don't tell anyone about this"

By the time a victim realizes they are being scammed, they are already emotionally controlled. **There is no tool that intervenes at the moment of manipulation** — before the victim clicks, transfers money, or shares credentials.

### Our Solution

**SafeX** is an AI-powered Android companion app that intercepts scam attempts **at the point of emotional manipulation** — before the user acts. It runs silently in the background, monitors incoming notifications and gallery images for scam indicators, and delivers **instant, calm, easy-to-understand warnings** with clear next steps.

Unlike traditional anti-virus or SMS blockers that rely on known blacklists, SafeX uses a **hybrid on-device + cloud AI pipeline** to detect scam *intent* — the psychological manipulation patterns that are common across all scam types, regardless of language (English, Malay, Chinese).

### SDG Alignment

| SDG | Target | How SafeX Contributes |
|-----|--------|----------------------|
| **SDG 16 — Peace, Justice & Strong Institutions** | **16.4** — Significantly reduce illicit financial flows | SafeX directly combats financial fraud by detecting and preventing scam attempts before money is lost, reducing the flow of funds to criminal scam syndicates. |
| **SDG 16** | **16.a** — Strengthen relevant national institutions for preventing crime | SafeX's scam news awareness feed educates users on emerging scam patterns, building a more informed and resilient society against fraud. |
| **SDG 10 — Reduced Inequalities** | **10.2** — Empower and promote social inclusion | SafeX specifically protects vulnerable populations — elders, non-tech-savvy users, and non-English speakers — through multi-language support (EN/MS/ZH), elder-friendly language, and proactive Guardian mode that requires zero user effort. |

---

## ✨ Key Features

### 1. 🛡️ Guardian Mode — Real-Time Background Protection

SafeX can run in **Guardian mode**, silently monitoring incoming content without any user effort:

- **Notification Monitoring** — Uses Android's `NotificationListenerService` to read notification previews from WhatsApp, SMS, Telegram, and any messaging app. When a notification arrives, SafeX extracts the preview text and runs it through the **hybrid triage pipeline** (heuristic rules + TFLite AI model). If flagged as high-risk, a **warning notification is posted immediately**.
- **Gallery Monitoring** — Uses `WorkManager` to periodically scan newly saved images (screenshots, forwarded images, QR code posters). Images are processed through **ML Kit OCR** (text extraction) and **ML Kit Barcode Scanning** (QR code detection). Extracted text and URLs are then run through the same triage pipeline.

### 2. 🔍 Manual Scan — User-Initiated Scanning

Users can manually scan suspicious content from the Home tab:

- **Paste Link** — Enter any URL and SafeX sends it to **Gemini 2.5 Flash** via the `checkLink` Cloud Function for deep analysis. Gemini examines domain structure, TLD suspiciousness, typosquatting patterns, brand impersonation, and known phishing patterns — then returns a structured risk assessment with reasons, recommended actions, and warnings. The backend also runs a **local heuristic check** (Levenshtein distance-based typosquatting detection against 30+ Malaysian and global brands) to give Gemini additional context.
- **Pick Image** — Select an image from the gallery. SafeX uses **ML Kit Text Recognition** (OCR) and **ML Kit Barcode Scanning** (QR) to extract text and URLs, then sends the extracted content directly to Gemini via the `explainAlert` Cloud Function for full AI analysis — bypassing on-device triage for maximum accuracy.
- **Camera Scan** — Real-time camera scanning using **CameraX**. Scan QR codes and printed text (posters, flyers, receipts) live. Extracted content is sent directly to Gemini via `explainAlert` for immediate analysis.

### 3. 🧠 AI-Powered Explanation (Gemini 2.5 Flash)

During Guardian detection, when the on-device combined score ≥ 0.30, the `HybridTriageEngine` immediately calls `explainAlert` via Firebase Cloud Functions, which sends the alert data to **Gemini 2.5 Flash** (accessed through the **Google Gen AI SDK** with Vertex AI backend). Gemini acts as the ultimate judge — it can override on-device scores if the text is clearly benign (e.g., legitimate OTPs, university announcements) or clearly malicious (well-disguised scams). The Gemini analysis is **cached in the alert**, so when the user opens the alert detail, the explanation loads instantly. If no cache exists (e.g., Gemini was offline), a fresh call is made. Gemini returns:

- **Risk Level** (HIGH / MEDIUM / LOW)
- **Scam Category** (Phishing, Investment Scam, Love Scam, Job Scam, Impersonation, etc.)
- **"Why SafeX flagged this"** — Clear bullet points explaining the manipulation tactics detected
- **"What you should do now"** — Actionable safety steps
- **"What NOT to do"** — Prevent the user from making common mistakes (e.g., "Do not share your OTP")
- **Confidence Score** — Transparency on how certain the AI is
- **Score Breakdown** — Shows the on-device heuristic (20%) and TFLite AI (80%) weighted contributions

All explanations use **calm, non-blaming, elder-friendly language**. If the network is unavailable, a **fallback generic safety advisory** is displayed — the app never crashes or shows an error.

### 4. 📰 Scam News Awareness Feed

The Insights tab features a **curated scam news feed** to increase user awareness:

- A **scheduled Cloud Function** (`periodicScamNewsScraper`) runs every hour, scraping **Google News RSS** for scam-related articles using targeted keywords (SMS scam, WhatsApp scam, fake app, smishing, job scam, love scam, investment scam, pig butchering, etc.)
- Each scraped article is sent to **Gemini 2.5 Flash** for intelligent filtering — Gemini **rejects** articles about corporate fraud, B2B issues, or non-mobile scams, and only **accepts** scams targeting individuals through phones, messaging apps, social media, or calls
- Accepted articles are **re-titled and summarized** by Gemini with a concise anti-scam summary and preventative tips, then stored in **Firestore** (`scam_news` collection)
- The Android app fetches these pre-processed articles via the `getScamNewsDigest` Cloud Function and caches them locally using **Room** for offline access
- Articles include Gemini-generated **warnings and tips** specific to each scam type
- Headlines can be **translated on-device** using ML Kit Translation when the user selects a non-English language

### 5. 🌐 Multi-Language Support (EN / MS / ZH)

SafeX supports **English, Bahasa Melayu, and Simplified Chinese** — the three most spoken languages in Malaysia:

- **Full UI localization** — All tab labels, buttons, instructions, and disclaimers are translated using Android's resource configuration (`values/`, `values-ms/`, `values-zh/`)
- **On-device content translation** — Gemini analysis results and news headlines are translated in real-time using **ML Kit Translation** (no network required after initial model download)
- **Language selection** — Users choose their language during first-run onboarding, and can change it anytime in Settings. The change applies immediately across the entire app via `AppCompatDelegate.setApplicationLocales()`

### 6. ⚙️ Flexible Operating Modes

- **Guardian Mode** (proactive) — Background monitoring of notifications and gallery. Designed for elders and vulnerable users who need protection with zero effort.
- **Companion Mode** (manual only) — No background monitoring. Users scan content manually when they want. Designed for privacy-conscious users.

---

## 🛠️ Google Technologies Used

### Core Google AI & Cloud

| Technology | How It's Used in SafeX |
|------------|----------------------|
| **Gemini 2.5 Flash** (via Google Gen AI SDK + Vertex AI backend) | The AI backbone of SafeX. Accessed through the `@google/genai` SDK with `vertexai: true`, authenticated via the Cloud Functions service account (no API key needed). Used in 3 Cloud Functions: (1) `explainAlert` — analyzes flagged messages and returns structured scam explanations with category, risk level, and actionable advice; (2) `checkLink` — performs deep URL phishing analysis with domain structure, typosquatting, and brand impersonation detection; (3) `periodicScamNewsScraper` — intelligently filters and summarizes scraped scam news articles, rejecting irrelevant content. |
| **Firebase Cloud Functions** (v2, TypeScript) | Hosts 5 serverless functions in `asia-southeast1`: `explainAlert`, `checkLink`, `getScamNewsDigest`, `periodicScamNewsScraper`, `backfillHistoricalScams`. All callable functions require Firebase Auth. Scheduled function runs hourly for news aggregation. |
| **Firebase Authentication** (Anonymous) | Every device gets an anonymous auth token automatically. This secures all callable Cloud Functions without requiring user signup — critical for reducing friction for elder users. |
| **Cloud Firestore** | Stores the `scam_news` collection — pre-processed, Gemini-verified scam news articles with titles, summaries, warnings/tips, source URLs, and timestamps. Indexed by `createdAt` for efficient retrieval. |

### On-Device Google AI (ML Kit)

| Technology | How It's Used in SafeX |
|------------|----------------------|
| **ML Kit Text Recognition** (OCR) | Extracts text from images during gallery scanning (Guardian mode) and manual image/camera scanning. Supports English and Chinese text recognition (`text-recognition` + `text-recognition-chinese`). |
| **ML Kit Barcode Scanning** | Detects and decodes QR codes from images and camera feed. Extracted URLs are either triaged (Guardian mode) or sent directly to Gemini (manual scan). |
| **ML Kit Language Identification** | Included as a dependency (`language-id`) for future use in language-specific heuristic selection. Currently, the heuristic engine applies all language patterns (EN/MS/ZH) simultaneously. |
| **ML Kit Translation** | Translates Gemini analysis results (headline, why flagged, what to do, what not to do), news headlines, and news summaries into the user's selected language entirely **on-device** — no network call needed after the initial model download. Supports EN ↔ MS ↔ ZH. |

### On-Device ML (TensorFlow Lite)

| Technology | How It's Used in SafeX |
|------------|----------------------|
| **TensorFlow Lite** (Interpreter) | Runs the custom **Char-CNN scam detection model** on-device. The model takes raw text as character-level integer IDs (`int32[1, 512]`) and outputs a scam probability (`float32[1, 1]`). Inference runs in < 50ms on mid-range devices. |
| **Custom Char-CNN Model** | A character-level convolutional neural network trained on EN/MS/ZH scam datasets (collected from Kaggle and curated sources). Trained in Google Colab, exported as `safex_charcnn_dynamic.tflite` (428KB). Uses a 5000-token character vocabulary with NFKC normalization, URL/number masking, and sequence padding to 512 characters. |

### Other Google Technologies

| Technology | How It's Used in SafeX |
|------------|----------------------|
| **Android Jetpack Compose** | Entire UI is built with Compose + Material 3. Modern, declarative UI with theme customization (SafeX Blue theme). |
| **Android CameraX** | Powers the real-time camera scan feature. Provides lifecycle-aware camera preview and image analysis pipeline for QR/OCR scanning. |
| **Android WorkManager** | Schedules gallery monitoring scans responsibly. Runs background image processing without draining battery. |
| **Android Room** | Local SQLite database for alert storage (`AlertEntity`) and news article caching (`NewsArticleEntity`). Provides offline-first experience — all alerts and cached news are available without internet. |
| **Android DataStore** (Preferences) | Persists user preferences: language selection, operating mode (Guardian/Companion), notification/gallery toggle states, onboarding completion status. |
| **AndroidX Navigation Compose** | Handles 4-tab bottom navigation (Home, Alerts, Insights, Settings) and screen routing (alert detail, scan screens, onboarding). |
| **Google News RSS** | Public news feed used as source for scam news aggregation. Queried by the scheduled Cloud Function with targeted scam keywords. |

---

## 🏗️ Implementation Details & Innovation

### System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                              │
│                                                                      │
│  ── GUARDIAN MODE (Background Detection) ──────────────────────────  │
│                                                                      │
│  ┌──────────────┐    ┌─────────────────────────────────────────┐     │
│  │ Notification  │    │     HYBRID TRIAGE ENGINE                │     │
│  │ Listener     │───▶│                                         │     │
│  │ Service       │    │  ┌────────────┐    ┌──────────────┐    │     │
│  └──────────────┘    │  │ Heuristic  │    │  TFLite      │    │     │
│                       │  │ Engine     │    │  Char-CNN    │    │     │
│  ┌──────────────┐    │  │ (20% wt)   │    │  (80% wt)    │    │     │
│  │ Gallery Scan │───▶│  └─────┬──────┘    └──────┬───────┘    │     │
│  │ Worker       │    │        └───────┬──────────┘            │     │
│  │ (ML Kit OCR  │    │         Combined Score                  │     │
│  │  + QR scan)  │    │               │                         │     │
│  └──────────────┘    │        ┌──────▼──────┐                  │     │
│                       │        │  ≥ 0.30?    │                  │     │
│                       │        └──────┬──────┘                  │     │
│                       │               │ YES                     │     │
│                       │        ┌──────▼──────────────────┐      │     │
│                       │        │ Level 3: Gemini 2.5     │      │     │
│                       │        │ Flash (explainAlert)    │      │     │
│                       │        │ → Final risk judgment   │      │     │
│                       │        │ → Explanation cached    │      │     │
│                       │        └──────┬──────────────────┘      │     │
│                       └──────────────┼─────────────────────────┘     │
│                               ┌──────▼──────┐                        │
│                               │ Create Alert│                        │
│                               │ + Warning   │                        │
│                               │ Notification│                        │
│                               └──────┬──────┘                        │
│                                      │                                │
│  ── MANUAL SCAN (User-Initiated) ────┼──────────────────────────     │
│                                      │                                │
│  ┌──────────────┐                    │                                │
│  │ Paste Link   │─── checkLink ──────┼──▶ Gemini (Cloud Function)    │
│  └──────────────┘    (direct)        │                                │
│  ┌──────────────┐                    │                                │
│  │ Pick Image   │─ ML Kit OCR/QR ─── explainAlert ──▶ Gemini (CF)   │
│  └──────────────┘   (bypass triage)  │                                │
│  ┌──────────────┐                    │                                │
│  │ Camera Scan  │─ CameraX+ML Kit ── explainAlert ──▶ Gemini (CF)   │
│  └──────────────┘   (bypass triage)  │                                │
│                                      ▼                                │
│                              ┌───────────────┐                        │
│  ┌────────────┐              │   Room DB      │                       │
│  │ ML Kit     │              │   (Alerts +    │                       │
│  │ Translation│◀────────────▶│   News Cache)  │                       │
│  └────────────┘              └───────────────┘                        │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │
                                       │  Firebase Callable Functions
                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      GOOGLE CLOUD BACKEND                            │
│                      (asia-southeast1)                               │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │ explainAlert     │  │ checkLink        │  │ getScamNews      │   │
│  │ Cloud Function   │  │ Cloud Function   │  │ Digest (CF)      │   │
│  │                  │  │                  │  │                  │   │
│  │ ┌──────────────┐│  │ ┌──────────────┐│  │ Reads from       │   │
│  │ │ Gemini 2.5   ││  │ │ Gemini 2.5   ││  │ Firestore        │   │
│  │ │ Flash        ││  │ │ Flash        ││  │                  │   │
│  │ │ (Vertex AI)  ││  │ │ + Heuristic  ││  │                  │   │
│  │ └──────────────┘│  │ │   checks     ││  │                  │   │
│  └──────────────────┘  │ └──────────────┘│  └──────────────────┘   │
│                         └──────────────────┘                         │
│  ┌──────────────────────────────────────────┐                        │
│  │ periodicScamNewsScraper (Hourly)          │                        │
│  │ Google News RSS → Gemini Filter/Summary → │                        │
│  │ Firestore (scam_news collection)          │                        │
│  └──────────────────────────────────────────┘                        │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐                         │
│  │ Firebase Auth    │  │ Cloud Firestore  │                         │
│  │ (Anonymous)      │  │ (scam_news)      │                         │
│  └──────────────────┘  └──────────────────┘                         │
└──────────────────────────────────────────────────────────────────────┘
```

### Detection Pipeline — How SafeX Catches Scams

SafeX uses **two distinct detection paths** depending on the context:

#### Path A: Guardian Mode (Background — Notification & Gallery Monitoring)

A **3-level hybrid pipeline** runs automatically when a notification arrives or a new gallery image is detected:

**Level 1 — Heuristic Rules Engine (20% weight)**
A pattern-matching engine that detects scam indicators across EN/MS/ZH:
- **Urgency + Money** compound signals (e.g., "segera" + "RM", "urgent" + "transfer")
- **Impersonation + Action** patterns (e.g., "Bank Negara" + "verify", "PDRM" + "warrant")
- **Suspicious URLs** — shortened domains, suspicious TLDs (`.xyz`, `.top`, `.club`, `.vip`)
- **Typosquatting detection** — Levenshtein distance matching against 30+ known brands (Maybank, CIMB, WhatsApp, Touch 'n Go, Shopee, Lazada, etc.)

**Level 2 — TFLite Char-CNN Model (80% weight)**
A custom character-level convolutional neural network trained on scam datasets:
- Input: Raw text → character-level integer IDs (vocab size: 5000, sequence length: 512)
- Preprocessing: NFKC normalization → URL masking (`<URL>`) → number masking (`<NUM>`)
- Output: Scam probability (0.0 to 1.0)
- Trained on EN/MS/ZH scam messages collected from Kaggle and curated sources
- Model size: 428KB — lightweight enough for any Android device

**Combined Scoring:**
```
Combined Score = (Heuristic Score × 0.20) + (TFLite Score × 0.80)
```
- Score **< 0.30** → No alert (low risk, user is not disturbed)
- Score **≥ 0.30** → Escalate to Level 3 (Gemini)
- Display label: ≥ 0.75 = HIGH | ≥ 0.40 = MEDIUM | < 0.40 = LOW

**Level 3 — Gemini 2.5 Flash (called inline, result cached)**
When the combined score ≥ 0.30, the `HybridTriageEngine` immediately calls the `explainAlert` Cloud Function. Gemini receives:
- The redacted text snippet (max 500 chars)
- On-device heuristic and TFLite scores
- Detected category and tactics

Gemini acts as the **ultimate judge**:
- If Gemini determines the message is benign (legitimate OTP, university poster, delivery receipt), it returns `riskLevel: LOW` → **no alert is created**, preventing false positives
- If Gemini confirms the threat, it returns a full structured explanation → the **alert is created with the Gemini analysis pre-cached**, so when the user opens the alert detail, the explanation loads instantly without another network call
- Gemini also determines the specific scam category (from 13 types: Phishing, Investment Scam, Love Scam, Job Scam, E-commerce Scam, Impersonation Scam, Loan Scam, Giveaway Scam, Tech Support Scam, Deepfake, KK Farm Scam, Spam, Other)

If the Gemini call fails (no internet), the alert is still created with the on-device scores, and Gemini is called when the user opens the alert detail.

#### Path B: Manual Scan (User-Initiated)

Manual scans **bypass the on-device triage entirely** and go straight to Gemini for maximum accuracy:

- **Paste Link** → calls `checkLink` Cloud Function → Gemini 2.5 Flash analyzes the full URL for domain structure, TLD risk, typosquatting, brand impersonation, and phishing patterns. The backend also runs a Levenshtein-distance heuristic against 30+ brands for extra context.
- **Pick Image** → ML Kit OCR + QR extracts text/URLs → sends directly to `explainAlert` Cloud Function → Gemini analyzes the extracted content
- **Camera Scan** → CameraX + ML Kit OCR/QR → same as above, sends directly to Gemini

This design decision means manual scan results are always **Gemini-quality** — no threshold filtering, no on-device shortcuts. The user explicitly asked to scan something, so they get the full AI analysis.

#### Alert Detail Screen

When the user opens an alert:
1. If the alert has a **cached Gemini analysis** (from Level 3 during Guardian detection or from manual scan), it loads instantly
2. If there is no cache (e.g., Gemini was offline during detection), a fresh `explainAlert` call is made
3. **ML Kit Translation** then translates all explanation text (headline, why flagged, what to do, what not to do) into the user's selected language **on-device** — no additional cloud call needed

### Innovation Highlights

1. **3-Level Hybrid AI Pipeline** — On-device heuristics + TFLite provide instant triage (< 50ms), while Gemini 2.5 Flash acts as the inline final judge during Guardian detection. This means alerts arrive with **pre-cached Gemini explanations** — the user sees the full analysis immediately.

2. **False-Positive-Tolerant Design** — We deliberately set the on-device escalation threshold low (0.30) to **catch every possible scam**. Gemini then filters out false positives inline before the alert is even created. It's better to let Gemini evaluate a borderline message than to silently miss a real scam.

3. **Dual Detection Paths** — Guardian mode uses the fast 3-level pipeline for zero-effort background protection. Manual scans bypass the on-device pipeline entirely and go straight to Gemini for maximum accuracy. Each path is optimized for its use case.

4. **Privacy-First Architecture** — No raw conversations, images, or full URLs ever leave the device by default. Gemini only receives redacted snippets (max 500 chars). No personal data is stored in the cloud.

5. **Scam News Intelligence Pipeline** — Rather than showing raw RSS feeds, every news article passes through Gemini for relevance filtering and summarization. This ensures users only see mobile-targeted scam news with actionable tips — not corporate fraud or unrelated cybersecurity news.

6. **Multi-Script Detection** — The Char-CNN model and heuristic engine both handle English, Malay, and Chinese text natively — critical for Malaysia's multilingual population. The character-level approach means the model handles mixed-language messages (code-switching) naturally.

### App Workflow

```
┌───────────────────────┐
│    FIRST LAUNCH       │
│  1. Choose Language   │
│  2. Choose Mode       │
│  3. Grant Permissions │
└──────────┬────────────┘
           ▼
┌────────────────────────────────────────────────────┐
│                    HOME TAB                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  Dashboard: Protection Status + Stats        │  │
│  │  Scan Options: Paste Link | Image | Camera   │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────┬───────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Link     │  │ Image    │  │ Camera   │
│ Scan     │  │ Scan     │  │ Scan     │
│(checkLink│  │ (ML Kit  │  │ (CameraX │
│  Gemini) │  │→ Gemini) │  │→ Gemini) │
└──────────┘  └──────────┘  └──────────┘
        │            │            │
        └────────────┼────────────┘
                     ▼
┌────────────────────────────────────────────────────┐
│                   ALERTS TAB                        │
│  List of detected threats (auto detected ones)      │
│  Each alert shows: headline, risk tag, timestamp    │
│                     │                               │
│                     ▼                               │
│  ┌──────────────────────────────────────────────┐  │
│  │  ALERT DETAIL SCREEN                         │  │
│  │  • Cached Gemini Explanation (instant load)  │  │
│  │  • Score Breakdown (Heuristic + TFLite %)    │  │
│  │  • ML Kit Translation (on-device)            │  │
│  │  • [Mark Safe] button → deletes alert        │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│                  INSIGHTS TAB                       │
│  • Safety Tips (Education carousel)                 │
│  • Scam News Feed (Gemini-curated from Firestore)   │
│  • News Translation (ML Kit on-device)              │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│                  SETTINGS TAB                       │
│  • Language Selection (EN / MS / ZH)                │
│  • Mode Selection (Guardian / Companion)            │
│  • Notification Monitoring Toggle                   │
│  • Gallery Monitoring Toggle                        │
│  • About / Version Info                             │
└────────────────────────────────────────────────────┘
```

---

## 💪 Challenges Faced

### 1. Training a Robust On-Device TFLite Model

The biggest challenge was building a TFLite model that could reliably detect scams across **three languages** (English, Malay, Chinese) while handling the enormous variety of scam patterns in the wild. Scam messages constantly evolve — new templates, new social engineering tactics, mixed-language messages — making it nearly impossible to cover every condition with maximum accuracy.

After extensive experimentation with different architectures and datasets on Google Colab and Kaggle, we adopted a **character-level CNN (Char-CNN)** approach. Unlike word-level models, Char-CNN processes raw characters, making it naturally robust to typos, mixed scripts, and character substitutions that scammers frequently use (e.g., "M@ybank" instead of "Maybank"). However, the trade-off is that the model may flag some legitimate messages as suspicious.

We made a deliberate design decision to embrace this: **we chose to prioritize recall (catching every scam) over precision (avoiding false alarms)**. Our philosophy is that it is far better to warn a user about a message that turns out to be safe, than to miss a real scam that leads to financial loss. The false-positive-tolerant on-device model acts as a safety net, while **Gemini 2.5 Flash serves as the inline second opinion** — during Guardian detection, when the on-device score exceeds the threshold, Gemini is called immediately to either confirm the threat (creating an alert with a pre-cached explanation) or downgrade false positives (like legitimate bank OTPs or university announcements) before the alert is even created.

### 2. Gemini Prompt Engineering for Structured Output

Getting Gemini to consistently return valid, parseable JSON with the exact schema we needed — across different scam types, languages, and edge cases — required significant iteration. We also had to carefully engineer the system prompt to produce **calm, non-blaming, elder-friendly explanations** while being technically accurate enough to be useful.

### 3. Scam News Relevance Filtering

Scraping "scam news" from Google News RSS returns a mix of corporate fraud, political news, cybersecurity reports, and actual mobile-targeted scams. We needed Gemini to act as an intelligent filter — accepting only articles about scams that target individuals through their phones, and rejecting everything else. Getting the prompt specificity right was an iterative process.

### 4. Multi-Language Heuristic Pattern Design

Creating compound heuristic patterns that work across English, Malay, and Chinese without producing false positives on legitimate content (bank notifications, delivery updates, event posters) was surprisingly difficult. Each language has its own patterns for urgency, authority, and money references. We solved this by requiring **both sides of a compound signal to match** — for example, an urgency keyword alone doesn't trigger a flag, but urgency + money reference does.

### 5. Build Environment & Dependency Conflicts

Integrating multiple Google SDKs (Firebase, ML Kit, TFLite, CameraX, Room, Compose) in a single Android project created complex dependency conflicts — particularly KSP annotation processing for Room, CameraX native library alignment for 16KB page support, and Firebase BoM version compatibility. Additionally, OneDrive file sync caused persistent file-locking issues during Gradle builds, requiring specific workflow adjustments.

---

## 🚀 Installation & Setup

### Prerequisites

- **Android Studio** (latest stable — Ladybug or newer)
- A physical **Android phone** running **Android 8.0+** (API 26+)
- A **USB cable** (data cable, not charge-only)

### Step 1 — Clone & Open the Project

```bash
git clone https://github.com/fuchuin19/SafeX---Your-AI-powered-and-real-time-safety-expert.git
```

1. Open **Android Studio**
2. **File → Open** → navigate to the cloned `SafeX---Your-AI-powered-and-real-time-safety-expert` folder → click **OK**
3. Wait for **Gradle sync** to finish (bottom progress bar). First time takes a few minutes — it downloads all dependencies
4. If sync fails with JDK errors: **File → Settings → Build → Gradle → Gradle JDK** → set to **JDK 17** (bundled with Android Studio)

### Step 2 — Firebase Configuration

The app uses Firebase. The `google-services.json` is **already included** in `app/` in the repo — **no action needed**, it should just work.

> If for some reason it's missing (e.g., gitignored), place the file at:
> `SafeX---Your-AI-powered-and-real-time-safety-expert/app/google-services.json`

### Step 3 — Enable Developer Options on the Phone

1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times rapidly → *"You are now a developer!"* toast appears
3. Go back to **Settings → System → Developer Options** (or search "Developer options")
4. Enable the **USB Debugging** toggle

> **OEM-specific paths:**
> - **Samsung:** Settings → About Phone → Software Information → Build Number
> - **Xiaomi:** Settings → About Phone → MIUI Version

### Step 4 — Connect Phone via USB

1. Plug phone into laptop with **USB data cable**
2. On the phone, a prompt says *"Allow USB debugging?"* → tap **Allow** (check *"Always allow from this computer"*)
3. If prompted for USB mode, select **File Transfer / MTP** (not charging only)
4. In Android Studio, the phone should now appear in the **device dropdown** (top toolbar, next to the green ▶ play button) — e.g., `Samsung SM-G998B`

> **Phone not showing up?**
> - Try a **different USB cable** (many cables are charge-only)
> - Install **OEM USB drivers**: Samsung needs [Samsung USB Driver](https://developer.samsung.com/android-usb-driver), others usually work with Google USB Driver (SDK Manager → SDK Tools → Google USB Driver)
> - On Windows: check **Device Manager** for unknown devices

### Step 5 — Run the App

1. Select the **phone** from the device dropdown (NOT an emulator)
2. Click the green **▶ Run** button (or `Shift+F10`)
3. First build takes **2–5 minutes** — subsequent builds are faster
4. The app auto-installs and launches on the phone
5. If the phone asks *"Install from unknown source?"* → tap **Allow / Install**

### Step 6 — Grant Permissions

On first launch, SafeX will ask for several permissions during onboarding:

| Permission | Why It's Needed |
|---|---|
| **Notification Listener** | Real-time SMS/notification scanning in Guardian mode |
| **Storage / Media** | Gallery image scanning for scam screenshots |
| **Camera** | Manual camera scan feature |
| **Notifications** | Posting scam warning alerts |

> **Grant all of them** for the full experience.

### Step 7 — Using the App

1. **Onboarding** — Pick language (EN / BM / ZH), choose operating mode (Guardian / Companion)
2. **Home** — Dashboard showing protection status and scan options
3. **Manual Scan** — Paste a link, pick an image, or use camera to scan for scams
4. **Alerts** — View detected threats with Gemini-powered explanations
5. **Insights** — Scam news feed and safety tips
6. **Settings** — Toggle Guardian features, change language

### Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | **File → Invalidate Caches → Restart** |
| SDK not found | **File → Project Structure → SDK Location** → point to your Android SDK (usually `C:\Users\<name>\AppData\Local\Android\Sdk`) |
| Build fails with OneDrive lock errors | Move the project folder **outside OneDrive** |
| App crashes on launch | Check **Logcat** (bottom panel), filter by `com.safex.app` for the stack trace |
| Phone not detected | Run `adb devices` in terminal — if empty, reinstall USB drivers |
| `minSdk 26` error | Phone's Android version is too old (needs **8.0+**) |

> **That's it.** Clone → Open → Plug in phone → Hit Run. Should take ~10 min total for first-time setup.

---

## 🔮 Future Roadmap

- **Multimodal Gemini Analysis** — Use Gemini's vision capabilities to directly analyze suspicious images (with explicit user consent), enabling detection of visual scam patterns like fake bank interfaces and fraudulent receipts
- **Link Gatekeeper** — Intercept link taps system-wide using Android's "Open with SafeX" intent filter, scanning URLs before they open in the browser
- **Elder Mode UI** — A simplified interface with larger fonts, higher contrast, and reduced options specifically designed for elderly users
- **Trusted Contacts** — Allow users to designate trusted contacts; messages from these contacts bypass scanning
- **Community Scam Intelligence** — Re-introduce anonymized, privacy-safe community reporting with k-anonymity thresholds to build crowdsourced scam trend data
- **iOS Version** — Expand beyond Android to reach a wider audience (within iOS platform limitations)
- **Offline Gemini** — Explore Gemini Nano for fully on-device explanations, eliminating cloud dependency entirely

---

## 📄 Privacy

SafeX is designed to be **privacy-first**:

- ❌ Does **NOT** store any messages and personal data on the cloud
- ❌ Does **NOT** bypass WhatsApp encryption
- ❌ Does **NOT** read private chat history — only notification previews (what the OS exposes)
- ❌ Does **NOT** upload full conversations or images
- ✅ Gemini receives only **redacted text snippets** (max 500 chars) — never full messages or images
- ✅ On-device triage runs first — Gemini is called only when the on-device score exceeds the threshold, or when the user manually scans something
- ✅ All scam news is pre-processed server-side — no user data is involved in news aggregation

### What Data Leaves the Device?

| Feature | Local Processing | Sent to Cloud | When | Stored |
|---|---|---|---|---|
| **Guardian notifications** | Heuristic Rules + TFLite Char-CNN (on-device) | Redacted snippet (max 500 chars) + on-device scores → `explainAlert` Cloud Function | Automatically when combined score ≥ 0.30 | Room DB (local only) — alert + cached Gemini analysis |
| **Gallery scan** | ML Kit OCR + QR → Heuristic Rules + TFLite (on-device) | Redacted extracted text (max 500 chars) → `explainAlert` Cloud Function | Automatically via WorkManager when score ≥ 0.30 | Room DB (local only) — alert + cached Gemini analysis |
| **Manual link scan** | None — sent directly to cloud | Full URL → `checkLink` Cloud Function (Gemini) | User-initiated only | Not persisted — result displayed on screen |
| **Manual image / camera scan** | ML Kit OCR + QR (on-device text extraction) | Extracted text (max 500 chars) → `explainAlert` Cloud Function (Gemini) | User-initiated only | Not persisted — result displayed on screen |
| **Alert detail view** | ML Kit Translation (on-device) | Only if no cached analysis — calls `explainAlert` | User opens an alert | Translation done on-device, no cloud call |
| **Scam news feed** | N/A — no user data involved | No user data sent | Scheduled backend scrapes Google News RSS hourly | Firestore (public content only) → cached in Room DB |

---

## 📁 Repository Structure

```
SafeX/
├── app/                          # Android application
│   └── src/main/java/com/safex/app/
│       ├── ui/                   # Jetpack Compose screens & navigation
│       │   ├── screens/          # Home, Alerts, Settings screens
│       │   ├── alerts/           # Alert detail screen & ViewModel
│       │   ├── insights/         # Insights & news feed screen
│       │   ├── onboarding/       # First-run onboarding flow
│       │   ├── navigation/       # Bottom nav & routing
│       │   └── theme/            # SafeX Blue theme & typography
│       ├── guardian/             # Background detection pipeline
│       │   ├── GuardianNotificationListener.kt
│       │   ├── NotificationTriageEngine.kt  (heuristic rules)
│       │   ├── HybridTriageEngine.kt        (combined scoring)
│       │   ├── GalleryScanWork.kt           (WorkManager)
│       │   └── SafeXNotificationHelper.kt
│       ├── scan/                 # Manual scan feature
│       │   ├── LinkScanScreen.kt
│       │   ├── ScanScreen.kt
│       │   ├── CameraScanScreen.kt
│       │   └── ScanViewModel.kt
│       ├── data/                 # Data layer
│       │   ├── CloudFunctionsClient.kt
│       │   ├── AlertRepository.kt
│       │   ├── NewsRepository.kt
│       │   ├── MlKitTranslator.kt
│       │   └── local/            # Room database
│       └── ml/                   # TFLite integration
│           └── ScamDetector.kt
├── functions/                    # Firebase Cloud Functions (TypeScript)
│   └── src/index.ts              # explainAlert, checkLink, getScamNewsDigest, etc.
├── model/                        # TFLite model artifacts
│   ├── safex_charcnn_dynamic.tflite
│   ├── char_vocab.json
│   └── model_config.json
├── PRD.md                        # Product Requirements Document
├── SETUP_GUIDE.md                # Detailed setup instructions
└── DEMO_CHECKLIST.md             # Demo preparation checklist
```

---

<p align="center">
  Built with ❤️ by <b>KMPians</b> for <b>KitaHack 2026</b>
</p>
