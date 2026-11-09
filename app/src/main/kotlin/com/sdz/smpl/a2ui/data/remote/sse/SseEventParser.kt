package com.sdz.smpl.a2ui.data.remote.sse

/**
 * One Server-Sent Event.
 *
 * @property event the event name (`event:` field); `"message"` when the server sends none.
 * @property data the event payload (`data:` lines joined with `\n`).
 * @property id the event id (`id:` field), if any.
 */
data class ServerSentEvent(
    val event: String,
    val data: String,
    val id: String? = null,
)

/**
 * Incremental parser for the `text/event-stream` format
 * ([WHATWG spec](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)).
 *
 * Feed it one line at a time (without the line terminator). It returns an event when a blank line
 * completes one. Comment lines (starting with `:`, e.g. the server's `: ping` heartbeats) are ignored.
 *
 * Not thread-safe: use one instance per stream.
 */
class SseEventParser {
    private var eventName: String? = null
    private var eventId: String? = null
    private val dataLines = mutableListOf<String>()

    /**
     * Processes one line of the stream.
     *
     * @param line a single line, without `\n` / `\r\n`.
     * @return the completed event if [line] is blank and data was buffered, otherwise `null`.
     */
    fun feed(line: String): ServerSentEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        // Per spec, a single space after the colon is not part of the value.
        val value = if (colon == -1) "" else line.substring(colon + 1).removePrefix(" ")

        when (field) {
            "event" -> eventName = value
            "data" -> dataLines += value
            "id" -> eventId = value
            // "retry" and unknown fields are ignored: this client never reconnects automatically.
        }
        return null
    }

    /**
     * Flushes a pending event at end of stream (when the server closed without a final blank line).
     *
     * @return the pending event, or `null` if nothing was buffered.
     */
    fun finish(): ServerSentEvent? = dispatch()

    private fun dispatch(): ServerSentEvent? {
        val event = if (dataLines.isEmpty()) {
            null
        } else {
            ServerSentEvent(event = eventName ?: "message", data = dataLines.joinToString("\n"), id = eventId)
        }
        eventName = null
        dataLines.clear()
        return event
    }
}
