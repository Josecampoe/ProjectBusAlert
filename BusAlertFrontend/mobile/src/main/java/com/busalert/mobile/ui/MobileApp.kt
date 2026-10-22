package com.busalert.mobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.busalert.mobile.R
import com.busalert.mobile.theme.BusAlertMobileTheme
import com.busalert.mobile.ui.components.NoticeRow
import com.busalert.mobile.ui.history.HistoryScreen
import com.busalert.mobile.ui.routes.RouteDetailScreen
import com.busalert.mobile.ui.routes.RouteDetailViewModel
import com.busalert.mobile.ui.routes.RoutesScreen
import com.busalert.mobile.ui.routes.RoutesViewModel
import com.busalert.mobile.ui.settings.BackendMode
import com.busalert.mobile.ui.settings.SettingsScreen
import com.busalert.mobile.ui.settings.UserDataViewModel
import com.busalert.mobile.ui.stops.StopsScreen
import com.busalert.mobile.ui.stops.StopsViewModel
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.preferences.PreferencesRepository

/** Pestañas principales. */
private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    ROUTES("routes", R.string.tab_routes, Icons.AutoMirrored.Filled.List),
    STOPS("stops", R.string.tab_stops, Icons.Filled.Place),
    HISTORY("history", R.string.tab_history, Icons.Filled.Star),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val ROUTE_DETAIL = "route/{code}?name={name}"
private const val ARG_CODE = "code"
private const val ARG_NAME = "name"

/** Dependencias que la UI necesita (permite pasar dobles en pruebas). */
class MobileAppDependencies(
    val transit: TransitRepository,
    val preferences: PreferencesRepository,
    val mapsEnabled: Boolean,
    val backendMode: BackendMode,
)

@Composable
fun MobileApp(deps: MobileAppDependencies) {
    val userData: UserDataViewModel = viewModel(factory = UserDataViewModel.Factory(deps.preferences))
    val preferences by userData.preferences.collectAsState()

    ProvideAppLanguage(preferences.language) {
        BusAlertMobileTheme(darkTheme = preferences.darkTheme) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route

            Scaffold(
                bottomBar = {
                    if (Tab.entries.any { it.route == currentRoute }) {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.labelRes)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    NavHost(nav, startDestination = Tab.ROUTES.route) {
                        composable(Tab.ROUTES.route) {
                            Column {
                                if (deps.backendMode == BackendMode.DEMO) NoticeRow(stringResource(R.string.demo_notice))
                                RoutesScreen(
                                    viewModel = viewModel(factory = RoutesViewModel.Factory(deps.transit, deps.preferences)),
                                    onOpenRoute = { nav.navigate("route/${Uri.encode(it.code)}?name=${Uri.encode(it.name)}") },
                                )
                            }
                        }
                        composable(
                            ROUTE_DETAIL,
                            arguments = listOf(
                                navArgument(ARG_CODE) { type = NavType.StringType },
                                navArgument(ARG_NAME) { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            val code = entry.arguments?.getString(ARG_CODE).orEmpty()
                            val name = entry.arguments?.getString(ARG_NAME).orEmpty().ifBlank { code }
                            RouteDetailScreen(
                                viewModel = viewModel(
                                    key = code,
                                    factory = RouteDetailViewModel.Factory(code, deps.transit, deps.preferences),
                                ),
                                routeName = name,
                                mapsEnabled = deps.mapsEnabled,
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(Tab.STOPS.route) {
                            StopsScreen(viewModel(factory = StopsViewModel.Factory(deps.transit, deps.preferences)))
                        }
                        composable(Tab.HISTORY.route) { HistoryScreen(userData) }
                        composable(Tab.SETTINGS.route) { SettingsScreen(userData, deps.backendMode) }
                    }
                }
            }
        }
    }
}
