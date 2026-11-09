package com.sdz.smpl.a2ui.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sdz.smpl.a2ui.A2uiChatApplication
import com.sdz.smpl.a2ui.ui.a2ui.A2uiRenderer
import com.sdz.smpl.a2ui.ui.chat.ChatViewModel
import com.sdz.smpl.a2ui.ui.home.HomeViewModel

/**
 * Creates every ViewModel of the app from the [AppContainer].
 *
 * Usage in a composable: `viewModel<HomeViewModel>(factory = AppViewModelFactory)`.
 */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = appContainer()
        HomeViewModel(
            agentBaseUrl = container.agentBaseUrl,
            observeAgentMode = container.observeAgentModeUseCase(),
            setAgentMode = container.setAgentModeUseCase(),
            getServerStatus = container.getServerStatusUseCase(),
        )
    }
    initializer {
        val container = appContainer()
        ChatViewModel(
            // A fresh renderer per chat screen: surfaces belong to one conversation.
            renderer = A2uiRenderer(container.a2uiCatalog),
            sendChatMessage = container.sendChatMessageUseCase(),
            sendClientMessage = container.sendClientMessageUseCase(),
            observeAgentMode = container.observeAgentModeUseCase(),
            setAgentMode = container.setAgentModeUseCase(),
            startNewConversation = container.startNewConversationUseCase(),
        )
    }
}

/** Looks up the [AppContainer] owned by the application. */
private fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as A2uiChatApplication).container
