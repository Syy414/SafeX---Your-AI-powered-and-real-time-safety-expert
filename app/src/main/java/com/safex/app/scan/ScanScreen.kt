package com.safex.app.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Which sub-screen is currently shown inside the scan flow.
 */
enum class ScanPage { MENU, LINK, CAMERA, IMAGE }

/**
 * Main scan entry point — dropped into Home tab.
 * 3 options: paste link, pick image (Photo Picker), camera scan.
 */
@Composable
fun ScanScreen(
    initialPage: ScanPage = ScanPage.MENU,
    onNavigateToAlert: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember { ScanViewModel(context) }
    val uiState by viewModel.state.collectAsState()

    // Read device/app locale for language-aware backend responses
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val deviceLang = androidx.core.os.ConfigurationCompat.getLocales(config).get(0)?.language ?: "en"

    var currentPage by remember { mutableStateOf(initialPage) }

    // Photo Picker result
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // We got an image, so run the scan
            scope.launch { viewModel.scanImage(uri) }
            // If we started as IMAGE, we want to see the result on the MENU (or specific result screen).
            // ScanMenu shows the inline result if state is Result.
            // So switching to MENU is fine.
            currentPage = ScanPage.MENU
        } else {
            // User cancelled picker
            currentPage = ScanPage.MENU
        }
    }

    // Auto-launch picker if landing on IMAGE page (e.g. from Home shortcut)
    LaunchedEffect(currentPage) {
        if (currentPage == ScanPage.IMAGE) {
            pickImage.launch("image/*")
        }
    }

    // Helper to go back and reset
    val goBack: () -> Unit = {
        currentPage = ScanPage.MENU
        viewModel.reset()
    }

    // Current save-to-alerts lambda
    val saveToAlerts: () -> Unit = {
        val st = uiState
        if (st is ScanUiState.Result) {
            scope.launch {
                val alertId = viewModel.saveToAlerts(st.result)
                onNavigateToAlert(alertId)
                // We don't goBack() here because we are navigating away.
                // When user comes back, they might see the result again or we can reset.
                // Resetting is probably safer to avoid stale state on back press.
                viewModel.reset()
            }
        }
    }

    when (currentPage) {
        ScanPage.MENU, ScanPage.IMAGE -> ScanMenu(
            uiState = uiState,
            onPasteLink = {
                viewModel.reset()
                currentPage = ScanPage.LINK
            },
            onPickImage = {
                viewModel.reset()
                // Directly launch picker or switch to IMAGE? 
                // Creating a simplified flow: just launch picker here without changing page to IMAGE
                // unless we want to support the deep link logic.
                // But since we support IMAGE page for deep link, might as well use it or just launch.
                pickImage.launch("image/*")
            },
            onCamera = {
                viewModel.reset()
                currentPage = ScanPage.CAMERA
            },
            onSaveToAlerts = saveToAlerts,
            onReset = { viewModel.reset() }
        )

        ScanPage.LINK -> LinkScanScreen(
            uiState = uiState,
            onCheck = { url -> scope.launch { viewModel.scanLink(url, deviceLang) } },
            onSaveToAlerts = saveToAlerts,
            onBack = goBack
        )

        ScanPage.CAMERA -> CameraScanScreen(
            uiState = uiState,
            onCaptured = { uri -> scope.launch { viewModel.scanCameraCapture(uri) } },
            onSaveToAlerts = saveToAlerts,
            onBack = goBack
        )
    }
}

@Composable
private fun ScanMenu(
    uiState: ScanUiState,
    onPasteLink: () -> Unit,
    onPickImage: () -> Unit,
    onCamera: () -> Unit,
    onSaveToAlerts: () -> Unit,
    onReset: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ScanOptionCard(
                emoji = "🔗",
                title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_link_title),
                subtitle = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_link_desc),
                onClick = onPasteLink
            )
            Spacer(modifier = Modifier.height(12.dp))

            ScanOptionCard(
                emoji = "🖼️",
                title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_image_title),
                subtitle = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_image_desc),
                onClick = onPickImage
            )
            Spacer(modifier = Modifier.height(12.dp))

            ScanOptionCard(
                emoji = "📷",
                title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_camera_title),
                subtitle = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_opt_camera_desc),
                onClick = onCamera
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Inline result / scanning state below the cards ───
            when (uiState) {
                is ScanUiState.Scanning -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_progress))
                    }
                }
                is ScanUiState.Result -> {
                    ScanResultScreen(
                        result = uiState.result,
                        onSaveToAlerts = onSaveToAlerts,
                        onScanAgain = onReset
                    )
                }
                is ScanUiState.Error -> {
                    Text(
                        text = "Error: ${uiState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> { /* Idle */ }
            }
        }
    }
}

@Composable
private fun ScanOptionCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
