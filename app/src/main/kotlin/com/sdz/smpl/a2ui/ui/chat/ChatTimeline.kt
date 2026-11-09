package com.sdz.smpl.a2ui.ui.chat

import androidx.compose.runtime.Immutable
import com.sdz.smpl.a2ui.domain.model.UiMessageKind

/**
 * One entry of the conversation, in display order. Surfaces are only referenced by id: their live
 * content comes from the A2UI renderer (see [ChatViewModel]).
 */
@Immutable
sealed interface TimelineEntry {
    /** Stable, unique key for lazy lists. */
    val key: String

    /** A message the user sent. */
    data class User(override val key: String, val text: String) : TimelineEntry

    /** A chat bubble from the agent. */
    data class Agent(override val key: String, val text: String) : TimelineEntry

    /** A failed turn (network error or server-reported failure). */
    data class Error(override val key: String, val text: String) : TimelineEntry

    /** Placeholder for the A2UI surface [surfaceId]: the agent's UI is shown here. */
    data class Surface(override val key: String, val surfaceId: String) : TimelineEntry
}

/**
 * Immutable model of the conversation, i.e. the pure "reducer" part of the chat screen.
 * Every operation returns a new timeline.
 *
 * Surface placement rule: a surface appears where its `createSurface` message arrived. If the
 * agent re-creates a surface with the same id, it moves to the end (it is the newest answer).
 * `deleteSurface` removes it.
 */
@Immutable
data class ChatTimeline(
    val entries: List<TimelineEntry> = emptyList(),
    private val nextId: Long = 0,
) {
    /** Appends a user message. */
    fun addUserMessage(text: String): ChatTimeline = append { TimelineEntry.User(it, text) }

    /** Appends an agent chat bubble. */
    fun addAgentMessage(text: String): ChatTimeline = append { TimelineEntry.Agent(it, text) }

    /** Appends an error line. */
    fun addError(text: String): ChatTimeline = append { TimelineEntry.Error(it, text) }

    /**
     * Updates surface placement for one A2UI message.
     *
     * @param kind the message type; only create and delete affect placement.
     * @param surfaceId the targeted surface; messages without one are ignored.
     */
    fun onUiMessage(kind: UiMessageKind, surfaceId: String?): ChatTimeline {
        if (surfaceId == null) return this
        return when (kind) {
            UiMessageKind.CREATE_SURFACE ->
                withoutSurface(surfaceId).append { TimelineEntry.Surface(it, surfaceId) }
            UiMessageKind.DELETE_SURFACE -> withoutSurface(surfaceId)
            UiMessageKind.UPDATE_COMPONENTS, UiMessageKind.UPDATE_DATA_MODEL, UiMessageKind.UNKNOWN -> this
        }
    }

    /** `true` when nothing has been said yet. */
    val isEmpty: Boolean get() = entries.isEmpty()

    private fun withoutSurface(surfaceId: String): ChatTimeline =
        copy(entries = entries.filterNot { it is TimelineEntry.Surface && it.surfaceId == surfaceId })

    private inline fun append(create: (key: String) -> TimelineEntry): ChatTimeline =
        copy(entries = entries + create("entry-$nextId"), nextId = nextId + 1)
}
