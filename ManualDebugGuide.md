# SafeX — Complete Test Guide (Final)

**Device:** Xiaomi 13T Pro (Android 15)
**Status:** Backend Deployed. Hybrid Engine Active. Alerts Live.

Follow these steps **exactly** to reset and test everything.

---

## Phase 1: Clean Install (Do this first!)

Since you have an old version, you **must** delete it first to clear old data.

1.  **Uninstall Old App:**
    -   Find **SafeX** on your phone home screen.
    -   **Long press** the icon.
    -   Tap **App Info** (or 'i' icon).
    -   Tap **Uninstall** -> **OK**.

2.  **Install Fresh Version:**
    -   Connect phone to PC via USB.
    -   In Android Studio, verify **Xiaomi 13T Pro** is selected.
    -   Click the **Green Play Button (▶)**.
    -   *Phone Popup:* If asked "Install via USB?", tap **Install**.

---

## Phase 2: Initial Setup

1.  **Open SafeX.**
2.  Tap **Get Started**.
3.  **Guardian Mode Setup:**
    -   Tap **Enable Guardian Mode**.
    -   *Permission Popup (Notifications):* Tap **Allow**.
    -   *System Settings (Notification Access):*
        -   Find **SafeX** in the list.
        -   **Toggle ON**.
        -   *Xiaomi Warning:* Wait 10s, check "I am aware", tap **OK**.
        -   Tap **Back** twice to return to app.

4.  **Gallery & Camera Setup:**
    -   Go to **Home** tab.
    -   Tap **Scan Now** (Camera icon).
    -   *Permission Popup:* Tap **While using the app**.
    -   Tap **Back** to return home.

---

## Phase 3: Testing All Features

### Test 1: The News Feed (Asia + Global)
1.  Go to **Insights** tab.
2.  Look at the tabs at top. You should see **"Asia"** (Default) and **"Global"**.
3.  **Pull down** to refresh.
4.  *Verify:* You should see ~5 varied news articles about scams in Asia (SG, MY, PH, etc.).

### Test 2: Link Checker (Backend)
1.  Go to **Home** tab.
2.  Tap **"Check Link Safety"**.
3.  **Safe Test:**
    -   Enter `http://google.com` -> Tap Check.
    -   *Result:* "✅ Link appears safe".
4.  **Danger Test:**
    -   Enter `http://testsafebrowsing.appspot.com/s/phishing.html`
    -   Tap Check.
    -   *Result:* "⛔ Dangerous URL Detected" (High Risk).

### Test 3: Guardian (WhatsApp Scam)
*This tests the new Hybrid Engine & Alerts Tab.*
1.  **Close SafeX** (Swipe up to home screen).
2.  Ask a friend to WhatsApp you this **EXACT** message:
    > "URGENT: Bank Negara has suspended your account. Click bit.ly/verify-now immediately or face legal action."
3.  **Wait:** You should get a **SafeX Notification** ("Suspicious Message Detected").
4.  **Tap the Notification.**
5.  It should open SafeX.
6.  Go to **Alerts** tab.
7.  *Verify:* The message should be listed there as **HIGH** risk.

### Test 4: Gallery Scan (Background)
1.  Save a screenshot of a scam SMS to your phone gallery.
2.  Wait ~15 minutes (Android runs background jobs periodically).
3.  *OR Manual Trigger:* Go to **Settings** -> **Testing & Diagnostics** -> **Run Gallery Scan Now** (if available) OR just use the **Manual Scan** feature (below) to test the engine immediately.

### Test 5: Manual Image Scan
1.  Go to **Home** -> Tap **Scan Now** -> Select **Gallery** icon.
2.  Pick the screenshot you just saved.
3.  *Verify:* It detects the text and flags it as **High Risk**.
