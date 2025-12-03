package com.powerschedule.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.powerschedule.app.ui.main.MainScreen
import com.powerschedule.app.ui.schedule.ScheduleScreen
import com.powerschedule.app.ui.settings.NotificationTimePickerScreen
import com.powerschedule.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Schedule : Screen("schedule/{queueId}") {
        fun createRoute(queueId: String) = "schedule/$queueId"
    }
    object Settings : Screen("settings")
    object NotificationTimePicker : Screen("notification_time_picker")
}

@Composable
fun PowerScheduleNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSchedule = { queueId ->
                    navController.navigate(Screen.Schedule.createRoute(queueId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Schedule.route,
            arguments = listOf(navArgument("queueId") { type = NavType.StringType })
        ) { backStackEntry ->
            val queueId = backStackEntry.arguments?.getString("queueId") ?: return@composable
            ScheduleScreen(
                queueId = queueId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationTimePicker = {
                    navController.navigate(Screen.NotificationTimePicker.route)
                }
            )
        }

        composable(Screen.NotificationTimePicker.route) {
            NotificationTimePickerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}