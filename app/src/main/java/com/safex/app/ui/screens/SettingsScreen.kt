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

@Composable
fun SettingsScreen(userPrefs: UserPrefs) {
    val scope = rememberCoroutineScope()
    val mode by userPrefs.mode.collectAsState(initial = "Companion")
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
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Protection Mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        ModeSelectionCard(
            title = "Guardian Mode (Proactive)",
            description = "Automatically scans notifications and new images for threats.",
            selected = isGuardian,
            onClick = { scope.launch { userPrefs.setMode("Guardian") } }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeSelectionCard(
            title = "Companion Mode (Manual)",
            description = "Only scans when you manually choose to.",
            selected = !isGuardian,
            onClick = { scope.launch { userPrefs.setMode("Companion") } }
        )

        if (isGuardian) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Guardian Monitors", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                title = "Notification Monitoring",
                description = "Scan incoming messages for scam patterns.",
                checked = notifEnabled,
                onCheckedChange = { scope.launch { userPrefs.setNotificationMonitoring(it) } }
            )
            
            SettingsSwitch(
                title = "Gallery Monitoring",
                description = "Scan new screenshots and images for threats.",
                checked = galleryEnabled,
                onCheckedChange = { scope.launch { userPrefs.setGalleryMonitoring(it) } }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text("About SafeX", style = MaterialTheme.typography.titleMedium)
        Text("Version 1.0 (KitaHack 2026)", style = MaterialTheme.typography.bodyMedium)
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
