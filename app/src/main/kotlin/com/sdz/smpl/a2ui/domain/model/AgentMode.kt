package com.sdz.smpl.a2ui.domain.model

/**
 * How the agent server answers a turn. Sent with every request as the `mode` field.
 *
 * @property wireValue the value the server expects in the request body.
 */
enum class AgentMode(val wireValue: String) {
    /** Scripted demo flows only: deterministic and works without an LLM key. */
    MOCK("mock"),

    /** Scripted demos for known prompts, the server's LLM for everything else. */
    HYBRID("hybrid"),

    /** Every turn goes to the LLM. */
    LLM("llm");

    companion object {
        /** The mode used until the user picks one. */
        val Default: AgentMode = HYBRID

        /**
         * Parses a [wireValue] (case-insensitive).
         *
         * @return the matching mode, or `null` if [value] is unknown or `null`.
         */
        fun fromWireValue(value: String?): AgentMode? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}
