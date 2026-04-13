package com.example.treksafetyapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(viewModel: TrekViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onTimeout = {
                navController.navigate("setup") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("setup") {
            SetupScreen(
                viewModel = viewModel,
                onNavigateToTracking = {
                    navController.navigate("tracking") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("tracking") {
            TrackingScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.navigate("setup") {
                        popUpTo("tracking") { inclusive = true }
                    }
                }
            )
        }
    }
}
