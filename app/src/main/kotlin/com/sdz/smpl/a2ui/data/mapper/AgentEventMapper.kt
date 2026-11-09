package com.sdz.smpl.a2ui.data.mapper

import com.sdz.smpl.a2ui.data.remote.dto.DonePayloadDto
import com.sdz.smpl.a2ui.data.remote.dto.ErrorPayloadDto
import com.sdz.smpl.a2ui.data.remote.dto.SseEventNames
import com.sdz.smpl.a2ui.data.remote.dto.TextPayloadDto
import com.sdz.smpl.a2ui.data.remote.sse.ServerSentEvent
import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.UiMessageKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps the agent server's SSE events to domain [AgentEvent]s. */
class AgentEventMapper(private val json: Json) {

    /**
     * Converts one SSE event.
     *
     * @return the domain event, or `null` for event names this app doesn't know (forward compatible).
     * @throws SerializationException if a known event carries malformed JSON.
     */
    fun map(event: ServerSentEvent): AgentEvent? = when (event.event) {
        SseEventNames.A2UI -> mapUiMessage(event.data)
        SseEventNames.TEXT -> AgentEvent.AgentText(json.decodeFromString<TextPayloadDto>(event.data).text)
        SseEventNames.STATUS -> AgentEvent.Status(json.decodeFromString<TextPayloadDto>(event.data).text)
        SseEventNames.ERROR -> AgentEvent.Failure(json.decodeFromString<ErrorPayloadDto>(event.data).message)
        SseEventNames.DONE -> AgentEvent.TurnComplete(json.decodeFromString<DonePayloadDto>(event.data).sessionId)
        else -> null
    }

    /**
     * Reads only the envelope of an A2UI message (its type and surface id). The full message is
     * passed on as raw JSON, because parsing and validation belong to the A2UI renderer.
     */
    private fun mapUiMessage(raw: String): AgentEvent.UiMessage {
        val envelope = json.parseToJsonElement(raw).jsonObject
        val (kind, body) = MESSAGE_KEYS.firstNotNullOfOrNull { (key, kind) ->
            (envelope[key] as? JsonObject)?.let { kind to it }
        } ?: (UiMessageKind.UNKNOWN to null)
        val surfaceId = body?.get("surfaceId")?.jsonPrimitive?.content
        return AgentEvent.UiMessage(json = raw, kind = kind, surfaceId = surfaceId)
    }

    private companion object {
        val MESSAGE_KEYS = listOf(
            "createSurface" to UiMessageKind.CREATE_SURFACE,
            "updateComponents" to UiMessageKind.UPDATE_COMPONENTS,
            "updateDataModel" to UiMessageKind.UPDATE_DATA_MODEL,
            "deleteSurface" to UiMessageKind.DELETE_SURFACE,
        )
    }
}
