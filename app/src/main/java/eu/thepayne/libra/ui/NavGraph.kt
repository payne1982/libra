package eu.thepayne.libra.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.thepayne.libra.R
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.thepayne.libra.ui.screens.ChartScreen
import eu.thepayne.libra.ui.screens.HistoryScreen
import eu.thepayne.libra.ui.screens.LiveWeightScreen
import eu.thepayne.libra.ui.screens.SettingsScreen
import eu.thepayne.libra.ui.screens.SyncScreen

sealed class Screen(val route: String) {
    data object History : Screen("history")
    data object Chart : Screen("chart")
    data object Sync : Screen("sync")
    data object LiveWeight : Screen("live_weight")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomItems = listOf(
        Triple(Screen.History, Icons.Default.History, stringResource(R.string.nav_history)),
        Triple(Screen.Chart, Icons.Default.BarChart, stringResource(R.string.nav_charts)),
        Triple(Screen.Sync, Icons.Default.Sync, stringResource(R.string.nav_sync)),
        Triple(Screen.Settings, Icons.Default.Settings, stringResource(R.string.nav_settings)),
    )

    val showBottomBar = currentDestination?.route != Screen.LiveWeight.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
            startDestination = Screen.History.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Chart.route) { ChartScreen() }
            composable(Screen.Sync.route) {
                SyncScreen(onOpenLiveWeight = { navController.navigate(Screen.LiveWeight.route) })
            }
            composable(Screen.LiveWeight.route) {
                LiveWeightScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
