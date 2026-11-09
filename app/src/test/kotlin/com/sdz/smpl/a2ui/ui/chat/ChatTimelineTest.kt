package com.sdz.smpl.a2ui.ui.chat

import com.sdz.smpl.a2ui.domain.model.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChatTimelineTest {

    private fun ChatTimeline.describe(): List<String> = entries.map { entry ->
        when (entry) {
            is TimelineEntry.User -> "user:${entry.text}"
            is TimelineEntry.Agent -> "agent:${entry.text}"
            is TimelineEntry.Error -> "error:${entry.text}"
            is TimelineEntry.Surface -> "surface:${entry.surfaceId}"
        }
    }

    @Test
    fun `surfaces appear where createSurface arrived, between messages`() {
        val timeline = ChatTimeline()
            .addUserMessage("plan a trip")
            .addAgentMessage("Let's plan Goa.")
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "trip")
            .onUiMessage(UiMessageKind.UPDATE_COMPONENTS, "trip")
            .onUiMessage(UiMessageKind.UPDATE_DATA_MODEL, "trip")

        assertEquals(listOf("user:plan a trip", "agent:Let's plan Goa.", "surface:trip"), timeline.describe())
    }

    @Test
    fun `re-created surface moves to the end`() {
        val timeline = ChatTimeline()
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "trip")
            .addUserMessage("plan a trip to Lisbon")
            .onUiMessage(UiMessageKind.DELETE_SURFACE, "trip")
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "trip")

        assertEquals(listOf("user:plan a trip to Lisbon", "surface:trip"), timeline.describe())
    }

    @Test
    fun `deleteSurface removes only that surface`() {
        val timeline = ChatTimeline()
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "stays")
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "booking")
            .onUiMessage(UiMessageKind.DELETE_SURFACE, "stays")

        assertEquals(listOf("surface:booking"), timeline.describe())
    }

    @Test
    fun `messages without a surface id are ignored`() {
        val timeline = ChatTimeline().addUserMessage("hi")
        assertSame(timeline, timeline.onUiMessage(UiMessageKind.CREATE_SURFACE, null))
    }

    @Test
    fun `keys stay unique even when a surface is re-created`() {
        val timeline = ChatTimeline()
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "trip")
            .addAgentMessage("again")
            .onUiMessage(UiMessageKind.CREATE_SURFACE, "trip")
            .addError("oops")

        val keys = timeline.entries.map { it.key }
        assertEquals(keys.distinct(), keys)
    }
}
