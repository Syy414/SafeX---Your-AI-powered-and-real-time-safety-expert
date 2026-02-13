package com.safex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safex.app.data.FakeAlertRepository
import com.safex.app.data.RiskLevel
import com.safex.app.ui.theme.DangerRed
import com.safex.app.ui.theme.SafeXBlue
import com.safex.app.ui.theme.SafetyGreen
import com.safex.app.ui.theme.SurfaceWhite
import com.safex.app.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    alertId: String?,
    onBack: () -> Unit
) {
    val alert = remember(alertId) {
        alertId?.let { FakeAlertRepository.getAlert(it) }
    }

    if (alert == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Alert not found.")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = SafeXBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SAFEX ALERT", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Bold,
                            color = SafeXBlue
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(DangerRed.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Potential scam detected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "SafeX has analyzed this ${alert.source.lowercase()} and flagged it as unsafe.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Risk Meter
            RiskMeter(alert.riskLevel)

            Spacer(modifier = Modifier.height(32.dp))
            
            // Explanation Cards
            LabelledCard("Why it was flagged") {
                BulletList(alert.reasons, DangerRed)
            }
            Spacer(modifier = Modifier.height(16.dp))

            LabelledCard("What to do instead") {
                BulletList(alert.actions, SafetyGreen)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Action Buttons
            Text(
                text = "Suspicious Activity",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { /* TODO: Report */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeXBlue)
            ) {
                Icon(Icons.Default.Block, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Block & Ignore")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { /* TODO: Report Scam */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SafeXBlue)
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = SafeXBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report Scam", color = SafeXBlue)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

             TextButton(onClick = { /* TODO Mark Safe */ }) {
                 Text("Mark as Safe", color = Color.Gray)
             }
        }
    }
}

@Composable
fun RiskMeter(riskLevel: RiskLevel) {
    val color = when (riskLevel) {
        RiskLevel.HIGH -> DangerRed
        RiskLevel.MEDIUM -> WarningOrange
        RiskLevel.LOW -> SafetyGreen
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RISK LEVEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("92% Match", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DangerRed) // Mock %
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${riskLevel.name} Risk", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress Bar Visual
            LinearProgressIndicator(
                progress = 0.92f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = Color(0xFFEEEEEE),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                 modifier = Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SAFE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("CRITICAL", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun LabelledCard(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
             modifier = Modifier.fillMaxWidth(),
             shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
             elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}


@Composable
fun BulletList(items: List<String>, bulletColor: Color) {
    Column {
        items.forEach { item ->
            Row(modifier = Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Text("• ", fontWeight = FontWeight.Bold, color = bulletColor, fontSize = 20.sp, modifier = Modifier.offset(y = (-4).dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = Color.Black.copy(alpha = 0.8f))
            }
        }
    }
}
