package com.sdz.smpl.a2ui.domain.repository

import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import com.sdz.smpl.a2ui.domain.model.ServerStatus
import kotlinx.coroutines.flow.Flow

/**
 * Talks to the agent. Implementations keep track of the conversation (session) so callers only
 * deal with messages and events.
 */
interface AgentRepository {

    /**
     * Sends a chat message and streams the agent's response.
     *
     * @param text what the user typed.
     * @param mode how the agent should answer.
     * @param metadata A2UI request metadata, e.g. `{"a2uiClientCapabilities": …}`.
     * @return a cold flow of events that completes after [AgentEvent.TurnComplete]. It throws if
     *   the server can't be reached or rejects the request.
     */
    fun sendChatMessage(text: String, mode: AgentMode, metadata: Map<String, Any?>): Flow<AgentEvent>

    /**
     * Sends a renderer message (user action or client error) and streams the agent's response.
     *
     * @param message the A2UI client-to-server message.
     * @param mode how the agent should answer.
     * @param metadata A2UI request metadata (capabilities and, if present, the data model).
     * @return a cold flow of events, as for [sendChatMessage].
     */
    fun sendClientMessage(message: ClientMessage, mode: AgentMode, metadata: Map<String, Any?>): Flow<AgentEvent>

    /**
     * Checks whether the server is reachable.
     *
     * @return the server's status, or a failure if it can't be reached.
     */
    suspend fun getServerStatus(): Result<ServerStatus>

    /** Forgets the current conversation; the next message starts a new server session. */
    fun startNewConversation()
}
