package com.sdz.smpl.a2ui.data.mapper

import com.sdz.smpl.a2ui.data.remote.sse.ServerSentEvent
import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.UiMessageKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentEventMapperTest {

    private val mapper = AgentEventMapper(Json { ignoreUnknownKeys = true })

    @Test
    fun `a2ui event keeps the raw JSON and reads kind and surface id`() {
        val raw = """{"version":"v0.9","createSurface":{"surfaceId":"trip","catalogId":"x","sendDataModel":true}}"""

        val event = mapper.map(ServerSentEvent("a2ui", raw))

        assertEquals(AgentEvent.UiMessage(json = raw, kind = UiMessageKind.CREATE_SURFACE, surfaceId = "trip"), event)
    }

    @Test
    fun `recognises all four A2UI message types`() {
        val kinds = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface").map { key ->
            (mapper.map(ServerSentEvent("a2ui", """{"version":"v0.9","$key":{"surfaceId":"s"}}""")) as AgentEvent.UiMessage).kind
        }
        assertEquals(
            listOf(
                UiMessageKind.CREATE_SURFACE,
                UiMessageKind.UPDATE_COMPONENTS,
                UiMessageKind.UPDATE_DATA_MODEL,
                UiMessageKind.DELETE_SURFACE,
            ),
            kinds,
        )
    }

    @Test
    fun `unknown A2UI message is passed through as UNKNOWN`() {
        val event = mapper.map(ServerSentEvent("a2ui", """{"version":"v0.9","somethingNew":{}}""")) as AgentEvent.UiMessage
        assertEquals(UiMessageKind.UNKNOWN, event.kind)
        assertNull(event.surfaceId)
    }

    @Test
    fun `maps text, status, error and done events`() {
        assertEquals(AgentEvent.AgentText("Hi"), mapper.map(ServerSentEvent("text", """{"text":"Hi"}""")))
        assertEquals(AgentEvent.Status("Thinking…"), mapper.map(ServerSentEvent("status", """{"text":"Thinking…"}""")))
        assertEquals(AgentEvent.Failure("boom"), mapper.map(ServerSentEvent("error", """{"message":"boom"}""")))
        assertEquals(AgentEvent.TurnComplete("abc"), mapper.map(ServerSentEvent("done", """{"sessionId":"abc"}""")))
    }

    @Test
    fun `ignores unknown event names`() {
        assertNull(mapper.map(ServerSentEvent("future-event", "{}")))
    }
}
