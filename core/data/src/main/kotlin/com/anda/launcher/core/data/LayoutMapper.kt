package com.anda.launcher.core.data

import com.anda.launcher.core.data.db.ItemEntity
import com.anda.launcher.core.data.db.ItemType
import com.anda.launcher.core.data.db.ScreenEntity
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.LauncherAction
import com.anda.launcher.core.model.ShortcutItem
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** Turns flat database rows into the nested, render-ready [HomeLayout]. */
object LayoutMapper {

    fun assemble(screens: List<ScreenEntity>, items: List<ItemEntity>): HomeLayout {
        val byContainer = items.groupBy { it.container }

        fun children(containerId: Long): List<HomeItem> =
            byContainer[containerId].orEmpty().sortedBy { it.rank }.mapNotNull { toItem(it, byContainer) }

        val desktop = byContainer[Containers.DESKTOP].orEmpty()
            .mapNotNull { toItem(it, byContainer) }
            .groupBy { it.screenId }
            .mapValues { (_, list) -> list.toImmutableList() }
        return HomeLayout(
            screens = screens.sortedBy { it.rank }.map { it.id }.toImmutableList(),
            desktop = desktop.toImmutableMap(),
            dock = children(Containers.DOCK).sortedBy { it.rect.x }.toImmutableList(),
        )
    }

    private fun toItem(e: ItemEntity, byContainer: Map<Long, List<ItemEntity>>): HomeItem? {
        val rect = CellRect(e.x, e.y, e.spanX, e.spanY)
        return when (e.type) {
            ItemType.APP -> {
                val key = e.component?.let(ComponentKey::parse) ?: return null
                AppItem(e.id, e.container, e.screenId, rect, e.rank, key, e.title, e.swipeAction.decodeAction())
            }
            ItemType.SHORTCUT -> ShortcutItem(
                e.id, e.container, e.screenId, rect, e.rank,
                packageName = e.packageName ?: return null,
                shortcutId = e.shortcutId ?: return null,
                user = e.user,
                title = e.title.orEmpty(),
            )
            ItemType.FOLDER -> FolderItem(
                e.id, e.container, e.screenId, rect, e.rank,
                title = e.title.orEmpty(),
                children = byContainer[e.id].orEmpty().sortedBy { it.rank }
                    .mapNotNull { toItem(it, byContainer) }
                    .filter { it is AppItem || it is ShortcutItem }
                    .toImmutableList(),
                tapAction = e.tapAction.decodeAction() ?: LauncherAction.OpenFolder,
                swipeAction = e.swipeAction.decodeAction() ?: LauncherAction.None,
            )
            ItemType.WIDGET -> WidgetItem(
                e.id, e.container, e.screenId, rect, e.rank,
                appWidgetId = e.appWidgetId,
                provider = e.component?.let(ComponentKey::parse) ?: return null,
            )
            ItemType.WIDGET_STACK -> WidgetStackItem(
                e.id, e.container, e.screenId, rect, e.rank,
                widgets = byContainer[e.id].orEmpty().sortedBy { it.rank }
                    .mapNotNull { toItem(it, byContainer) as? WidgetItem }
                    .toImmutableList(),
            )
            else -> null
        }
    }
}
