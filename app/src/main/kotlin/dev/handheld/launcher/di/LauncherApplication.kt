package dev.handheld.launcher.di

import android.app.Application

class LauncherApplication : Application() {
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext)
    }
}
