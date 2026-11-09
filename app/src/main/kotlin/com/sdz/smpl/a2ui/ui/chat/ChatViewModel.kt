package com.sdz.smpl.a2ui.ui.chat

import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdz.smpl.a2ui.domain.model.AgentEvent
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import com.sdz.smpl.a2ui.domain.usecase.ObserveAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.SendChatMessageUseCase
import com.sdz.smpl.a2ui.domain.usecase.SendClientMessageUseCase
import com.sdz.smpl.a2ui.domain.usecase.SetAgentModeUseCase
import com.sdz.smpl.a2ui.domain.usecase.StartNewConversationUseCase
import com.sdz.smpl.a2ui.ui.a2ui.A2uiRenderer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel of the chat screen.
 *
 * Data flow:
 * ```
 * user text ──▶ SendChatMessageUseCase ──▶ SSE events ──▶ handle(event)
 *                                                         ├─ UiMessage  → renderer.render(json) + timeline placement
 *                                                         ├─ AgentText  → timeline bubble
 *                                                         └─ Status     → progress line
 * renderer.outboundMessages (button taps, client errors) ──▶ SendClientMessageUseCase ──▶ same handling
 * ```
 * Turns run one at a time, in order ([turnMutex]): an action tapped while the agent is still
 * answering is queued behind the current turn.
 *
 * @param renderer the A2UI renderer for this conversation (owned by this ViewModel).
 * @param rendererDispatcher where the renderer's processing loop runs.
 */
class ChatViewModel(
    private val renderer: A2uiRenderer,
    private val sendChatMessage: SendChatMessageUseCase,
    private val sendClientMessage: SendClientMessageUseCase,
    observeAgentMode: ObserveAgentModeUseCase,
    private val setAgentMode: SetAgentModeUseCase,
    private val startNewConversation: StartNewConversationUseCase,
    rendererDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Input field and turn progress: the parts of the state that aren't the timeline. */
    private data class ComposerState(
        val input: String = "",
        val isAgentWorking: Boolean = false,
        val statusText: String? = null,
    )

    private val timeline = MutableStateFlow(ChatTimeline())
    private val composer = MutableStateFlow(ComposerState())
    private val effectChannel = Channel<ChatEffect>(Channel.BUFFERED)
    private val turnMutex = Mutex()
    private val activeTurns = mutableSetOf<Job>()

    /** The screen state: timeline + live surfaces + composer + selected mode. */
    val uiState: StateFlow<ChatUiState> =
        combine(timeline, renderer.surfaces, composer, observeAgentMode()) { timeline, surfaces, composer, mode ->
            ChatUiState(
                mode = mode,
                items = timeline.toItems(surfaces),
                input = composer.input,
                isAgentWorking = composer.isAgentWorking,
                statusText = composer.statusText,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ChatUiState(),
        )

    /** One-off events; collect them once from the screen. */
    val effects: Flow<ChatEffect> = effectChannel.receiveAsFlow()

    init {
        // Each chat screen is a new conversation: forget any previous server session.
        startNewConversation()

        // The renderer's processing loop lives as long as this ViewModel.
        viewModelScope.launch(rendererDispatcher) { renderer.run() }

        // Subscribe before anything is rendered: outbound events are not replayed.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            renderer.outboundMessages.collect(::forwardToAgent)
        }
    }

    /** Handles one user [intent]. */
    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> composer.update { it.copy(input = intent.text) }
            ChatIntent.SendInput -> sendText(composer.value.input)
            is ChatIntent.SendSuggestion -> sendText(intent.text)
            is ChatIntent.SelectMode -> selectMode(intent.mode)
            ChatIntent.StartNewConversation -> resetConversation()
        }
    }

    private fun sendText(text: String) {
        if (text.isBlank() || composer.value.isAgentWorking) return
        timeline.update { it.addUserMessage(text.trim()) }
        composer.update { it.copy(input = "") }
        runTurn { sendChatMessage(text, renderer.capabilitiesMetadata) }
    }

    /** Sends a renderer message (action or client error) to the agent as its own turn. */
    private fun forwardToAgent(message: ClientMessage) {
        val dataModel = (message as? ClientMessage.UserAction)?.dataModelMetadata.orEmpty()
        runTurn { sendClientMessage(message, renderer.capabilitiesMetadata + dataModel) }
    }

    private fun selectMode(mode: AgentMode) {
        viewModelScope.launch {
            setAgentMode(mode)
            effectChannel.send(ChatEffect.ModeChanged(mode))
        }
    }

    private fun resetConversation() {
        activeTurns.toList().forEach(Job::cancel)
        renderer.clear()
        startNewConversation()
        timeline.value = ChatTimeline()
        composer.value = ComposerState()
    }

    /**
     * Runs one agent turn after any turn already in progress. Failures become an error entry in
     * the timeline, so a bad turn never breaks the conversation.
     */
    private fun runTurn(request: () -> Flow<AgentEvent>) {
        val job = viewModelScope.launch {
            turnMutex.withLock {
                composer.update { it.copy(isAgentWorking = true, statusText = null) }
                try {
                    request().collect(::handle)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    timeline.update { it.addError(e.message ?: e::class.simpleName.orEmpty()) }
                } finally {
                    composer.update { it.copy(isAgentWorking = false, statusText = null) }
                }
            }
        }
        activeTurns += job
        job.invokeOnCompletion { activeTurns -= job }
    }

    /** Applies one streamed agent event to the state and the renderer. */
    private fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.UiMessage -> {
                timeline.update { it.onUiMessage(event.kind, event.surfaceId) }
                renderer.render(event.json)
            }
            is AgentEvent.AgentText -> {
                timeline.update { it.addAgentMessage(event.text) }
                composer.update { it.copy(statusText = null) }
            }
            is AgentEvent.Status -> composer.update { it.copy(statusText = event.text) }
            is AgentEvent.Failure -> timeline.update { it.addError(event.message) }
            is AgentEvent.TurnComplete -> Unit
        }
    }

    /**
     * Resolves surface placeholders against the renderer's live surfaces. A placeholder whose
     * surface isn't live yet (still being created) or anymore is skipped.
     */
    private fun ChatTimeline.toItems(surfaces: List<A2uiSurfaceModel>): List<ChatItem> {
        val byId = surfaces.associateBy { it.id }
        return entries.mapNotNull { entry ->
            when (entry) {
                is TimelineEntry.User -> ChatItem.UserMessage(entry.key, entry.text)
                is TimelineEntry.Agent -> ChatItem.AgentMessage(entry.key, entry.text)
                is TimelineEntry.Error -> ChatItem.ErrorMessage(entry.key, entry.text)
                is TimelineEntry.Surface -> byId[entry.surfaceId]?.let { ChatItem.AgentSurface(entry.key, it) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
