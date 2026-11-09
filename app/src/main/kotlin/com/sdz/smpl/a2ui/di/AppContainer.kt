package com.sdz.smpl.a2ui.di

import android.content.Context
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sdz.smpl.a2ui.BuildConfig
import com.sdz.smpl.a2ui.data.mapper.AgentEventMapper
import com.sdz.smpl.a2ui.data.remote.AgentApi
import com.sdz.smpl.a2ui.data.repository.AgentRepositoryImpl
import com.sdz.smpl.a2ui.data.repository.SettingsRepositoryImpl
import com.sdz.smpl.a2ui.domain.repository.AgentRepository
import com.sdz.smpl.a2ui.domain.repository.SettingsRepository
import com.sdz.smpl.a2ui.domain.usecase.GetServerStatusUseCase
import com.sdz.smpl.a2ui.domain.usecase.ObserveAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.SendChatMessageUseCase
import com.sdz.smpl.a2ui.domain.usecase.SendClientMessageUseCase
import com.sdz.smpl.a2ui.domain.usecase.SetAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.StartNewConversationUseCase
import com.sdz.smpl.a2ui.ui.a2ui.createAppA2uiCatalog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/** The single preferences file of the app. Must be declared once, at top level. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manual dependency-injection container: creates the app's long-lived objects once and wires
 * them together. A DI framework (Hilt) would add little for an app this size.
 *
 * Created by [com.sdz.smpl.a2ui.A2uiChatApplication]; ViewModels get their dependencies from it
 * through [AppViewModelFactory].
 *
 * @param context any context; only the application context is kept.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Agent server address, from `BuildConfig` (see `agentBaseUrl` in app/build.gradle.kts). */
    val agentBaseUrl: String = BuildConfig.AGENT_BASE_URL

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(CIO) {
        engine {
            // 0 disables CIO's default 15 s request timeout: an agent turn is one long SSE response.
            requestTimeout = 0
            endpoint.connectTimeout = CONNECT_TIMEOUT_MILLIS
        }
    }

    val agentRepository: AgentRepository = AgentRepositoryImpl(
        api = AgentApi(httpClient, agentBaseUrl, json),
        eventMapper = AgentEventMapper(json),
        ioDispatcher = Dispatchers.IO,
    )

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext.settingsDataStore)

    /** The A2UI component catalog (Material 3 basic catalog). Shared: it holds no per-chat state. */
    val a2uiCatalog: A2uiCatalog by lazy { createAppA2uiCatalog(appContext) }

    // Use cases are stateless; create them on demand.
    fun sendChatMessageUseCase() = SendChatMessageUseCase(agentRepository, settingsRepository)
    fun sendClientMessageUseCase() = SendClientMessageUseCase(agentRepository, settingsRepository)
    fun getServerStatusUseCase() = GetServerStatusUseCase(agentRepository)
    fun startNewConversationUseCase() = StartNewConversationUseCase(agentRepository)
    fun observeAgentModeUseCase() = ObserveAgentModeUseCase(settingsRepository)
    fun setAgentModeUseCase() = SetAgentModeUseCase(settingsRepository)

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
    }
}
