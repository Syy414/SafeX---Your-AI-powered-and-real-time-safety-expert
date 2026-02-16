package com.safex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.safex.app.ui.navigation.BottomNavItem
import com.safex.app.ui.navigation.Screen
import com.safex.app.ui.alerts.AlertDetailScreen
import com.safex.app.ui.screens.AlertsScreen
import com.safex.app.ui.screens.HomeScreen
import com.safex.app.ui.insights.InsightsScreen
import com.safex.app.ui.screens.SettingsScreen
import androidx.compose.runtime.collectAsState
import com.safex.app.data.UserPrefs

@Composable
fun SafeXApp(userPrefs: UserPrefs) {
    // No SafeXTheme wrapper here — MainActivity already provides it
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // State
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = com.safex.app.data.local.SafeXDatabase.getInstance(context)
    val alertRepository = com.safex.app.data.AlertRepository.getInstance(db)

    // Global Preferences
    val mode by userPrefs.mode.collectAsState(initial = "Companion")
    val notifEnabled by userPrefs.notificationMonitoringEnabled.collectAsState(initial = false)
    val galleryEnabled by userPrefs.galleryMonitoringEnabled.collectAsState(initial = false)

    val bottomNavItems = listOf(
        BottomNavItem(androidx.compose.ui.res.stringResource(com.safex.app.R.string.nav_home), Screen.Home.route, Icons.Default.Home),
        BottomNavItem(androidx.compose.ui.res.stringResource(com.safex.app.R.string.nav_alerts), Screen.Alerts.route, Icons.Default.Warning),
        BottomNavItem(androidx.compose.ui.res.stringResource(com.safex.app.R.string.nav_insights), Screen.Insights.route, Icons.Default.Insights),
        BottomNavItem(androidx.compose.ui.res.stringResource(com.safex.app.R.string.nav_settings), Screen.Settings.route, Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route
            if (currentRoute != null && !currentRoute.startsWith("alert_detail") && !currentRoute.startsWith("scan/")) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.name) },
                            label = { Text(item.name) },
                            selected = currentDestination?.route == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    isGuardianMode = mode == "Guardian",
                    notifEnabled = notifEnabled,
                    galleryEnabled = galleryEnabled,
                    onScanLink = { navController.navigate(Screen.Scan.createRoute("LINK")) },
                    onScanImage = { navController.navigate(Screen.Scan.createRoute("IMAGE")) },
                    onScanCamera = { navController.navigate(Screen.Scan.createRoute("CAMERA")) }
                )
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(
                    onAlertClick = { alertId ->
                        navController.navigate("${Screen.AlertDetail.route}/$alertId")
                    }
                )
            }
            composable(Screen.Insights.route) {
                InsightsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(userPrefs, alertRepository)
            }
            composable("${Screen.AlertDetail.route}/{alertId}") { backStackEntry ->
                val alertId = backStackEntry.arguments?.getString("alertId") ?: return@composable
                AlertDetailScreen(
                    alertId = alertId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Scan.route) { backStackEntry ->
                val modeStr = backStackEntry.arguments?.getString("mode") ?: "MENU"
                val initialPage = try {
                    com.safex.app.scan.ScanPage.valueOf(modeStr)
                } catch (e: Exception) {
                    com.safex.app.scan.ScanPage.MENU
                }
                com.safex.app.scan.ScanScreen(
                    initialPage = initialPage,
                    onNavigateToAlert = { alertId ->
                        navController.navigate("${Screen.AlertDetail.route}/$alertId")
                    }
                )
            }
        }
    }
}
