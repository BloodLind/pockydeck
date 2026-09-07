package dev.handheld.launcher.platform.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

data class SupportedSystemAction(val key: String, val title: String, val description: String, val action: String)

/** Local, supported Android destinations. These never participate in launch recency. */
class SystemActionRegistry(context: Context) {
    private val context = context.applicationContext
    val actions: List<SupportedSystemAction> = listOf(
        SupportedSystemAction("wifi", "Wi-Fi", "Manage wireless networks", Settings.ACTION_WIFI_SETTINGS),
        SupportedSystemAction("bluetooth", "Bluetooth", "Connect controllers and accessories", Settings.ACTION_BLUETOOTH_SETTINGS),
        SupportedSystemAction("display", "Display & text size", "Android display, brightness and readability", Settings.ACTION_DISPLAY_SETTINGS),
        SupportedSystemAction("sound", "Sound", "Android volume and sound settings", Settings.ACTION_SOUND_SETTINGS),
        SupportedSystemAction("storage", "Storage", "Review device storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SupportedSystemAction("android-settings", "Android settings", "Open all system settings", Settings.ACTION_SETTINGS),
    ).filter { entry -> context.packageManager.resolveActivity(Intent(entry.action), 0) != null }

    fun open(key: String): Boolean {
        val entry = actions.find { it.key == key } ?: return false
        return dispatch(Intent(entry.action))
    }

    fun openAppInfo(packageName: String): Boolean = dispatch(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

    private fun dispatch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
