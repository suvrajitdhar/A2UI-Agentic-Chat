package com.sdz.smpl.a2ui.domain.model

/**
 * An A2UI client-to-server message produced by the renderer: something the user did, or a
 * problem the renderer found in the agent's UI. Values are JSON-compatible (maps, lists, strings,
 * numbers, booleans, `null`).
 */
sealed interface ClientMessage {

    /** The surface the message relates to. */
    val surfaceId: String

    /**
     * The user triggered a component action (e.g. tapped a button).
     *
     * @property name the action name declared by the component (`action.event.name`).
     * @property sourceComponentId the id of the component that triggered it.
     * @property timestampMillis when it happened, in epoch milliseconds.
     * @property context the action's context with all data bindings already resolved.
     * @property dataModelMetadata the renderer's data-model snapshot as request metadata
     *   (`{"a2uiClientDataModel": …}`), or `null` if no surface asked for it.
     */
    data class UserAction(
        val name: String,
        override val surfaceId: String,
        val sourceComponentId: String,
        val timestampMillis: Long,
        val context: Map<String, Any?>,
        val dataModelMetadata: Map<String, Any?>? = null,
    ) : ClientMessage

    /**
     * The renderer rejected or failed to render part of a surface.
     *
     * @property code the error category, e.g. [VALIDATION_FAILED].
     * @property message a human-readable description.
     * @property context extra details; for [VALIDATION_FAILED] it holds exactly the `path` key.
     */
    data class ClientError(
        val code: String,
        override val surfaceId: String,
        val message: String,
        val context: Map<String, Any?> = emptyMap(),
    ) : ClientMessage {
        companion object {
            /** Error code for payloads that failed schema validation. */
            const val VALIDATION_FAILED = "VALIDATION_FAILED"
        }
    }
}
