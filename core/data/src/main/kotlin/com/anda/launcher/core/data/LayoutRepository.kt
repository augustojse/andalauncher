package com.anda.launcher.core.data

import androidx.room.withTransaction
import com.anda.launcher.core.data.db.ItemEntity
import com.anda.launcher.core.data.db.ItemType
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.data.db.ScreenEntity
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.GridOccupancy
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.LauncherAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * All mutations of the home layout. Every operation is a single Room transaction so the
 * observed [layout] never shows a half-applied change.
 */
class LayoutRepository(private val db: LauncherDatabase) {
    private val dao = db.layoutDao()

    val layout: Flow<HomeLayout> = combine(dao.observeScreens(), dao.observeItems()) { screens, items ->
        LayoutMapper.assemble(screens, items)
    }.conflate().flowOn(Dispatchers.Default)

    suspend fun isEmpty(): Boolean = dao.itemCount() == 0 && dao.screens().isEmpty()

    // ---- Screens -------------------------------------------------------------------------------

    suspend fun addScreen(atRank: Int? = null): Long = db.withTransaction {
        val screens = dao.screens()
        val rank = atRank?.coerceIn(0, screens.size) ?: screens.size
        dao.updateScreens(screens.filter { it.rank >= rank }.map { it.copy(rank = it.rank + 1) })
        dao.insertScreen(ScreenEntity(rank = rank))
    }

    /** Removes a page and everything on it. Returns app widget ids the caller must release. */
    suspend fun removeScreen(screenId: Long): List<Int> = db.withTransaction {
        val doomed = dao.itemsOnScreen(Containers.DESKTOP, screenId)
        val widgetIds = collectWidgetIds(doomed)
        deleteRecursively(doomed)
        dao.deleteScreen(screenId)
        dao.updateScreens(dao.screens().mapIndexed { i, s -> s.copy(rank = i) })
        widgetIds
    }

    suspend fun moveScreen(screenId: Long, toRank: Int) = db.withTransaction {
        val screens = dao.screens().toMutableList()
        val screen = screens.firstOrNull { it.id == screenId } ?: return@withTransaction
        screens.remove(screen)
        screens.add(toRank.coerceIn(0, screens.size), screen)
        dao.updateScreens(screens.mapIndexed { i, s -> s.copy(rank = i) })
    }

    // ---- Adding --------------------------------------------------------------------------------

    suspend fun addApp(key: ComponentKey, container: Long, screenId: Long, rect: CellRect, rank: Int = 0): Long =
        dao.insert(
            ItemEntity(
                type = ItemType.APP, container = container, screenId = screenId,
                x = rect.x, y = rect.y, spanX = rect.spanX, spanY = rect.spanY, rank = rank,
                component = key.flatten(), packageName = key.packageName, user = key.user,
            ),
        )

    suspend fun addShortcut(
        packageName: String,
        shortcutId: String,
        user: Long,
        title: String,
        container: Long,
        screenId: Long,
        rect: CellRect,
    ): Long = dao.insert(
        ItemEntity(
            type = ItemType.SHORTCUT, container = container, screenId = screenId,
            x = rect.x, y = rect.y, spanX = rect.spanX, spanY = rect.spanY,
            packageName = packageName, shortcutId = shortcutId, user = user, title = title,
        ),
    )

    suspend fun addWidget(appWidgetId: Int, provider: ComponentKey, screenId: Long, rect: CellRect): Long =
        dao.insert(
            ItemEntity(
                type = ItemType.WIDGET, container = Containers.DESKTOP, screenId = screenId,
                x = rect.x, y = rect.y, spanX = rect.spanX, spanY = rect.spanY,
                component = provider.flatten(), packageName = provider.packageName,
                user = provider.user, appWidgetId = appWidgetId,
            ),
        )

    /**
     * Places an item in the first page with room for it, appending a page when all are full.
     * Used for "add to home" from the drawer, pinned shortcuts and new installs.
     */
    suspend fun findSpace(spec: GridSpec, spanX: Int, spanY: Int): Pair<Long, CellRect> = db.withTransaction {
        val screens = dao.screens()
        val items = dao.itemsIn(Containers.DESKTOP)
        for (screen in screens) {
            val occupancy = GridOccupancy(spec.fineColumns, spec.fineRows)
            items.filter { it.screenId == screen.id }.forEach { occupancy.mark(CellRect(it.x, it.y, it.spanX, it.spanY), true) }
            occupancy.findVacant(spanX * GridSpec.SUBGRID, spanY * GridSpec.SUBGRID)?.let { return@withTransaction screen.id to it }
        }
        val newScreen = dao.insertScreen(ScreenEntity(rank = screens.size))
        newScreen to spec.cellRect(0, 0, spanX, spanY)
    }

    // ---- Moving / editing ----------------------------------------------------------------------

    suspend fun moveItem(id: Long, container: Long, screenId: Long, rect: CellRect, rank: Int = 0) = db.withTransaction {
        val item = dao.item(id) ?: return@withTransaction
        val oldContainer = item.container
        dao.update(
            item.copy(
                container = container, screenId = screenId, x = rect.x, y = rect.y,
                spanX = rect.spanX, spanY = rect.spanY, rank = rank,
            ),
        )
        if (oldContainer != container) normalizeContainer(oldContainer)
    }

    suspend fun resizeItem(id: Long, rect: CellRect) = db.withTransaction {
        val item = dao.item(id) ?: return@withTransaction
        dao.update(item.copy(x = rect.x, y = rect.y, spanX = rect.spanX, spanY = rect.spanY))
    }

    suspend fun setTitle(id: Long, title: String?) {
        val item = dao.item(id) ?: return
        dao.update(item.copy(title = title?.takeIf { it.isNotBlank() }))
    }

    suspend fun setActions(id: Long, tap: LauncherAction?, swipe: LauncherAction?) {
        val item = dao.item(id) ?: return
        dao.update(item.copy(tapAction = tap.encode(), swipeAction = swipe.encode()))
    }

    /**
     * What a removal changed: the rows as they were before (the item, its contents, and its parent
     * folder with siblings, since removing from a two-item folder unwraps it) and the widget ids
     * the caller should release once the removal is final.
     */
    class Removal(val before: List<ItemEntity>, val widgetIds: List<Int>)

    /** Removes an item (and folder/stack contents). Pass the result to [restore] to undo. */
    suspend fun removeItem(id: Long): Removal = db.withTransaction {
        val item = dao.item(id) ?: return@withTransaction Removal(emptyList(), emptyList())
        val before = buildList {
            addAll(subtree(item))
            if (item.container > 0) {
                dao.item(item.container)?.let { add(it) }
                addAll(dao.itemsIn(item.container).filter { it.id != item.id })
            }
        }
        val widgetIds = collectWidgetIds(listOf(item))
        deleteRecursively(listOf(item))
        normalizeContainer(item.container)
        Removal(before, widgetIds)
    }

    /** Undoes [removeItem]: puts every affected row back exactly as it was (same ids). */
    suspend fun restore(removal: Removal) = db.withTransaction {
        dao.insertItemsReplacing(removal.before)
    }

    private suspend fun subtree(item: ItemEntity): List<ItemEntity> = buildList {
        add(item)
        if (item.type == ItemType.FOLDER || item.type == ItemType.WIDGET_STACK) {
            dao.itemsIn(item.id).forEach { addAll(subtree(it)) }
        }
    }

    /** Removes every item belonging to an uninstalled package. Returns widget ids to release. */
    suspend fun removePackage(packageName: String, user: Long): List<Int> = db.withTransaction {
        val items = dao.itemsForPackage(packageName, user)
        val widgetIds = collectWidgetIds(items)
        deleteRecursively(items)
        items.map { it.container }.distinct().forEach { normalizeContainer(it) }
        widgetIds
    }

    // ---- Folders -------------------------------------------------------------------------------

    /** Nova/Launcher3 behaviour: dropping an icon on another icon creates a folder in its place. */
    suspend fun createFolder(targetId: Long, droppedId: Long, title: String): Long = db.withTransaction {
        val target = dao.item(targetId) ?: return@withTransaction -1L
        val dropped = dao.item(droppedId) ?: return@withTransaction -1L
        val folderId = dao.insert(
            ItemEntity(
                type = ItemType.FOLDER, container = target.container, screenId = target.screenId,
                x = target.x, y = target.y, spanX = target.spanX, spanY = target.spanY,
                rank = target.rank, title = title,
            ),
        )
        val oldContainer = dropped.container
        dao.update(target.copy(container = folderId, screenId = 0, rank = 0))
        dao.update(dropped.copy(container = folderId, screenId = 0, rank = 1))
        if (oldContainer != target.container) normalizeContainer(oldContainer)
        folderId
    }

    suspend fun addToFolder(folderId: Long, itemId: Long, rank: Int? = null) = db.withTransaction {
        val item = dao.item(itemId) ?: return@withTransaction
        val siblings = dao.itemsIn(folderId).sortedBy { it.rank }.filter { it.id != itemId }.toMutableList()
        val oldContainer = item.container
        siblings.add((rank ?: siblings.size).coerceIn(0, siblings.size), item.copy(container = folderId, screenId = 0))
        dao.updateAll(siblings.mapIndexed { i, e -> e.copy(rank = i) })
        if (oldContainer != folderId) normalizeContainer(oldContainer)
    }

    suspend fun reorderContainer(containerId: Long, orderedIds: List<Long>) = db.withTransaction {
        val items = dao.itemsIn(containerId).associateBy { it.id }
        dao.updateAll(orderedIds.mapIndexedNotNull { i, id -> items[id]?.copy(rank = i) })
    }

    // ---- Widget stacks -------------------------------------------------------------------------

    /** Drops a widget onto another widget (or stack) to stack them. */
    suspend fun stackWidget(targetId: Long, droppedId: Long) = db.withTransaction {
        val target = dao.item(targetId) ?: return@withTransaction
        val dropped = dao.item(droppedId) ?: return@withTransaction
        val oldContainer = dropped.container
        val stackId = if (target.type == ItemType.WIDGET_STACK) {
            target.id
        } else {
            val id = dao.insert(target.copy(id = 0, type = ItemType.WIDGET_STACK, appWidgetId = -1, component = null, packageName = null))
            dao.update(target.copy(container = id, screenId = 0, rank = 0))
            id
        }
        val count = dao.itemsIn(stackId).size
        dao.update(dropped.copy(container = stackId, screenId = 0, rank = count))
        if (oldContainer != stackId) normalizeContainer(oldContainer)
    }

    // ---- Dock ----------------------------------------------------------------------------------

    /** Dock items are ordered by x; this rewrites x for a new order. */
    suspend fun reorderDock(orderedIds: List<Long>) = db.withTransaction {
        val items = dao.itemsIn(Containers.DOCK).associateBy { it.id }
        dao.updateAll(orderedIds.mapIndexedNotNull { i, id -> items[id]?.copy(x = i * GridSpec.SUBGRID, rank = i) })
    }

    // ---- Bulk ----------------------------------------------------------------------------------

    suspend fun snapshot(): Pair<List<ScreenEntity>, List<ItemEntity>> = db.withTransaction { dao.screens() to dao.items() }

    suspend fun replaceAll(screens: List<ScreenEntity>, items: List<ItemEntity>) = dao.replaceAll(screens, items)

    // ---- Internals -----------------------------------------------------------------------------

    private suspend fun collectWidgetIds(items: List<ItemEntity>): List<Int> = buildList {
        for (item in items) {
            if (item.type == ItemType.WIDGET && item.appWidgetId >= 0) add(item.appWidgetId)
            if (item.type == ItemType.FOLDER || item.type == ItemType.WIDGET_STACK) addAll(collectWidgetIds(dao.itemsIn(item.id)))
        }
    }

    private suspend fun deleteRecursively(items: List<ItemEntity>) {
        for (item in items) {
            if (item.type == ItemType.FOLDER || item.type == ItemType.WIDGET_STACK) deleteRecursively(dao.itemsIn(item.id))
        }
        dao.deleteIds(items.map { it.id })
    }

    /**
     * Folders with one child turn back into that child (Launcher3 behaviour); empty folders and
     * stacks disappear; single-widget stacks unwrap.
     */
    private suspend fun normalizeContainer(containerId: Long) {
        if (containerId <= 0) return
        val container = dao.item(containerId) ?: return
        val children = dao.itemsIn(containerId).sortedBy { it.rank }
        when {
            children.isEmpty() -> dao.delete(container)
            children.size == 1 -> {
                val only = children.first()
                dao.update(
                    only.copy(
                        container = container.container, screenId = container.screenId,
                        x = container.x, y = container.y,
                        spanX = if (container.type == ItemType.WIDGET_STACK) container.spanX else only.spanX.coerceAtLeast(GridSpec.SUBGRID).coerceAtMost(container.spanX),
                        spanY = if (container.type == ItemType.WIDGET_STACK) container.spanY else only.spanY.coerceAtLeast(GridSpec.SUBGRID).coerceAtMost(container.spanY),
                        rank = container.rank,
                    ),
                )
                dao.delete(container)
            }
            else -> dao.updateAll(children.mapIndexed { i, e -> e.copy(rank = i) })
        }
    }
}
