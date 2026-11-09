package com.sdz.smpl.a2ui.data.mapper

import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RequestBodyMapperTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `chat request carries session, text, mode and metadata`() {
        val body = RequestBodyMapper.chatRequest(
            sessionId = "s-1",
            text = "hi",
            mode = AgentMode.MOCK,
            metadata = mapOf("a2uiClientCapabilities" to mapOf("v0.9" to mapOf("supportedCatalogIds" to listOf("cat")))),
        )

        assertEquals(
            json("""{"sessionId":"s-1","text":"hi","mode":"mock","metadata":{"a2uiClientCapabilities":{"v0.9":{"supportedCatalogIds":["cat"]}}}}"""),
            body,
        )
    }

    @Test
    fun `chat request omits the session id on the first turn`() {
        val body = RequestBodyMapper.chatRequest(null, "hi", AgentMode.HYBRID, emptyMap())
        assertFalse(body.containsKey("sessionId"))
    }

    @Test
    fun `user action becomes a spec-compliant action envelope with an ISO timestamp`() {
        val message = ClientMessage.UserAction(
            name = "search_stays",
            surfaceId = "trip",
            sourceComponentId = "trip-search",
            timestampMillis = 0L,
            context = mapOf("budget" to 220, "vibes" to listOf("food"), "checkIn" to null),
        )

        assertEquals(
            json(
                """{"version":"v0.9","action":{"name":"search_stays","surfaceId":"trip","sourceComponentId":"trip-search",
                   "timestamp":"1970-01-01T00:00:00Z","context":{"budget":220,"vibes":["food"],"checkIn":null}}}""",
            ),
            RequestBodyMapper.clientMessage(message),
        )
    }

    @Test
    fun `validation error moves the path out of the context`() {
        val message = ClientMessage.ClientError(
            code = ClientMessage.ClientError.VALIDATION_FAILED,
            surfaceId = "weather",
            message = "Missing required property 'text'",
            context = mapOf("path" to "/components/3/text"),
        )

        assertEquals(
            json(
                """{"version":"v0.9","error":{"code":"VALIDATION_FAILED","surfaceId":"weather",
                   "message":"Missing required property 'text'","path":"/components/3/text"}}""",
            ),
            RequestBodyMapper.clientMessage(message),
        )
    }

    @Test
    fun `other errors keep their context`() {
        val message = ClientMessage.ClientError("RUNTIME_ERROR", "s", "boom", mapOf("detail" to 1))
        assertEquals(
            json("""{"version":"v0.9","error":{"code":"RUNTIME_ERROR","surfaceId":"s","message":"boom","context":{"detail":1}}}"""),
            RequestBodyMapper.clientMessage(message),
        )
    }

    @Test
    fun `event request wraps the message`() {
        val message = ClientMessage.ClientError("RUNTIME_ERROR", "s", "boom")
        val body = RequestBodyMapper.eventRequest("s-1", message, AgentMode.LLM, emptyMap())
        assertEquals(RequestBodyMapper.clientMessage(message), body["message"])
        assertEquals(json("\"llm\""), body["mode"])
    }
}
