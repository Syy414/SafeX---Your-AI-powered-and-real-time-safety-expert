# SafeX Demo Checklist

## 1. Setup Verification
- [ ] **Google Services**: Ensure `google-services.json` is present in `app/`.
- [ ] **Firebase**: Confirm Cloud Functions (`explainAlert`, `reportAlert`) are deployed.
- [ ] **Android Build**: `clean project` -> `rebuild project` should pass.
- [ ] **Device**: Logic is tested on Android 13+ (requires runtime Notification/Gallery permissions). For older Android versions, permissions might be auto-granted or behave differently.

## 2. Permissions (Critical)
On first launch, you must grant:
1. **Notification Permission (POST_NOTIFICATIONS)**: System dialog "Allow SafeX to send you notifications?". **ALLOW**.
2. **Notification Access (Listener)**: App will redirect you to "Device & App Notifications" settings. **Toggle SafeX ON**.
   - *If this is missed, Guardian mode checks will fail silently.*
3. **Gallery/Storage**: If prompted for storage, **ALLOW**.

## 3. Demo Flow
1. **Home**:
   - Verify specific "Status: Idle" or "Protection: ON" in Home.
   - Tap "Scan" -> "Privacy Policy" (or any scan stub) to show responsiveness.
   
2. **Guardian Notification Test**:
   - (Debug) Tap "Create Sample Alert (Debug)" on the Home/Debug screen.
   - **Expected**: A system notification "⚠️ SafeX Warning" appears immediately.
   - Tap the warning notification.
   - **Expected**: App opens Alerts tab (or Alert Detail).
   
3. **Alert Detail (Gemini)**:
   - On the Alert Detail screen, wait 2-3 seconds for "AI Analysis".
   - **Success**: Bullet points with "Why flagged" and "What to do".
   - **Failure**: "AI analysis unavailable" fallback text appears (check internet).

4. **Manual Scan**:
   - Home -> Scan -> Pick Image.
   - Choose a screenshot with text.
   - **Expected**: Toast or Result screen showing "OCR OK" or risk level.

## 4. Common Failures & Fixes

| Issue | Cause | Fix |
|---|---|---|
| **Crash on Launch** | UserPrefs / DataStore corruption or Migration | Uninstall app (`adb uninstall com.safex.app`) and reinstall. |
| **No Warning Notification** | "Notification Access" permission missing | Go to Android Settings -> Search "Notification Access" -> Enable SafeX. |
| **OCR / Scan Fails** | ML Kit models downloading | Connect to Wi-Fi. Wait 1 min. Restart app. |
| **Gemini Error** | Firebase Auth / Functions error | Check internet. Verify `google-services.json`. |

## 5. Live Demo Script (2 mins)
1. "SafeX runs quietly in background."
2. *Send scam SMS/WhatsApp to device*.
3. "SafeX detects the 'Urgent' keyword and bank pattern..."
4. *Show notification on screen*.
5. "Tap to understand why..."
6. *Open Alert Detail*.
7. "Gemini explains it's likely a Phishing attempt."
8. "We can report it to help others." -> *Tap Report*.
