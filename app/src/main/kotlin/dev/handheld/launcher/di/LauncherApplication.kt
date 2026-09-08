package dev.handheld.launcher.di

import android.app.Application

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.handheld.launcher.rom.RomReconciliationWorker.schedule(this)
    }
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext)
    }
}
