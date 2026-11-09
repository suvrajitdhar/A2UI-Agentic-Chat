package com.sdz.smpl.a2ui.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdz.smpl.a2ui.R
import com.sdz.smpl.a2ui.domain.model.AgentMode
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme

/** Display name of this mode, e.g. "Hybrid". */
@get:StringRes
val AgentMode.labelRes: Int
    get() = when (this) {
        AgentMode.MOCK -> R.string.mode_mock
        AgentMode.HYBRID -> R.string.mode_hybrid
        AgentMode.LLM -> R.string.mode_llm
    }

/** One-sentence explanation of this mode. */
@get:StringRes
val AgentMode.descriptionRes: Int
    get() = when (this) {
        AgentMode.MOCK -> R.string.mode_mock_description
        AgentMode.HYBRID -> R.string.mode_hybrid_description
        AgentMode.LLM -> R.string.mode_llm_description
    }

/**
 * Segmented control for choosing the [AgentMode], with a description of the selected mode below.
 * Stateless: the caller owns the selection.
 *
 * @param selected the currently selected mode.
 * @param onSelect called when the user taps a different mode.
 * @param showDescription whether to show the description line.
 */
@Composable
fun AgentModeSelector(
    selected: AgentMode,
    onSelect: (AgentMode) -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true,
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AgentMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AgentMode.entries.size),
                    label = { Text(stringResource(mode.labelRes)) },
                )
            }
        }
        if (showDescription) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(selected.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AgentModeSelectorPreview() {
    A2uiAgenticChatTheme(dynamicColor = false) {
        AgentModeSelector(selected = AgentMode.HYBRID, onSelect = {})
    }
}
