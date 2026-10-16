package com.anda.launcher.feature.home

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.anda.launcher.core.data.LayoutRepository
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.GridOccupancy
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.LauncherAction
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.ShortcutItem
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import com.anda.launcher.core.ui.LauncherGraph
import com.anda.launcher.core.ui.drag.DragController
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.drag.DropTarget
import com.anda.launcher.core.ui.menu.ItemMenuState
import com.anda.launcher.core.ui.menu.MenuAction
import com.anda.launcher.core.ui.menu.MenuShortcut
import com.anda.launcher.core.ui.menu.OverlayController
import com.anda.launcher.core.ui.menu.confirm
import com.anda.launcher.feature.gestures.ActionDispatcher
import com.anda.launcher.feature.home.widget.WidgetHostManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Activity-side hooks for flows that need `startActivityForResult`. */
interface WidgetRequestHandler {
    fun requestBind(intent: Intent)
    fun requestConfigure(appWidgetId: Int)
}

/** Text of the undo bar shown after a removal. */
data class UndoState(val message: String)

private const val UNDO_WINDOW_MS = 4_000L

/** A widget waiting for bind/configure before it is written to the layout. */
data class PendingWidget(val appWidgetId: Int, val provider: ComponentKey, val screenId: Long, val rect: CellRect)

/**
 * Home-screen state and operations: drop resolution, folders, widgets, menus. Composables stay
 * dumb; everything that mutates the layout goes through here (UDF, docs/RND.md).
 */
@Stable
class HomeController(
    private val context: Context,
    val graph: LauncherGraph,
    val widgets: WidgetHostManager,
    val drag: DragController,
    val overlay: OverlayController,
    private val scope: CoroutineScope,
    private val dispatcher: ActionDispatcher,
    private val widgetRequests: WidgetRequestHandler,
) {
    val layout: StateFlow<HomeLayout> = graph.layoutRepository.layout.stateIn(scope, SharingStarted.Eagerly, HomeLayout())

    var settings: LauncherSettings = LauncherSettings()
    var apps: Map<ComponentKey, AppInfo> = emptyMap()

    var openFolderId by mutableStateOf<Long?>(null)
    var folderAnchor by mutableStateOf(Rect.Zero)
    var resizingItemId by mutableStateOf<Long?>(null)
    var editMode by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    var dockHidden by mutableStateOf(false)

    /** The live Customize panel (edit mode → Customize, or "Try on home" from settings). */
    var customizeOpen by mutableStateOf(false)
        private set

    /**
     * Uncommitted settings shown on the real home while a Customize slider is dragged. The root
     * clears it once the stored settings change.
     */
    var draftSettings by mutableStateOf<LauncherSettings?>(null)

    fun openCustomize() {
        overlay.dismissMenu()
        editMode = false
        customizeOpen = true
    }

    fun closeCustomize() {
        customizeOpen = false
        draftSettings = null
    }

    fun updateSettings(block: (LauncherSettings) -> LauncherSettings) {
        scope.launch { graph.settingsRepository.update(block) }
    }

    /** Crash-loop protection: widgets are not inflated in safe mode. */
    var safeMode = false

    /** Target page for the pager (set by "add to home", default page, etc.). */
    var requestedScreen by mutableStateOf<Long?>(null)

    /** Incremented to scroll to the default page (home button while on home). */
    var defaultPageRequest by mutableIntStateOf(0)
        private set

    fun goToDefaultPage() {
        defaultPageRequest++
    }

    private var pendingWidget: PendingWidget? = null

    /** Inner grid bounds (root coords) of composed pages, keyed by screen id. */
    internal val pageBounds = HashMap<Long, Rect>()
    internal var dockBounds: Rect? = null
    internal var dockPage: Int = 0

    val gridSpec: GridSpec get() = GridSpec(settings.home.columns, settings.home.rows)

    init {
        drag.setFallback(DropTarget { payload, position -> onDrop(payload, position) })
    }

    // ---- Taps & actions ------------------------------------------------------------------------

    fun dispatch(action: LauncherAction, bounds: Rect? = null) = dispatcher.dispatch(action, bounds?.toAndroid())

    fun onItemTap(item: HomeItem, bounds: Rect) {
        when (item) {
            is AppItem -> dispatch(LauncherAction.LaunchApp(item.key), bounds)
            is ShortcutItem -> graph.appLauncher.launchShortcut(item.packageName, item.shortcutId, item.user, bounds.toAndroid())
            is FolderItem -> when (val tap = item.tapAction) {
                LauncherAction.OpenFolder -> openFolder(item, bounds)
                // Cover mode: tap opens the first app.
                else -> dispatch(firstAppAction(item) ?: tap, bounds)
            }
            is WidgetItem, is WidgetStackItem -> Unit
        }
    }

    /** Returns true if the swipe was handled (so the home swipe-up doesn't fire). */
    fun onItemSwipeUp(item: HomeItem, bounds: Rect): Boolean {
        if (!settings.gestures.iconSwipeActions) return false
        val action = when (item) {
            is AppItem -> item.swipeAction ?: return false
            is FolderItem -> if (item.tapAction != LauncherAction.OpenFolder) LauncherAction.OpenFolder else item.swipeAction
            else -> return false
        }
        if (action == LauncherAction.None) return false
        if (action == LauncherAction.OpenFolder && item is FolderItem) openFolder(item, bounds) else dispatch(action, bounds)
        return true
    }

    private fun firstAppAction(folder: FolderItem): LauncherAction? = when (val first = folder.children.firstOrNull()) {
        is AppItem -> LauncherAction.LaunchApp(first.key)
        is ShortcutItem -> LauncherAction.LaunchShortcut(first.packageName, first.shortcutId, first.user, first.title)
        else -> null
    }

    fun openFolder(folder: FolderItem, anchor: Rect) {
        folderAnchor = anchor
        openFolderId = folder.id
    }

    fun closeFolder() {
        openFolderId = null
    }

    // ---- Drag ----------------------------------------------------------------------------------

    fun startDrag(item: HomeItem, icon: ImageBitmap?, iconSize: Dp, rootPosition: Offset, touchOffset: Offset) {
        if (settings.home.lockLayout) {
            toast("Home screen layout is locked")
            return
        }
        overlay.dismissMenu()
        drag.start(DragPayload.Existing(item), icon, iconSize, rootPosition, touchOffset)
    }

    /** Fallback drop target: the workspace and dock. */
    private fun onDrop(payload: DragPayload, position: Offset): Boolean {
        dockBounds?.takeIf { it.contains(position) }?.let { return dropOnDock(payload, position, it) }
        val (screenId, bounds) = pageBounds.entries.firstOrNull { it.value.contains(position) }?.toPair() ?: return false
        val spec = gridSpec
        val (spanX, spanY) = spanOf(payload, spec)
        val unitW = bounds.width / spec.fineColumns
        val unitH = bounds.height / spec.fineRows
        val step = if (settings.home.subgridPositioning) 1 else GridSpec.SUBGRID
        fun snap(v: Float, max: Int): Int {
            val raw = v.roundToInt().coerceIn(0, max.coerceAtLeast(0))
            return (raw / step) * step
        }
        val fx = snap((position.x - bounds.left) / unitW - spanX / 2f, spec.fineColumns - spanX)
        val fy = snap((position.y - bounds.top) / unitH - spanY / 2f, spec.fineRows - spanY)
        val px = ((position.x - bounds.left) / unitW).toInt()
        val py = ((position.y - bounds.top) / unitH).toInt()
        val items = layout.value.itemsOn(screenId)
        val ignoreId = (payload as? DragPayload.Existing)?.item?.id
        val under = items.firstOrNull { it.id != ignoreId && it.rect.contains(px, py) }
        scope.launch {
            if (under != null && tryMerge(payload, under)) return@launch
            val occupancy = GridOccupancy(spec, items, ignoreId)
            val target = CellRect(fx, fy, spanX, spanY).takeIf { occupancy.isFree(it) }
                ?: occupancy.nearestVacant(fx, fy, spanX, spanY, step)
            if (target == null) {
                toast("No room on this page")
                return@launch
            }
            place(payload, Containers.DESKTOP, screenId, target)
        }
        return true
    }

    private fun dropOnDock(payload: DragPayload, position: Offset, bounds: Rect): Boolean {
        if (payload is DragPayload.NewWidget || (payload as? DragPayload.Existing)?.item.let { it is WidgetItem || it is WidgetStackItem }) {
            toast("Widgets can't go in the dock")
            return true
        }
        val columns = settings.dock.columns
        val slot = (((position.x - bounds.left) / (bounds.width / columns)).toInt().coerceIn(0, columns - 1)) + dockPage * columns
        val dockItems = layout.value.dock
        val ignoreId = (payload as? DragPayload.Existing)?.item?.id
        val under = dockItems.firstOrNull { it.id != ignoreId && it.rect.x / GridSpec.SUBGRID == slot }
        scope.launch {
            if (under != null && tryMerge(payload, under)) return@launch
            val taken = dockItems.filter { it.id != ignoreId }.map { it.rect.x / GridSpec.SUBGRID }.toSet()
            val total = columns * settings.dock.pages
            val free = if (slot !in taken) slot else (0 until total).filter { it !in taken }.minByOrNull { kotlin.math.abs(it - slot) }
            if (free == null) {
                toast("The dock is full")
                return@launch
            }
            place(payload, Containers.DOCK, 0, CellRect(free * GridSpec.SUBGRID, 0, GridSpec.SUBGRID, GridSpec.SUBGRID))
        }
        return true
    }

    private fun spanOf(payload: DragPayload, spec: GridSpec): Pair<Int, Int> = when (payload) {
        is DragPayload.Existing -> when (val item = payload.item) {
            is WidgetItem, is WidgetStackItem -> item.rect.spanX.coerceAtMost(spec.fineColumns) to item.rect.spanY.coerceAtMost(spec.fineRows)
            else -> GridSpec.SUBGRID to GridSpec.SUBGRID
        }
        is DragPayload.NewWidget -> (payload.spanX * GridSpec.SUBGRID).coerceAtMost(spec.fineColumns) to
            (payload.spanY * GridSpec.SUBGRID).coerceAtMost(spec.fineRows)
        else -> GridSpec.SUBGRID to GridSpec.SUBGRID
    }

    private fun DragPayload.isIconLike(): Boolean = when (this) {
        is DragPayload.NewApp, is DragPayload.NewShortcut -> true
        is DragPayload.Existing -> item is AppItem || item is ShortcutItem
        is DragPayload.NewWidget -> false
    }

    /** Folder creation / add-to-folder / widget stacking. Returns false when nothing merged. */
    private suspend fun tryMerge(payload: DragPayload, under: HomeItem): Boolean {
        val repo = graph.layoutRepository
        return when {
            payload.isIconLike() && (under is AppItem || under is ShortcutItem) -> {
                val id = materialize(payload, under.container, under.screenId, under.rect) ?: return false
                repo.createFolder(under.id, id, defaultFolderTitle(under, payload))
                true
            }
            payload.isIconLike() && under is FolderItem -> {
                val id = materialize(payload, under.id, 0, CellRect(0, 0, GridSpec.SUBGRID, GridSpec.SUBGRID)) ?: return false
                repo.addToFolder(under.id, id)
                true
            }
            payload is DragPayload.Existing && payload.item is WidgetItem && (under is WidgetItem || under is WidgetStackItem) -> {
                repo.stackWidget(under.id, payload.item.id)
                true
            }
            else -> false
        }
    }

    /** Smart default folder name from the apps' shared category (Smart Launcher-ish). */
    private fun defaultFolderTitle(under: HomeItem, payload: DragPayload): String {
        val a = (under as? AppItem)?.key?.let { apps[it]?.category }
        val b = when (payload) {
            is DragPayload.NewApp -> payload.app.category
            is DragPayload.Existing -> (payload.item as? AppItem)?.key?.let { apps[it]?.category }
            else -> null
        }
        return if (a != null && a == b && a != com.anda.launcher.core.model.AppCategory.Other) a.name else "Folder"
    }

    /** Ensures the payload exists as a row (new apps/shortcuts are inserted) and returns its id. */
    private suspend fun materialize(payload: DragPayload, container: Long, screenId: Long, rect: CellRect): Long? {
        val repo = graph.layoutRepository
        return when (payload) {
            is DragPayload.Existing -> payload.item.id
            is DragPayload.NewApp -> repo.addApp(payload.app.key, container, screenId, rect)
            is DragPayload.NewShortcut -> {
                withContext(Dispatchers.IO) { graph.shortcutRepository.pin(payload.packageName, payload.shortcutId, payload.user) }
                repo.addShortcut(payload.packageName, payload.shortcutId, payload.user, payload.label, container, screenId, rect)
            }
            is DragPayload.NewWidget -> null
        }
    }

    private suspend fun place(payload: DragPayload, container: Long, screenId: Long, rect: CellRect) {
        when (payload) {
            is DragPayload.Existing -> graph.layoutRepository.moveItem(payload.item.id, container, screenId, rect)
            is DragPayload.NewWidget -> beginAddWidget(payload.provider, screenId, rect)
            else -> materialize(payload, container, screenId, rect)
        }
    }

    // ---- Adding --------------------------------------------------------------------------------

    fun addToHome(app: AppInfo) {
        scope.launch {
            val (screenId, rect) = graph.layoutRepository.findSpace(gridSpec, 1, 1)
            graph.layoutRepository.addApp(app.key, Containers.DESKTOP, screenId, rect)
            requestedScreen = screenId
            toast("Added to home screen")
        }
    }

    fun addShortcutToHome(packageName: String, shortcutId: String, user: Long, label: String) {
        scope.launch {
            withContext(Dispatchers.IO) { graph.shortcutRepository.pin(packageName, shortcutId, user) }
            val (screenId, rect) = graph.layoutRepository.findSpace(gridSpec, 1, 1)
            graph.layoutRepository.addShortcut(packageName, shortcutId, user, label, Containers.DESKTOP, screenId, rect)
            requestedScreen = screenId
        }
    }

    /** Called from the widget picker "tap to add". */
    fun addWidgetToHome(info: AppWidgetProviderInfo, spanX: Int, spanY: Int) {
        editMode = false
        scope.launch {
            val (screenId, rect) = graph.layoutRepository.findSpace(gridSpec, spanX, spanY)
            beginAddWidget(info.toKey(), screenId, rect)
            requestedScreen = screenId
        }
    }

    private fun AppWidgetProviderInfo.toKey() = ComponentKey(provider.packageName, provider.className, graph.users.serialOf(profile))

    private fun providerInfo(key: ComponentKey): AppWidgetProviderInfo? =
        widgets.providers().firstOrNull {
            it.provider.packageName == key.packageName && it.provider.className == key.className && graph.users.serialOf(it.profile) == key.user
        }

    private suspend fun beginAddWidget(provider: ComponentKey, screenId: Long, rect: CellRect) {
        val info = withContext(Dispatchers.Default) { providerInfo(provider) } ?: return
        val id = widgets.allocateId()
        pendingWidget = PendingWidget(id, provider, screenId, rect)
        if (widgets.bindIfAllowed(id, info)) onWidgetBound(id) else widgetRequests.requestBind(widgets.bindIntent(id, info))
    }

    /** Bind permission granted (or not needed). */
    fun onWidgetBound(appWidgetId: Int) {
        val pending = pendingWidget?.takeIf { it.appWidgetId == appWidgetId } ?: return
        val info = widgets.info(appWidgetId)
        if (info != null && widgets.needsConfiguration(info)) {
            widgetRequests.requestConfigure(appWidgetId)
        } else {
            commitWidget(pending)
        }
    }

    fun onWidgetConfigured(appWidgetId: Int, ok: Boolean) {
        val pending = pendingWidget?.takeIf { it.appWidgetId == appWidgetId } ?: return
        if (ok) commitWidget(pending) else cancelWidget(appWidgetId)
    }

    fun cancelWidget(appWidgetId: Int) {
        if (pendingWidget?.appWidgetId == appWidgetId) pendingWidget = null
        widgets.deleteId(appWidgetId)
    }

    private fun commitWidget(pending: PendingWidget) {
        pendingWidget = null
        scope.launch { graph.layoutRepository.addWidget(pending.appWidgetId, pending.provider, pending.screenId, pending.rect) }
    }

    // ---- Removing / editing --------------------------------------------------------------------

    /** The "Removed · Undo" bar; null when nothing can be undone. */
    var undo by mutableStateOf<UndoState?>(null)
        private set

    private var pendingRemoval: PendingRemoval? = null

    private inner class PendingRemoval(val item: HomeItem, val removal: LayoutRepository.Removal) {
        /** Makes the removal permanent: releases widget ids and unpins the shortcut. */
        fun finalize() {
            removal.widgetIds.forEach { widgets.deleteId(it) }
            if (item is ShortcutItem) {
                scope.launch(Dispatchers.IO) { graph.shortcutRepository.unpin(item.packageName, item.shortcutId, item.user) }
            }
        }
    }

    /**
     * Removes [item] with a few seconds to undo. Widget ids and pinned shortcuts are only released
     * once the undo window closes, so an undone widget comes back bound and live.
     */
    fun remove(item: HomeItem) {
        scope.launch {
            val removal = graph.layoutRepository.removeItem(item.id)
            if (removal.before.isEmpty()) return@launch
            pendingRemoval?.finalize()
            val pending = PendingRemoval(item, removal)
            pendingRemoval = pending
            undo = UndoState("${labelOf(item)} removed")
            delay(UNDO_WINDOW_MS)
            if (pendingRemoval === pending) {
                pending.finalize()
                pendingRemoval = null
                undo = null
            }
        }
    }

    fun undoRemove() {
        val pending = pendingRemoval ?: return
        pendingRemoval = null
        undo = null
        scope.launch { graph.layoutRepository.restore(pending.removal) }
    }

    private fun labelOf(item: HomeItem): String = when (item) {
        is AppItem -> item.title ?: apps[item.key]?.label ?: "App"
        is ShortcutItem -> item.title
        is FolderItem -> item.title.ifBlank { "Folder" }
        is WidgetItem, is WidgetStackItem -> "Widget"
    }

    fun reorderFolder(folderId: Long, orderedIds: List<Long>) {
        scope.launch { graph.layoutRepository.reorderContainer(folderId, orderedIds) }
    }

    fun addToFolder(folderId: Long, itemId: Long, rank: Int? = null) {
        scope.launch { graph.layoutRepository.addToFolder(folderId, itemId, rank) }
    }

    fun resize(item: HomeItem, rect: CellRect) {
        scope.launch { graph.layoutRepository.resizeItem(item.id, rect) }
    }

    fun rename(itemId: Long, title: String) {
        scope.launch { graph.layoutRepository.setTitle(itemId, title) }
    }

    fun setActions(itemId: Long, tap: LauncherAction?, swipe: LauncherAction?) {
        scope.launch { graph.layoutRepository.setActions(itemId, tap, swipe) }
    }

    fun addScreen() {
        scope.launch { requestedScreen = graph.layoutRepository.addScreen() }
    }

    /** Removes a page; asks first when it still holds icons or widgets. */
    fun removeScreen(screenId: Long) {
        if (layout.value.screens.size <= 1) {
            toast("The last page can't be removed")
            return
        }
        val count = layout.value.itemsOn(screenId).size
        val remove = { scope.launch { graph.layoutRepository.removeScreen(screenId).forEach { widgets.deleteId(it) } }; Unit }
        if (count == 0) {
            remove()
        } else {
            val what = if (count == 1) "1 item" else "$count items"
            overlay.confirm("Remove this page?", "Its $what will be removed from the home screen. Apps stay installed.", "Remove", remove)
        }
    }

    // ---- Menus ---------------------------------------------------------------------------------

    fun showMenu(item: HomeItem, anchor: Rect, onEdit: () -> Unit) {
        val token = overlay.requestMenu()
        scope.launch {
            val shortcuts = if (item is AppItem) loadShortcuts(item.key) else emptyList()
            val actions = buildList<MenuAction> {
                if (item is AppItem) add(MenuAction("App info", Icons.Filled.Info) { graph.appLauncher.openAppInfo(item.key, anchor.toAndroid()) })
                if (item is WidgetItem) {
                    add(MenuAction("Resize", Icons.Filled.AspectRatio) { resizingItemId = item.id })
                    val info = widgets.info(item.appWidgetId)
                    if (info != null && widgets.isReconfigurable(info)) {
                        add(MenuAction("Settings", Icons.Filled.Settings) { widgetRequests.requestConfigure(item.appWidgetId) })
                    }
                }
                if (item is WidgetStackItem) add(MenuAction("Resize", Icons.Filled.AspectRatio) { resizingItemId = item.id })
                if (item !is WidgetItem && item !is WidgetStackItem) add(MenuAction("Edit", Icons.Filled.Edit, onEdit))
                add(MenuAction("Remove", Icons.Filled.Delete) { this@HomeController.remove(item) })
                if (item is AppItem) add(MenuAction("Uninstall", Icons.Filled.DeleteForever) { graph.appLauncher.uninstall(item.key) })
            }
            overlay.showMenu(token, ItemMenuState(anchor, shortcuts, actions))
        }
    }

    suspend fun loadShortcuts(key: ComponentKey): List<MenuShortcut> = withContext(Dispatchers.IO) {
        graph.shortcutRepository.shortcutsFor(key).map { info ->
            val icon = graph.shortcutRepository.icon(info)?.let { graph.iconRepository.render(it).asImageBitmap() }
            val label = (info.shortLabel ?: info.longLabel ?: "").toString()
            MenuShortcut(label, icon) {
                graph.appLauncher.launchShortcut(info.`package`, info.id, key.user)
            }
        }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

internal fun Rect.toAndroid(): android.graphics.Rect =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
