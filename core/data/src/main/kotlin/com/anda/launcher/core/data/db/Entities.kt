package com.anda.launcher.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object ItemType {
    const val APP = 0
    const val SHORTCUT = 1
    const val FOLDER = 2
    const val WIDGET = 3
    const val WIDGET_STACK = 4
}

/** One placed item (Launcher3-style row). Positions are in subgrid units (ADR 0002). */
@Serializable
@Entity(tableName = "items", indices = [Index("container"), Index("screenId")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val container: Long,
    val screenId: Long,
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
    val rank: Int = 0,
    /** Flattened ComponentKey: the app, or the widget provider. */
    val component: String? = null,
    val packageName: String? = null,
    val shortcutId: String? = null,
    val user: Long = 0,
    val appWidgetId: Int = -1,
    val title: String? = null,
    /** JSON-encoded LauncherAction. */
    val tapAction: String? = null,
    val swipeAction: String? = null,
)

@Serializable
@Entity(tableName = "screens")
data class ScreenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rank: Int,
)

@Serializable
@Entity(tableName = "app_overrides")
data class AppOverrideEntity(
    /** Flattened ComponentKey. */
    @PrimaryKey val component: String,
    val label: String? = null,
    val iconPath: String? = null,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val category: String? = null,
    val swipeAction: String? = null,
)

object DrawerGroupKind {
    const val TAB = 0
    const val FOLDER = 1
}

/** A user-defined drawer tab or drawer folder (Nova "drawer groups"). */
@Serializable
@Entity(tableName = "drawer_groups")
data class DrawerGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,
    val name: String,
    val rank: Int,
)

@Serializable
@Entity(
    tableName = "drawer_group_members",
    primaryKeys = ["groupId", "component"],
    indices = [Index("component")],
)
data class DrawerGroupMemberEntity(
    val groupId: Long,
    val component: String,
)

@Serializable
@Entity(tableName = "launch_stats")
data class LaunchStatEntity(
    @PrimaryKey val component: String,
    val launchCount: Int,
    val lastLaunch: Long,
)
