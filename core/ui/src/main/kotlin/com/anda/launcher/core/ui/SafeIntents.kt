package com.anda.launcher.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Opens a system screen without ever crashing: tries [intent], then each of [fallbacks], and
 * finally tells the user. Settings pages differ between Android builds (Go editions, OEM skins,
 * work profiles), so a direct deep link may not exist on a given phone.
 *
 * App launches go through `AppLauncher.startSafely`; this is for settings and system screens.
 */
fun Context.startActivitySafely(intent: Intent, vararg fallbacks: Intent): Boolean {
    for (candidate in listOf(intent, *fallbacks)) {
        try {
            startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        } catch (_: SecurityException) {
            // Try the next one.
        }
    }
    Toast.makeText(this, "This screen isn't available on your phone", Toast.LENGTH_SHORT).show()
    return false
}

/** Last-resort fallback for any settings deep link: the main Settings app. */
val SystemSettingsIntent: Intent get() = Intent(Settings.ACTION_SETTINGS)
