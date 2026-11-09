package com.sdz.smpl.a2ui.ui.a2ui

import android.content.Context
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sdz.smpl.a2ui.R

/**
 * Creates the A2UI component catalog this app renders: the standard A2UI **basic catalog**,
 * implemented with Material 3 by `androidx.compose.material3:material3-a2ui`.
 *
 * The library doesn't bundle media dependencies, so the app plugs them in:
 * - `Image` is rendered with Coil.
 * - `Video` and `AudioPlayer` show a placeholder, because this POC has no player.
 *
 * Client functions (`formatCurrency`, `required`, `openUrl`, …) use the library defaults.
 *
 * @param context any context; the library keeps only the application context.
 */
fun createAppA2uiCatalog(context: Context): A2uiCatalog = materialA2uiBasicCatalogV1(
    image = MaterialA2uiBasicCatalogV1Defaults.image { url, contentDescription, contentScale, modifier, onError ->
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onError = { state -> onError(state.result.throwable) },
        )
    },
    video = MaterialA2uiBasicCatalogV1Defaults.video { _, modifier, _ ->
        UnsupportedMediaPlaceholder(modifier)
    },
    audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, modifier, _ ->
        UnsupportedMediaPlaceholder(modifier)
    },
    functions = A2uiBasicCatalogV1.Functions(context),
)

/** Shown in place of `Video` / `AudioPlayer` components. */
@Composable
private fun UnsupportedMediaPlaceholder(modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_tune), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.a2ui_media_unsupported),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
