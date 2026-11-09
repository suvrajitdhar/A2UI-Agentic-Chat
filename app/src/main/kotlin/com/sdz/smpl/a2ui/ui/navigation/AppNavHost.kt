package com.sdz.smpl.a2ui.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sdz.smpl.a2ui.di.AppViewModelFactory
import com.sdz.smpl.a2ui.ui.chat.ChatRoute
import com.sdz.smpl.a2ui.ui.chat.ChatViewModel
import com.sdz.smpl.a2ui.ui.home.HomeRoute
import com.sdz.smpl.a2ui.ui.home.HomeViewModel
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable
data object HomeDestination

@Serializable
data object ChatDestination

/**
 * The app's navigation graph: Home → Chat.
 *
 * Each destination gets its own ViewModel scoped to its back-stack entry, so leaving the chat
 * disposes the conversation and opening it again starts a new one.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = HomeDestination, modifier = modifier) {
        composable<HomeDestination> {
            HomeRoute(
                viewModel = viewModel<HomeViewModel>(factory = AppViewModelFactory),
                onLaunchAgent = { navController.navigate(ChatDestination) { launchSingleTop = true } },
            )
        }
        composable<ChatDestination> {
            ChatRoute(
                viewModel = viewModel<ChatViewModel>(factory = AppViewModelFactory),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
