package dev.handheld.launcher.di

import android.app.Application
import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Bundle

class LauncherApplication : Application() {
    @Volatile private var startedActivities = 0
    private val containerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext).also { it.setArtworkForeground(startedActivities > 0) }
    }
    val appContainer: AppContainer get() = containerDelegate.value

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1 && containerDelegate.isInitialized()) appContainer.setArtworkForeground(true)
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && containerDelegate.isInitialized()) appContainer.setArtworkForeground(false)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        dev.handheld.launcher.rom.RomReconciliationWorker.schedule(this)
        dev.handheld.launcher.artwork.ArtworkWorker.schedule(this)
    }
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!containerDelegate.isInitialized()) return
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> appContainer.trimArtworkMemory(clear = true)
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> appContainer.trimArtworkMemory(clear = false)
        }
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        if (containerDelegate.isInitialized()) appContainer.trimArtworkMemory(clear = true)
    }
}
