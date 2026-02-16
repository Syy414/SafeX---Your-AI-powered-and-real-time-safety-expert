package com.safex.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safex.app.data.UserPrefs
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.safex.app.data.AlertRepository

@Composable
fun SettingsScreen(userPrefs: UserPrefs, alertRepository: AlertRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by userPrefs.mode.collectAsState(initial = "Companion")
    val languageTag by userPrefs.languageTag.collectAsState(initial = "en")
    val notifEnabled by userPrefs.notificationMonitoringEnabled.collectAsState(initial = false)
    val galleryEnabled by userPrefs.galleryMonitoringEnabled.collectAsState(initial = false)

    val isGuardian = mode == "Guardian"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(com.safex.app.R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Language Section ---
        Text(stringResource(com.safex.app.R.string.language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        LanguageChoiceRow(
            selected = if (languageTag.isBlank()) "en" else languageTag,
            onSelect = { scope.launch { userPrefs.setLanguageTag(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(com.safex.app.R.string.protection_mode), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        ModeSelectionCard(
            title = stringResource(com.safex.app.R.string.mode_guardian_title),
            description = stringResource(com.safex.app.R.string.mode_guardian_desc),
            selected = isGuardian,
            onClick = { scope.launch { userPrefs.setMode("Guardian") } }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeSelectionCard(
            title = stringResource(com.safex.app.R.string.mode_companion_title),
            description = stringResource(com.safex.app.R.string.mode_companion_desc),
            selected = !isGuardian,
            onClick = { scope.launch { userPrefs.setMode("Companion") } }
        )

        if (isGuardian) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(com.safex.app.R.string.guardian_monitors), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // Gallery Permission Launcher
            val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) scope.launch { userPrefs.setGalleryMonitoring(true) }
            }

            SettingsSwitch(
                title = stringResource(com.safex.app.R.string.monitor_notif_title),
                description = stringResource(com.safex.app.R.string.monitor_notif_desc),
                checked = notifEnabled,
                onCheckedChange = {  wantEnabled ->
                    scope.launch { 
                        if (wantEnabled) {
                            val packageName = context.packageName
                            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                            val isGranted = flat != null && flat.contains(packageName)
                            
                            if (isGranted) {
                                userPrefs.setNotificationMonitoring(true)
                            } else {
                                android.widget.Toast.makeText(context, "Please grant Notification Access first", android.widget.Toast.LENGTH_LONG).show()
                                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                // Do not enable pref yet
                            }
                        } else {
                            userPrefs.setNotificationMonitoring(false)
                        }
                    }
                }
            )
            
            SettingsSwitch(
                title = stringResource(com.safex.app.R.string.monitor_gallery_title),
                description = stringResource(com.safex.app.R.string.monitor_gallery_desc),
                checked = galleryEnabled,
                onCheckedChange = { wantEnabled ->
                    if (wantEnabled) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                scope.launch { userPrefs.setGalleryMonitoring(true) }
                            } else {
                                galleryLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                            }
                        } else {
                             if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                scope.launch { userPrefs.setGalleryMonitoring(true) }
                            } else {
                                galleryLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                    } else {
                         scope.launch { userPrefs.setGalleryMonitoring(false) }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // --- Demo / Testing Section ---
        Text(stringResource(com.safex.app.R.string.testing_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                  scope.launch {
                      alertRepository.generateDemoData()
                      android.widget.Toast.makeText(context, context.getString(com.safex.app.R.string.toast_demo_added), android.widget.Toast.LENGTH_SHORT).show()
                  }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(com.safex.app.R.string.btn_demo_data))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                  scope.launch {
                      alertRepository.deleteAllAlerts()
                      android.widget.Toast.makeText(context, context.getString(com.safex.app.R.string.toast_data_cleared), android.widget.Toast.LENGTH_SHORT).show()
                  }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(com.safex.app.R.string.btn_reset_data))
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(com.safex.app.R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(com.safex.app.R.string.version_info), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ModeSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 40.dp))
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun LanguageChoiceRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = selected == "en",
            onClick = { onSelect("en") },
            label = { Text(stringResource(com.safex.app.R.string.language_english)) }
        )
        FilterChip(
            selected = selected == "ms",
            onClick = { onSelect("ms") },
            label = { Text(stringResource(com.safex.app.R.string.language_malay)) }
        )
        FilterChip(
            selected = selected == "zh",
            onClick = { onSelect("zh") },
            label = { Text(stringResource(com.safex.app.R.string.language_chinese)) }
        )
    }
}
