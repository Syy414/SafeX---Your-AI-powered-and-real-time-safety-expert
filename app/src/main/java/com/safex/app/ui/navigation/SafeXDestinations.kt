package com.safex.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Alerts : Screen("alerts")
    object Insights : Screen("insights")
    object Settings : Screen("settings")
    object AlertDetail : Screen("alert_detail")
    object Scan : Screen("scan/{mode}") {
        fun createRoute(mode: String) = "scan/$mode"
    }
}
