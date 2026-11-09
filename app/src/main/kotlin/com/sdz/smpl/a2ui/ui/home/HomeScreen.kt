package com.sdz.smpl.a2ui.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdz.smpl.a2ui.R
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.ui.components.AgentModeSelector
import com.sdz.smpl.a2ui.ui.components.labelRes
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme

/**
 * Stateful entry point of the home screen: connects [HomeViewModel] to [HomeScreen] and turns
 * [HomeEffect]s into navigation.
 *
 * @param onLaunchAgent called when the user launches the chat.
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onLaunchAgent: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLaunchAgent by rememberUpdatedState(onLaunchAgent)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToChat -> currentOnLaunchAgent()
            }
        }
    }

    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Stateless home screen: app intro, server status, agent mode selector and the launch button.
 *
 * @param state what to render.
 * @param onIntent receives every user interaction.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader()
            ServerStatusCard(server = state.server, onRetry = { onIntent(HomeIntent.RefreshServerStatus) })
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.agent_mode), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    AgentModeSelector(
                        selected = state.selectedMode,
                        onSelect = { onIntent(HomeIntent.SelectMode(it)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onIntent(HomeIntent.LaunchAgent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_chat), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_launch_agent), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** App icon, title and one-line explanation. */
@Composable
private fun HomeHeader() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(88.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_chat),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
    }
    Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.home_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Shows whether the agent server is reachable, with a retry action when it isn't. */
@Composable
private fun ServerStatusCard(server: ServerState, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (server) {
                is ServerState.Checking -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                is ServerState.Online -> StatusDot(Color(0xFF2E7D32))
                is ServerState.Offline -> StatusDot(MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_server), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (server) {
                        is ServerState.Checking -> stringResource(R.string.home_server_checking, server.baseUrl)
                        is ServerState.Online -> stringResource(
                            R.string.home_server_online,
                            server.defaultMode?.let { stringResource(it.labelRes) } ?: "?",
                        )
                        is ServerState.Offline -> stringResource(R.string.home_server_offline, server.baseUrl)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val detail = when (server) {
                    is ServerState.Online -> if (server.llmAvailable) {
                        stringResource(
                            R.string.home_server_llm_available,
                            server.llmProvider.orEmpty(),
                            server.llmModel.orEmpty(),
                        )
                    } else {
                        stringResource(R.string.home_server_llm_missing)
                    }
                    is ServerState.Offline -> stringResource(R.string.home_server_offline_hint)
                    is ServerState.Checking -> null
                }
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (server !is ServerState.Checking) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Surface(shape = CircleShape, color = color, modifier = Modifier.size(12.dp)) {}
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenOnlinePreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                selectedMode = AgentMode.HYBRID,
                server = ServerState.Online(
                    baseUrl = "http://10.0.2.2:3000",
                    defaultMode = AgentMode.HYBRID,
                    llmAvailable = true,
                    llmProvider = "claude",
                    llmModel = "claude-opus-5-5",
                ),
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenOfflinePreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                selectedMode = AgentMode.MOCK,
                server = ServerState.Offline("http://10.0.2.2:3000", "Connection refused"),
            ),
            onIntent = {},
        )
    }
}
