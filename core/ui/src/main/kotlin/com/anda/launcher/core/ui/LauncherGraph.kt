package com.anda.launcher.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.anda.launcher.core.apps.AppLauncher
import com.anda.launcher.core.apps.AppsRepository
import com.anda.launcher.core.apps.DefaultLayoutProvider
import com.anda.launcher.core.apps.ShortcutRepository
import com.anda.launcher.core.apps.UserResolver
import com.anda.launcher.core.data.AppOverridesRepository
import com.anda.launcher.core.data.BackupManager
import com.anda.launcher.core.data.CrashReports
import com.anda.launcher.core.data.DrawerGroupsRepository
import com.anda.launcher.core.data.LaunchStatsRepository
import com.anda.launcher.core.data.LayoutRepository
import com.anda.launcher.core.data.SettingsRepository
import com.anda.launcher.core.icons.IconRepository
import com.anda.launcher.core.model.LauncherSettings
import kotlinx.coroutines.CoroutineScope

/** The app-wide object graph (ADR 0003). Implemented by `AppGraph` in the app module. */
interface LauncherGraph {
    val appScope: CoroutineScope
    val users: UserResolver
    val settingsRepository: SettingsRepository
    val layoutRepository: LayoutRepository
    val appsRepository: AppsRepository
    val shortcutRepository: ShortcutRepository
    val iconRepository: IconRepository
    val appLauncher: AppLauncher
    val overridesRepository: AppOverridesRepository
    val drawerGroupsRepository: DrawerGroupsRepository
    val launchStatsRepository: LaunchStatsRepository
    val backupManager: BackupManager
    val defaultLayoutProvider: DefaultLayoutProvider
    val crashReports: CrashReports
}

/** Implemented by the Application so services and secondary activities can reach the graph. */
interface LauncherGraphOwner {
    val graph: LauncherGraph
}

val LocalLauncherGraph = staticCompositionLocalOf<LauncherGraph> { error("LauncherGraph not provided") }

/** Settings change rarely, so a static local (whole-subtree invalidation) is cheaper than tracking reads. */
val LocalLauncherSettings = staticCompositionLocalOf { LauncherSettings() }
