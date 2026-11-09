package com.sdz.smpl.a2ui.data.repository

import com.sdz.smpl.a2ui.data.mapper.AgentEventMapper
import com.sdz.smpl.a2ui.data.remote.AgentApi
import com.sdz.smpl.a2ui.data.remote.AgentApiException
import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.UiMessageKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val requests = mutableListOf<HttpRequestData>()

    /** Builds a repository whose HTTP calls are answered by [handler]. */
    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AgentRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val api = AgentApi(HttpClient(engine), "http://agent.test", json)
        // Unconfined: MockEngine does its own I/O. (A standalone StandardTestDispatcher would never be
        // advanced by runTest's scheduler, and the flow would hang.)
        return AgentRepositoryImpl(api, AgentEventMapper(json), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.sse(body: String, sessionId: String = "s-1") = respond(
        content = ByteReadChannel(body),
        headers = headersOf(
            HttpHeaders.ContentType to listOf("text/event-stream"),
            "X-Session-Id" to listOf(sessionId),
        ),
    )

    private fun HttpRequestData.jsonBody() = json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun `streams a turn and remembers the session for the next request`() = runTest {
        val turn = """
            |: connected
            |
            |event: text
            |data: {"text":"Hi!"}
            |
            |event: a2ui
            |data: {"version":"v0.9","createSurface":{"surfaceId":"welcome","catalogId":"c"}}
            |
            |event: done
            |data: {"sessionId":"s-1"}
            |
        """.trimMargin()
        val repo = repository { sse(turn) }

        val events = repo.sendChatMessage("hi", AgentMode.MOCK, emptyMap()).toList()
        repo.sendChatMessage("again", AgentMode.MOCK, emptyMap()).toList()

        assertEquals(AgentEvent.AgentText("Hi!"), events[0])
        assertEquals(UiMessageKind.CREATE_SURFACE, (events[1] as AgentEvent.UiMessage).kind)
        assertEquals(AgentEvent.TurnComplete("s-1"), events[2])
        assertEquals("/api/agent/chat", requests[0].url.encodedPath)
        assertNull(requests[0].jsonBody()["sessionId"])
        assertEquals("mock", requests[0].jsonBody()["mode"]?.jsonPrimitive?.content)
        assertEquals("s-1", requests[1].jsonBody()["sessionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `new conversation drops the session id`() = runTest {
        val repo = repository { sse("event: done\ndata: {\"sessionId\":\"s-1\"}\n\n") }

        repo.sendChatMessage("hi", AgentMode.MOCK, emptyMap()).toList()
        repo.startNewConversation()
        repo.sendChatMessage("hi", AgentMode.MOCK, emptyMap()).toList()

        assertNull(requests[1].jsonBody()["sessionId"])
    }

    @Test
    fun `HTTP errors surface as AgentApiException`() = runTest {
        val repo = repository {
            respond("""{"error":"\"text\" is required"}""", HttpStatusCode.BadRequest)
        }

        val result = runCatching { repo.sendChatMessage("hi", AgentMode.MOCK, emptyMap()).toList() }

        val error = result.exceptionOrNull()
        assertTrue(error is AgentApiException)
        assertEquals(400, (error as AgentApiException).statusCode)
    }

    @Test
    fun `server status is read from health`() = runTest {
        val repo = repository {
            respond(
                """{"ok":true,"mode":"hybrid","modes":["mock","hybrid","llm"],"llm":true,"llmProvider":"claude","llmModel":"claude-opus-5-5","basicCatalogId":"c"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val status = repo.getServerStatus().getOrThrow()

        assertEquals(AgentMode.HYBRID, status.defaultMode)
        assertEquals(true, status.llmAvailable)
        assertEquals("claude", status.llmProvider)
        assertEquals("claude-opus-5-5", status.llmModel)
        assertEquals("http://agent.test", status.baseUrl)
    }
}
