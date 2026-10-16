package com.anda.launcher

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.anda.launcher.core.ui.LauncherGraph
import com.anda.launcher.core.ui.LauncherGraphOwner

class LauncherApplication : Application(), LauncherGraphOwner {
    lateinit var appGraph: AppGraph
        private set

    override val graph: LauncherGraph get() = appGraph

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        appGraph = AppGraph(this)
        appGraph.crashGuard.install()
        appGraph.start()
    }

    /**
     * Debug builds log every main-thread disk/network access and leaked closeable. Violations are
     * reviewed as part of M11 (penaltyDeath is too strict: framework calls such as
     * WallpaperManager and resource loading read disk on the main thread).
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
