package com.safex.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.safex.app.data.UserPrefs
import com.safex.app.ui.SafeXApp
import com.safex.app.ui.theme.SafeXBlue
import com.safex.app.ui.theme.SafeXTheme
import kotlinx.coroutines.launch

import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SafeXTheme {
                SafeXAppRoot()
            }
        }
    }
}

@Composable
fun SafeXAppRoot() {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val scope = rememberCoroutineScope()

    // Ensure anonymous auth at startup
    LaunchedEffect(Unit) {
        try {
            com.safex.app.data.FirebaseAuthHelper.ensureSignedIn()
        } catch (e: Exception) {
            // Log it but don't crash. App can still work offline/without auth for some features.
            android.util.Log.e("SafeX", "Startup Auth Failed", e)
        }
    }

    val languageTag by prefs.languageTag.collectAsState(initial = "")
    val mode by prefs.mode.collectAsState(initial = "")
    // Use null as initial state to show splash
    val onboarded by prefs.onboarded.collectAsState(initial = null)

    // Apply locale whenever language changes
    LaunchedEffect(languageTag) {
        if (languageTag.isNotBlank()) {
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (current != languageTag) {
                // Only set if different to avoid loop/redraw
                val locales = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }

    // Auto-request POST_NOTIFICATIONS on Android 13+
    val requestPostNotif = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied, user can change in Settings later */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPostNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    when (onboarded) {
        null -> {
            // Loading / Splash Screen
            SplashScreen()
        }
        false -> {
            // Onboarding on first run
            com.safex.app.ui.onboarding.OnboardingScreen(
                initialLanguage = languageTag,
                initialMode = mode
            ) { chosenLang, chosenMode ->
                scope.launch {
                    prefs.setLanguageTag(chosenLang)
                    prefs.setMode(chosenMode)
                    // Auto-enable monitoring if Guardian mode is selected
                    if (chosenMode == "Guardian") {
                        prefs.setNotificationMonitoring(true)
                        prefs.setGalleryMonitoring(true)
                        
                        // Start background worker immediately
                        com.safex.app.guardian.GalleryScanWork.startPeriodic(context)
                    }
                    prefs.setOnboarded(true)
                }
            }
        }
        true -> {
            // Ensure worker is running if enabled (idempotent call)
            LaunchedEffect(mode) {
                 if (mode == "Guardian") {
                     com.safex.app.guardian.GalleryScanWork.startPeriodic(context)
                 }
            }
            
            // Real app with full navigation
            SafeXApp(userPrefs = prefs)
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                 painter = painterResource(id = R.drawable.ic_launcher_foreground),
                 contentDescription = null,
                 modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SafeX",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SafeXBlue
            )
        }
    }
}