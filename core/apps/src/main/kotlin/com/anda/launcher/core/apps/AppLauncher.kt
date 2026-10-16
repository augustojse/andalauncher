package com.anda.launcher.core.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import com.anda.launcher.core.model.ComponentKey

/** Starts activities and shortcuts, and opens system app pages. */
class AppLauncher(
    private val context: Context,
    private val users: UserResolver,
    private val onLaunched: (ComponentKey) -> Unit,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(key: ComponentKey, sourceBounds: Rect? = null, options: Bundle? = null): Boolean {
        val user = users.userOf(key.user) ?: return false
        return try {
            launcherApps.startMainActivity(ComponentName(key.packageName, key.className), user, sourceBounds, options)
            onLaunched(key)
            true
        } catch (e: SecurityException) {
            fail(e)
        } catch (e: ActivityNotFoundException) {
            fail(e)
        } catch (e: IllegalStateException) {
            // Work profile paused or user locked.
            fail(e)
        }
    }

    fun launchShortcut(packageName: String, shortcutId: String, user: Long, sourceBounds: Rect? = null, options: Bundle? = null): Boolean {
        val handle = users.userOf(user) ?: return false
        return try {
            launcherApps.startShortcut(packageName, shortcutId, sourceBounds, options, handle)
            true
        } catch (e: SecurityException) {
            fail(e)
        } catch (e: ActivityNotFoundException) {
            fail(e)
        } catch (e: IllegalStateException) {
            fail(e)
        }
    }

    fun openAppInfo(key: ComponentKey, sourceBounds: Rect? = null) {
        val user = users.userOf(key.user) ?: return
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.className), user, sourceBounds, null)
        }
    }

    fun uninstall(key: ComponentKey) {
        val user = users.userOf(key.user) ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        startSafely(intent)
    }

    fun openStorePage(packageName: String) {
        startSafely(
            Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun startSafely(intent: Intent, options: Bundle? = null): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        true
    } catch (e: ActivityNotFoundException) {
        fail(e)
    } catch (e: SecurityException) {
        fail(e)
    }

    private fun fail(e: Exception): Boolean {
        Toast.makeText(context, e.message ?: "Can't open this app", Toast.LENGTH_SHORT).show()
        return false
    }
}
