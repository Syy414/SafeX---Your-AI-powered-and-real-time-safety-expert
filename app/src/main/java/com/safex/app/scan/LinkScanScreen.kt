package com.safex.app.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Paste-link scan screen.
 * User enters a URL, taps Check, sees result (placeholder until backend ready).
 */
@Composable
fun LinkScanScreen(
    uiState: ScanUiState,
    onCheck: (String) -> Unit,
    onSaveToAlerts: () -> Unit,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "🔗 Check a Link",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Paste a suspicious URL below to check if it's safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            placeholder = { Text("https://example.com/...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            Button(
                onClick = { onCheck(url.trim()) },
                enabled = url.isNotBlank() && uiState !is ScanUiState.Scanning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Check")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Result area ──────────────────────────────
        when (uiState) {
            is ScanUiState.Scanning -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Checking link…")
                }
            }

            is ScanUiState.Result -> {
                ScanResultScreen(
                    result = uiState.result,
                    onSaveToAlerts = onSaveToAlerts,
                    onScanAgain = onBack
                )
            }

            is ScanUiState.Error -> {
                Text(
                    text = "Error: ${uiState.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> { /* Idle — nothing to show yet */ }
        }
    }
}
