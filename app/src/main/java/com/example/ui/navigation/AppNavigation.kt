package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.activity.AddActivityScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.SignupScreen
import com.example.ui.bmi.BmiCalculatorScreen
import com.example.ui.components.CustomBottomNav
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val currentUser by viewModel.currentUser.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    val startDestination = if (currentUser != null) "dashboard" else "login"

    val showBottomBar = currentRoute in listOf("dashboard", "add_activity", "history", "bmi")

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar && currentUser != null) {
                CustomBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    authRepository = viewModel.authRepository,
                    onNavigateToSignup = { navController.navigate("signup") },
                    onLoginSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable("signup") {
                SignupScreen(
                    authRepository = viewModel.authRepository,
                    onNavigateToLogin = { navController.popBackStack() },
                    onSignupSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("signup") { inclusive = true }
                        }
                    }
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToAddActivity = { navController.navigate("add_activity") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }

            composable("add_activity") {
                AddActivityScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToAddActivity = { navController.navigate("add_activity") }
                )
            }

            composable("bmi") {
                BmiCalculatorScreen(viewModel = viewModel)
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
