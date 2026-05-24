package studio.freestyle.labs.danjiangsunseeker.presentation.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.presentation.ar.ARScreen
import studio.freestyle.labs.danjiangsunseeker.presentation.calendar.GoldenCalendarScreen
import studio.freestyle.labs.danjiangsunseeker.presentation.hotspot.HotspotDetailScreen
import studio.freestyle.labs.danjiangsunseeker.presentation.hotspot.HotspotListScreen
import studio.freestyle.labs.danjiangsunseeker.presentation.map.MapScreen
import studio.freestyle.labs.danjiangsunseeker.presentation.simulator.FocalSimulatorScreen

private sealed class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    data object Hotspots : TopLevelDestination("hotspots", R.string.tab_hotspots, Icons.Outlined.LocationOn)
    data object Map : TopLevelDestination("map", R.string.tab_map, Icons.Outlined.Map)
    data object Simulator : TopLevelDestination("simulator", R.string.tab_simulator, Icons.Outlined.Tune)
    data object Ar : TopLevelDestination("ar", R.string.tab_ar, Icons.Outlined.CameraAlt)
    data object Calendar : TopLevelDestination("calendar", R.string.tab_calendar, Icons.Outlined.CalendarMonth)
}

private val TopLevelDestinations = listOf(
    TopLevelDestination.Hotspots,
    TopLevelDestination.Map,
    TopLevelDestination.Simulator,
    TopLevelDestination.Ar,
    TopLevelDestination.Calendar,
)

@Composable
fun DanjiangApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestinations.forEach { dest ->
                    val selected = currentRoute?.startsWith(dest.route) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Hotspots.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Hotspots.route) {
                HotspotListScreen(onHotspotClick = { id ->
                    navController.navigate("hotspot_detail/$id")
                })
            }
            composable("hotspot_detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                HotspotDetailScreen(hotspotId = id, onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.Map.route) { MapScreen() }
            composable(TopLevelDestination.Simulator.route) { FocalSimulatorScreen() }
            composable(TopLevelDestination.Ar.route) { ARScreen() }
            composable(TopLevelDestination.Calendar.route) { GoldenCalendarScreen() }
        }
    }
}
