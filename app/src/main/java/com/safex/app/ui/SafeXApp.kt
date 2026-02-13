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
import com.safex.app.ui.theme.SafeXTheme
import com.safex.app.ui.screens.AlertDetailScreen
import com.safex.app.ui.screens.AlertsScreen
import com.safex.app.ui.screens.HomeScreen
import com.safex.app.ui.insights.InsightsScreen
import com.safex.app.ui.screens.SettingsScreen
import androidx.compose.runtime.collectAsState
import com.safex.app.data.UserPrefs

@Composable
fun SafeXApp(userPrefs: UserPrefs) {
    SafeXTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        
        // Global State
        val mode by userPrefs.mode.collectAsState(initial = "Companion")
        val notifEnabled by userPrefs.notificationMonitoringEnabled.collectAsState(initial = false)
        val galleryEnabled by userPrefs.galleryMonitoringEnabled.collectAsState(initial = false)

        val bottomNavItems = listOf(
            BottomNavItem("Home", Screen.Home.route, Icons.Default.Home),
            BottomNavItem("Alerts", Screen.Alerts.route, Icons.Default.Warning),
            BottomNavItem("Insights", Screen.Insights.route, Icons.Default.Insights),
            BottomNavItem("Settings", Screen.Settings.route, Icons.Default.Settings)
        )

        Scaffold(
            bottomBar = {
                // Hide bottom bar on detail screens if desired, but for now show always or check route
                val currentRoute = currentDestination?.route
                if (currentRoute != null && !currentRoute.startsWith("alert_detail")) {
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
                    SettingsScreen(userPrefs)
                }
                composable("${Screen.AlertDetail.route}/{alertId}") { backStackEntry ->
                    val alertId = backStackEntry.arguments?.getString("alertId")
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
                    com.safex.app.scan.ScanScreen(initialPage = initialPage)
                }
            }
        }
    }
}
