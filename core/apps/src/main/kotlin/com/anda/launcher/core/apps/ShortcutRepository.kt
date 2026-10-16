package com.anda.launcher.core.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import com.anda.launcher.core.model.ComponentKey

/** App shortcuts (long-press menu, search) and pinned shortcuts placed on the home screen. */
class ShortcutRepository(context: Context, private val users: UserResolver) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    /** Shortcuts are only accessible to the default home app. */
    val isAvailable: Boolean get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutsFor(key: ComponentKey): List<ShortcutInfo> {
        if (!isAvailable) return emptyList()
        val user = users.userOf(key.user) ?: return emptyList()
        val query = ShortcutQuery()
            .setPackage(key.packageName)
            .setActivity(android.content.ComponentName(key.packageName, key.className))
            .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(query, user).orEmpty() }.getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo> { if (it.isDynamic) 1 else 0 }.thenBy { it.rank })
            .take(MAX_MENU_SHORTCUTS)
    }

    /** Every dynamic + manifest shortcut across all profiles, for universal search. */
    fun allShortcuts(): List<ShortcutInfo> {
        if (!isAvailable) return emptyList()
        val query = ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST)
        return users.profiles().flatMap { user ->
            runCatching { launcherApps.getShortcuts(query, user).orEmpty() }.getOrDefault(emptyList())
        }.filter { it.isEnabled }
    }

    fun find(packageName: String, shortcutId: String, user: Long): ShortcutInfo? {
        if (!isAvailable) return null
        val handle = users.userOf(user) ?: return null
        val query = ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED)
        return runCatching { launcherApps.getShortcuts(query, handle)?.firstOrNull() }.getOrNull()
    }

    fun icon(shortcut: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull()

    /** Pins a shortcut so it survives app updates; launchers own their pinned set. */
    fun pin(packageName: String, shortcutId: String, user: Long) {
        if (!isAvailable) return
        val handle = users.userOf(user) ?: return
        val pinnedQuery = ShortcutQuery().setPackage(packageName).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
        val pinned = runCatching { launcherApps.getShortcuts(pinnedQuery, handle).orEmpty().map { it.id } }.getOrDefault(emptyList())
        runCatching { launcherApps.pinShortcuts(packageName, (pinned + shortcutId).distinct(), handle) }
    }

    fun unpin(packageName: String, shortcutId: String, user: Long) {
        if (!isAvailable) return
        val handle = users.userOf(user) ?: return
        val pinnedQuery = ShortcutQuery().setPackage(packageName).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
        val pinned = runCatching { launcherApps.getShortcuts(pinnedQuery, handle).orEmpty().map { it.id } }.getOrDefault(emptyList())
        runCatching { launcherApps.pinShortcuts(packageName, pinned - shortcutId, handle) }
    }

    companion object {
        const val MAX_MENU_SHORTCUTS = 5
    }
}
