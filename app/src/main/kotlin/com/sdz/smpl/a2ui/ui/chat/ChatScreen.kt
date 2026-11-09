package com.sdz.smpl.a2ui.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdz.smpl.a2ui.R
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.ui.chat.components.AgentBubble
import com.sdz.smpl.a2ui.ui.chat.components.AgentSurfaceCard
import com.sdz.smpl.a2ui.ui.chat.components.ChatInputBar
import com.sdz.smpl.a2ui.ui.chat.components.ErrorBubble
import com.sdz.smpl.a2ui.ui.chat.components.UserBubble
import com.sdz.smpl.a2ui.ui.components.descriptionRes
import com.sdz.smpl.a2ui.ui.components.labelRes
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme

/**
 * Stateful entry point of the chat screen: connects [ChatViewModel] to [ChatScreen] and shows
 * [ChatEffect]s as snackbars.
 *
 * @param onNavigateBack called when the user taps the back arrow.
 */
@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.ModeChanged -> snackbarHostState.showSnackbar(
                    context.getString(R.string.chat_mode_changed, context.getString(effect.mode.labelRes)),
                )
            }
        }
    }

    ChatScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless chat screen: top bar (back, mode menu, new conversation), the conversation with
 * inline agent surfaces, a progress line while the agent works, and the input bar.
 *
 * @param state what to render.
 * @param onIntent receives every user interaction.
 * @param onNavigateBack called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chat_title))
                        Text(
                            text = stringResource(state.mode.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.chat_navigate_back))
                    }
                },
                actions = {
                    ModeMenuButton(selected = state.mode, onSelect = { onIntent(ChatIntent.SelectMode(it)) })
                    IconButton(onClick = { onIntent(ChatIntent.StartNewConversation) }) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.chat_new_conversation))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.showSuggestions) {
                EmptyState(
                    onSuggestion = { onIntent(ChatIntent.SendSuggestion(it)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                ConversationList(items = state.items, modifier = Modifier.weight(1f))
            }
            if (state.isAgentWorking) {
                WorkingIndicator(state.statusText)
            }
            HorizontalDivider()
            ChatInputBar(
                value = state.input,
                onValueChange = { onIntent(ChatIntent.InputChanged(it)) },
                canSend = state.canSend,
                onSend = { onIntent(ChatIntent.SendInput) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The conversation. Scrolls to the newest item whenever one is added, so progressively streamed
 * UI stays in view.
 */
@Composable
private fun ConversationList(items: List<ChatItem>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.UserMessage -> UserBubble(item.text)
                is ChatItem.AgentMessage -> AgentBubble(item.text)
                is ChatItem.ErrorMessage -> ErrorBubble(stringResource(R.string.chat_error_generic, item.text))
                is ChatItem.AgentSurface -> AgentSurfaceCard(item.surface)
            }
        }
    }
}

/** Shown before the first message: a prompt and one-tap suggestions for the scripted demos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        stringResource(R.string.chat_suggestion_hi),
        stringResource(R.string.chat_suggestion_trip),
        stringResource(R.string.chat_suggestion_heal),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            suggestions.forEach { suggestion ->
                SuggestionChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
    }
}

/** A thin progress row with the agent's latest status hint. */
@Composable
private fun WorkingIndicator(statusText: String?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = statusText ?: stringResource(R.string.chat_thinking),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Top-bar button opening a menu to switch the agent mode. */
@Composable
private fun ModeMenuButton(selected: AgentMode, onSelect: (AgentMode) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.chat_change_mode))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        AgentMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(mode.labelRes))
                        Text(
                            text = stringResource(mode.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { RadioButton(selected = mode == selected, onClick = null) },
                onClick = {
                    expanded = false
                    if (mode != selected) onSelect(mode)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenEmptyPreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        ChatScreen(state = ChatUiState(), onIntent = {}, onNavigateBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenConversationPreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        ChatScreen(
            state = ChatUiState(
                mode = AgentMode.MOCK,
                items = listOf(
                    ChatItem.UserMessage("1", "Plan a trip to Lisbon"),
                    ChatItem.AgentMessage("2", "Let's plan Lisbon. Tweak anything below."),
                ),
                isAgentWorking = true,
                statusText = "Searching stays…",
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
