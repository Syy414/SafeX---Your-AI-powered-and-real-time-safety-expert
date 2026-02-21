package com.safex.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.safex.app.R

@Composable
fun OnboardingScreen(
    initialLanguage: String,
    initialMode: String,
    onDone: (languageTag: String, mode: String) -> Unit
) {
    var lang by remember { mutableStateOf(if (initialLanguage.isBlank()) "en" else initialLanguage) }
    var mode by remember { mutableStateOf(if (initialMode.isBlank()) "Guardian" else initialMode) }

    var hasNotifAccess by remember { mutableStateOf(false) }
    var hasGalleryAccess by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    
    // Check permissions on resume
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check Notification Listener
                val packageName = context.packageName
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                hasNotifAccess = flat != null && flat.contains(packageName)
                
                // Check Gallery
                if (Build.VERSION.SDK_INT >= 33) {
                     hasGalleryAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                } else {
                     hasGalleryAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Gallery launcher
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasGalleryAccess = isGranted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logo
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
             Image(
                 painter = painterResource(id = R.drawable.ic_launcher_foreground),
                 contentDescription = null,
                 modifier = Modifier.size(80.dp)
             )
        }
        
        Text("Welcome to SafeX", style = MaterialTheme.typography.headlineMedium)

        Text("Choose language:")
        LanguageChoiceRow(selected = lang, onSelect = { lang = it })

        HorizontalDivider()

        Text("Choose mode:")
        ModeChoiceRow(selected = mode, onSelect = { mode = it })
        
        if (mode == "Guardian") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
               Column(modifier = Modifier.padding(16.dp)) {
                   Text("Guardian Permissions Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                   Spacer(Modifier.height(8.dp))
                   
                    // Notification Listener
                    var showNotifDialog by remember { mutableStateOf(false) }

                    if (showNotifDialog) {
                        AlertDialog(
                            onDismissRequest = { showNotifDialog = false },
                            title = { Text("Enable Notification Access") },
                            text = { Text("To detect scam messages, SafeX needs access to notifications.\n\n1. You will be taken to Settings.\n2. Find 'SafeX' in the list.\n3. Turn the switch ON.") },
                            confirmButton = {
                                Button(onClick = {
                                    showNotifDialog = false
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                }) {
                                    Text("Go to Settings")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNotifDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hasNotifAccess) "✅ Notifications" else "❌ Notifications", modifier = Modifier.weight(1f))
                        if (!hasNotifAccess) {
                            Button(onClick = { showNotifDialog = true }) { 
                                Text("Grant") 
                            }
                        }
                    }
                   
                   // Gallery
                   Spacer(Modifier.height(4.dp))
                   Row(verticalAlignment = Alignment.CenterVertically) {
                       Text(if (hasGalleryAccess) "✅ Gallery Scan" else "❌ Gallery Scan", modifier = Modifier.weight(1f))
                       if (!hasGalleryAccess) {
                           Button(onClick = {
                               if (Build.VERSION.SDK_INT >= 33) {
                                   galleryLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                               } else {
                                   galleryLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                               }
                           }) { Text("Grant") }
                       }
                   }
               }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onDone(lang, mode) },
            enabled = mode != "Guardian" || (hasNotifAccess && hasGalleryAccess),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mode == "Guardian" && (!hasNotifAccess || !hasGalleryAccess)) "Grant Permissions to Continue" else "Continue")
        }
    }
}

@Composable
private fun LanguageChoiceRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = selected == "en",
            onClick = { onSelect("en") },
            label = { Text(androidx.compose.ui.res.stringResource(R.string.language_english)) }
        )
        FilterChip(
            selected = selected == "ms",
            onClick = { onSelect("ms") },
            label = { Text(androidx.compose.ui.res.stringResource(R.string.language_malay)) }
        )
        FilterChip(
            selected = selected == "zh",
            onClick = { onSelect("zh") },
            label = { Text(androidx.compose.ui.res.stringResource(R.string.language_chinese)) }
        )
    }
}

@Composable
private fun ModeChoiceRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = selected == "Guardian",
            onClick = { onSelect("Guardian") },
            label = { Text("Guardian") }
        )
        FilterChip(
            selected = selected == "Companion",
            onClick = { onSelect("Companion") },
            label = { Text("Companion") }
        )
    }
}