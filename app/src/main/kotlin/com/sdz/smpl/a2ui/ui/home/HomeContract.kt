package com.sdz.smpl.a2ui.ui.home

import androidx.compose.runtime.Immutable
import com.sdz.smpl.a2ui.domain.model.AgentMode

/**
 * MVI contract of the home screen.
 *
 * - [HomeUiState]: everything the screen renders (single source of truth).
 * - [HomeIntent]: everything the user can do.
 * - [HomeEffect]: one-off events for the UI (navigation).
 */
@Immutable
data class HomeUiState(
    val selectedMode: AgentMode = AgentMode.Default,
    val server: ServerState,
)

/** Reachability of the agent server, as shown on the home screen. */
@Immutable
sealed interface ServerState {
    /** The server address the app uses. */
    val baseUrl: String

    /** A health check is in progress. */
    data class Checking(override val baseUrl: String) : ServerState

    /**
     * The server answered.
     *
     * @property defaultMode the server's own default mode, if it reported one.
     * @property llmAvailable whether the server has an LLM provider configured.
     * @property llmProvider the server's LLM provider, e.g. "claude", if any.
     * @property llmModel the model that provider calls, if any.
     */
    data class Online(
        override val baseUrl: String,
        val defaultMode: AgentMode?,
        val llmAvailable: Boolean,
        val llmProvider: String? = null,
        val llmModel: String? = null,
    ) : ServerState

    /** The server couldn't be reached. */
    data class Offline(override val baseUrl: String, val reason: String) : ServerState
}

/** User intents on the home screen. */
sealed interface HomeIntent {
    /** The user picked an agent mode. */
    data class SelectMode(val mode: AgentMode) : HomeIntent

    /** The user asked to check the server again. */
    data object RefreshServerStatus : HomeIntent

    /** The user tapped "Launch agent". */
    data object LaunchAgent : HomeIntent
}

/** One-off events emitted by the home screen's ViewModel. */
sealed interface HomeEffect {
    /** Open the chat screen. */
    data object NavigateToChat : HomeEffect
}
