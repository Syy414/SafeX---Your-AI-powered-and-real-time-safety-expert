package com.safex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Weekly Summary Card (Real Data)
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
                    AlertItem(
                        title = alert.headline ?: "Suspicious Activity",
                        description = alert.snippetRedacted ?: "Details hidden for safety",
                        riskLevel = try { RiskLevel.valueOf(alert.riskLevel) } catch(_:Exception) { RiskLevel.MEDIUM },
                        timestamp = alert.createdAt, 
                        onClick = { onAlertClick(alert.id) }
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
    title: String,
    description: String,
    riskLevel: RiskLevel,
    timestamp: Long,
    onClick: () -> Unit
) {
    val (color, icon) = when (riskLevel) {
        RiskLevel.HIGH -> DangerRed to Icons.Default.Error
        RiskLevel.MEDIUM -> WarningOrange to Icons.Default.Warning
        RiskLevel.LOW -> SafetyGreen to Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}
