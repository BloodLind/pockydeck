package dev.handheld.launcher.core.data.android.status

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Only the system may bind this service, after explicit Notification access approval. */
class LauncherNotificationListenerService : NotificationListenerService() {
    private val token = Any()
    private var connected = false

    override fun onListenerConnected() {
        connected = true
        NotificationPresenceStore.presence.connected(token)
        updatePresence(runCatching { currentRanking }.getOrNull())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        updatePresence(rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        updatePresence(rankingMap)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        updatePresence(rankingMap)
    }

    private fun updatePresence(rankingMap: RankingMap?) {
        if (!connected) return
        // Ranking keys cover active notifications visible to this listener. Do not read sbn,
        // request notification payloads, keep keys, or infer that denied access means empty.
        val present = if (NotificationStatusAccess(this).isAccessGranted()) {
            runCatching { rankingMap?.orderedKeys?.isNotEmpty() }.getOrNull()
        } else null
        NotificationPresenceStore.presence.update(token, present)
    }

    override fun onListenerDisconnected() {
        connected = false
        NotificationPresenceStore.presence.disconnected(token)
    }

    override fun onDestroy() {
        connected = false
        NotificationPresenceStore.presence.disconnected(token)
        super.onDestroy()
    }
}
