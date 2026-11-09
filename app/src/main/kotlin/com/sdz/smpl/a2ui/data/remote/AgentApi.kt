package com.sdz.smpl.a2ui.data.remote

import com.sdz.smpl.a2ui.data.remote.dto.HealthDto
import com.sdz.smpl.a2ui.data.remote.sse.ServerSentEvent
import com.sdz.smpl.a2ui.data.remote.sse.SseEventParser
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Thrown when the agent server answers with a non-2xx status.
 *
 * @property statusCode the HTTP status code.
 * @property body the response body (the server sends `{"error": …, "details": …}`).
 */
class AgentApiException(val statusCode: Int, val body: String) :
    Exception("Agent server returned HTTP $statusCode: $body")

/**
 * HTTP client for the A2UI agent server.
 *
 * Each turn is a POST whose response is an SSE stream (`text/event-stream`) that the server closes
 * when the turn ends. Parsing is done here with [SseEventParser] rather than Ktor's SSE plugin,
 * because this protocol has no reconnection semantics: one request, one finite stream.
 *
 * @param client a Ktor client whose engine has no request timeout (turns can stream for a while).
 * @param baseUrl e.g. `http://10.0.2.2:3000`, without a trailing slash.
 */
class AgentApi(
    private val client: HttpClient,
    val baseUrl: String,
    private val json: Json,
) {
    /**
     * Posts [body] to `/api/agent/chat` and streams the response events.
     *
     * @param onSessionId called with the `X-Session-Id` response header as soon as the stream opens.
     */
    fun streamChat(body: JsonObject, onSessionId: (String) -> Unit): Flow<ServerSentEvent> =
        streamTurn(CHAT_PATH, body, onSessionId)

    /** Posts [body] to `/api/agent/event` and streams the response events. See [streamChat]. */
    fun streamEvent(body: JsonObject, onSessionId: (String) -> Unit): Flow<ServerSentEvent> =
        streamTurn(EVENT_PATH, body, onSessionId)

    /**
     * Fetches `GET /health`.
     *
     * @throws AgentApiException on a non-2xx response; Ktor/IO exceptions if unreachable.
     */
    suspend fun health(): HealthDto {
        val response = client.get("$baseUrl$HEALTH_PATH")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw AgentApiException(response.status.value, text)
        return json.decodeFromString(text)
    }

    /**
     * Opens one turn. The flow is cold: the request is sent on collection, and cancelling the
     * collector closes the connection, which the server notices and stops the turn.
     */
    private fun streamTurn(path: String, body: JsonObject, onSessionId: (String) -> Unit): Flow<ServerSentEvent> =
        flow {
            client.preparePost("$baseUrl$path") {
                accept(ContentType.Text.EventStream)
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw AgentApiException(response.status.value, response.bodyAsText())
                }
                response.headers[SESSION_HEADER]?.let(onSessionId)

                val channel = response.bodyAsChannel()
                val parser = SseEventParser()
                while (true) {
                    val line = channel.readLine() ?: break
                    parser.feed(line)?.let { emit(it) }
                }
                parser.finish()?.let { emit(it) }
            }
        }

    private companion object {
        const val CHAT_PATH = "/api/agent/chat"
        const val EVENT_PATH = "/api/agent/event"
        const val HEALTH_PATH = "/health"
        const val SESSION_HEADER = "X-Session-Id"
    }
}
