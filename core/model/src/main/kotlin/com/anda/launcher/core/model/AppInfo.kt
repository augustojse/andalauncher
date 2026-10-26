package com.anda.launcher.core.model

import kotlinx.serialization.Serializable

/** A launchable activity as seen by the launcher, with user overrides already applied. */
data class AppInfo(
    val key: ComponentKey,
    val label: String,
    val originalLabel: String,
    val installTime: Long,
    val lastUpdateTime: Long,
    val category: AppCategory,
    val isWorkProfile: Boolean,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    /** Lower-cased label with diacritics removed, precomputed for search and sorting. */
    val sortKey: String = label.lowercase(),
    /** [originalLabel] normalized the same way, so search never normalizes it per query. */
    val originalSortKey: String = originalLabel.lowercase(),
)

/** Drawer categories used for Smart-Launcher-style automatic tabs. */
@Serializable
enum class AppCategory {
    Communication,
    Social,
    Media,
    Games,
    Photography,
    Productivity,
    Navigation,
    Shopping,
    News,
    Health,
    Finance,
    Tools,
    Other,
}

/** A user-defined drawer tab, or a drawer folder whose members are pulled out of the main list. */
data class DrawerGroup(
    val id: Long,
    val isFolder: Boolean,
    val name: String,
    val rank: Int,
    val members: Set<ComponentKey>,
)

data class LaunchStat(val count: Int, val lastLaunch: Long)

/** Per-app user customisation (Nova "edit" dialog + hide/lock). */
data class AppOverride(
    val key: ComponentKey,
    val label: String? = null,
    val iconPath: String? = null,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val category: AppCategory? = null,
    val swipeAction: LauncherAction? = null,
)
