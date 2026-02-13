# ManualDebugGuide.md — SafeX Manual Fixes

> **Last updated:** 2026-02-13  
> Items marked ✅ SOLVED have been fixed. Items marked 🔴 ACTIVE still need your attention.  
> Follow each section's steps exactly — no extra Googling needed.

---

## Table of Contents

1. [OneDrive File-Locking Gradle Cache Error](#1-onedrive-file-locking-gradle-cache-error)
2. [Notification Listener Permission (Guardian Mode)](#2-notification-listener-permission-guardian-mode)
3. [POST_NOTIFICATIONS Runtime Permission (Android 13+)](#3-post_notifications-runtime-permission-android-13)
4. [ML Kit Models Not Downloaded Yet](#4-ml-kit-models-not-downloaded-yet)
5. [Room Database Schema Change / Migration Crash](#5-room-database-schema-change--migration-crash)
6. [Firebase Cloud Functions Not Deployed](#6-firebase-cloud-functions-not-deployed)
7. [Gradle Sync Fails: "Cannot resolve" or Plugin Errors](#7-gradle-sync-fails-cannot-resolve-or-plugin-errors)
8. [App Crashes on Launch (DataStore Corruption)](#8-app-crashes-on-launch-datastore-corruption)
9. [Emulator: No Google Play Services / Firebase Crash](#9-emulator-no-google-play-services--firebase-crash)
10. [Camera Permission Denied (Manual Scan)](#10-camera-permission-denied-manual-scan)

---

## 1. OneDrive File-Locking Gradle Cache Error

**Status:** 🔴 ACTIVE (recurring — happens whenever OneDrive syncs during build)

**Error message:**
```
java.io.IOException: Unable to delete directory '...\app\build\kotlin\compileDebugKotlin\cacheable'
AccessDeniedException: ...\caches-jvm\inputs
```

**Root cause:** Your project lives inside OneDrive. OneDrive locks files while syncing, and Gradle can't delete its own caches.

### Fix (do ALL of these):

#### Option A: Quick fix (do this every time it happens)
1. **Close Android Studio** completely (File → Exit, not just minimize).
2. Open **Task Manager** (Ctrl + Shift + Esc):
   - Look for any `java.exe` or `OpenJDK Platform binary` processes.
   - Right-click each → **End Task**.
3. Open **File Explorer**, navigate to your project folder:
   ```
   C:\Users\Fu Chuin\OneDrive\Desktop\Documents\Kitahack2026\SafeX---Your-AI-powered-and-real-time-safety-expert\
   ```
4. **Delete the entire `build` folder** inside `app/`:
   - Right-click `app\build` → Delete.
   - If Windows says "file in use", wait 10 seconds for OneDrive to release, then try again.
5. Also delete the **root** `build` folder (same level as `app/`).
6. Reopen Android Studio.
7. Click **Build → Clean Project**, then **Build → Rebuild Project**.

#### Option B: Permanent fix (recommended)
1. **Pause OneDrive sync** during development:
   - Click the **OneDrive icon** in the system tray (bottom-right of taskbar, blue cloud).
   - Click the **gear icon** → **Pause syncing** → Choose **24 hours**.
   - Resume syncing when done coding.
2. Alternatively, **move the project out of OneDrive** entirely:
   - Copy the entire `SafeX---Your-AI-powered-and-real-time-safety-expert` folder to `C:\Dev\SafeX\` or similar.
   - Open the new location in Android Studio (File → Open → navigate to new path).

#### Option C: Nuclear option (if nothing else works)
1. Open **PowerShell as Administrator**:
   - Press Windows key → type `powershell` → right-click → **Run as administrator**.
2. Run:
   ```powershell
   # Kill all Gradle daemons
   taskkill /F /IM java.exe 2>$null
   
   # Delete the Gradle build cache
   Remove-Item -Recurse -Force "C:\Users\Fu Chuin\OneDrive\Desktop\Documents\Kitahack2026\SafeX---Your-AI-powered-and-real-time-safety-expert\app\build" -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force "C:\Users\Fu Chuin\OneDrive\Desktop\Documents\Kitahack2026\SafeX---Your-AI-powered-and-real-time-safety-expert\build" -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force "C:\Users\Fu Chuin\OneDrive\Desktop\Documents\Kitahack2026\SafeX---Your-AI-powered-and-real-time-safety-expert\.gradle" -ErrorAction SilentlyContinue
   ```
3. Reopen Android Studio → **File → Invalidate Caches → Invalidate and Restart**.
4. Wait for full Gradle sync to finish, then Build → Rebuild Project.

---

## 2. Notification Listener Permission (Guardian Mode)

**Status:** 🔴 ACTIVE (must be done on every fresh install / new device)

**Symptom:** Guardian mode is "ON" in the app, but no scam notifications are detected — the `GuardianNotificationListener` service never fires.

### Fix steps (on your Android device or emulator):

1. **Open the SafeX app** → Go to **Settings** tab.
2. Make sure mode is set to **Guardian**.
3. Toggle **Notification monitoring** ON.
4. The app should redirect you to the system settings page. If it doesn't, do it manually:
   - Open **Android Settings** (swipe down → gear icon).
   - Search for **"Notification access"** (or go to **Settings → Apps → Special app access → Notification access**).
     - On Samsung: **Settings → Apps → Menu (3 dots) → Special access → Notification access**.
     - On Pixel: **Settings → Notifications → Device & app notifications** (or **Notification access**).
5. Find **SafeX** (or **SafeX Guardian**) in the list.
6. **Toggle it ON**.
7. A system dialog will warn: "SafeX will be able to read all your notifications." → Tap **Allow** / **OK**.
8. Go back to SafeX → the notification monitoring toggle should now show as enabled.

### How to verify it works:
1. Send yourself a test SMS or WhatsApp message containing scam-like text, e.g.:
   ```
   URGENT: Your bank account has been compromised! Click here immediately to verify: http://fake-bank.com
   ```
2. Within a few seconds, you should see a "⚠️ SafeX Warning" notification.
3. If nothing happens, check **Settings → Apps → SafeX → Notifications** and make sure notifications are allowed.

---

## 3. POST_NOTIFICATIONS Runtime Permission (Android 13+)

**Status:** 🔴 ACTIVE (required on any Android 13+ device/emulator)

**Symptom:** SafeX detects scams correctly but no warning notification appears on screen.

### Fix steps:

1. When the app launches for the first time on Android 13+, it should show a system dialog: **"Allow SafeX to send you notifications?"** → Tap **Allow**.
2. If you already dismissed it or tapped "Don't Allow":
   - Open **Android Settings** → **Apps** → **SafeX** → **Notifications**.
   - Toggle **"All SafeX notifications"** to **ON**.
3. If using an emulator running API 33+:
   - Same steps — the notification permission dialog should appear on first launch.
   - If it doesn't appear, long-press the app icon → **App info** → **Notifications** → enable.

---

## 4. ML Kit Models Not Downloaded Yet

**Status:** 🔴 ACTIVE (first run only — models download automatically on Wi-Fi)

**Symptom:** OCR scan or QR scan returns empty results or crashes with "Model not available".

### Fix steps:

1. **Connect your device/emulator to Wi-Fi** (not cellular — models are large).
2. Open SafeX → go to **Home** → tap **Scan** → **Choose Image** and pick any image with text.
3. The first time, ML Kit will download OCR/barcode models in the background.
   - This takes **30 seconds to 2 minutes** on a good connection.
4. If it still fails after 2 minutes:
   - Close the app completely (swipe away from recent apps).
   - Open **Android Settings** → **Apps** → **SafeX** → **Storage** → Tap **Clear Cache** (NOT Clear Data).
   - Reopen the app and try scanning again.
5. To verify models are downloaded, the `AndroidManifest.xml` already has:
   ```xml
   <meta-data
       android:name="com.google.mlkit.vision.DEPENDENCIES"
       android:value="ocr,barcode" />
   ```
   This tells Google Play Services to auto-download models at install time.

### If using an emulator WITHOUT Google Play Services:
- ML Kit **will not work** on emulators without Google Play Services.
- Use an emulator image that says **"Google Play"** (not just "Google APIs").
- In Android Studio: **Tools → Device Manager → Create Virtual Device** → pick a device with the **Play Store icon** (▶️ triangle) in the "Play Store" column.

---

## 5. Room Database Schema Change / Migration Crash

**Status:** 🔴 ACTIVE (happens whenever code changes add/remove columns from Room entities)

**Symptom:** App crashes on launch with:
```
java.lang.IllegalStateException: Room cannot verify the data integrity.
Looks like you've changed schema but forgot to update the version number.
```
or:
```
Migration didn't properly handle: alerts
```

### Fix steps:

#### Quick fix (destroys local data — fine for dev/demo):
1. **Uninstall the app** from your device/emulator:
   - Long-press the SafeX app icon → **App info** → **Uninstall**.
   - Or via ADB:
     ```
     adb uninstall com.safex.app
     ```
2. Reinstall by clicking **Run ▶** in Android Studio again.

#### If you can't uninstall (or want to keep data):
1. Open **Android Settings** → **Apps** → **SafeX** → **Storage & cache**.
2. Tap **Clear Storage** (this deletes the Room database).
3. Reopen SafeX.

### To prevent this in the future:
- Every time a Room entity (e.g., `AlertEntity`, `NewsArticleEntity`) gets new columns or removed columns, the database version in `SafeXDatabase` must be incremented.
- For MVP/demo, using `.fallbackToDestructiveMigration()` in the database builder is fine.

---

## 6. Firebase Cloud Functions Not Deployed

**Status:** 🔴 ACTIVE (must be done before Gemini explanation or Report features work)

**Symptom:** Alert Detail screen shows "AI analysis unavailable" even with internet. Report button silently fails.

### Fix steps:

1. Open **PowerShell** or **Terminal** (NOT inside Android Studio).
2. Navigate to your project root:
   ```powershell
   cd "C:\Users\Fu Chuin\OneDrive\Desktop\Documents\Kitahack2026\SafeX---Your-AI-powered-and-real-time-safety-expert"
   ```
3. Login to Firebase:
   ```powershell
   firebase login
   ```
   - A browser window opens. Sign in with your Google account.
4. Check you're targeting the right project:
   ```powershell
   firebase projects:list
   ```
   - You should see your `SafeX` project. If not:
     ```powershell
     firebase use YOUR_PROJECT_ID
     ```
5. Deploy the functions:
   ```powershell
   firebase deploy --only functions
   ```
6. If deployment fails with "Billing required":
   - Go to **Firebase Console** → **Project Settings** → **Usage and billing** → **Upgrade to Blaze** (pay-as-you-go).
   - You won't be charged much (or at all) for hackathon usage.
7. If deployment fails with "Vertex AI API not enabled":
   - Go to **Google Cloud Console** → search **"Vertex AI API"** → click **Enable**.
8. Verify deployment:
   ```powershell
   firebase functions:log
   ```
   - Should show your `explainAlert` and `reportAlert` functions deployed.

### Also deploy Firestore rules:
```powershell
firebase deploy --only firestore:rules
```

---

## 7. Gradle Sync Fails: "Cannot resolve" or Plugin Errors

**Status:** 🔴 ACTIVE (happens after code changes to Gradle files)

### Error: "Cannot add extension with name 'kotlin', already registered"
**Fix:**
1. Open `app/build.gradle.kts`.
2. Make sure you do **NOT** have both `id("kotlin-android")` AND `alias(libs.plugins.kotlin.compose)`.
3. The file should have (and currently has) the `id("kotlin-android")` line **commented out**:
   ```kotlin
   plugins {
       alias(libs.plugins.android.application)
       // id("kotlin-android") // <-- THIS MUST STAY COMMENTED OUT
       alias(libs.plugins.kotlin.compose)
       id("com.google.gms.google-services")
       id("com.google.devtools.ksp")
   }
   ```
4. **File → Sync Project with Gradle Files** (or click the elephant icon with the blue arrow).

### Error: "Unresolved reference 'android'" in libs.versions.toml
**Fix:**
1. Open `gradle/libs.versions.toml`.
2. Make sure there's **no duplicate** `kotlin-android` entry in the `[plugins]` section.
3. Current file is correct — the plugin is named `kotlinAndroid` (camelCase, no dash), which avoids the clash.

### Error: "Unresolved reference 'kotlinOptions'"
**Fix:**
1. Open `app/build.gradle.kts`.
2. Replace any `kotlinOptions { jvmTarget = "17" }` block with:
   ```kotlin
   tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
       compilerOptions {
           jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
       }
   }
   ```
3. This is already done in the current file — just make sure the old `kotlinOptions` block stays commented out.

### Error: "Duplicate android {} block"
The current `app/build.gradle.kts` has TWO `android {}` blocks (lines 9–11 and lines 19–56). The first one (`android { // ... existing android block ... }`) is a leftover skeleton.
**Fix:**
1. Open `app/build.gradle.kts`.
2. Delete lines 9–11 (the empty first `android {}` block):
   ```kotlin
   // DELETE THESE 3 LINES:
   android {
   // ... existing android block ...
   }
   ```
3. Re-sync Gradle.

---

## 8. App Crashes on Launch (DataStore Corruption)

**Status:** 🔴 ACTIVE (can happen after unclean kills or schema changes to DataStore prefs)

**Symptom:** App crashes immediately on launch with a stack trace mentioning `DataStore`, `CorruptionException`, or `proto`.

### Fix steps:

1. Uninstall and reinstall the app:
   - ADB:
     ```
     adb uninstall com.safex.app
     ```
   - Or long-press app icon → App info → Uninstall.
2. Run the app again from Android Studio (Run ▶).

### Alternative (keeps other app data):
1. Open a terminal/PowerShell.
2. Run:
   ```
   adb shell rm -rf /data/data/com.safex.app/files/datastore
   ```
3. Relaunch the app.

---

## 9. Emulator: No Google Play Services / Firebase Crash

**Status:** 🔴 ACTIVE (affects emulators created with wrong system image)

**Symptom:** App crashes with `com.google.android.gms.common.api.ApiException` or Firebase `DEVELOPER_ERROR`.

### Fix steps:

1. Open Android Studio → **Tools → Device Manager**.
2. Click **Create Virtual Device** (or edit your existing one).
3. **Critical:** When choosing a system image, pick one with **Google Play** (not just "Google APIs"):
   - In the "Select a system image" step, go to the **Recommended** tab.
   - Look for images that have the **Play Store icon** (▶️) in the "Play Store" column.
   - Recommended: **API 34** or **API 35** with Google Play, **x86_64** architecture.
4. Click **Next** → **Finish**.
5. Start the new emulator.
6. On first boot, sign in to Google Play Store with any Google account (needed for Play Services updates).
7. Run SafeX on this emulator.

### If using a physical device:
- Make sure **Google Play Services** is up to date:
  - **Settings → Apps → Google Play Services** → check for updates.
- Make sure the `SHA-1` fingerprint is added in Firebase Console:
  - Firebase Console → **Project Settings** → **Your apps** → **Android app** → **Add fingerprint**.
  - To get your debug SHA-1, run in Android Studio terminal:
    ```
    ./gradlew signingReport
    ```
  - Copy the `SHA1:` value from the debug variant and paste it in Firebase Console.

---

## 10. Camera Permission Denied (Manual Scan)

**Status:** 🔴 ACTIVE (runtime permission required)

**Symptom:** Camera scan screen shows black screen or crashes.

### Fix steps:

1. When you tap **Scan → Camera**, the app should request camera permission.
2. Tap **Allow** on the system dialog.
3. If you previously denied it:
   - **Android Settings** → **Apps** → **SafeX** → **Permissions** → **Camera** → set to **Allow**.
4. If on emulator:
   - The emulator has a virtual camera by default.
   - If QR scanning doesn't work on emulator, test on a physical device instead.
   - You can point the emulator camera at a QR code displayed on your computer screen.

---

## Quick Reference: "Build Won't Work" Checklist

Run through this in order when the build fails:

| Step | Action | Where |
|------|--------|-------|
| 1 | Kill all Java/Gradle processes | Task Manager |
| 2 | Pause OneDrive syncing | System tray → OneDrive icon |
| 3 | Delete `app/build` and root `build` folders | File Explorer |
| 4 | Delete `.gradle` folder in project root | File Explorer |
| 5 | Open Android Studio | Desktop |
| 6 | File → Invalidate Caches → Invalidate and Restart | Android Studio |
| 7 | Wait for Gradle sync to complete | Android Studio (bottom bar) |
| 8 | Build → Clean Project | Android Studio top menu |
| 9 | Build → Rebuild Project | Android Studio top menu |

If it STILL fails after all 9 steps, read the exact error message in the **Build** tab (bottom of Android Studio) and look it up in the sections above.

---

## Solved Issues Archive

> Move items here when they're fixed. Keep the steps for reference.

*(None yet — all issues above are currently active)*
