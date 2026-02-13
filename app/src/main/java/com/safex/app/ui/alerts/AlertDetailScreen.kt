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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    viewModel: AlertDetailViewModel = viewModel(
        factory = AlertDetailViewModel.Factory(
            alertId = alertId,
            repository = AlertRepository.getInstance(SafeXDatabase.getInstance(LocalContext.current)),
            functionsClient = CloudFunctionsClient.INSTANCE
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert Details") },
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
                        Text("Analyzing with Gemini...")
                    }
                }

                is AlertDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Error: ${state.message}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Go Back") }
                    }
                }

                is AlertDetailUiState.Success -> {
                    AlertDetailContent(
                        alert = state.alert,
                        explanation = state.explanation,
                        onReport = { viewModel.reportAlert(onBack) },
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
    onReport: () -> Unit,
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
        ResultSection("Why SafeX flagged this", explanation.whyFlagged, Icons.Default.Warning, MaterialTheme.colorScheme.errorContainer)

        Spacer(modifier = Modifier.height(16.dp))

        // 3. What to do
        ResultSection("What you should do", explanation.whatToDoNow, Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondaryContainer)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. What NOT to do
        ResultSection("What NOT to do", explanation.whatNotToDo, Icons.Default.Report, MaterialTheme.colorScheme.errorContainer)

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Action Buttons
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onReport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Report Scam")
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(
                onClick = onMarkSafe,
                modifier = Modifier.weight(1f)
            ) {
                Text("Mark as Safe")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Confidence: ${(explanation.confidence * 100).toInt()}% • Source: Gemini",
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

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "RISK LEVEL: ${level.uppercase()}",
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
