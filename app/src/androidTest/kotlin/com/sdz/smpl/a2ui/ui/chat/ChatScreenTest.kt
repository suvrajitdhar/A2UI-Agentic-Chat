package com.sdz.smpl.a2ui.ui.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests of the stateless [ChatScreen]: state in, intents out. */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ChatIntent>()

    private fun show(state: ChatUiState) = composeRule.setContent {
        A2uiAgenticChatTheme(dynamicColor = false) {
            ChatScreen(state = state, onIntent = { intents += it }, onNavigateBack = {})
        }
    }

    @Test
    fun emptyState_suggestionSendsIt() {
        show(ChatUiState())

        composeRule.onNodeWithText("Plan a trip to Lisbon").performClick()

        assertEquals(listOf(ChatIntent.SendSuggestion("Plan a trip to Lisbon")), intents)
    }

    @Test
    fun sendButton_isDisabledWhileTheAgentWorks() {
        show(ChatUiState(input = "hello", isAgentWorking = true, statusText = "Thinking…"))

        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
        composeRule.onNodeWithText("Thinking…").assertExists()
    }

    @Test
    fun conversation_showsMessagesAndModeInTitle() {
        show(
            ChatUiState(
                mode = AgentMode.MOCK,
                input = "next",
                items = listOf(
                    ChatItem.UserMessage("1", "hi"),
                    ChatItem.AgentMessage("2", "Pick a demo"),
                ),
            ),
        )

        composeRule.onNodeWithText("hi").assertExists()
        composeRule.onNodeWithText("Pick a demo").assertExists()
        composeRule.onNodeWithText("Mock").assertExists()
        composeRule.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        assertEquals(listOf(ChatIntent.SendInput), intents)
    }
}
