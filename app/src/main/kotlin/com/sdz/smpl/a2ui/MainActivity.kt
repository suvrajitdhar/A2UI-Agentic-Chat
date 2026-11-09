package com.sdz.smpl.a2ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sdz.smpl.a2ui.ui.navigation.AppNavHost
import com.sdz.smpl.a2ui.ui.theme.A2uiAgenticChatTheme

/** Single activity hosting the Compose navigation graph. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A2uiAgenticChatTheme {
                AppNavHost()
            }
        }
    }
}
