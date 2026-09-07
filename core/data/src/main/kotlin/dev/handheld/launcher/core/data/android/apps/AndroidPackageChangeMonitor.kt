package dev.handheld.launcher.core.data.android.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

interface AndroidPackageChangeMonitor {
    fun start(onPackageChanged: () -> Unit)

    fun stop()
}

/** Receives protected package-manager broadcasts only while its consumer is active. */
class BroadcastAndroidPackageChangeMonitor(context: Context) : AndroidPackageChangeMonitor {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var receiver: BroadcastReceiver? = null

    override fun start(onPackageChanged: () -> Unit) {
        synchronized(lock) {
            if (receiver != null) return
            val registeredReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action in PACKAGE_CHANGE_ACTIONS) onPackageChanged()
                }
            }
            val filter = IntentFilter().apply {
                PACKAGE_CHANGE_ACTIONS.forEach(::addAction)
                addDataScheme("package")
            }
            applicationContext.registerReceiver(
                registeredReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiver = registeredReceiver
        }
    }

    override fun stop() {
        synchronized(lock) {
            receiver?.let(applicationContext::unregisterReceiver)
            receiver = null
        }
    }

    private companion object {
        val PACKAGE_CHANGE_ACTIONS = setOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED,
        )
    }
}
