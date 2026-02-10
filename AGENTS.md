# SafeX — Agents Plan (Antigravity Agent Manager)

This file defines how to split SafeX into multiple parallel workstreams so agents don’t overlap or hallucinate.

**Rule 0 (non-negotiable):** Every agent must read:
- `README.md`
- `productrequirementdocument.md`
- this `agents.md`

…and must follow them as the source of truth.

---

## Team split (3 developers, 1 extra for slides)

You said: 4 people total, but 1 is doing slides/research/presentation.  
So we split engineering into **3 parallel lanes** with clean boundaries so nobody waits on anyone.

### Dev 1 — App UI + Localization + Insights News
Owns:
- Jetpack Compose UI for all 4 tabs (Home, Alerts, Insights, Settings)
- Onboarding flow (first-run language + mode selection)
- Language picker + localized strings (EN/MS/ZH)
- Insights tab UI (personal summary, community trends UI, **News feed UI**)
- Room caching for news feed (or DataStore cache if you keep it simpler)
Deliverables:
- Screens + navigation working with fake repositories (no backend needed yet)
- UI states: loading / empty / error / success for Alerts + News

### Dev 2 — On-device Detection (Notifications, Gallery, Scan) + ML Kit + (optional) TFLite
Owns:
- NotificationListenerService pipeline (extract previews → local triage → create alert)
- WorkManager gallery scan (new images → OCR/QR → risk triage → alert)
- Home “Scan” actions:
  - pick image from gallery → OCR link/text
  - camera scan → QR
  - paste link → Safe Browsing check
- Optional: TFLite triage model integration (fast scam intent score)
Deliverables:
- `DetectionRepository` + `AlertStore` + unit tests
- End-to-end local alert creation (stored in Room) without Gemini

### Dev 3 — Firebase Backend + Gemini + Insights Aggregation
Owns:
- Firebase project setup (Firestore, App Check, Functions)
- Cloud Functions:
  - `analyzeAlertWithGemini` (callable): explanation + advice (JSON)
  - `reportAlert` (callable): increment weekly aggregated counters
- Firestore data model for insightsWeekly
- Security rules + App Check enforcement
Deliverables:
- Deployable backend + emulator testing
- Contract JSON schemas that Dev 1 & 2 use

### Contracts (so you can work in parallel without blocking)
All 3 devs agree on these shared models FIRST (one 30-minute sync):
- `Alert` model (id, createdAt, sourceType, rawSnippet?, extractedUrl?, riskLevel, reasons[], geminiExplanation?, advice[])
- `ReportType` enum (LOVE_SCAM, INVESTMENT, PHISHING, JOB, PARCEL, BANK_IMPERSONATION, OTHER)
- `NewsItem` model (title, url, domain, imageUrl?, retrievedAt, region=MY|GLOBAL)
Then:
- Dev 1 builds UI against **FakeRepositories** that return mock data.
- Dev 2 builds detection to write Alerts into Room using the same `Alert` model.
- Dev 3 builds cloud functions returning exactly the schema Dev 1 expects.

## Overall workflow (recommended)

### Phase 1 — Manual setup (human does)
Before any agent writes backend-integrated code, you must complete:
- Firebase project created
- Android app registered + `google-services.json` placed into `app/`
- Firestore enabled
- Anonymous Auth enabled
- Cloud Functions initialized + deployed
- Safe Browsing API key stored as a Firebase Functions secret
- Vertex AI API enabled

Agents must **pause** if any dependency is missing.

### Phase 2 — Build skeleton app (agents)
- Compose navigation with 4 tabs
- Local database for alerts (Room)
- Permission UI in Settings

### Phase 3 — Detection pipeline (agents)
- NotificationListenerService (Guardian)
- Gallery scan worker (Guardian)
- Manual scan UI and pipelines (Home)

### Phase 4 — Cloud integration (agents)
- Functions callable integration
- Gemini explanation rendering
- Report + Insights data display

### Phase 5 — Demo hardening (agents)
- Test flows
- Error handling
- Offline fallbacks
- Demo script

---

## Agents (recommended roles)

### Agent 1 — Android UI + Navigation
**Goal:** Implement 4-tab Compose UI and navigation.

**Owns:**
- Bottom navigation: Home / Alerts / Insights / Settings
- Screens skeleton + routing
- UI components (cards, lists, detail layout)
- “Scan” entry from Home (options UI)

**Deliverables:**
- `MainActivity` + Compose NavGraph
- `HomeScreen`, `AlertsScreen`, `InsightsScreen`, `SettingsScreen`
- `AlertDetailScreen` UI layout (without backend yet)

**Dependencies:** none

---

### Agent 2 — Local Data Layer (Room + repositories)
**Goal:** Provide local persistence for alerts and personal stats.

**Owns:**
- Room entities + DAO
- Repository APIs
- DataStore prefs (mode, toggles, last scanned timestamp)

**Deliverables:**
- `AlertEntity`, `AlertDao`, `SafeXDatabase`
- `AlertRepository`
- `SettingsRepository`

**Dependencies:** none

---

### Agent 3 — Guardian Notification Monitoring
**Goal:** Implement NotificationListenerService scanning + alert creation.

**Owns:**
- NotificationListenerService implementation
- Extract text safely from notification extras
- Run on-device triage (heuristics + optional TFLite)
- Create local alert and system notification

**Deliverables:**
- `SafeXNotificationListenerService`
- `NotificationTriageEngine`
- Notification channel setup

**Dependencies:**
- Agent 2’s local DB APIs

---

### Agent 4 — Guardian Gallery Monitoring
**Goal:** Scan new gallery images (Guardian toggle) using WorkManager.

**Owns:**
- WorkManager worker scheduling
- MediaStore query for new images
- ML Kit OCR + QR extraction
- On-device triage
- Create alerts + warning notifications

**Deliverables:**
- `GalleryScanWorker`
- `GalleryMonitorScheduler`
- ML Kit OCR/QR pipeline for URIs

**Dependencies:**
- Agent 2 local DB
- Agent 1 UI for toggles (Settings)

---

### Agent 5 — Manual Scan (Home)
**Goal:** Implement user-initiated scanning.

**Owns:**
- Paste link input screen
- Photo Picker image selection + OCR/QR
- Camera scan (CameraX) for QR + OCR
- Display scan result screen

**Deliverables:**
- `ScanScreen` / bottom sheet
- `LinkScanScreen` + result UI
- `ImageScanScreen` + result UI

**Dependencies:**
- Agent 1 UI shell
- Optional: Agent 6 backend for Safe Browsing link check

---

### Agent 6 — Backend + Firebase Functions integration
**Goal:** Connect Android app to callable functions (Gemini explanation + report).

**Owns:**
- Kotlin client to call `explainAlert` and `reportAlert`
- Cloud Function request/response models
- Error handling + retries
- Secret/config best practices

**Deliverables:**
- `CloudFunctionsClient.kt`
- Data models for Gemini JSON response
- Integration in `AlertDetailScreen` (with Agent 1)

**Dependencies:**
- Manual setup completed (Functions deployed)
- Agent 2 local alert model

---

### Agent 7 — Insights (Firestore read + UI)
**Goal:** Show community trends + personal stats.

**Owns:**
- Firestore query for `insightsWeekly/{weekId}`
- UI list + charts (simple list is okay for MVP)
- Education content cards

**Deliverables:**
- `InsightsRepository`
- `InsightsScreen` full implementation

**Dependencies:**
- Firestore enabled
- report flow exists (Agent 6)

---

### Agent 8 — QA + Demo Hardening
**Goal:** Make sure demo does not crash.

**Owns:**
- End-to-end test checklist
- Fake data injection for demo (debug build)
- Offline + error fallback text
- Performance checks

**Deliverables:**
- `DEMO_CHECKLIST.md` (optional)
- Test plan in README additions
- Debug menu (optional)

**Dependencies:** all above

---

## Definition of Done (MVP)
- 4 tabs implemented
- Guardian mode toggles exist + correct permission flows
- Notification detection produces alert + warning notification
- Alert detail loads Gemini explanation via function
- Report updates Insights
- Manual scan supports paste link + image + camera

---

## Anti-hallucination guardrails for agents
- If a step depends on Firebase setup, agent must:
  1) ask the user to confirm setup is done
  2) provide exact error messages to user if builds fail
- Agents must not invent APIs. Use official docs or existing project dependencies.
- Agents must not expand scope beyond PRD without explicit approval.

