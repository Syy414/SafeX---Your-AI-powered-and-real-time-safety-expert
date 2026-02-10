# SafeX — Product Requirement Document (PRD)

Version: 1.0 (KitaHack 2026 MVP)  
Platform: **Android only**  
Navigation: **4 tabs only** — Home, Alerts, Insights, Settings

---

## 1) Product vision

SafeX is an AI scam safety companion that:
- detects scam **intent** early,
- warns users immediately,
- explains risks clearly,
- and does so with a privacy-first approach.

The MVP is optimized for **demo reliability**, **explainability**, and **responsible permissions**.

---

## 2) Goals & non-goals

### 2.1 Goals (MVP)
1. **Early detection** from notification previews and suspicious saved images (Guardian mode).
2. **Instant warning notification** when high risk is detected.
3. **Alert review flow**: tapping notification opens Alerts tab + alert detail.
4. **Gemini-powered explanation** on alert detail (lazy-loaded to reduce cost).
5. **Manual Scan** from Home (paste link / choose image / camera scan).
6. **Report → Insights update** with privacy-safe aggregated statistics.

### 2.2 Non-goals (explicit)
- No OTP interception (not allowed; also user trust issue).
- No reading private WhatsApp content beyond notification preview.
- No auto-blocking links on tap (for MVP; reduces demo complexity).
- No always-on spyware-like scanning or intrusive accessibility hacks.
- No cloud storage of raw chats or raw images.

---

## 3) Personas

### Persona A: Elder / vulnerable user (Guardian target)
- Wants safety with minimal effort.
- Gets anxious easily; needs calm wording.
- Needs big buttons, simple actions.

### Persona B: Normal user (Companion target)
- Privacy-conscious, wants control.
- Uses manual scan occasionally.
- Wants reassurance and education.

---

## 4) Information architecture (4 tabs)

### 4.1 Home tab
**Purpose:** show “am I protected?” + provide one-tap scan entry.

**Components:**
- Status card:
  - Protection status: ON/OFF
  - Which monitors enabled: Notifications / Gallery
  - Last scan time (optional)
- Primary CTA: **Scan**
  - Opens Scan menu/screen with 3 options:
    1) Paste link
    2) Choose image from gallery
    3) Scan with camera (QR + OCR)
- Summary section:
  - “Threats detected this week” (local-only)
  - “Last alert” (optional)

**Acceptance criteria:**
- Home loads < 1 second on mid-range phone.
- Scan options accessible within 1 tap.

### 4.2 Alerts tab
**Purpose:** show detections that need review; functions as history until reviewed.

**Rules:**
- Alerts exist locally until the user reviews them.
- After user finishes review, alert is **deleted automatically** (MVP decision).
- Alerts tab includes:
  - Auto detections (notifications / gallery)
  - Manual scan results if flagged (optional)

**Alert list item fields:**
- Icon: message / image / link
- Headline: short (e.g., “Potential investment scam”)
- Timestamp
- Risk tag: HIGH / MEDIUM / LOW (MVP can show only HIGH/MEDIUM)

**Alert detail screen (critical):**
- Risk meter (HIGH/MEDIUM/LOW)
- “Why SafeX flagged this” (bullets)
- “What you should do now” (bullets)
- “What NOT to do” (bullets)
- Optional: “Detected link present → test in Scan”
- Actions:
  1) **Report** (sends aggregated data to backend)
  2) **Mark as safe** (local feedback; deletes alert)
- Optional: “Done” (same as Mark safe but without reporting)

**Gemini requirement:**
- Gemini must provide structured explanation.
- Gemini is called only when the detail screen opens.
- If offline / error: fallback generic advice.

**Acceptance criteria:**
- When user taps system notification, app navigates to Alerts tab and opens the correct alert.
- Alert detail must render even if Gemini fails (fallback message).

### 4.3 Insights tab
**Purpose:** show both personal summary, anonymized community scam trends, AND a lightweight scam-news awareness feed.

**Sections:**
1) Personal (local-only)
- “This week you received: X high-risk alerts”
- “Top categories (local)”
- “Top detected patterns (local)”

2) Community Trends (from Firestore, privacy-safe)
- “Top scam categories this week”
- “Top keywords / phrases” (aggregated, k-anonymity threshold)
- “Top fake-brand mentions” (aggregated)
- “New scam patterns emerging” (derived insights)

3) News (Awareness Feed)
- Goal: increase awareness by showing recent scam-related headlines.
- Implementation: **NOT web scraping random sites in-app**.
  - Use **GDELT DOC 2.0 API** as a public news index (no API key needed).
  - Fetch **on-demand** when Insights opens or user pulls-to-refresh.
  - Cache results locally (Room) with a TTL (e.g., 12 hours) to avoid spammy network calls.
- UX:
  - Two chips: **Malaysia** (sourcecountry:malaysia) and **Global**
  - Each item shows: title, source domain, optional image, and “Open” (external browser / Chrome Custom Tab)
  - Optional “Translate headlines” toggle (uses ML Kit on-device translation)
- Safety disclaimer (always visible): “News links open in your browser. Do not enter passwords/OTP from any page.”

**Acceptance criteria:**
- Insights shows “No community data yet” gracefully if Firestore empty.
- News feed shows loading + offline fallback (cached list or empty state).
- Never shows raw user content in Insights.
- News feed never auto-opens links; user must tap.
### 4.4 Settings tab
**Purpose:** control mode and permissions.

**Mode selection:**
- Guardian (proactive)
- Companion (manual-only)

**Guardian toggles (only visible if Guardian):**
- Notification monitoring toggle
- Gallery monitoring toggle

**Permission status UI:**
- For each toggle, show:
  - Status: Enabled/Disabled
  - Button: “Enable access”
  - On tap opens correct system page / permission request

**Other settings:**
- Privacy policy summary
- About SafeX
- Optional: “Reset local data” (delete local alerts)

**Acceptance criteria:**
- No “sensitivity slider” in MVP.
- If user disables permission, app reflects status.

---

**Settings in MVP:**
- Mode selection: Guardian / Companion
- Guardian toggles (if Guardian):
  - Notification access (on/off)
  - Gallery access (on/off)
- **Language (in-app): English / Malay / Chinese**
  - User selects during first-run onboarding and can change later.
  - Changing language updates ALL UI strings (tab labels, buttons, instructions).
  - News headlines: keep original title, optionally translated on-device when “Translate headlines” is enabled.

**Acceptance criteria:**
- Language setting applies immediately (Activity recreates) and is persisted (DataStore).
- If translation models aren’t downloaded yet, app prompts to download (Wi‑Fi recommended) and continues gracefully.

## 5) Core user flows

### 5.1 Onboarding (first run)
1. Intro screens (2–3)
2. Choose mode: Guardian or Companion
3. If Guardian:
   - Choose which monitors to enable (notif, gallery, or both)
   - Request permissions in correct order:
     1) POST_NOTIFICATIONS (Android 13+)
     2) Notification Access (system settings screen)
     3) Gallery permission (READ_MEDIA_IMAGES on Android 13+)
4. Finish → Home

### 5.2 Guardian detection → alert → review
1. System posts notification (WhatsApp/SMS/etc.) with preview
2. SafeX reads preview text (NotificationListenerService)
3. On-device triage:
   - heuristics + TFLite score
4. If HIGH risk:
   - Save local alert (Room)
   - Post SafeX warning notification
5. User taps SafeX notification
6. App opens Alerts tab + detail
7. Gemini explanation loads
8. User chooses:
   - Report → Insights updated
   - Mark safe → alert deleted

### 5.3 Gallery detection → alert → review
1. WorkManager detects new images (or periodic scan)
2. For each new image:
   - ML Kit OCR extracts text
   - ML Kit QR extracts URL if present
   - On-device triage (heuristics + TFLite)
3. If HIGH risk:
   - Save local alert
   - Post SafeX warning notification
4. Review same as above

### 5.4 Manual scan flow (Home)
User selects scan type:

**Paste link:**
- Call Cloud Function / Safe Browsing lookup
- Show result: SAFE / SUSPICIOUS / DANGEROUS
- If dangerous or suspicious: allow user to “Create alert” (optional)

**Choose image (Photo Picker):**
- OCR + QR
- On-device triage
- If flagged: show scan result + allow “Save to Alerts”

**Camera scan (CameraX):**
- QR + OCR
- Same as above

---

## 6) Detection logic (MVP)

### 6.1 On-device triage (no network)
Inputs:
- Notification text preview OR OCR extracted text
- Presence of URL / QR URL
- Language ID + optional on-device translation to English

Signals:
- Keyword patterns: urgency, impersonation, money, verification, threats
- Link present (increases caution, but no auto Safe Browsing call for auto-detection)
- TFLite text classifier score

Output:
- riskLevel: LOW/MEDIUM/HIGH
- category: investment / impersonation / romance / prize / delivery / job / unknown
- tactics: urgency / authority / paymentPressure / secrecy / fear / greed / etc.

Threshold:
- MVP: Only create alerts for HIGH (and maybe MEDIUM if you want more demo data)

### 6.2 Cloud reasoning (Gemini) — only on review
Inputs (privacy-minimized):
- alertType
- category, tactics
- redacted snippet (max 500 chars)
- extractedUrl (optional)
- (optional) Safe Browsing result if user manually scanned

Output schema:
- riskLevel, headline
- whyFlagged bullets
- whatToDoNow bullets
- whatNotToDo bullets
- confidence (0..1)
- notes

---

## 7) Backend requirements

### 7.1 Firebase Cloud Functions (callable)
Functions:
1. `explainAlert` — returns Gemini JSON
2. `reportAlert` — increments Firestore aggregated counters

Security:
- Require Firebase Auth (anonymous OK)
- Callable functions automatically include Auth + App Check tokens (when enabled)

### 7.2 Firestore data model
Collection:
- `insightsWeekly/{weekId}` with aggregated counters

NO raw content stored.

---

## 8) Privacy & safety requirements

### 8.1 Privacy promises (must be true)
- SafeX does not store or upload full conversations.
- SafeX does not upload images by default.
- Reporting uploads only aggregated metadata, never raw content.

### 8.2 Safety concerns & mitigations (even with aggregation)
Risks:
- Malicious users spam reports → poison insights
- Rare patterns could identify a person if sample size is tiny
- Masked domain patterns could still leak if too specific

Mitigations:
- Only show community insights when counts exceed a minimum threshold (k-anonymity)
- Rate limit report calls (App Check + server-side checks)
- Strict redaction + max length caps in reporting payload
- Do not store arbitrary “top phrases” from user content in MVP

---

## 9) Non-functional requirements

Performance:
- On-device detection must complete quickly (target < 500ms for notifications)
- Image scan may take longer; show progress indicator

Reliability:
- App must not crash if ML Kit models not downloaded yet
- Must fallback if Gemini unavailable

Battery:
- Gallery scans must be scheduled responsibly (WorkManager)
- Limit scans per run

---

## 10) Acceptance tests (MVP)

### A) Guardian notification detection
- Given a test scam notification text appears
- When SafeX has Notification Access enabled
- Then SafeX creates a local alert and posts a warning notification

### B) Alert detail explanation
- Given an alert exists
- When user opens detail screen
- Then app calls `explainAlert` and renders whyFlagged + actions
- If function fails, app shows fallback advice

### C) Reporting updates insights
- Given user taps Report
- Then `reportAlert` increments Firestore weekly counters
- Insights tab shows updated counts

### D) Manual link scan
- Given user pastes link
- Then app calls Safe Browsing and shows status

---

## 11) Future scope (post-MVP)
- Link gatekeeper (“open with SafeX”) optional
- Better image scam detection (multimodal Gemini with explicit consent)
- Elder mode UI (bigger font)
- Local “trusted contacts” check
- Community scam tip feed with moderation

