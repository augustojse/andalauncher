package com.anda.launcher.feature.home

import android.app.WallpaperManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.DockShape
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.core.model.PageIndicatorStyle
import com.anda.launcher.core.model.SearchBarPosition
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.drag.DropTarget
import com.anda.launcher.core.ui.drag.LocalDragController
import com.anda.launcher.core.ui.icon.iconSize
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import com.anda.launcher.core.ui.theme.toColorOr
import com.anda.launcher.feature.search.SearchBarPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/** Callbacks into the activity for things outside the home feature. */
interface HomeHost {
    fun openSearch()
    fun openVoiceSearch()
    fun openLens()

    /** [screen] deep-links into a settings page (e.g. "Home", "Folders"); null opens the main page. */
    fun openSettings(screen: String? = null)
    fun openWallpaperPicker()
    fun editItem(item: HomeItem)
}

private const val INFINITE_MULTIPLIER = 1000

/** The desktop: glance, paged grid, page indicator, dock, search bar and drag drop targets. */
@Composable
fun HomeScreen(
    controller: HomeController,
    badges: Map<PackageUserKey, Int>,
    host: HomeHost,
    modifier: Modifier = Modifier,
    /** Home gestures, applied to the content column (inside the window insets). */
    gestureModifier: Modifier = Modifier,
) {
    val settings = LocalLauncherSettings.current
    val layout by controller.layout.collectAsState()
    val home = settings.home
    val density = LocalDensity.current
    val wide = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val pagesPerView = if (wide && home.dualPanelOnLargeScreens) 2 else 1
    val viewCount = ceil(layout.screens.size / pagesPerView.toFloat()).toInt().coerceAtLeast(1)
    val infinite = home.infiniteScroll && viewCount > 1
    val pagerCount = if (infinite) viewCount * INFINITE_MULTIPLIER else viewCount
    val defaultView = (home.defaultPage / pagesPerView).coerceIn(0, viewCount - 1)
    val pagerState = rememberPagerState(initialPage = if (infinite) viewCount * (INFINITE_MULTIPLIER / 2) + defaultView else defaultView) { pagerCount }

    fun viewIndex(page: Int) = Math.floorMod(page, viewCount)

    // Scroll requests: default page and "show me the page I just added to".
    LaunchedEffect(controller.defaultPageRequest) {
        if (controller.defaultPageRequest > 0) {
            val target = pagerState.currentPage - viewIndex(pagerState.currentPage) + defaultView
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(controller.requestedScreen, layout.screens) {
        val screen = controller.requestedScreen ?: return@LaunchedEffect
        val index = layout.screens.indexOf(screen)
        if (index >= 0) {
            val view = index / pagesPerView
            pagerState.animateScrollToPage(pagerState.currentPage - viewIndex(pagerState.currentPage) + view)
            controller.requestedScreen = null
        }
    }

    WallpaperScrollEffect(pagerState, viewCount, enabled = home.wallpaperScrolling)
    EdgeScrollWhileDragging(pagerState)

    val editScale by animateFloatAsState(if (controller.editMode) 0.82f else 1f, label = "editScale")

    Box(modifier.fillMaxSize()) {
        if (home.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = home.wallpaperDim)))
        Column(Modifier.fillMaxSize().then(gestureModifier).statusBarsPadding().navigationBarsPadding()) {
            if (home.searchBar == SearchBarPosition.Top) HomeSearchBar(host)
            GlanceHeader(settings.glance)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                    scaleX = editScale
                    scaleY = editScale
                },
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val view = viewIndex(page)
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        applyPageTransition(home.pageTransition, offset)
                    },
                ) {
                    for (slot in 0 until pagesPerView) {
                        val screenIndex = view * pagesPerView + slot
                        val screenId = layout.screens.getOrNull(screenIndex)
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            if (screenId != null) {
                                WorkspacePage(
                                    screenId = screenId,
                                    isDefault = screenIndex == home.defaultPage,
                                    layout = layout,
                                    controller = controller,
                                    badges = badges,
                                    host = host,
                                )
                            }
                        }
                    }
                }
            }
            PageIndicator(home.pageIndicator, viewCount, pagerState, ::viewIndex)
            if (home.searchBar == SearchBarPosition.AboveDock) HomeSearchBar(host)
            AnimatedVisibility(settings.dock.enabled && !controller.dockHidden, enter = fadeIn(), exit = fadeOut()) {
                Dock(layout.dock, controller, badges, host)
            }
            if (home.searchBar == SearchBarPosition.BelowDock) HomeSearchBar(host)
        }

        DropTargetBar(controller, Modifier.align(Alignment.TopCenter))
        UndoBar(controller, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))

        if (controller.editMode) {
            EditModeBar(
                controller = controller,
                currentScreen = layout.screens.getOrNull(viewIndex(pagerState.currentPage) * pagesPerView),
                currentScreenIndex = viewIndex(pagerState.currentPage) * pagesPerView,
                host = host,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun HomeSearchBar(host: HomeHost) {
    val settings = LocalLauncherSettings.current
    SearchBarPill(
        style = settings.home.searchBarStyle,
        onClick = host::openSearch,
        onVoice = host::openVoiceSearch,
        onLens = host::openLens,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun WorkspacePage(
    screenId: Long,
    isDefault: Boolean,
    layout: HomeLayout,
    controller: HomeController,
    badges: Map<PackageUserKey, Int>,
    host: HomeHost,
) {
    val settings = LocalLauncherSettings.current
    val spec = GridSpec(settings.home.columns, settings.home.rows)
    val items = layout.itemsOn(screenId)
    val style = settings.icons.home
    DisposableEffect(screenId) {
        onDispose { controller.pageBounds.remove(screenId) }
    }
    Box(
        Modifier.fillMaxSize()
            .padding(horizontal = settings.home.horizontalPaddingDp.dp, vertical = settings.home.verticalPaddingDp.dp)
            .then(
                if (controller.editMode) {
                    Modifier.border(
                        2.dp,
                        if (isDefault) MaterialTheme.colorScheme.primary else LocalWallpaperTone.current.onWallpaper.copy(alpha = 0.5f),
                        MaterialTheme.shapes.extraLarge,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // Empty-space long press → edit mode. A sibling *behind* the grid only receives touches
        // that no item claimed.
        val haptics = LocalHapticFeedback.current
        Box(
            Modifier.fillMaxSize().emptySpaceLongPress {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                controller.editMode = true
            },
        )
        CellLayout(
            fineColumns = spec.fineColumns,
            fineRows = spec.fineRows,
            modifier = Modifier.fillMaxSize().testTag(TestTags.WORKSPACE_GRID)
                .onGloballyPositioned { controller.pageBounds[screenId] = it.boundsInRoot() },
        ) {
            items.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    HomeItemView(
                        item = item,
                        controller = controller,
                        style = style,
                        badges = badges,
                        onEdit = host::editItem,
                        modifier = Modifier.cell(item.rect),
                    )
                }
            }
        }
        val resizing = controller.resizingItemId?.let { id -> items.firstOrNull { it.id == id } }
        if (resizing != null) {
            ResizeFrame(resizing, spec, controller, items, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun dockShape(shape: DockShape): Shape = when (shape) {
    DockShape.Rectangle -> RoundedCornerShape(0.dp)
    DockShape.Rounded -> MaterialTheme.shapes.extraLarge
    DockShape.Pill -> CircleShape
}

/** Dock background, padding and height. Shared by the real dock and the settings preview. */
@Composable
internal fun DockFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settings = LocalLauncherSettings.current
    val dock = settings.dock
    val style = settings.icons.dock
    val height: Dp = style.iconSize() + if (style.showLabels) 36.dp else 20.dp
    val shape = dockShape(dock.backgroundShape)
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = settings.home.horizontalPaddingDp.dp, vertical = 4.dp)
            .then(
                if (dock.showBackground) {
                    Modifier.clip(shape).background(dock.backgroundColor.toColorOr(MaterialTheme.colorScheme.surfaceContainerHigh).copy(alpha = dock.backgroundOpacity))
                } else {
                    Modifier
                },
            )
            .height(height)
            .then(modifier),
        content = content,
    )
}

@Composable
private fun Dock(items: List<HomeItem>, controller: HomeController, badges: Map<PackageUserKey, Int>, host: HomeHost) {
    val settings = LocalLauncherSettings.current
    val dock = settings.dock
    val style = settings.icons.dock
    val dockPager = rememberPagerState { dock.pages.coerceAtLeast(1) }
    LaunchedEffect(dockPager.currentPage) { controller.dockPage = dockPager.currentPage }
    DisposableEffect(Unit) { onDispose { controller.dockBounds = null } }
    DockFrame(Modifier.onGloballyPositioned { controller.dockBounds = it.boundsInRoot() }) {
        HorizontalPager(state = dockPager, modifier = Modifier.fillMaxSize(), userScrollEnabled = dock.pages > 1) { page ->
            val range = (page * dock.columns) until ((page + 1) * dock.columns)
            CellLayout(fineColumns = dock.columns * GridSpec.SUBGRID, fineRows = GridSpec.SUBGRID, modifier = Modifier.fillMaxSize()) {
                items.filter { it.rect.x / GridSpec.SUBGRID in range }.forEach { item ->
                    androidx.compose.runtime.key(item.id) {
                        val slot = item.rect.x / GridSpec.SUBGRID - page * dock.columns
                        HomeItemView(
                            item = item,
                            controller = controller,
                            style = style,
                            badges = badges,
                            onEdit = host::editItem,
                            modifier = Modifier.cell(com.anda.launcher.core.model.CellRect(slot * GridSpec.SUBGRID, 0, GridSpec.SUBGRID, GridSpec.SUBGRID)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(style: PageIndicatorStyle, count: Int, pager: PagerState, viewIndex: (Int) -> Int) =
    PageIndicator(style, count) { viewIndex(pager.currentPage) + pager.currentPageOffsetFraction }

/** [position] is read in the draw phase, so scrolling never recomposes the indicator. */
@Composable
internal fun PageIndicator(style: PageIndicatorStyle, count: Int, position: () -> Float) {
    if (style == PageIndicatorStyle.None || count <= 1) return
    val color = LocalWallpaperTone.current.onWallpaper
    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
        when (style) {
            PageIndicatorStyle.Dots -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    Box(
                        Modifier.size(6.dp).graphicsLayer {
                            val distance = kotlin.math.abs(position() - i).coerceAtMost(1f)
                            alpha = 1f - 0.6f * distance
                        }.clip(CircleShape).background(color),
                    )
                }
            }
            PageIndicatorStyle.Line -> Box(Modifier.width(96.dp).height(3.dp).clip(CircleShape).background(color.copy(alpha = 0.3f))) {
                Box(
                    Modifier.width(96.dp / count).height(3.dp).graphicsLayer {
                        translationX = position() * size.width
                    }.clip(CircleShape).background(color),
                )
            }
            PageIndicatorStyle.None -> Unit
        }
    }
}

/** Launcher3-style drop bar: Remove / App info / Uninstall, shown while dragging. */
@Composable
private fun DropTargetBar(controller: HomeController, modifier: Modifier) {
    val drag = LocalDragController.current
    val payload = drag.payload ?: return
    val existing = (payload as? DragPayload.Existing)?.item
    val appKey = (existing as? AppItem)?.key ?: (payload as? DragPayload.NewApp)?.app?.key
    Row(
        modifier.statusBarsPadding().padding(top = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (existing != null) {
            DropChip("Remove", Icons.Filled.Delete, "remove") { p, _ ->
                (p as? DragPayload.Existing)?.let { controller.remove(it.item) }
                true
            }
        }
        if (appKey != null) {
            DropChip("App info", Icons.Filled.Info, "info") { _, _ ->
                controller.graph.appLauncher.openAppInfo(appKey)
                true
            }
            DropChip("Uninstall", Icons.Filled.DeleteForever, "uninstall") { _, _ ->
                controller.graph.appLauncher.uninstall(appKey)
                true
            }
        }
    }
}

/** "App removed · Undo", for a few seconds after any removal. */
@Composable
private fun UndoBar(controller: HomeController, modifier: Modifier) {
    val state = controller.undo
    // Keep the last message while animating out.
    val message = remember { arrayOf("") }
    if (state != null) message[0] = state.message
    AnimatedVisibility(state != null, modifier = modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message[0], color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1, modifier = Modifier.padding(vertical = 14.dp))
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = controller::undoRemove) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}

@Composable
private fun DropChip(label: String, icon: ImageVector, key: String, onDrop: (DragPayload, Offset) -> Boolean) {
    val drag = LocalDragController.current
    val bounds = remember { arrayOf(androidx.compose.ui.geometry.Rect.Zero) }
    DisposableEffect(key) {
        drag.register(key, { bounds[0] }, DropTarget { p, pos -> onDrop(p, pos) })
        onDispose { drag.unregister(key) }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        modifier = Modifier.onGloballyPositioned { coords ->
            // Generous hit area: the chip plus padding below it.
            val b = coords.boundsInRoot()
            bounds[0] = b.copy(top = 0f, bottom = b.bottom + 48f, left = b.left - 24f, right = b.right + 24f)
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EditModeBar(controller: HomeController, currentScreen: Long?, currentScreenIndex: Int, host: HomeHost, modifier: Modifier) {
    val graph = controller.graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Surface(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
            EditAction("Done", Icons.Filled.Check) { controller.editMode = false }
            EditAction("Customize", Icons.Filled.Tune) { controller.openCustomize() }
            EditAction("Wallpaper", Icons.Filled.Wallpaper) { host.openWallpaperPicker() }
            EditAction("Widgets", Icons.Filled.Widgets) { controller.widgetPickerOpen = true }
            EditAction("Add page", Icons.Filled.Add) { controller.addScreen() }
            if (currentScreen != null) {
                EditAction("Remove", Icons.Filled.Delete) { controller.removeScreen(currentScreen) }
                EditAction("Default", Icons.Filled.Home) {
                    scope.launch { graph.settingsRepository.update { it.copy(home = it.home.copy(defaultPage = currentScreenIndex)) } }
                }
            }
            EditAction("Settings", Icons.Filled.Settings) { host.openSettings("Home") }
        }
    }
}

/**
 * Long-press that never consumes anything unless it fires, so taps, double taps, swipes and the
 * pager keep working on empty space.
 */
private fun Modifier.emptySpaceLongPress(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (event.changes.size > 1 || change.isConsumed || !change.pressed ||
                    (change.position - down.position).getDistance() > slop
                ) {
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        if (interrupted == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun EditAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

/** Drives wallpaper parallax from the pager position (Nova "wallpaper scrolling"). */
@Composable
private fun WallpaperScrollEffect(pager: PagerState, viewCount: Int, enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(pager, viewCount, enabled) {
        val wm = WallpaperManager.getInstance(context)
        val step = if (viewCount > 1) 1f / (viewCount - 1) else 0f
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
            val token = view.windowToken ?: return@collect
            val x = if (!enabled || viewCount <= 1) {
                0.5f
            } else {
                val wrapped = position - floor(position / viewCount) * viewCount
                (wrapped / (viewCount - 1)).coerceIn(0f, 1f)
            }
            runCatching {
                wm.setWallpaperOffsetSteps(step, 1f)
                wm.setWallpaperOffsets(token, x, 0.5f)
            }
        }
    }
}

/** While dragging, hovering near a screen edge flips pages (Launcher3 behaviour). */
@Composable
private fun EdgeScrollWhileDragging(pager: PagerState) {
    val drag = LocalDragController.current
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val edge = with(density) { 28.dp.toPx() }
    val dragging = drag.isDragging
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var since = 0L
        while (true) {
            delay(80)
            val x = drag.position.x
            val dir = when {
                x < edge -> -1
                x > widthPx - edge -> 1
                else -> 0
            }
            if (dir == 0) {
                since = 0L
                continue
            }
            val now = System.currentTimeMillis()
            if (since == 0L) {
                since = now
            } else if (now - since > 550) {
                val target = (pager.currentPage + dir).coerceIn(0, pager.pageCount - 1)
                pager.animateScrollToPage(target)
                since = 0L
            }
        }
    }
}
