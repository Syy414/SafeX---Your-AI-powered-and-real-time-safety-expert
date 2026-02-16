package com.safex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safex.app.ui.theme.SafetyGreen
import com.safex.app.ui.theme.SafeXBlue
import com.safex.app.ui.theme.SafeXBlueLight
import com.safex.app.ui.theme.SurfaceWhite
import com.safex.app.ui.theme.SafeXBlueDark
import androidx.compose.ui.graphics.Brush

@Composable
fun HomeScreen(
    isGuardianMode: Boolean,
    notifEnabled: Boolean,
    galleryEnabled: Boolean,
    onScanLink: () -> Unit,
    onScanImage: () -> Unit,
    onScanCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security, 
                    contentDescription = null, 
                    tint = SafeXBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SafeX",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Dashboard Card
        DashboardCard(isGuardianMode)

        Spacer(modifier = Modifier.height(32.dp))

        // Scan Section
        Text(
            text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.manual_check),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Scan Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
             ScanCard(
                 title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.camera), 
                 icon = Icons.Default.CameraAlt, 
                 modifier = Modifier.weight(1f),
                 onClick = onScanCamera
             )
             Spacer(modifier = Modifier.width(12.dp))
             ScanCard(
                 title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.gallery), 
                 icon = Icons.Default.Image, 
                 modifier = Modifier.weight(1f),
                 onClick = onScanImage
             )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Link Scan - Full Width
        Button(
             onClick = onScanLink,
             modifier = Modifier.fillMaxWidth().height(56.dp),
             shape = RoundedCornerShape(16.dp),
             colors = ButtonDefaults.buttonColors(
                 containerColor = Color.Black,
                 contentColor = Color.White
             )
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(androidx.compose.ui.res.stringResource(com.safex.app.R.string.check_link))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Active Protections List
        Text(
            text = androidx.compose.ui.res.stringResource(com.safex.app.R.string.active_protections),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        ProtectionItem(
            title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.msg_guardian), 
            subtitle = if (notifEnabled) androidx.compose.ui.res.stringResource(com.safex.app.R.string.monitoring_active) else androidx.compose.ui.res.stringResource(com.safex.app.R.string.monitoring_disabled),
            enabled = notifEnabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProtectionItem(
            title = androidx.compose.ui.res.stringResource(com.safex.app.R.string.gal_guardian), 
            subtitle = if (galleryEnabled) androidx.compose.ui.res.stringResource(com.safex.app.R.string.monitoring_active) else androidx.compose.ui.res.stringResource(com.safex.app.R.string.monitoring_disabled),
            enabled = galleryEnabled
        )
    }
}

@Composable
fun DashboardCard(isGuardian: Boolean) {
    val gradient = if (isGuardian) {
        Brush.linearGradient(listOf(SafeXBlue, SafeXBlueDark))
    } else {
        Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
    }
    
    val title = if (isGuardian) androidx.compose.ui.res.stringResource(com.safex.app.R.string.status_protected) else androidx.compose.ui.res.stringResource(com.safex.app.R.string.status_paused)
    val subtitle = if (isGuardian) androidx.compose.ui.res.stringResource(com.safex.app.R.string.desc_protected) else androidx.compose.ui.res.stringResource(com.safex.app.R.string.desc_paused)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent) // We draw background in Box
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            // Background decoration (optional circles)
            Box(
                 modifier = Modifier
                     .align(Alignment.TopEnd)
                     .offset(x = 20.dp, y = (-20).dp)
                     .size(100.dp)
                     .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )
            
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.CenterStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGuardian) androidx.compose.ui.res.stringResource(com.safex.app.R.string.lbl_secure) else androidx.compose.ui.res.stringResource(com.safex.app.R.string.lbl_paused),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun ScanCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = SafeXBlue, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProtectionItem(title: String, subtitle: String, enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (enabled) SafeXBlueLight else Color(0xFFEEEEEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                 Icon(
                     imageVector = if (enabled) Icons.Default.Security else Icons.Default.Shield,
                     contentDescription = null,
                     tint = if (enabled) SafeXBlue else Color.Gray,
                     modifier = Modifier.size(20.dp)
                 )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            Switch(
                checked = enabled, 
                onCheckedChange = null, // Read-only here, change in settings
                enabled = false 
            )
        }
    }
}
