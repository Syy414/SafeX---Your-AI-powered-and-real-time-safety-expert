package com.safex.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.safex.app.data.UserPrefs
import com.safex.app.ui.theme.SafeXTheme
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem

class MainActivity : ComponentActivity() {
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

    // 16. Agent 6: Ensure anonymous auth at startup
    LaunchedEffect(Unit) {
        com.safex.app.data.FirebaseAuthHelper.ensureSignedIn()
    }

    val languageTag by prefs.languageTag.collectAsState(initial = "")
    val mode by prefs.mode.collectAsState(initial = "")
    val onboarded by prefs.onboarded.collectAsState(initial = false)
    var showInsights by remember { mutableStateOf(false) }

    // 14A.5 Apply locale whenever language changes
    LaunchedEffect(languageTag) {
        if (languageTag.isBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
    }

    if (!onboarded) {
        // 14A.6 Onboarding on first run
        // Make sure this exists: app/src/main/java/com/safex/app/ui/onboarding/OnboardingScreen.kt
        com.safex.app.ui.onboarding.OnboardingScreen(
            initialLanguage = languageTag,
            initialMode = mode
        ) { chosenLang, chosenMode ->
            scope.launch {
                prefs.setLanguageTag(chosenLang)
                prefs.setMode(chosenMode)
                prefs.setOnboarded(true)
            }
        }
    } else {
        // Interim Tab Navigation for verification
        var currentTab by remember { mutableStateOf("Home") }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == "Home",
                        onClick = { currentTab = "Home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Insights",
                        onClick = { currentTab = "Insights" },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Insights") },
                        label = { Text("Insights") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    "Home" -> HomeDebugPingScreen()
                    "Insights" -> com.safex.app.ui.insights.InsightsScreen()
                }
            }
        }
    }
}

@Composable
fun HomeDebugPingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Idle") }
    var loading by remember { mutableStateOf(false) }

    // Android 13+ permission to SHOW notifications
    val requestPostNotif = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "POST_NOTIFICATIONS: GRANTED" else "POST_NOTIFICATIONS: DENIED"
    }

    fun ensurePostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            status = "POST_NOTIFICATIONS not required (< Android 13)"
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!ok) requestPostNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        else status = "POST_NOTIFICATIONS already granted"
    }

    fun openNotificationAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    // Manual picker (no READ_MEDIA_IMAGES needed)
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            status = "Manual scan cancelled"
            return@rememberLauncherForActivityResult
        }

        status = "OCR running..."
        scope.launch {
            try {
                // This must exist if you want OCR now:
                // app/src/main/java/com/safex/app/guardian/GalleryTextExtractor.kt
                val text = com.safex.app.guardian.GalleryTextExtractor
                    .extractText(context, uri)
                    .trim()

                status = if (text.isBlank()) "OCR result: empty"
                else "OCR OK: ${text.take(120)}"

            } catch (e: Exception) {
                status = "OCR ERROR: ${e.message ?: e.toString()}"
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SafeX", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Text("Status: $status")
            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = { ensurePostNotificationsPermission() }) {
                Text("Request POST_NOTIFICATIONS")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = { openNotificationAccessSettings() }) {
                Text("Open Notification Access Settings")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = { pickImage.launch("image/*") }) {
                Text("Scan Image (Manual Picker)")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val db = com.safex.app.data.local.SafeXDatabase.getInstance(context)
                            val dao = db.alertDao()
                            val id = java.util.UUID.randomUUID().toString()
                            val entity = com.safex.app.data.local.AlertEntity(
                                id = id,
                                createdAt = System.currentTimeMillis(),
                                type = "NOTIFICATION",
                                riskLevel = "HIGH",
                                category = "impersonation",
                                tacticsJson = "[\"urgency\",\"authority\"]",
                                snippetRedacted = "Bank Negara: Akaun anda telah dibekukan. Sila hubungi 012-3456789 segera.",
                                extractedUrl = null,
                                headline = "Possible authority impersonation detected"
                            )
                            dao.insert(entity)
                            com.safex.app.guardian.SafeXNotificationHelper.postWarningNotification(context, entity)
                            status = "Sample alert created: $id"
                        } catch (e: Exception) {
                            status = "Error creating alert: ${e.message}"
                            e.printStackTrace()
                        }
                    }
                }
            ) {
                Text("Create Sample Alert (Debug)")
            }

            Spacer(modifier = Modifier.height(20.dp))


        }
    }
}