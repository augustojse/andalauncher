package com.anda.launcher.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in accessibility service. Its only job is `performGlobalAction` for lock screen, recents,
 * notification shade, quick settings, screenshot and power menu (docs/research/001). It
 * subscribes to no events and never reads window content.
 */
class LauncherAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        internal var instance: LauncherAccessibilityService? = null
    }
}

object SystemActions {
    val isAccessibilityEnabled: Boolean get() = LauncherAccessibilityService.instance != null

    /** Returns false when the accessibility service isn't enabled. */
    fun global(action: Int): Boolean = LauncherAccessibilityService.instance?.performGlobalAction(action) == true

    fun expandNotifications(context: Context): Boolean =
        global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) || statusBarReflection(context, "expandNotificationsPanel")

    fun expandQuickSettings(context: Context): Boolean =
        global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) || statusBarReflection(context, "expandSettingsPanel")

    /** Hidden-API fallback (needs EXPAND_STATUS_BAR). Sideload-only per the distribution decision. */
    @SuppressLint("WrongConstant", "PrivateApi")
    private fun statusBarReflection(context: Context, method: String): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        service.javaClass.getMethod(method).invoke(service)
        true
    }.getOrDefault(false)

    fun accessibilitySettingsIntent(context: Context): Intent {
        val component = ComponentName(context, LauncherAccessibilityService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
