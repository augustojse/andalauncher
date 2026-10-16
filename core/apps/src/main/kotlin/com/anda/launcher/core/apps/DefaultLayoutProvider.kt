package com.anda.launcher.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.net.toUri
import com.anda.launcher.core.model.ComponentKey

/**
 * First-run layout, Pixel-like: dock = phone, messages, browser, camera;
 * first page = store, email, photos, video, maps. Resolved from the user's default handlers.
 */
class DefaultLayoutProvider(private val context: Context, private val users: UserResolver) {
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    data class DefaultLayout(val dock: List<ComponentKey>, val home: List<ComponentKey>)

    fun resolve(): DefaultLayout {
        val dock = listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()
        val home = listOfNotNull(
            launcherActivity("com.android.vending"),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
            launcherActivity("com.google.android.youtube"),
            resolve(Intent(Intent.ACTION_VIEW, "geo:0,0?q=".toUri())),
        ).distinct().filterNot { it in dock }
        return DefaultLayout(dock, home)
    }

    private fun resolve(intent: Intent): ComponentKey? {
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = info.activityInfo?.packageName ?: return null
        if (pkg == "android") return null // chooser: no default set
        return launcherActivity(pkg)
    }

    private fun launcherActivity(packageName: String): ComponentKey? =
        launcherApps.getActivityList(packageName, users.myUser).firstOrNull()?.componentName?.let {
            ComponentKey(it.packageName, it.className, users.mySerial)
        }
}
