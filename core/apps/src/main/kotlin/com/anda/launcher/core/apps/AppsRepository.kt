package com.anda.launcher.core.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.FuzzyMatcher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.Collator

/** Raw activity data before user overrides are applied. */
internal data class RawApp(
    val key: ComponentKey,
    val label: String,
    val installTime: Long,
    val lastUpdateTime: Long,
    val manifestCategory: Int,
    val isWork: Boolean,
    val info: LauncherActivityInfo,
)

sealed interface PackageEvent {
    val packageName: String
    val user: Long

    data class Added(override val packageName: String, override val user: Long, val activities: List<ComponentKey>) : PackageEvent
    data class Removed(override val packageName: String, override val user: Long) : PackageEvent
    data class Changed(override val packageName: String, override val user: Long) : PackageEvent
}

/**
 * Single source of truth for launchable apps. Loads once, then applies incremental updates from
 * [LauncherApps.Callback]; it never reloads everything on a package event (docs/RND.md).
 */
class AppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val users: UserResolver,
    overrides: Flow<Map<ComponentKey, AppOverride>>,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val raw = MutableStateFlow<Map<ComponentKey, RawApp>?>(null)
    private val mutex = Mutex()
    private val collator = Collator.getInstance()

    private val _events = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PackageEvent> = _events.asSharedFlow()

    /** Bumped when shortcuts change so menus/search refresh. */
    private val _shortcutsVersion = MutableStateFlow(0)
    val shortcutsVersion: StateFlow<Int> = _shortcutsVersion

    /** All launchable apps with overrides applied, sorted by label. Empty until the first load. */
    val apps: StateFlow<ImmutableList<AppInfo>> = combine(raw.filterNotNull(), overrides) { rawApps, ov ->
        rawApps.values.map { r ->
            val o = ov[r.key]
            val label = o?.label ?: r.label
            val sortKey = FuzzyMatcher.normalize(label)
            AppInfo(
                key = r.key,
                label = label,
                originalLabel = r.label,
                installTime = r.installTime,
                lastUpdateTime = r.lastUpdateTime,
                category = o?.category ?: AppCategorizer.categorize(r.key.packageName, r.manifestCategory),
                isWorkProfile = r.isWork,
                isHidden = o?.hidden == true,
                isLocked = o?.locked == true,
                sortKey = sortKey,
                originalSortKey = if (label == r.label) sortKey else FuzzyMatcher.normalize(r.label),
            )
        }.sortedWith { a, b -> collator.compare(a.sortKey, b.sortKey) }.toImmutableList()
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = dropPackage(packageName, user, uninstalled = true)

        private fun dropPackage(packageName: String, user: UserHandle, uninstalled: Boolean) {
            scope.launch(Dispatchers.Default) {
                val serial = users.serialOf(user)
                mutex.withLock { raw.update { m -> m?.filterKeys { !(it.packageName == packageName && it.user == serial) } } }
                // Only a real uninstall removes home items; "unavailable" (work profile paused,
                // adoptable storage ejected) must keep the user's layout intact.
                if (uninstalled) _events.emit(PackageEvent.Removed(packageName, serial))
            }
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadPackage(packageName, user, added = true)

        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadPackage(packageName, user, added = false)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { reloadPackage(it, user, added = false) }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (!replacing) packageNames.forEach { dropPackage(it, user, uninstalled = false) }
        }

        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            _shortcutsVersion.update { it + 1 }
        }
    }

    init {
        scope.launch(Dispatchers.Default) { reloadAll() }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    /** Full reload; used at startup and when a profile becomes available. */
    fun reload() {
        scope.launch(Dispatchers.Default) { reloadAll() }
    }

    private suspend fun reloadAll() {
        val loaded = HashMap<ComponentKey, RawApp>(256)
        for (user in users.profiles()) {
            val serial = users.serialOf(user)
            val isWork = users.isWorkProfile(user)
            launcherApps.getActivityList(null, user).forEach { info ->
                val app = info.toRaw(serial, isWork)
                loaded[app.key] = app
            }
        }
        mutex.withLock { raw.value = loaded }
    }

    private fun reloadPackage(packageName: String, user: UserHandle, added: Boolean) {
        scope.launch(Dispatchers.Default) {
            val serial = users.serialOf(user)
            val isWork = users.isWorkProfile(user)
            val activities = launcherApps.getActivityList(packageName, user).map { it.toRaw(serial, isWork) }
            mutex.withLock {
                raw.update { m ->
                    val next = m.orEmpty().filterKeys { !(it.packageName == packageName && it.user == serial) }.toMutableMap()
                    activities.forEach { next[it.key] = it }
                    next
                }
            }
            _events.emit(
                if (added) {
                    PackageEvent.Added(packageName, serial, activities.map { it.key })
                } else {
                    PackageEvent.Changed(packageName, serial)
                },
            )
        }
    }

    private fun LauncherActivityInfo.toRaw(serial: Long, isWork: Boolean): RawApp {
        val appInfo = applicationInfo
        return RawApp(
            key = ComponentKey(componentName.packageName, componentName.className, serial),
            label = label?.toString().orEmpty().ifBlank { componentName.packageName },
            installTime = firstInstallTime,
            // A file stat is far cheaper than PackageManager.getPackageInfo per app at cold start.
            lastUpdateTime = File(appInfo.sourceDir).lastModified(),
            manifestCategory = appInfo.category,
            isWork = isWork,
            info = this,
        )
    }

    /** Cached activity info (no binder call) with a direct lookup fallback. */
    fun activityInfo(key: ComponentKey): LauncherActivityInfo? {
        raw.value?.get(key)?.let { return it.info }
        val user = users.userOf(key.user) ?: return null
        return launcherApps.getActivityList(key.packageName, user).firstOrNull { it.componentName.className == key.className }
            ?: launcherApps.getActivityList(key.packageName, user).firstOrNull()
    }
}
