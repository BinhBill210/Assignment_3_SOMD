package com.example.assignment_3.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.example.assignment_3.notifications.NotificationHelper
import com.example.assignment_3.ui.assessment.AssessmentDetailScreen
import com.example.assignment_3.ui.assessment.AssessmentFormScreen
import com.example.assignment_3.ui.dashboard.DashboardScreen
import com.example.assignment_3.ui.focus.FocusTimerScreen
import com.example.assignment_3.ui.navigation.AssessmentDetailRoute
import com.example.assignment_3.ui.navigation.AssessmentFormRoute
import com.example.assignment_3.ui.navigation.DashboardRoute
import com.example.assignment_3.ui.navigation.FocusTimerRoute
import com.example.assignment_3.ui.navigation.UnitDetailRoute
import com.example.assignment_3.ui.navigation.UnitFormRoute
import com.example.assignment_3.ui.navigation.UnitListRoute
import com.example.assignment_3.ui.units.UnitDetailScreen
import com.example.assignment_3.ui.units.UnitFormScreen
import com.example.assignment_3.ui.units.UnitListScreen
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class TopLevelDestination(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, DashboardRoute::class, "Dashboard", Icons.Filled.Dashboard),
    TopLevelDestination(UnitListRoute, UnitListRoute::class, "Units", Icons.Filled.School)
)

@Composable
fun StudyHiveApp(navController: NavHostController = rememberNavController()) {
    val snackbarHostState = remember { SnackbarHostState() }
    // App-level scope: a "Unit added" message must outlive the form screen that triggered it.
    val appScope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { dest -> currentDestination?.hasRoute(dest.routeClass) == true }

    // SnackbarHostState queues messages; replace the visible one so an Undo is never stuck behind stale info.
    val showSnackbar: suspend (String, String?) -> Boolean = { text, action ->
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text, action, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed
    }
    val popWithMessage: (String) -> Unit = { text ->
        navController.popBackStack()
        appScope.launch { showSnackbar(text, null) }
    }

    Scaffold(
        // Each screen draws its own top app bar and handles status-bar insets itself.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
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
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onAssessmentClick = { navController.navigate(AssessmentDetailRoute(it)) },
                    onUnitClick = { navController.navigate(UnitDetailRoute(it)) },
                    onAddUnit = { navController.navigate(UnitFormRoute()) }
                )
            }
            composable<UnitListRoute> {
                UnitListScreen(
                    onUnitClick = { navController.navigate(UnitDetailRoute(it)) },
                    onAddUnit = { navController.navigate(UnitFormRoute()) }
                )
            }
            composable<UnitDetailRoute> {
                UnitDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditUnit = { navController.navigate(UnitFormRoute(it)) },
                    onAddAssessment = { navController.navigate(AssessmentFormRoute(unitId = it)) },
                    onAssessmentClick = { navController.navigate(AssessmentDetailRoute(it)) },
                    onDeleted = popWithMessage,
                    onShowSnackbar = showSnackbar
                )
            }
            composable<UnitFormRoute> {
                UnitFormScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = popWithMessage,
                    onShowSnackbar = showSnackbar
                )
            }
            composable<AssessmentDetailRoute>(
                deepLinks = listOf(navDeepLink<AssessmentDetailRoute>(basePath = NotificationHelper.DEEP_LINK_BASE))
            ) {
                AssessmentDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { unitId, assessmentId ->
                        navController.navigate(AssessmentFormRoute(unitId = unitId, assessmentId = assessmentId))
                    },
                    onStartFocus = { navController.navigate(FocusTimerRoute(it)) },
                    onDeleted = popWithMessage,
                    onShowSnackbar = showSnackbar
                )
            }
            composable<AssessmentFormRoute> {
                AssessmentFormScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = popWithMessage,
                    onShowSnackbar = showSnackbar
                )
            }
            composable<FocusTimerRoute> {
                FocusTimerScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = popWithMessage,
                    onShowSnackbar = showSnackbar
                )
            }
        }
    }
}
