package com.safex.app.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays scan result: risk badge, reasons, next steps, and actions.
 */
@Composable
fun ScanResultScreen(
    result: ScanResult,
    onSaveToAlerts: () -> Unit,
    onScanAgain: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ── Risk Badge ──────────────────────────────────
        val (badgeColor, badgeText) = when (result.riskLevel) {
            RiskLevel.HIGH -> Color(0xFFD32F2F) to androidx.compose.ui.res.stringResource(com.safex.app.R.string.risk_high)
            RiskLevel.MEDIUM -> Color(0xFFFF9800) to androidx.compose.ui.res.stringResource(com.safex.app.R.string.risk_medium)
            RiskLevel.LOW -> Color(0xFF4CAF50) to androidx.compose.ui.res.stringResource(com.safex.app.R.string.risk_low)
            RiskLevel.SAFE -> Color(0xFF2E7D32) to androidx.compose.ui.res.stringResource(com.safex.app.R.string.risk_safe)
            RiskLevel.UNKNOWN -> Color(0xFF757575) to androidx.compose.ui.res.stringResource(com.safex.app.R.string.risk_unknown)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(badgeColor.copy(alpha = 0.12f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Extracted Text / Keyword (Shown BEFORE See Why) ────────────────
        result.extractedText?.takeIf { it.isNotBlank() }?.let { text ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_result_text_header),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (text.length > 500) text.take(500) + "…" else text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // ── Extracted URL (if any) ────────────────
        result.extractedUrl?.let { url ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.scan_result_url_header),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // NOTE: "Why it was flagged" and "Next Steps" are hidden here.
        // User must click "See Why" to go to Alert Detail for full Gemini explanation.

        // ── Action buttons ───────────────────────────────
        Spacer(modifier = Modifier.weight(1f)) // Push buttons to bottom if feasible, or just spacer
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.weight(1f)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.btn_scan_again))
            }

            if (result.riskLevel == RiskLevel.HIGH || result.riskLevel == RiskLevel.MEDIUM) {
                Button(
                    onClick = onSaveToAlerts,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.btn_see_why), color = Color.White)
                }
            } else {
                // Safe/Low risk — let user run AI analysis for confirmation
                Button(
                    onClick = onSaveToAlerts,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.btn_detect_ai))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
