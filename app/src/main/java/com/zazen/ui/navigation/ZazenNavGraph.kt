package com.zazen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zazen.data.model.TimerState
import com.zazen.service.TimerService
import com.zazen.ui.screens.setup.SetupScreen
import com.zazen.ui.screens.stats.StatsScreen
import com.zazen.ui.screens.timer.TimerScreen

@Composable
fun ZazenNavGraph() {
    val navController = rememberNavController()

    // If the timer is active when the app opens, go straight to the timer screen
    val timerState by TimerService.timerState.collectAsState()
    val startDest = if (timerState !is TimerState.Idle) Screen.Timer.route else Screen.Setup.route

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onStartTimer = {
                    navController.navigate(Screen.Timer.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
            )
        }
        composable(Screen.Timer.route) {
            TimerScreen(
                onTimerDone = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Timer.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
