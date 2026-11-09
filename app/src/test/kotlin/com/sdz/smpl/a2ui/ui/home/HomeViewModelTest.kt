package com.sdz.smpl.a2ui.ui.home

import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import com.sdz.smpl.a2ui.domain.model.ServerStatus
import com.sdz.smpl.a2ui.domain.repository.AgentRepository
import com.sdz.smpl.a2ui.domain.repository.SettingsRepository
import com.sdz.smpl.a2ui.domain.usecase.GetServerStatusUseCase
import com.sdz.smpl.a2ui.domain.usecase.ObserveAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.SetAgentModeUseCase
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeSettings : SettingsRepository {
        override val agentMode = MutableStateFlow(AgentMode.HYBRID)
        override suspend fun setAgentMode(mode: AgentMode) {
            agentMode.value = mode
        }
    }

    private class FakeAgent(var status: Result<ServerStatus>) : AgentRepository {
        override fun sendChatMessage(text: String, mode: AgentMode, metadata: Map<String, Any?>): Flow<AgentEvent> = emptyFlow()
        override fun sendClientMessage(message: ClientMessage, mode: AgentMode, metadata: Map<String, Any?>): Flow<AgentEvent> = emptyFlow()
        override suspend fun getServerStatus() = status
        override fun startNewConversation() = Unit
    }

    private val settings = FakeSettings()
    private val agent = FakeAgent(Result.success(ServerStatus(URL, AgentMode.MOCK, llmAvailable = true)))

    private fun viewModel() = HomeViewModel(
        agentBaseUrl = URL,
        observeAgentMode = ObserveAgentModeUseCase(settings),
        setAgentMode = SetAgentModeUseCase(settings),
        getServerStatus = GetServerStatusUseCase(agent),
    )

    /**
     * Keeps [HomeViewModel.uiState] subscribed for the test (it uses `WhileSubscribed`). The collector
     * runs eagerly on the test scheduler, so state updates are visible right after each action.
     */
    private fun TestScope.subscribe(vm: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the server online with its default mode`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        assertEquals(ServerState.Online(URL, AgentMode.MOCK, llmAvailable = true), vm.uiState.value.server)
        assertEquals(AgentMode.HYBRID, vm.uiState.value.selectedMode)
    }

    @Test
    fun `shows the server offline and recovers on retry`() = runTest {
        agent.status = Result.failure(IOException("Connection refused"))
        val vm = viewModel()
        subscribe(vm)
        assertEquals(ServerState.Offline(URL, "Connection refused"), vm.uiState.value.server)

        agent.status = Result.success(ServerStatus(URL, AgentMode.HYBRID, llmAvailable = false))
        vm.onIntent(HomeIntent.RefreshServerStatus)

        assertEquals(ServerState.Online(URL, AgentMode.HYBRID, llmAvailable = false), vm.uiState.value.server)
    }

    @Test
    fun `selecting a mode persists it`() = runTest {
        val vm = viewModel()
        subscribe(vm)

        vm.onIntent(HomeIntent.SelectMode(AgentMode.LLM))

        assertEquals(AgentMode.LLM, settings.agentMode.value)
        assertEquals(AgentMode.LLM, vm.uiState.value.selectedMode)
    }

    @Test
    fun `launch agent emits a navigation effect`() = runTest {
        val vm = viewModel()

        vm.onIntent(HomeIntent.LaunchAgent)

        assertEquals(HomeEffect.NavigateToChat, vm.effects.first())
    }

    private companion object {
        const val URL = "http://10.0.2.2:3000"
    }
}
