package com.anda.launcher.feature.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.DrawerSettings
import com.anda.launcher.core.model.DrawerSort
import com.anda.launcher.core.model.DrawerStyle
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.LaunchStat
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.core.model.packageUser
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.gesture.ItemGestureCallbacks
import com.anda.launcher.core.ui.gesture.launcherItemGestures
import com.anda.launcher.core.ui.gesture.pressScale
import com.anda.launcher.core.ui.gesture.rememberItemFeedback
import com.anda.launcher.core.ui.icon.AppIconTile
import com.anda.launcher.core.ui.icon.iconSize
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.theme.toColorOr
import com.anda.launcher.feature.search.SearchEngine
import com.anda.launcher.feature.search.SearchField
import com.anda.launcher.feature.search.SearchResultActions
import com.anda.launcher.feature.search.SearchResults
import com.anda.launcher.feature.search.SearchResultsList
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import kotlinx.collections.immutable.ImmutableList

/**
 * App drawer. Always composed (translated off-screen when closed) so it opens instantly and a
 * drag that starts here survives the drawer closing.
 */
@Composable
fun AppDrawer(
    controller: DrawerController,
    engine: SearchEngine,
    apps: ImmutableList<AppInfo>,
    groups: List<DrawerGroup>,
    stats: Map<ComponentKey, LaunchStat>,
    badges: Map<PackageUserKey, Int>,
    searchActions: SearchResultActions,
    modifier: Modifier = Modifier,
) {
    val settings = LocalLauncherSettings.current
    val drawer = settings.drawer
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = controller.isOpen) {
        if (controller.query.isNotEmpty()) controller.query = "" else controller.close()
    }
    LaunchedEffect(controller.focusRequests) {
        if (controller.focusRequests > 0) runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(controller.isOpen) {
        if (controller.isOpen && drawer.openKeyboard) runCatching { focusRequester.requestFocus() }
        if (!controller.isOpen) focusManager.clearFocus()
    }

    BoxWithConstraints(
        modifier.fillMaxSize().testTag(TestTags.APP_DRAWER).graphicsLayer {
            translationY = (1f - controller.progress.value) * size.height
        },
    ) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val dragToClose = remember(heightPx) { DrawerNestedScroll(controller, heightPx) }
        val background = drawer.backgroundColor.toColorOr(MaterialTheme.colorScheme.surface)
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = controller.progress.value }
                .background(background.copy(alpha = drawer.backgroundOpacity)),
        )

        val visible = remember(apps, groups, stats, drawer, controller.tab, controller.hiddenUnlocked) {
            visibleApps(apps, groups, stats, drawer.sort, controller.tab, controller.hiddenUnlocked, drawer.showWorkTab, drawer.showDrawerFolders)
        }
        val drawerFolders = if (drawer.showDrawerFolders && controller.tab == DrawerTab.All) groups.filter { it.isFolder } else emptyList()
        val suggestions = remember(apps, stats, drawer.columns) { suggestions(apps, stats, drawer.columns) }
        val hiddenCount = remember(apps) { apps.count { it.isHidden } }

        val results by produceState(SearchResults(), controller.query, apps, stats, settings.search) {
            value = engine.search(controller.query, settings.search, apps, stats, drawer.hiddenAppsInSearch || controller.hiddenUnlocked)
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .nestedScroll(dragToClose),
        ) {
            val header: @Composable () -> Unit = {
                Column(
                    Modifier.draggable(
                        state = rememberDraggableState { delta -> controller.dragBy(-delta / heightPx) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> controller.settle(-velocity / heightPx) },
                    ),
                ) {
                    SearchField(
                        query = controller.query,
                        onQueryChange = { controller.query = it },
                        onSubmit = {
                            val top = results.topApp
                            if (settings.search.launchTopOnEnter && top != null) controller.launch(top, Rect.Zero)
                        },
                        focusRequester = focusRequester,
                        style = settings.home.searchBarStyle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        trailing = { DrawerQuickMenu(controller, drawer) },
                    )
                    if (controller.query.isEmpty()) DrawerTabs(controller, apps, groups, drawer.manualTabs, drawer.autoCategoryTabs, drawer.showWorkTab)
                }
            }
            if (!drawer.searchBarBottom) header()
            Box(Modifier.weight(1f)) {
                if (controller.query.isNotBlank()) {
                    SearchResultsList(
                        results = results,
                        columns = drawer.columns,
                        actions = searchActions,
                        appRow = { row -> AppRow(row, controller, settings.icons.drawer, badges, drawer.columns) },
                        reverseLayout = drawer.searchBarBottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    when (drawer.style) {
                        DrawerStyle.VerticalGrid -> VerticalGridDrawer(
                            controller, visible, drawerFolders, if (drawer.showSuggestions && controller.tab == DrawerTab.All) suggestions else emptyList(),
                            settings.icons.drawer, badges, drawer.columns, drawer.fastScroller && drawer.sort == DrawerSort.Alphabetical,
                            hiddenCount, apps,
                        )
                        DrawerStyle.HorizontalPaged -> PagedDrawer(
                            controller, visible, drawerFolders, settings.icons.drawer, badges, drawer.columns, drawer.pageRows, hiddenCount, apps,
                        )
                        DrawerStyle.List -> ListDrawer(
                            controller, visible, drawerFolders, badges, drawer.fastScroller && drawer.sort == DrawerSort.Alphabetical, hiddenCount, apps,
                        )
                    }
                }
            }
            if (drawer.searchBarBottom) header()
        }

        controller.openGroupId?.let { id ->
            groups.firstOrNull { it.id == id }?.let { group ->
                DrawerFolderDialog(group, apps.filter { it.key in group.members }, controller, settings.icons.drawer, badges)
            }
        }
    }
}

/** Drag-down-at-top closes the drawer; drag-up while partly closed re-opens it. */
private class DrawerNestedScroll(private val controller: DrawerController, private val heightPx: Float) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && controller.progress.value < 1f && available.y < 0) {
            controller.dragBy(-available.y / heightPx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0) {
            controller.dragBy(-available.y / heightPx)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (controller.progress.value < 1f) {
            controller.settle(-available.y / heightPx)
            return available
        }
        return Velocity.Zero
    }
}

// ---- Data shaping --------------------------------------------------------------------------------

internal fun visibleApps(
    apps: List<AppInfo>,
    groups: List<DrawerGroup>,
    stats: Map<ComponentKey, LaunchStat>,
    sort: DrawerSort,
    tab: DrawerTab,
    hiddenUnlocked: Boolean,
    separateWork: Boolean,
    drawerFolders: Boolean,
): List<AppInfo> {
    val inFolders = if (drawerFolders) groups.filter { it.isFolder }.flatMap { it.members }.toSet() else emptySet()
    val base = apps.filter { !it.isHidden || hiddenUnlocked }
    val filtered = when (tab) {
        DrawerTab.All -> base.filter { (!separateWork || !it.isWorkProfile) && it.key !in inFolders }
        DrawerTab.Work -> base.filter { it.isWorkProfile }
        is DrawerTab.Group -> {
            val members = groups.firstOrNull { it.id == tab.id }?.members.orEmpty()
            base.filter { it.key in members }
        }
        is DrawerTab.Category -> base.filter { it.category == tab.category }
    }
    return when (sort) {
        DrawerSort.Alphabetical -> filtered
        DrawerSort.InstallDate -> filtered.sortedByDescending { it.installTime }
        DrawerSort.MostUsed -> filtered.sortedByDescending { stats[it.key]?.count ?: 0 }
    }
}

/** Frequency × recency: apps used often and lately float up (Pixel-like suggestions). */
internal fun suggestions(apps: List<AppInfo>, stats: Map<ComponentKey, LaunchStat>, count: Int, now: Long = System.currentTimeMillis()): List<AppInfo> =
    apps.asSequence()
        .filter { !it.isHidden && stats.containsKey(it.key) }
        .sortedByDescending { app ->
            val s = stats.getValue(app.key)
            val ageDays = (now - s.lastLaunch).coerceAtLeast(0) / 86_400_000.0
            s.count / (1.0 + ageDays)
        }
        .take(count)
        .toList()

// ---- Quick menu ----------------------------------------------------------------------------------

/** Layout, sort and columns right where you see the effect; everything else is one tap away. */
@Composable
private fun DrawerQuickMenu(controller: DrawerController, drawer: DrawerSettings) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Drawer options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("Layout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf(DrawerStyle.VerticalGrid to "Grid", DrawerStyle.HorizontalPaged to "Pages", DrawerStyle.List to "List").forEach { (style, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { controller.updateSettings { it.copy(drawer = it.drawer.copy(style = style)) } },
                    trailingIcon = { if (drawer.style == style) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
            HorizontalDivider()
            Text("Sort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            listOf(DrawerSort.Alphabetical to "A–Z", DrawerSort.InstallDate to "Newest first", DrawerSort.MostUsed to "Most used").forEach { (sort, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { controller.updateSettings { it.copy(drawer = it.drawer.copy(sort = sort)) } },
                    trailingIcon = { if (drawer.sort == sort) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
            if (drawer.style != DrawerStyle.List) {
                HorizontalDivider()
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Columns", Modifier.padding(start = 8.dp).weight(1f, fill = false))
                    IconButton(onClick = { controller.updateSettings { it.copy(drawer = it.drawer.copy(columns = (it.drawer.columns - 1).coerceAtLeast(3))) } }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Fewer columns")
                    }
                    Text("${drawer.columns}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { controller.updateSettings { it.copy(drawer = it.drawer.copy(columns = (it.drawer.columns + 1).coerceAtMost(8))) } }) {
                        Icon(Icons.Filled.Add, contentDescription = "More columns")
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Drawer settings") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    controller.openSettings()
                },
            )
        }
    }
}

// ---- Tabs ----------------------------------------------------------------------------------------

@Composable
private fun DrawerTabs(
    controller: DrawerController,
    apps: List<AppInfo>,
    groups: List<DrawerGroup>,
    manualTabs: Boolean,
    autoCategories: Boolean,
    workTab: Boolean,
) {
    val hasWork = remember(apps) { apps.any { it.isWorkProfile } }
    val categories = remember(apps, autoCategories) {
        if (autoCategories) AppCategory.entries.filter { c -> apps.count { it.category == c && !it.isHidden } >= 2 } else emptyList()
    }
    val tabs = buildList {
        add(DrawerTab.All)
        if (workTab && hasWork) add(DrawerTab.Work)
        if (manualTabs) groups.filter { !it.isFolder }.forEach { add(DrawerTab.Group(it.id, it.name)) }
        categories.forEach { add(DrawerTab.Category(it)) }
    }
    if (tabs.size <= 1 && !manualTabs) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.toString() }) { tab ->
            val label = when (tab) {
                DrawerTab.All -> "All"
                DrawerTab.Work -> "Work"
                is DrawerTab.Group -> tab.name
                is DrawerTab.Category -> tab.category.name
            }
            FilterChip(
                selected = controller.tab == tab,
                onClick = {
                    if (controller.tab == tab && tab is DrawerTab.Group) {
                        controller.editGroup(groups.firstOrNull { it.id == tab.id })
                    } else {
                        controller.tab = tab
                    }
                },
                label = { Text(label) },
            )
        }
        if (manualTabs) {
            item(key = "add") {
                IconButton(onClick = { controller.editGroup(null) }) { Icon(Icons.Filled.Add, contentDescription = "New tab or folder") }
            }
        }
    }
}

// ---- Layouts -------------------------------------------------------------------------------------

@Composable
private fun VerticalGridDrawer(
    controller: DrawerController,
    visible: List<AppInfo>,
    folders: List<DrawerGroup>,
    suggestions: List<AppInfo>,
    style: IconStyle,
    badges: Map<PackageUserKey, Int>,
    columns: Int,
    fastScroller: Boolean,
    hiddenCount: Int,
    allApps: List<AppInfo>,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(controller.isOpen) { if (!controller.isOpen) gridState.scrollToItem(0) }
    val headerCount = (if (suggestions.isNotEmpty()) suggestions.size + 1 else 0) + folders.size
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (suggestions.isNotEmpty()) {
                items(suggestions, key = { "s" + it.key.flatten() }) { app -> DrawerAppTile(app, controller, style, badges) }
                item(key = "divider", span = { GridItemSpan(maxLineSpan) }) { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
            }
            items(folders, key = { "f${it.id}" }) { group ->
                DrawerFolderTile(group, allApps.filter { it.key in group.members }, style) { controller.openGroupId = group.id }
            }
            items(visible, key = { it.key.flatten() }) { app -> DrawerAppTile(app, controller, style, badges) }
            if (showHiddenButton(controller, hiddenCount)) {
                item(key = "hidden", span = { GridItemSpan(maxLineSpan) }) { HiddenAppsButton(controller, hiddenCount) }
            }
        }
        if (fastScroller) {
            FastScroller(
                labels = visible.map { it.sortKey },
                onScrollTo = { index -> gridState.scrollToItem(index + headerCount) },
            )
        }
    }
}

private fun showHiddenButton(controller: DrawerController, hiddenCount: Int) =
    hiddenCount > 0 && !controller.hiddenUnlocked && controller.tab == DrawerTab.All

@Composable
private fun HiddenAppsButton(controller: DrawerController, hiddenCount: Int, modifier: Modifier = Modifier) {
    TextButton(onClick = controller::unlockHidden, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Hidden apps ($hiddenCount)")
    }
}

/** One cell of the paged drawer: drawer folders lead page one, then apps. */
private sealed interface PagedEntry {
    data class Folder(val group: DrawerGroup) : PagedEntry
    data class App(val app: AppInfo) : PagedEntry
}

@Composable
private fun PagedDrawer(
    controller: DrawerController,
    visible: List<AppInfo>,
    folders: List<DrawerGroup>,
    style: IconStyle,
    badges: Map<PackageUserKey, Int>,
    columns: Int,
    rows: Int,
    hiddenCount: Int,
    allApps: List<AppInfo>,
) {
    val pages = remember(visible, folders, columns, rows) {
        (folders.map { PagedEntry.Folder(it) } + visible.map { PagedEntry.App(it) }).chunked((columns * rows).coerceAtLeast(1))
    }
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    LaunchedEffect(controller.isOpen) { if (!controller.isOpen) pager.scrollToPage(0) }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                pages.getOrNull(page).orEmpty().chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { entry ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                when (entry) {
                                    is PagedEntry.App -> DrawerAppTile(entry.app, controller, style, badges)
                                    is PagedEntry.Folder -> DrawerFolderTile(entry.group, allApps.filter { it.key in entry.group.members }, style) {
                                        controller.openGroupId = entry.group.id
                                    }
                                }
                            }
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (showHiddenButton(controller, hiddenCount)) HiddenAppsButton(controller, hiddenCount)
    }
}

@Composable
private fun ListDrawer(
    controller: DrawerController,
    visible: List<AppInfo>,
    folders: List<DrawerGroup>,
    badges: Map<PackageUserKey, Int>,
    fastScroller: Boolean,
    hiddenCount: Int,
    allApps: List<AppInfo>,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(controller.isOpen) { if (!controller.isOpen) listState.scrollToItem(0) }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(folders, key = { "f${it.id}" }) { group ->
                DrawerFolderListRow(group, allApps.filter { it.key in group.members }) { controller.openGroupId = group.id }
            }
            items(visible, key = { it.key.flatten() }) { app -> DrawerListRow(app, controller, badges) }
            if (showHiddenButton(controller, hiddenCount)) item(key = "hidden") { HiddenAppsButton(controller, hiddenCount) }
        }
        if (fastScroller) FastScroller(labels = visible.map { it.sortKey }, onScrollTo = { listState.scrollToItem(it + folders.size) })
    }
}

@Composable
private fun DrawerFolderListRow(group: DrawerGroup, members: List<AppInfo>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                members.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        row.forEach { app ->
                            val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                            if (icon != null) Image(icon, null, Modifier.size(14.dp)) else Box(Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        Text(
            group.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("${members.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AppRow(row: List<AppInfo>, controller: DrawerController, style: IconStyle, badges: Map<PackageUserKey, Int>, columns: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        row.forEach { app -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DrawerAppTile(app, controller, style, badges) } }
        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
    }
}

private class Coords {
    var value: LayoutCoordinates? = null
    fun bounds(): Rect = value?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
    fun toRoot(p: Offset): Offset = value?.takeIf { it.isAttached }?.localToRoot(p) ?: p
}

@Composable
internal fun DrawerAppTile(app: AppInfo, controller: DrawerController, style: IconStyle, badges: Map<PackageUserKey, Int>) {
    val settings = LocalLauncherSettings.current
    val icon = rememberAppIcon(app.key, app.lastUpdateTime)
    val coords = remember { Coords() }
    val iconPx = with(LocalDensity.current) { style.iconSize().toPx() }
    val feedback = rememberItemFeedback()
    val callbacks = remember(app, icon, iconPx) {
        object : ItemGestureCallbacks {
            override fun onTap(position: Offset) = controller.launch(app, coords.bounds())
            override fun onLongPress(position: Offset) = controller.showMenu(app, coords.bounds())
            override fun onDragStart(position: Offset) {
                controller.overlay.dismissMenu()
                controller.drag.start(DragPayload.NewApp(app), icon, style.iconSize(), coords.toRoot(position), Offset(iconPx / 2, iconPx / 2))
                controller.onDragOut()
            }
            override fun onDrag(delta: Offset, position: Offset) = controller.drag.moveTo(coords.toRoot(position))
        }
    }
    AppIconTile(
        icon = icon,
        label = app.label,
        style = style,
        defaultLabelColor = MaterialTheme.colorScheme.onSurface,
        badgeCount = badges[app.key.packageUser] ?: 0,
        badgeMode = settings.badges.mode,
        badgeColor = settings.badges.color.toColorOr(MaterialTheme.colorScheme.primary),
        isWork = app.isWorkProfile,
        isLocked = app.isLocked,
        iconAlpha = { if (app.isHidden) 0.45f else 1f },
        modifier = Modifier.onGloballyPositioned { coords.value = it }.launcherItemGestures(app.key, callbacks, feedback).pressScale(feedback),
    )
}

@Composable
private fun DrawerListRow(app: AppInfo, controller: DrawerController, badges: Map<PackageUserKey, Int>) {
    val icon = rememberAppIcon(app.key, app.lastUpdateTime)
    val coords = remember { Coords() }
    val callbacks = remember(app, icon) {
        object : ItemGestureCallbacks {
            override fun onTap(position: Offset) = controller.launch(app, coords.bounds())
            override fun onLongPress(position: Offset) = controller.showMenu(app, coords.bounds())
            override fun onDragStart(position: Offset) {
                controller.overlay.dismissMenu()
                controller.drag.start(DragPayload.NewApp(app), icon, 56.dp, coords.toRoot(position), Offset.Zero)
                controller.onDragOut()
            }
            override fun onDrag(delta: Offset, position: Offset) = controller.drag.moveTo(coords.toRoot(position))
        }
    }
    val feedback = rememberItemFeedback()
    Row(
        // Full-width rows get the long-press haptic but no press-in scale (it reads as a wobble).
        Modifier.fillMaxWidth().onGloballyPositioned { coords.value = it }.launcherItemGestures(app.key, callbacks, feedback)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            color = if (app.isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        val count = badges[app.key.packageUser] ?: 0
        if (count > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun DrawerFolderTile(group: DrawerGroup, members: List<AppInfo>, style: IconStyle, onClick: () -> Unit) {
    val size = style.iconSize()
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                members.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { app ->
                            val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                            if (icon != null) Image(icon, null, Modifier.size(size * 0.34f)) else Box(Modifier.size(size * 0.34f))
                        }
                    }
                }
            }
        }
        if (style.showLabels) {
            Spacer(Modifier.height(4.dp))
            Text(group.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
