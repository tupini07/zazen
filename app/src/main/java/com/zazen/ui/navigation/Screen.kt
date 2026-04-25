package com.zazen.ui.navigation

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Timer : Screen("timer")
    data object Stats : Screen("stats")
}
