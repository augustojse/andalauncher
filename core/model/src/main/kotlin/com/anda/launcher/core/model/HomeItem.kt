package com.anda.launcher.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Container ids. Positive ids are folder or widget-stack item ids. */
object Containers {
    const val DESKTOP = -100L
    const val DOCK = -101L
}

/**
 * Position in **subgrid units** ([GridSpec.SUBGRID] units per cell), so Nova-style half-cell
 * positioning needs no schema change.
 */
data class CellRect(val x: Int, val y: Int, val spanX: Int, val spanY: Int) {
    val right: Int get() = x + spanX
    val bottom: Int get() = y + spanY

    fun overlaps(other: CellRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun contains(px: Int, py: Int): Boolean = px in x until right && py in y until bottom
}

/** Anything placed on the desktop, in the dock, in a folder or in a widget stack. */
sealed interface HomeItem {
    val id: Long
    val container: Long
    val screenId: Long
    val rect: CellRect
    val rank: Int
}

data class AppItem(
    override val id: Long,
    override val container: Long,
    override val screenId: Long,
    override val rect: CellRect,
    override val rank: Int,
    val key: ComponentKey,
    val title: String? = null,
    val swipeAction: LauncherAction? = null,
) : HomeItem

data class ShortcutItem(
    override val id: Long,
    override val container: Long,
    override val screenId: Long,
    override val rect: CellRect,
    override val rank: Int,
    val packageName: String,
    val shortcutId: String,
    val user: Long,
    val title: String,
) : HomeItem

data class FolderItem(
    override val id: Long,
    override val container: Long,
    override val screenId: Long,
    override val rect: CellRect,
    override val rank: Int,
    val title: String,
    val children: ImmutableList<HomeItem> = persistentListOf(),
    /** Action Launcher "cover" / Nova swipe-to-open: what a tap does (OpenFolder or LaunchApp). */
    val tapAction: LauncherAction = LauncherAction.OpenFolder,
    val swipeAction: LauncherAction = LauncherAction.None,
) : HomeItem

data class WidgetItem(
    override val id: Long,
    override val container: Long,
    override val screenId: Long,
    override val rect: CellRect,
    override val rank: Int,
    val appWidgetId: Int,
    val provider: ComponentKey,
) : HomeItem

/** iOS / Smart-Launcher-style stack of widgets sharing one rect, swiped vertically. */
data class WidgetStackItem(
    override val id: Long,
    override val container: Long,
    override val screenId: Long,
    override val rect: CellRect,
    override val rank: Int,
    val widgets: ImmutableList<WidgetItem> = persistentListOf(),
) : HomeItem

/** The fully assembled home layout, ready to render. */
data class HomeLayout(
    val screens: ImmutableList<Long> = persistentListOf(),
    val desktop: ImmutableMap<Long, ImmutableList<HomeItem>> = persistentMapOf(),
    val dock: ImmutableList<HomeItem> = persistentListOf(),
) {
    fun itemsOn(screenId: Long): ImmutableList<HomeItem> = desktop[screenId] ?: persistentListOf()

    fun findItem(id: Long): HomeItem? {
        fun search(items: List<HomeItem>): HomeItem? {
            for (item in items) {
                if (item.id == id) return item
                when (item) {
                    is FolderItem -> search(item.children)?.let { return it }
                    is WidgetStackItem -> search(item.widgets)?.let { return it }
                    else -> Unit
                }
            }
            return null
        }
        return search(dock) ?: desktop.values.firstNotNullOfOrNull { search(it) }
    }
}
