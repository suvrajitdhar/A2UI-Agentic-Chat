package com.sdz.smpl.a2ui.domain.model

/**
 * One event streamed by the agent during a turn (one SSE event from the server).
 * A turn always ends with [TurnComplete], possibly preceded by [Failure].
 */
sealed interface AgentEvent {

    /**
     * An A2UI v0.9 server-to-client message, to be handed to the A2UI renderer unchanged.
     *
     * @property json the raw message JSON, e.g. `{"version":"v0.9","createSurface":{…}}`.
     * @property kind which of the four A2UI message types this is.
     * @property surfaceId the surface the message targets, or `null` if it couldn't be read.
     */
    data class UiMessage(
        val json: String,
        val kind: UiMessageKind,
        val surfaceId: String?,
    ) : AgentEvent

    /** A chat bubble from the agent. */
    data class AgentText(val text: String) : AgentEvent

    /** A transient progress hint such as "Thinking…". */
    data class Status(val text: String) : AgentEvent

    /** The turn failed on the server; [TurnComplete] still follows. */
    data class Failure(val message: String) : AgentEvent

    /** The turn is over and the server closed the stream. */
    data class TurnComplete(val sessionId: String?) : AgentEvent
}

/** The four A2UI v0.9 server-to-client message types, plus [UNKNOWN] for anything else. */
enum class UiMessageKind {
    CREATE_SURFACE,
    UPDATE_COMPONENTS,
    UPDATE_DATA_MODEL,
    DELETE_SURFACE,
    UNKNOWN,
}
