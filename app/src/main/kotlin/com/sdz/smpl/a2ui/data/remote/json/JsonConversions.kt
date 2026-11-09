package com.sdz.smpl.a2ui.data.remote.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts a JSON-compatible Kotlin value into a [JsonElement].
 *
 * The A2UI library represents dynamic JSON (action contexts, data models, capability payloads) as
 * plain `Map<String, Any?>` / `List<Any?>` trees. This bridges them to kotlinx.serialization.
 *
 * Supported: `null`, [JsonElement], [String], [Number], [Boolean], [Map] (keys via `toString()`),
 * [Iterable], [Array]. Anything else is encoded with `toString()` so that one unexpected value never
 * fails a whole request.
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

/** Converts a string-keyed map into a [JsonObject]. See [toJsonElement]. */
fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, value) -> value.toJsonElement() })
