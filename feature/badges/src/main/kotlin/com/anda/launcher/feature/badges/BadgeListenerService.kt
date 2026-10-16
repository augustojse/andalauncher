package com.anda.launcher.feature.badges

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.anda.launcher.core.model.PackageUserKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live notification counts per package, fed by [BadgeListenerService]. */
object BadgeRepository {
    private val _counts = MutableStateFlow<Map<PackageUserKey, Int>>(emptyMap())
    val counts: StateFlow<Map<PackageUserKey, Int>> = _counts.asStateFlow()

    internal fun publish(counts: Map<PackageUserKey, Int>) {
        if (_counts.value != counts) _counts.value = counts
    }

    fun isAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, BadgeListenerService::class.java))

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, BadgeListenerService::class.java).flattenToString(),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The general list of notification listeners, for builds without the per-app detail page. */
    val accessSettingsFallback: Intent get() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

/** Opt-in notification listener for notification dots/counts. Reads only package + count. */
class BadgeListenerService : NotificationListenerService() {
    private lateinit var userManager: UserManager

    override fun onCreate() {
        super.onCreate()
        userManager = getSystemService(UserManager::class.java)
    }

    override fun onListenerConnected() = recompute()

    override fun onListenerDisconnected() = BadgeRepository.publish(emptyMap())

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recompute()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recompute()

    private fun recompute() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = HashMap<PackageUserKey, Int>()
        for (sbn in active) {
            if (!sbn.countsAsBadge()) continue
            val key = PackageUserKey(sbn.packageName, userManager.getSerialNumberForUser(sbn.user))
            val number = sbn.notification.number.takeIf { it > 0 } ?: 1
            counts[key] = (counts[key] ?: 0) + number
        }
        BadgeRepository.publish(counts)
    }

    private fun StatusBarNotification.countsAsBadge(): Boolean {
        val n = notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (isOngoing) return false
        if (n.badgeIconType == Notification.BADGE_ICON_NONE) return false
        return true
    }
}
