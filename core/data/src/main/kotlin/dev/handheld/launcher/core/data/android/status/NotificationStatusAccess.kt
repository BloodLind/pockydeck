package dev.handheld.launcher.core.data.android.status

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.handheld.launcher.core.domain.model.StatusValue

/** Read-only setup boundary. Calling this helper neither starts settings nor grants access. */
class NotificationStatusAccess(context: Context) {
    private val context = context.applicationContext
    private val component = ComponentName(this.context, LauncherNotificationListenerService::class.java)

    fun accessState(): StatusValue<Boolean> = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager == null) StatusValue.Unsupported
        else StatusValue.Available(manager.isNotificationListenerAccessGranted(component))
    } catch (_: RuntimeException) {
        StatusValue.Unavailable
    }

    fun isAccessGranted(): Boolean = accessState() == StatusValue.Available(true)

    /** The caller opens this only after the user selects the notification-indicator setup action. */
    fun settingsIntent(): Intent? {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        return listOf(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).firstOrNull {
            runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
