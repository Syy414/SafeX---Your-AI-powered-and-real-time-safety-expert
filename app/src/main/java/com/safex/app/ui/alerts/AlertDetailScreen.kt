package com.safex.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safex.app.R
import com.safex.app.data.AlertRepository
import com.safex.app.data.CloudFunctionsClient
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.local.SafeXDatabase
import com.safex.app.data.models.ExplainAlertResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    alertId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val languageCode = androidx.core.os.ConfigurationCompat.getLocales(config).get(0)?.language ?: "en"

    val viewModel: AlertDetailViewModel = viewModel(
        factory = AlertDetailViewModel.Factory(
            alertId = alertId,
            repository = AlertRepository.getInstance(SafeXDatabase.getInstance(context)),
            languageCode = languageCode,
            functionsClient = CloudFunctionsClient.INSTANCE
        )
    )

    androidx.compose.runtime.LaunchedEffect(languageCode) {
        viewModel.updateLanguage(languageCode)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alert_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is AlertDetailUiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.analyzing_gemini))
                    }
                }

                is AlertDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.news_failed) + ": ${state.message}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text(stringResource(R.string.btn_go_back)) }
                    }
                }

                is AlertDetailUiState.Success -> {
                    AlertDetailContent(
                        alert = state.alert,
                        explanation = state.explanation,
                        onMarkSafe = { viewModel.markSafe(onBack) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertDetailContent(
    alert: AlertEntity,
    explanation: ExplainAlertResponse,
    onMarkSafe: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Header Card with Risk Level
        RiskHeader(explanation.riskLevel, explanation.headline)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Why Flagged
        ResultSection(stringResource(R.string.why_flagged_title), explanation.whyFlagged, Icons.Default.Warning, MaterialTheme.colorScheme.errorContainer)

        Spacer(modifier = Modifier.height(16.dp))

        // 3. What to do
        ResultSection(stringResource(R.string.what_to_do_title), explanation.whatToDoNow, Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondaryContainer)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. What NOT to do
        ResultSection(stringResource(R.string.what_not_to_do_title), explanation.whatNotToDo, Icons.Default.Report, MaterialTheme.colorScheme.errorContainer)

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Action Buttons
        Button(
            onClick = onMarkSafe,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(stringResource(R.string.mark_safe))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (alert.heuristicScore != null) {
            val rulesScorePct = (alert.heuristicScore * 100).toInt()
            val tfliteAvailable = alert.tfliteScore != null && alert.tfliteScore >= 0f
            val tfliteScorePct = if (tfliteAvailable) (alert.tfliteScore!! * 100).toInt() else 0
            val combinedPct = if (tfliteAvailable) {
                ((alert.heuristicScore * 0.20f + alert.tfliteScore!! * 0.80f) * 100).toInt()
            } else {
                rulesScorePct  // heuristics-only mode
            }

            // DEBUG: Get the exact reason why TFLite failed
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val mlError = androidx.compose.runtime.remember { 
                com.safex.app.ml.ScamDetector(ctx).initError ?: "Model loading"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.score_breakdown_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.score_rules, rulesScorePct), style = MaterialTheme.typography.bodySmall)
                    if (tfliteAvailable) {
                        Text(stringResource(R.string.score_tflite, tfliteScorePct), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(stringResource(R.string.score_tflite_na, mlError), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.score_total, combinedPct), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (explanation.notes.isNotBlank() && !explanation.notes.contains("Fallback") && !explanation.notes.contains("Rules detect scam")) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.analysis_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(explanation.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            text = stringResource(R.string.confidence_source, (explanation.confidence * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun RiskHeader(level: String, headline: String) {
    val color = when (level.uppercase()) {
        "HIGH" -> Color.Red
        "MEDIUM" -> Color(0xFFFFA000) // Orange
        else -> Color(0xFF388E3C) // Green
    }

    val localizedLevelText = when (level.uppercase()) {
        "HIGH" -> stringResource(R.string.level_high)
        "MEDIUM" -> stringResource(R.string.level_medium)
        "LOW" -> stringResource(R.string.level_low)
        else -> level.uppercase()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${stringResource(R.string.risk_level_prefix)}$localizedLevelText",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResultSection(title: String, items: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, bgColor: Color) {
    if (items.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", modifier = Modifier.padding(end = 8.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
