package com.anda.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.anda.launcher.core.apps.AppLauncher
import com.anda.launcher.core.apps.AppsRepository
import com.anda.launcher.core.apps.DefaultLayoutProvider
import com.anda.launcher.core.apps.LatinKeyIndex
import com.anda.launcher.core.apps.PackageEvent
import com.anda.launcher.core.apps.ShortcutRepository
import com.anda.launcher.core.apps.UserResolver
import com.anda.launcher.core.data.AppOverridesRepository
import com.anda.launcher.core.data.BackupManager
import com.anda.launcher.core.data.CrashReports
import com.anda.launcher.core.data.DevNotes
import com.anda.launcher.core.data.DrawerGroupsRepository
import com.anda.launcher.core.data.FavoritesRepository
import com.anda.launcher.core.data.LaunchStatsRepository
import com.anda.launcher.core.data.LayoutRepository
import com.anda.launcher.core.data.SettingsRepository
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.icons.IconRepository
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LauncherGraph
import com.anda.launcher.feature.home.widget.WidgetHostManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The object graph (ADR 0003). Everything is lazy so cold start only pays for what it touches. */
class AppGraph(private val app: Application) : LauncherGraph {
    override val crashReports = CrashReports(app)

    /**
     * Background work (package events, backups, cache trims) must never take the launcher down:
     * a failure is logged to [crashReports] and the launcher keeps running.
     */
    val recoverErrors = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Background task failed", error)
        crashReports.record(error, Thread.currentThread().name, fatal = false)
    }

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + recoverErrors)

    val crashGuard = CrashGuard(app, crashReports)
    private val database by lazy { LauncherDatabase.build(app) }

    override val users by lazy { UserResolver(app) }
    override val settingsRepository by lazy { SettingsRepository(app, appScope) }
    override val layoutRepository by lazy { LayoutRepository(database) }
    override val overridesRepository by lazy { AppOverridesRepository(database) }
    override val appsRepository by lazy { AppsRepository(app, appScope, users, overridesRepository.overrides) }
    override val shortcutRepository by lazy { ShortcutRepository(app, users) }
    override val latinKeys by lazy { LatinKeyIndex() }
    override val iconRepository by lazy { IconRepository(app, appsRepository, shortcutRepository) }
    override val launchStatsRepository by lazy { LaunchStatsRepository(database) }
    override val drawerGroupsRepository by lazy { DrawerGroupsRepository(database) }
    override val favoritesRepository by lazy { FavoritesRepository(database) }
    override val devNotes by lazy { DevNotes(app) }
    override val backupManager by lazy { BackupManager(app, database, settingsRepository, devNotes) }
    override val defaultLayoutProvider by lazy { DefaultLayoutProvider(app, users) }
    override val appLauncher by lazy {
        AppLauncher(app, users) { key -> appScope.launch { launchStatsRepository.recordLaunch(key) } }
    }
    val widgetHost by lazy { WidgetHostManager(app, users) }

    fun start() {
        appScope.launch(Dispatchers.Default) { ensureDefaultLayout() }
        appScope.launch {
            appsRepository.events.collect { event ->
                // One bad event (profile removed mid-query, DB error) must not stop the collector.
                runCatching { onPackageEvent(event) }.onFailure { crashReports.record(it, "package-events", fatal = false) }
            }
        }
        registerProfileReceiver()
        appScope.launch(Dispatchers.Default) {
            latinKeys.run(
                labels = appsRepository.apps.map { apps -> apps.map { it.label } },
                enabled = settingsRepository.settings.filterNotNull().map { it.search.transliterate },
                startDelayMs = LATIN_KEYS_DELAY_MS,
            )
        }
        // Housekeeping waits until start-up is over, and is skipped entirely in safe mode: it may
        // be what keeps crashing.
        if (!crashGuard.safeMode) {
            appScope.launch {
                delay(HOUSEKEEPING_DELAY_MS)
                backupManager.autoBackupIfDue()
                launch(Dispatchers.IO) { iconRepository.trimDiskCache() }
            }
        }
    }

    private suspend fun settings(): LauncherSettings = settingsRepository.settings.filterNotNull().first()

    /** Pixel-like first-run layout from the user's default apps. */
    private suspend fun ensureDefaultLayout() {
        if (!layoutRepository.isEmpty()) return
        // An empty layout next to existing automatic backups means the database was lost (e.g.
        // recreated after a downgrade): bring back the newest backup this version can read.
        val restored = backupManager.autoBackups().any { file ->
            runCatching { file.inputStream().use { backupManager.import(it) } }.getOrDefault(false)
        }
        if (restored) return
        val settings = settings()
        val spec = GridSpec(settings.home.columns, settings.home.rows)
        val defaults = defaultLayoutProvider.resolve()
        val screen = layoutRepository.addScreen()
        defaults.dock.take(settings.dock.columns).forEachIndexed { i, key ->
            layoutRepository.addApp(key, Containers.DOCK, 0, CellRect(i * GridSpec.SUBGRID, 0, GridSpec.SUBGRID, GridSpec.SUBGRID), i)
        }
        defaults.home.take(spec.columns).forEachIndexed { i, key ->
            layoutRepository.addApp(key, Containers.DESKTOP, screen, spec.cellRect(i, spec.rows - 1))
        }
    }

    private suspend fun onPackageEvent(event: PackageEvent) {
        when (event) {
            is PackageEvent.Removed -> {
                layoutRepository.removePackage(event.packageName, event.user).forEach(widgetHost::deleteId)
                favoritesRepository.removePackage(event.packageName, event.user)
            }
            is PackageEvent.Added -> {
                val settings = settings()
                val first = event.activities.firstOrNull() ?: return
                if (settings.home.addNewAppsToHome && event.user == users.mySerial) {
                    val (screen, rect) = layoutRepository.findSpace(GridSpec(settings.home.columns, settings.home.rows), 1, 1)
                    layoutRepository.addApp(first, Containers.DESKTOP, screen, rect)
                }
            }
            is PackageEvent.Changed -> Unit
        }
    }

    /** Work profile paused/resumed → refresh the app list. */
    private fun registerProfileReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Covers private space as well as work profiles.
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
        app.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = appsRepository.reload()
            },
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val TAG = "AndaLauncher"
        const val HOUSEKEEPING_DELAY_MS = 30_000L

        /** Latin search keys load ICU rules; a few seconds keeps that out of start-up. */
        const val LATIN_KEYS_DELAY_MS = 3_000L
    }
}
