package com.sdz.smpl.a2ui.domain.repository

import com.sdz.smpl.a2ui.domain.model.AgentMode
import kotlinx.coroutines.flow.Flow

/** Persisted user preferences. */
interface SettingsRepository {

    /** The selected agent mode; emits the current value and every change. */
    val agentMode: Flow<AgentMode>

    /** Persists [mode] as the selected agent mode. */
    suspend fun setAgentMode(mode: AgentMode)
}
