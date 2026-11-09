package com.sdz.smpl.a2ui.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdz.smpl.a2ui.domain.usecase.GetServerStatusUseCase
import com.sdz.smpl.a2ui.domain.usecase.ObserveAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.SetAgentModeUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel of the home screen: shows whether the agent server is reachable, lets the user pick
 * the agent mode, and launches the chat.
 *
 * @param agentBaseUrl the server address, shown while checking and when offline.
 */
class HomeViewModel(
    private val agentBaseUrl: String,
    observeAgentMode: ObserveAgentModeUseCase,
    private val setAgentMode: SetAgentModeUseCase,
    private val getServerStatus: GetServerStatusUseCase,
) : ViewModel() {

    private val serverState = MutableStateFlow<ServerState>(ServerState.Checking(agentBaseUrl))
    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    private var statusJob: Job? = null

    /** The screen state. */
    val uiState: StateFlow<HomeUiState> =
        combine(observeAgentMode(), serverState) { mode, server -> HomeUiState(selectedMode = mode, server = server) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HomeUiState(server = serverState.value),
            )

    /** One-off events; collect them once from the screen. */
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    init {
        refreshServerStatus()
    }

    /** Handles one user [intent]. */
    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SelectMode -> viewModelScope.launch { setAgentMode(intent.mode) }
            HomeIntent.RefreshServerStatus -> refreshServerStatus()
            HomeIntent.LaunchAgent -> effectChannel.trySend(HomeEffect.NavigateToChat)
        }
    }

    /** Runs a health check, replacing any check still in flight. */
    private fun refreshServerStatus() {
        statusJob?.cancel()
        serverState.value = ServerState.Checking(agentBaseUrl)
        statusJob = viewModelScope.launch {
            serverState.value = getServerStatus().fold(
                onSuccess = { status ->
                    ServerState.Online(
                        baseUrl = status.baseUrl,
                        defaultMode = status.defaultMode,
                        llmAvailable = status.llmAvailable,
                        llmProvider = status.llmProvider,
                        llmModel = status.llmModel,
                    )
                },
                onFailure = { error ->
                    ServerState.Offline(agentBaseUrl, error.message ?: error::class.simpleName.orEmpty())
                },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
