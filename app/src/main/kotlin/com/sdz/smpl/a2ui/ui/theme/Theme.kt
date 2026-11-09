package com.sdz.smpl.a2ui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF00796B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C0FF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFF80CBC4),
)

/**
 * The app's Material 3 theme. A2UI surfaces rendered by the Material basic catalog inherit it,
 * so agent-generated UI matches the rest of the app.
 *
 * @param darkTheme whether to use the dark palette; follows the system setting by default.
 * @param dynamicColor whether to use wallpaper-based colors on Android 12+.
 */
@Composable
fun A2uiAgenticChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
