package com.sdz.smpl.a2ui.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdz.smpl.a2ui.R
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme

/**
 * Text field and send button at the bottom of the chat.
 *
 * @param value the current input text.
 * @param onValueChange called on every edit.
 * @param canSend whether sending is allowed (non-blank input, agent idle).
 * @param onSend called when the user taps send or presses the keyboard's send action.
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onSend, enabled = canSend) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.chat_send))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatInputBarPreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        ChatInputBar(value = "Plan a trip", onValueChange = {}, canSend = true, onSend = {})
    }
}
