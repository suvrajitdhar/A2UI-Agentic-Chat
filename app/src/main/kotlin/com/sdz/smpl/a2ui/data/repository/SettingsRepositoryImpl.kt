package com.sdz.smpl.a2ui.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] persisted with Jetpack DataStore, so the chosen mode survives restarts.
 *
 * @param dataStore the app's preferences DataStore (one instance per file, see `AppContainer`).
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val agentMode: Flow<AgentMode> = dataStore.data
        .map { prefs -> AgentMode.fromWireValue(prefs[AGENT_MODE_KEY]) ?: AgentMode.Default }
        .distinctUntilChanged()

    override suspend fun setAgentMode(mode: AgentMode) {
        dataStore.edit { prefs -> prefs[AGENT_MODE_KEY] = mode.wireValue }
    }

    private companion object {
        val AGENT_MODE_KEY = stringPreferencesKey("agent_mode")
    }
}
