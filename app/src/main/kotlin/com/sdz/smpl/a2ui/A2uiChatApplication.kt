package com.sdz.smpl.a2ui

import android.app.Application
import com.sdz.smpl.a2ui.di.AppContainer

/** Application class: owns the [AppContainer] for the lifetime of the process. */
class A2uiChatApplication : Application() {

    /** App-wide dependencies. Created lazily on first access. */
    val container: AppContainer by lazy { AppContainer(this) }
}
