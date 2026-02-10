# SafeX — AI Scam Safety Companion (KitaHack 2026)

SafeX is an **Android-only** scam safety companion that helps people avoid scams **before they act**.

It focuses on **early detection** (manipulation patterns like urgency, impersonation, money pressure) and **clear, calm guidance** — especially for elders and non-technical users.

---

## One-sentence pitch
**“SafeX detects scam intent early from notifications and suspicious images, warns you instantly, and explains what to do next — without invading your privacy.”**

---

## Why this matters (Malaysia + global)
Scams are fast, emotional, and increasingly convincing. Most victims don’t lose money because they are “careless” — they lose money because scammers exploit:
- **Urgency** (“act now”)
- **Authority** (“bank / police / delivery company”)
- **Fear + shame** (“your account is locked”)
- **Greed** (“guaranteed investment returns”)
- **Isolation** (“don’t tell anyone”)

SafeX is designed to interrupt that moment with a simple warning + next steps.

---

## SDG alignment
- **SDG 16 — Peace, Justice & Strong Institutions (Target 16.4)**: Reduce fraud and illicit financial flows  
- **SDG 10 — Reduced Inequalities**: Protects vulnerable populations (elders, new smartphone users)

---

## What SafeX does (MVP scope)

### ✅ Real-time warning (Guardian mode)
When enabled, SafeX can detect risk from:
1. **Notification previews** (WhatsApp/SMS/Telegram/etc. — only what the OS exposes)
2. **New images saved to Gallery** (posters, screenshots, QR images)

If SafeX detects high-risk content, it:
- Posts a **SafeX warning notification immediately**
- Tapping that notification opens **Alerts** tab to review details

### ✅ Manual Scan (Home → Scan)
Users can manually scan:
- **Paste a link** → Safe Browsing check
- **Select image from gallery** → OCR + QR + scam intent detection
- **Scan with camera** → QR + OCR

### ✅ Clear explanation & advice (Gemini-powered)
On the Alert Detail screen SafeX shows:
- Why it was flagged
- What to do now
- What NOT to do
- Confidence and risk level

Gemini is used **only when user opens an alert** to minimize cost and protect privacy.

### ✅ Insights (community trends + personal summary)
Insights tab shows:
- Personal weekly summary (on-device)
- Community trends (from anonymous user “Report” actions):
  - Most common scam categories
  - Most common scam tactics
  - Masked domain patterns (never raw URLs)

### ✅ Settings (simple, high-protection default)
- Mode: **Guardian / Companion**
- If Guardian:
  - Toggle **Notification Access monitoring**
  - Toggle **Gallery scanning**
- No sensitivity slider (default: high protection)

---

## What SafeX does NOT do (important for privacy + Play policies)
- ❌ Does NOT read OTP codes
- ❌ Does NOT bypass WhatsApp encryption
- ❌ Does NOT read private chat history
- ❌ Does NOT upload full conversations
- ❌ Does NOT automatically block links on tap in MVP

SafeX is designed to be privacy-first and transparent about Android limitations.

---

## App navigation (4 tabs only)
1. **Home**
   - Protection summary
   - “Scan” entry point (paste link / image / camera)
2. **Alerts**
   - List of detected threats (history until reviewed)
   - Alert detail: Gemini explanation + actions
3. **Insights**
   - Community scam trends (aggregated)
   - Education / tips
4. **Settings**
   - Guardian/Companion mode
   - Permission toggles
   - Privacy & About

---

## Architecture (high level)

### On-device (fast, private)
- Notification preview parsing (NotificationListenerService)
- Image scan (WorkManager + ML Kit OCR/QR)
- Language ID + on-device translation (ML Kit)
- Triage classifier (TensorFlow Lite) + heuristics

### Cloud (only when needed)
- Firebase Cloud Functions (callable)
- Gemini via Vertex AI (Google Gen AI SDK)
- Google Safe Browsing (manual link scan)
- Firestore for aggregated Insights counters

---

## Data & privacy model
**Default behavior:** nothing is uploaded.

**Only when user explicitly taps “Report”:**
- SafeX uploads *only*:
  - category (e.g., investment)
  - tactics (e.g., urgency)
  - masked domain pattern (optional)
- No raw chats, no phone numbers, no full URLs

**Gemini calls (minimized):**
- Only on alert detail open
- Payload is redacted snippet + category/tactics (not full messages)

---

## Demo script (2 minutes)
1. Open SafeX → show **Home** summary + Scan options
2. Enable **Guardian mode** (notification monitoring)
3. Trigger a scam notification (test phone or emulator)
4. SafeX posts a warning notification → tap it
5. App opens **Alerts → Alert detail**
6. Gemini explanation appears: “Why flagged / What to do”
7. Tap **Report** → switch to **Insights** → see counters updated

---

## Tech stack
- Android: Kotlin + Jetpack Compose
- ML Kit: OCR, QR, Language ID, Translation
- TFLite: text triage classifier
- Firebase: Auth (Anonymous), Firestore, Cloud Functions
- Google Safe Browsing API
- Gemini on Vertex AI via Google Gen AI SDK

---

## Known limitations (honest constraints)
- Notification previews depend on what each app exposes
- Background gallery scanning depends on Android media permissions + OS scheduling
- Safe Browsing detects known bad URLs, but not every scam (social engineering often uses “clean” domains)

---

## Repository layout (suggested)
- `/app` — Android app
- `/functions` — Firebase Cloud Functions
- `/docs` — PRD + agent prompts

---

## How to run (developer quickstart)
1. Follow `SETUP_GUIDE.md` step-by-step.
2. Put `google-services.json` into `app/`.
3. Deploy Cloud Functions (`firebase deploy --only functions`).
4. Run app on physical device (recommended) and enable:
   - Notification access
   - POST_NOTIFICATIONS permission
   - (Optional) Gallery access

---

## Judges: what to look for
- Real-time, early interruption of scams
- Explainability (Gemini output is structured)
- Privacy-first reporting & insights
- Strong alignment to real scam psychology

