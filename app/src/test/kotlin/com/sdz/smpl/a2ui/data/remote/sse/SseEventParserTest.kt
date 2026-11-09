package com.sdz.smpl.a2ui.data.remote.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventParserTest {

    private fun parseAll(stream: String): List<ServerSentEvent> {
        val parser = SseEventParser()
        return stream.lines().mapNotNull(parser::feed) + listOfNotNull(parser.finish())
    }

    @Test
    fun `parses events exactly as the agent server sends them`() {
        val stream = """
            |: connected
            |
            |id: 1
            |event: text
            |data: {"text":"Hi!"}
            |
            |id: 2
            |event: a2ui
            |data: {"version":"v0.9","deleteSurface":{"surfaceId":"trip"}}
            |
        """.trimMargin()

        val events = parseAll(stream)

        assertEquals(
            listOf(
                ServerSentEvent("text", """{"text":"Hi!"}""", id = "1"),
                ServerSentEvent("a2ui", """{"version":"v0.9","deleteSurface":{"surfaceId":"trip"}}""", id = "2"),
            ),
            events,
        )
    }

    @Test
    fun `ignores heartbeat comments between events`() {
        val events = parseAll("event: status\n: ping\ndata: {\"text\":\"…\"}\n\n")
        assertEquals(listOf(ServerSentEvent("status", "{\"text\":\"…\"}")), events)
    }

    @Test
    fun `joins multi-line data with newlines and defaults the event name`() {
        val events = parseAll("data: line one\ndata: line two\n\n")
        assertEquals(listOf(ServerSentEvent("message", "line one\nline two")), events)
    }

    @Test
    fun `keeps a colon-containing value intact and strips only one leading space`() {
        val events = parseAll("event: a2ui\ndata:  {\"a\":\"b:c\"}\n\n")
        assertEquals(" {\"a\":\"b:c\"}", events.single().data)
    }

    @Test
    fun `flushes a final event without trailing blank line`() {
        val parser = SseEventParser()
        parser.feed("event: done")
        parser.feed("data: {}")
        assertEquals(ServerSentEvent("done", "{}"), parser.finish())
    }

    @Test
    fun `blank line without data emits nothing`() {
        val parser = SseEventParser()
        parser.feed("event: orphan")
        assertNull(parser.feed(""))
    }
}
