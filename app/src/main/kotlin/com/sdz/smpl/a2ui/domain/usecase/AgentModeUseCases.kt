package com.sdz.smpl.a2ui.domain.usecase

import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Observes the agent mode the user selected. */
class ObserveAgentModeUseCase(private val settingsRepository: SettingsRepository) {
    /** @return the current mode, followed by every change. */
    operator fun invoke(): Flow<AgentMode> = settingsRepository.agentMode
}

/** Changes the agent mode. It applies from the next request to the agent. */
class SetAgentModeUseCase(private val settingsRepository: SettingsRepository) {
    /** Persists [mode] as the selected agent mode. */
    suspend operator fun invoke(mode: AgentMode) = settingsRepository.setAgentMode(mode)
}
