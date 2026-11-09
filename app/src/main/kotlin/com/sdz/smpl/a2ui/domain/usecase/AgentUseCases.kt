package com.sdz.smpl.a2ui.domain.usecase

import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import com.sdz.smpl.a2ui.domain.model.ServerStatus
import com.sdz.smpl.a2ui.domain.repository.AgentRepository
import com.sdz.smpl.a2ui.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Sends what the user typed to the agent, using the agent mode the user selected.
 */
class SendChatMessageUseCase(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * @param text the user's message; must not be blank.
     * @param metadata A2UI request metadata (client capabilities).
     * @return the agent's events for this turn.
     */
    operator fun invoke(text: String, metadata: Map<String, Any?>): Flow<AgentEvent> = flow {
        require(text.isNotBlank()) { "Message must not be blank" }
        val mode = settingsRepository.agentMode.first()
        emitAll(agentRepository.sendChatMessage(text.trim(), mode, metadata))
    }
}

/**
 * Forwards a message produced by the A2UI renderer (a user action or a client error) to the agent,
 * using the agent mode the user selected.
 */
class SendClientMessageUseCase(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * @param message the renderer's message.
     * @param metadata A2UI request metadata (capabilities, plus the data model for actions).
     * @return the agent's events for this turn.
     */
    operator fun invoke(message: ClientMessage, metadata: Map<String, Any?>): Flow<AgentEvent> = flow {
        val mode = settingsRepository.agentMode.first()
        emitAll(agentRepository.sendClientMessage(message, mode, metadata))
    }
}

/** Checks whether the agent server is reachable and what it supports. */
class GetServerStatusUseCase(private val agentRepository: AgentRepository) {
    /** @return the server status, or a failure if the server can't be reached. */
    suspend operator fun invoke(): Result<ServerStatus> = agentRepository.getServerStatus()
}

/** Starts a fresh conversation: the next message opens a new server session. */
class StartNewConversationUseCase(private val agentRepository: AgentRepository) {
    /** Forgets the current server session. */
    operator fun invoke() = agentRepository.startNewConversation()
}
