package com.safex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import com.safex.app.data.MlKitTranslator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safex.app.data.RiskLevel
import com.safex.app.ui.theme.DangerRed
import com.safex.app.ui.theme.SafeXBlue
import com.safex.app.ui.theme.SafetyGreen
import com.safex.app.ui.theme.SurfaceWhite
import com.safex.app.ui.theme.WarningOrange

@Composable
fun AlertsScreen(
    onAlertClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = remember { com.safex.app.ui.viewmodel.AlertViewModel(context) }
    
    val alerts by viewModel.alerts.collectAsState(initial = emptyList())
    val weeklyCount by viewModel.weeklyCount.collectAsState(initial = 0)
    
    var expandedAlertId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val currentLocale = androidx.core.os.ConfigurationCompat.getLocales(config).get(0)?.language ?: "en"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.activity_history),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        WeeklySummaryCard(count = weeklyCount)

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.latest_threats),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        if (alerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.no_alerts), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alerts) { alert ->
                    val isExpanded = expandedAlertId == alert.id
                    AlertItem(
                        alert = alert,
                        isExpanded = isExpanded,
                        onToggleExpand = { 
                            expandedAlertId = if (isExpanded) null else alert.id
                        },
                        onSeeWhy = { onAlertClick(alert.id) },
                        onAnalyze = {
                            viewModel.analyzeAlert(alert.id, alert.fullMessage ?: alert.snippetRedacted, currentLocale)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklySummaryCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SafeXBlue) 
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.weekly_summary),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.threats_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun AlertItem(
    alert: com.safex.app.data.local.AlertEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSeeWhy: () -> Unit,
    onAnalyze: () -> Unit
) {
    val riskLevel = try { RiskLevel.valueOf(alert.riskLevel) } catch(_: Exception) { RiskLevel.MEDIUM }
    
    val (color, icon) = when (riskLevel) {
        RiskLevel.HIGH -> DangerRed to Icons.Default.Error
        RiskLevel.MEDIUM -> WarningOrange to Icons.Default.Warning
        RiskLevel.LOW -> SafetyGreen to Icons.Default.Info
    }

    val currentLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)?.language ?: "en"
    val defaultHeadline = androidx.compose.ui.res.stringResource(com.safex.app.R.string.suspicious_activity)
    val headline = alert.headline ?: defaultHeadline
    val translatedHeadline = translateDynamicText(headline, currentLocale)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translatedHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alert.snippetRedacted ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray
                )
            }

            // Expanded Content — clean structured view
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // ── Section 1: Detected Content ──────────────────────────────
                Text(
                    text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.detected_content),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = alert.fullMessage ?: alert.snippetRedacted ?: androidx.compose.ui.res.stringResource(com.safex.app.R.string.no_content_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF333333)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Section 2: Gemini Analysis ───────────────────────────────
                Text(
                    text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.gemini_analysis),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (!alert.geminiAnalysis.isNullOrBlank()) {
                    // Analysis is already stored as clean markdown sections — render structured
                    GeminiAnalysisSummaryView(alert.geminiAnalysis, currentLocale)
                } else {
                    // No analysis yet — trigger fetch on first expand
                    androidx.compose.runtime.LaunchedEffect(alert.id) { onAnalyze() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.analyzing_wait), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── "See Full Analysis" button ────────────────────────────────
                OutlinedButton(
                    onClick = onSeeWhy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.see_full_analysis))
                }
            }
        }
    }
}

/**
 * Renders stored JSON analysis as structured "Why flagged" bullets.
 * Falls back to markdown line parsing for legacy stored entries.
 */
@Composable
fun GeminiAnalysisSummaryView(analysisText: String, currentLocale: String) {
    val whyLines: List<String> = remember(analysisText) {
        // Try JSON first (new format)
        try {
            val obj = org.json.JSONObject(analysisText)
            val arr = obj.optJSONArray("whyFlagged")
            if (arr != null && arr.length() > 0) {
                (0 until arr.length()).map { arr.getString(it) }
            } else emptyList()
        } catch (_: Exception) {
            // Fallback: parse old markdown format
            extractSection(analysisText, "**Why flagged:**")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFEEEE), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        if (whyLines.isEmpty()) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.flagged_heuristics),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF333333)
            )
        } else {
            Column {
                whyLines.forEach { line ->
                    val translatedLine = translateDynamicText(line, currentLocale)
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = DangerRed, fontWeight = FontWeight.Bold)
                        Text(
                            text = translatedLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}

/** Extracts bullet lines under [sectionHeader] until the next ** section. */
private fun extractSection(text: String, sectionHeader: String): List<String> {
    val lines = text.lines()
    val startIdx = lines.indexOfFirst { it.trim().startsWith(sectionHeader) }
    if (startIdx < 0) return emptyList()

    val result = mutableListOf<String>()
    for (i in (startIdx + 1)..lines.lastIndex) {
        val line = lines[i].trim()
        if (line.startsWith("**") && line.endsWith("**")) break // next section
        if (line.isBlank()) continue
        result.add(line.removePrefix("- ").removePrefix("• "))
    }
    return result
}

@Composable
fun translateDynamicText(text: String, currentLocale: String): String {
    val translated = produceState(initialValue = text, text, currentLocale) {
        if (text.isBlank() || currentLocale == "en") {
            value = text
        } else {
            value = MlKitTranslator.translate(text, currentLocale)
        }
    }
    return translated.value
}
