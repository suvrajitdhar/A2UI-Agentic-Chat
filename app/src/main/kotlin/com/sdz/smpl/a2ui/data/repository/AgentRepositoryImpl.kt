package com.sdz.smpl.a2ui.data.repository

import com.sdz.smpl.a2ui.data.mapper.AgentEventMapper
import com.sdz.smpl.a2ui.data.mapper.RequestBodyMapper
import com.sdz.smpl.a2ui.data.remote.AgentApi
import com.sdz.smpl.a2ui.data.remote.sse.ServerSentEvent
import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import com.sdz.smpl.a2ui.domain.model.ServerStatus
import com.sdz.smpl.a2ui.domain.repository.AgentRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * [AgentRepository] backed by the A2UI agent server.
 *
 * Owns the server session id: it is learned from the first response (header, then `done` event)
 * and sent with every later request, so the server can keep its mirror of the app's surfaces.
 *
 * @param ioDispatcher where network I/O runs (`Dispatchers.IO` in the app).
 */
class AgentRepositoryImpl(
    private val api: AgentApi,
    private val eventMapper: AgentEventMapper,
    private val ioDispatcher: CoroutineDispatcher,
) : AgentRepository {

    @Volatile
    private var sessionId: String? = null

    // The body is built inside flow {} so it carries the session id current at *collection* time
    // (a queued turn must use the id learned from the turn before it).
    override fun sendChatMessage(text: String, mode: AgentMode, metadata: Map<String, Any?>): Flow<AgentEvent> =
        flow {
            val body = RequestBodyMapper.chatRequest(sessionId, text, mode, metadata)
            emitAll(api.streamChat(body, ::rememberSession))
        }.toAgentEvents()

    override fun sendClientMessage(
        message: ClientMessage,
        mode: AgentMode,
        metadata: Map<String, Any?>,
    ): Flow<AgentEvent> =
        flow {
            val body = RequestBodyMapper.eventRequest(sessionId, message, mode, metadata)
            emitAll(api.streamEvent(body, ::rememberSession))
        }.toAgentEvents()

    override suspend fun getServerStatus(): Result<ServerStatus> = withContext(ioDispatcher) {
        try {
            val health = api.health()
            Result.success(
                ServerStatus(
                    baseUrl = api.baseUrl,
                    defaultMode = AgentMode.fromWireValue(health.mode),
                    llmAvailable = health.llm,
                    llmProvider = health.llmProvider,
                    llmModel = health.llmModel,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun startNewConversation() {
        sessionId = null
    }

    private fun rememberSession(id: String) {
        sessionId = id
    }

    /** Maps raw SSE events to domain events, tracking the session id from `done`. */
    private fun Flow<ServerSentEvent>.toAgentEvents(): Flow<AgentEvent> =
        mapNotNull(eventMapper::map)
            .onEach { event -> if (event is AgentEvent.TurnComplete) event.sessionId?.let(::rememberSession) }
            .flowOn(ioDispatcher)
}
