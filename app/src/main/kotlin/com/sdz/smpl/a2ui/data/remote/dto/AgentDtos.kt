package com.sdz.smpl.a2ui.data.remote.dto

import kotlinx.serialization.Serializable

/** Response of `GET /health`. Unknown fields are ignored, so the server can add more. */
@Serializable
data class HealthDto(
    val ok: Boolean = false,
    val mode: String? = null,
    val modes: List<String> = emptyList(),
    val llm: Boolean = false,
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val basicCatalogId: String? = null,
)

/** `data` of the SSE `text` and `status` events: `{"text": "..."}`. */
@Serializable
data class TextPayloadDto(val text: String)

/** `data` of the SSE `error` event: `{"message": "..."}`. */
@Serializable
data class ErrorPayloadDto(val message: String)

/** `data` of the SSE `done` event: `{"sessionId": "..."}`. */
@Serializable
data class DonePayloadDto(val sessionId: String? = null)

/** Names of the SSE events the agent server emits. */
object SseEventNames {
    const val A2UI = "a2ui"
    const val TEXT = "text"
    const val STATUS = "status"
    const val ERROR = "error"
    const val DONE = "done"
}
