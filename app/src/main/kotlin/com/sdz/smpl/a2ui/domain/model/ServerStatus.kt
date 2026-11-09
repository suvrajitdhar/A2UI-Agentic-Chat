package com.sdz.smpl.a2ui.domain.model

/**
 * What the agent server reports about itself (`GET /health`).
 *
 * @property baseUrl the server address the app is configured to use.
 * @property defaultMode the mode the server uses when a request doesn't specify one.
 * @property llmAvailable whether the server has an LLM provider configured.
 * @property llmProvider which provider the server uses ("gemini", "claude", "custom"), if any.
 * @property llmModel the model the provider calls, if any.
 */
data class ServerStatus(
    val baseUrl: String,
    val defaultMode: AgentMode?,
    val llmAvailable: Boolean,
    val llmProvider: String? = null,
    val llmModel: String? = null,
)
