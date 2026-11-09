package com.sdz.smpl.a2ui.data.mapper

import com.sdz.smpl.a2ui.data.remote.json.toJsonElement
import com.sdz.smpl.a2ui.data.remote.json.toJsonObject
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the JSON request bodies for the agent server
 * (see a2ui-agent-server/docs/03-api.md).
 */
object RequestBodyMapper {

    /** A2UI protocol version written into client-to-server messages. */
    const val A2UI_VERSION = "v0.9"

    /**
     * Body of `POST /api/agent/chat`: `{sessionId?, text, mode, metadata}`.
     *
     * @param sessionId the current server session, or `null` to start one.
     */
    fun chatRequest(sessionId: String?, text: String, mode: AgentMode, metadata: Map<String, Any?>): JsonObject =
        buildJsonObject {
            sessionId?.let { put("sessionId", it) }
            put("text", text)
            put("mode", mode.wireValue)
            put("metadata", metadata.toJsonObject())
        }

    /**
     * Body of `POST /api/agent/event`: `{sessionId?, message, mode, metadata}`, where `message` is
     * the A2UI client-to-server envelope built by [clientMessage].
     */
    fun eventRequest(
        sessionId: String?,
        message: ClientMessage,
        mode: AgentMode,
        metadata: Map<String, Any?>,
    ): JsonObject = buildJsonObject {
        sessionId?.let { put("sessionId", it) }
        put("message", clientMessage(message))
        put("mode", mode.wireValue)
        put("metadata", metadata.toJsonObject())
    }

    /**
     * Converts a [ClientMessage] into the A2UI v0.9 client-to-server envelope defined by
     * `client_to_server.json`:
     * - action: `{"version","action":{name, surfaceId, sourceComponentId, timestamp (ISO 8601), context}}`
     * - error:  `{"version","error":{code, surfaceId, message, path}}` for `VALIDATION_FAILED`, or
     *   `{code, surfaceId, message, context?}` for other codes.
     */
    fun clientMessage(message: ClientMessage): JsonObject = buildJsonObject {
        put("version", A2UI_VERSION)
        when (message) {
            is ClientMessage.UserAction -> put("action", buildJsonObject {
                put("name", message.name)
                put("surfaceId", message.surfaceId)
                put("sourceComponentId", message.sourceComponentId)
                put("timestamp", Instant.ofEpochMilli(message.timestampMillis).toString())
                put("context", message.context.toJsonObject())
            })

            is ClientMessage.ClientError -> put("error", buildJsonObject {
                put("code", message.code)
                put("surfaceId", message.surfaceId)
                put("message", message.message)
                if (message.code == ClientMessage.ClientError.VALIDATION_FAILED) {
                    // The schema requires `path` and allows nothing else for validation errors.
                    put("path", message.context["path"]?.toString() ?: "/")
                } else if (message.context.isNotEmpty()) {
                    put("context", message.context.toJsonElement())
                }
            })
        }
    }
}
