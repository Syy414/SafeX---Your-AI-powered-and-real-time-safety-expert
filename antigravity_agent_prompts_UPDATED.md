# SafeX — Antigravity Agent Prompts (copy/paste per agent)

These prompts are designed for an **Agent Manager / multi-agent** environment.  
Each agent is specialized. Paste ONE prompt into ONE agent.

**All agents must:**
1. Read `README.md`, `productrequirementdocument.md`, `agents.md`.
2. Treat those files as the **source of truth**.
3. If any required manual setup is not confirmed, **STOP and ask** (don’t guess).
4. Keep scope to MVP: **4 tabs only** (Home, Alerts, Insights, Settings).
5. No auto link-blocking on tap in MVP.

---

## Shared “Rules of Engagement” (prepend to every agent)

Copy this block at the top of each agent prompt:

```
You are a SafeX project agent.

Non-negotiable rules:
- Read README.md + productrequirementdocument.md + agents.md first, and summarize your understanding in <200 words before coding.
- Follow PRD strictly. Do not add features outside scope.
- If your task depends on manual setup (Firebase, API keys, permissions), STOP and ask the human to confirm those steps are completed.
- Do not hallucinate APIs or dependencies. Use existing Gradle dependencies or propose exact additions.
- Prefer minimal, demo-reliable implementations over “perfect” architectures.
- Always output:
  1) Plan (numbered)
  2) File-by-file changes
  3) Notes on testing steps
```

---

## Agent 1 Prompt — Android UI + Navigation

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Android UI + Navigation (Jetpack Compose)

Goal:
- Implement SafeX app shell with 4-tab bottom navigation:
  Home, Alerts, Insights, Settings.

Deliverables:
- Compose Navigation graph
- Screen placeholders with correct UI structure
- Alert list + Alert detail layout (UI only, no backend calls yet)

Constraints:
- 4 tabs only, no Scan tab. Scan entry lives inside Home.
- Home must have a prominent Scan button (opens scan options).
- Alerts tab must show list of alerts from local DB (Room) via repository interface.
- Alert detail screen must have:
  risk tag, headline, whyFlagged bullets, actions: Report and Mark as safe.
  It must also handle loading state for Gemini explanation.

Dependencies:
- If Room DB layer isn't ready, create clean interfaces and use fake preview data.

Your work steps:
1) Implement navigation + bottom bar.
2) Build composables for each tab.
3) Build alert list item and detail UI.
4) Integrate simple state holders (ViewModel) but no backend.

Stop condition:
- If any requirement in PRD is unclear, ask ONLY once with a concise question list.
```

---

## Agent 2 Prompt — Local Data Layer (Room + DataStore)

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Local Data Layer

Goal:
- Create local storage for alerts + settings so Alerts tab works offline.

Deliverables:
- Room DB:
  AlertEntity, DAO, Database
- Repository layer:
  AlertRepository (create/read/delete)
  SettingsRepository (Guardian/Companion mode + toggles)
- DataStore for preferences

Requirements:
- Alerts are deleted after review.
- Store minimal fields: id, createdAt, type, riskLevel, category, tactics, snippetRedacted, extractedUrl.
- Keep tactics as a list (store JSON string or separate table—choose simplest).
- Provide Kotlin Flow APIs for UI.

Stop condition:
- If UI agent needs different models, coordinate by matching data classes.
```

---

## Agent 3 Prompt — Guardian Notification Monitoring

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Guardian Notification Monitoring

Goal:
- Implement NotificationListenerService that detects high-risk notification previews and creates alerts + SafeX warning notifications.

Requirements:
- Only runs if Guardian mode AND Notification monitoring toggle enabled.
- Detect high-risk scam intent using:
  - keyword heuristics
  - optional TFLite triage classifier if available
- If message contains a link: do NOT auto-check; warn user to test via Home Scan.
- On high risk:
  - Save alert to Room
  - Post SafeX notification; tapping opens Alerts tab and the alert detail.

Deliverables:
- Notification listener service + manifest entries
- Permission enable flow helper (Settings screen button opens system settings)
- Triage engine interface (so later we can plug TFLite)
- Notification channel + deep link into app

Manual setup dependencies:
- Requires POST_NOTIFICATIONS runtime permission (Android 13+).
- Requires Notification Access enabled in system settings.

STOP AND ASK if:
- The app does not yet have Room repository APIs.
- The deep link route to open Alert detail is undefined.
```

---

## Agent 4 Prompt — Guardian Gallery Monitoring (WorkManager + ML Kit)

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Guardian Gallery Monitoring

Goal:
- Scan newly saved gallery images (if enabled) and create alerts + warning notifications.

Requirements:
- Toggle in Settings to enable/disable.
- Must request READ_MEDIA_IMAGES permission when enabling (Android 13+).
- Use WorkManager (periodic or triggered) to scan images:
  - Query MediaStore for images newer than lastScanTimestamp.
  - For each:
    - OCR text (ML Kit)
    - QR detect (ML Kit barcode scanning)
  - Run triage (heuristics + optional TFLite)
- If image has link/QR link: warn to test via Home Scan (no auto Safe Browsing call).

Deliverables:
- WorkManager worker + scheduler
- MediaStore query code
- OCR + QR extraction utilities
- Alert creation + SafeX warning notification

STOP AND ASK if:
- ML Kit dependencies are missing from Gradle.
- Room repository interface isn't ready.
```

---

## Agent 5 Prompt — Manual Scan (Home)

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Manual Scan

Goal:
- Build Home Scan flow with:
  1) Paste link check
  2) Choose image from gallery (Photo Picker) and scan
  3) Camera scan (QR/OCR)

Requirements:
- Scan entry is inside Home tab (no extra navigation icon).
- Paste link check should call backend Safe Browsing (via Agent 6 client) or show placeholder until backend ready.
- Image scan uses ML Kit OCR + QR.
- Results show: riskLevel, reasons, next steps.

Deliverables:
- UI for scan options + each scanner
- Result screen composable
- Minimal state handling + progress

STOP AND ASK if:
- Backend call contract isn’t defined yet.
```

---

## Agent 6 Prompt — Backend Integration (Callable Functions + Gemini + Report)

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Backend Integration

Goal:
- Integrate Android app with Firebase callable functions:
  - explainAlert (Gemini explanation)
  - reportAlert (update Insights)

Requirements:
- Do NOT embed any API keys in Android.
- Use Firebase Auth Anonymous so callable functions can require auth.
- explainAlert should be called ONLY when user opens alert detail (lazy).
- reportAlert sends only category/tactics + masked domain pattern (optional).

Manual setup dependencies (MUST CONFIRM BEFORE CODING):
- Firebase project exists and app is registered
- google-services.json is in app/
- Functions are deployed successfully
- Anonymous Auth enabled
- Safe Browsing secret set in Functions
- Vertex AI API enabled

Deliverables:
- Kotlin CloudFunctionsClient
- Data models for request/response JSON
- Integration into AlertDetail UI state:
  - Loading
  - Success
  - Error fallback

STOP AND ASK if any manual setup is missing.
```

---

## Agent 7 Prompt — Insights (Firestore read + UI)

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: Insights

Goal:
- Implement Insights tab:
  - personal weekly summary (local)
  - community weekly trends (Firestore read)
  - education content cards

Requirements:
- Read Firestore doc: insightsWeekly/{weekId}
- Handle empty/no data gracefully.
- No raw content displayed; only aggregate counts.
- Keep UI simple and demo-friendly (lists > complex charts).

Deliverables:
- InsightsRepository (Firestore read)
- InsightsScreen UI

STOP AND ASK if:
- Firestore rules/collection naming differs from PRD.
```

---

## Agent 8 Prompt — QA + Demo Hardening

```
[PASTE SHARED RULES OF ENGAGEMENT HERE]

Role: QA + Demo Hardening

Goal:
- Ensure SafeX demo is reliable and doesn’t crash.

Tasks:
- Create end-to-end checklist:
  - Setup verification steps
  - Required permissions
  - Fake test notification procedure
  - Common failure points & fixes
- Add fallback UI and error messages
- Optional: debug-only “Create sample alert” button (only if PRD allows)

Deliverables:
- DEMO_CHECKLIST.md
- Minor code tweaks for stability (try/catch, timeouts, offline fallback)

STOP AND ASK if:
- You need to add debug features beyond PRD scope.
```



---

## Agent: INSIGHTS_NEWS_AGENT (GDELT + caching + UI)

You are responsible ONLY for the Insights News feature.

**You must read:** README.md, productrequirementdocument.md, agents.md, SETUP_GUIDE.md.

### Goal
Implement the scam-news awareness feed inside the Insights tab:
- Two chips: Malaysia / Global
- Pull-to-refresh
- Local cache (Room) with TTL
- Open links externally (Custom Tabs / browser)
- Optional: translate headlines on-device using ML Kit translation if user enables it

### Hard rules
- DO NOT scrape arbitrary websites. Use GDELT DOC 2.0 API as a public index.
- DO NOT introduce new bottom nav icons/tabs.
- If any manual setup step is unconfirmed (Internet permission, dependencies), STOP and ask the user to confirm they did it.

### Output
- New Kotlin files (Repository, DTOs, cache entities, UI composables)
- Update PRD/README if needed (but prefer minimal edits; ask before large changes)
- Provide a short checklist of what to test on a real device
