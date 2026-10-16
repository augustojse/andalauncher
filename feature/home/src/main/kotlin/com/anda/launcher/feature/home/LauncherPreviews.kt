package com.anda.launcher.feature.home

import android.appwidget.AppWidgetManager
import android.view.RoundedCorner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.DrawerStyle
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.FolderOpenStyle
import com.anda.launcher.core.model.FolderPreview
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.SearchBarPosition
import com.anda.launcher.core.model.ShortcutItem
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.icon.AppIconTile
import com.anda.launcher.core.ui.icon.BaseIconSize
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.theme.LauncherTheme
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import com.anda.launcher.core.ui.theme.WallpaperTone
import com.anda.launcher.core.ui.theme.toColorOr
import com.anda.launcher.feature.search.SearchBarPill
import com.anda.launcher.feature.search.shape
import kotlinx.collections.immutable.toImmutableList
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.roundToInt

/** What a settings preview shows on top of the home screen. */
enum class PreviewMode { Home, Folder, Drawer }

/**
 * A faithful miniature of the launcher for the settings screens.
 *
 * The phone is laid out at the **real window size** with the **real composables** (CellLayout,
 * AppIconTile, FolderIcon, DockFrame, SearchBarPill, GlanceHeader, PageIndicator) and the user's
 * actual layout, then scaled down as one layer, so spacing, label wrapping and icon sizes match
 * the home screen exactly. Only two things are approximated: the wallpaper (third-party apps
 * can't read the wallpaper bitmap, so it's a gradient of its colors) and live widgets (shown as
 * their preview images).
 */
@Composable
fun LauncherPreview(
    settings: LauncherSettings,
    layout: HomeLayout,
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    mode: PreviewMode = PreviewMode.Home,
    suggestions: List<AppInfo> = emptyList(),
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val phoneW = with(density) { window.width.toDp() }.coerceAtLeast(1.dp)
    val phoneH = with(density) { window.height.toDp() }.coerceAtLeast(1.dp)
    val view = LocalView.current
    val cornerPx = remember(view) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
    }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val lookup: (ComponentKey) -> AppInfo? = remember(appsByKey) { { appsByKey[it] } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / phoneW, maxHeight / phoneH).coerceIn(0.05f, 1f)
        val corner = with(density) { cornerPx.toDp() }.coerceAtLeast(20.dp) * scale
        Box(
            Modifier.clip(RoundedCornerShape(corner))
                .background(Color.Black),
        ) {
            ScaledPhone(phoneW, phoneH, scale) {
                CompositionLocalProvider(LocalLauncherSettings provides settings, LocalAppLookup provides lookup) {
                    LauncherTheme(settings.theme) {
                        PhoneContent(settings, layout, apps, mode, suggestions)
                    }
                }
            }
        }
    }
}

/** Measures [content] at full phone size and reports/draws it at [scale]. */
@Composable
private fun ScaledPhone(width: Dp, height: Dp, scale: Float, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val w = width.roundToPx()
        val h = height.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixed(w, h))
        layout((w * scale).roundToInt(), (h * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun PhoneContent(settings: LauncherSettings, layout: HomeLayout, apps: List<AppInfo>, mode: PreviewMode, suggestions: List<AppInfo>) {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val tone = LocalWallpaperTone.current
    Box(Modifier.fillMaxSize().background(wallpaperBrush(tone))) {
        if (mode == PreviewMode.Drawer) {
            DrawerContent(settings, apps, suggestions, statusTop, navBottom)
        } else {
            HomeContent(settings, layout, if (settings.home.showStatusBar) statusTop else 0.dp, navBottom)
            if (mode == PreviewMode.Folder) FolderContent(settings, layout, apps)
        }
        if (settings.home.showStatusBar || mode == PreviewMode.Drawer) {
            StatusBarMock(statusTop, if (mode == PreviewMode.Drawer) MaterialTheme.colorScheme.onSurface else tone.onWallpaper)
        }
        NavHandleMock(navBottom, if (mode == PreviewMode.Drawer) MaterialTheme.colorScheme.onSurface else tone.onWallpaper)
    }
}

private fun wallpaperBrush(tone: WallpaperTone): Brush {
    val base = tone.primary ?: Color(0xFF2B2F3A)
    val second = tone.secondary ?: base.copy(red = base.red * 0.6f, green = base.green * 0.6f, blue = base.blue * 0.6f)
    val third = tone.tertiary ?: second
    return Brush.linearGradient(listOf(base, second, third))
}

// ---- Home ----------------------------------------------------------------------------------------

@Composable
private fun HomeContent(settings: LauncherSettings, layout: HomeLayout, topInset: Dp, navBottom: Dp) {
    val home = settings.home
    val wide = LocalWindowInfo.current.containerSize.let { with(LocalDensity.current) { it.width.toDp() } } >= 600.dp
    val pagesPerView = if (wide && home.dualPanelOnLargeScreens) 2 else 1
    val viewCount = ceil(layout.screens.size / pagesPerView.toFloat()).toInt().coerceAtLeast(1)
    val defaultView = (home.defaultPage / pagesPerView).coerceIn(0, viewCount - 1)
    if (home.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = home.wallpaperDim)))
    Column(Modifier.fillMaxSize().padding(top = topInset, bottom = navBottom)) {
        if (home.searchBar == SearchBarPosition.Top) StaticSearchBar(settings)
        GlanceHeader(settings.glance)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            for (slot in 0 until pagesPerView) {
                val screenId = layout.screens.getOrNull(defaultView * pagesPerView + slot)
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (screenId != null) StaticPage(layout.itemsOn(screenId), settings)
                }
            }
        }
        PageIndicator(home.pageIndicator, viewCount) { defaultView.toFloat() }
        if (home.searchBar == SearchBarPosition.AboveDock) StaticSearchBar(settings)
        if (settings.dock.enabled) StaticDock(layout.dock, settings)
        if (home.searchBar == SearchBarPosition.BelowDock) StaticSearchBar(settings)
    }
}

@Composable
private fun StaticSearchBar(settings: LauncherSettings) {
    SearchBarPill(
        style = settings.home.searchBarStyle,
        onClick = {},
        onVoice = {},
        onLens = {},
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun StaticPage(items: List<HomeItem>, settings: LauncherSettings) {
    val spec = GridSpec(settings.home.columns, settings.home.rows)
    Box(Modifier.fillMaxSize().padding(horizontal = settings.home.horizontalPaddingDp.dp, vertical = settings.home.verticalPaddingDp.dp)) {
        CellLayout(fineColumns = spec.fineColumns, fineRows = spec.fineRows, modifier = Modifier.fillMaxSize()) {
            items.forEach { item ->
                // Items that no longer fit the (draft) grid are skipped, as the real grid clips them.
                if (item.rect.right <= spec.fineColumns && item.rect.bottom <= spec.fineRows) {
                    androidx.compose.runtime.key(item.id) {
                        Box(Modifier.cell(item.rect), contentAlignment = Alignment.Center) {
                            StaticItem(item, settings.icons.home, settings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticDock(items: List<HomeItem>, settings: LauncherSettings) {
    val columns = settings.dock.columns
    DockFrame {
        CellLayout(fineColumns = columns * GridSpec.SUBGRID, fineRows = GridSpec.SUBGRID, modifier = Modifier.fillMaxSize()) {
            items.filter { it.rect.x / GridSpec.SUBGRID < columns }.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    val slot = item.rect.x / GridSpec.SUBGRID
                    Box(Modifier.cell(CellRect(slot * GridSpec.SUBGRID, 0, GridSpec.SUBGRID, GridSpec.SUBGRID)), contentAlignment = Alignment.Center) {
                        StaticItem(item, settings.icons.dock, settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticItem(item: HomeItem, style: IconStyle, settings: LauncherSettings, labelColor: Color? = null) {
    when (item) {
        is AppItem -> {
            val app = LocalAppLookup.current(item.key)
            AppIconTile(
                icon = itemIcon(item),
                label = item.title ?: app?.label ?: "",
                style = style,
                defaultLabelColor = labelColor ?: LocalWallpaperTone.current.onWallpaper,
                badgeMode = settings.badges.mode,
                isWork = app?.isWorkProfile == true,
            )
        }
        is ShortcutItem -> AppIconTile(
            icon = itemIcon(item),
            label = item.title,
            style = style,
            defaultLabelColor = labelColor ?: LocalWallpaperTone.current.onWallpaper,
        )
        is FolderItem -> FolderIcon(item, style, emptyMap(), labelColor ?: LocalWallpaperTone.current.onWallpaper)
        is WidgetItem -> WidgetPreviewTile(item.provider, settings.home.widgetPaddingDp.dp)
        is WidgetStackItem -> item.widgets.firstOrNull()?.let { WidgetPreviewTile(it.provider, settings.home.widgetPaddingDp.dp) }
    }
}

/** The widget's own preview image (what the widget picker shows), never a live view. */
@Composable
private fun WidgetPreviewTile(provider: ComponentKey, padding: Dp) {
    val context = LocalContext.current
    val densityDpi = (LocalDensity.current.density * 160).roundToInt()
    val image: ImageBitmap? = remember(provider, densityDpi) {
        runCatching {
            AppWidgetManager.getInstance(context).installedProviders
                .firstOrNull { it.provider.packageName == provider.packageName && it.provider.className == provider.className }
                ?.let { it.loadPreviewImage(context, densityDpi) ?: it.loadIcon(context, densityDpi) }
                ?.toBitmap()?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        Modifier.fillMaxSize().padding(padding).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
    }
}

// ---- Folder --------------------------------------------------------------------------------------

/** Mirrors FolderOverlay: scrim plus the panel, using a real folder when the layout has one. */
@Composable
private fun FolderContent(settings: LauncherSettings, layout: HomeLayout, apps: List<AppInfo>) {
    val folder = remember(layout, apps) { firstFolder(layout) ?: sampleFolder(apps) } ?: return
    val fullScreen = settings.folders.openStyle == FolderOpenStyle.FullScreen
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Surface(
            shape = if (fullScreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.extraLarge,
            color = settings.folders.backgroundColor.toColorOr(MaterialTheme.colorScheme.surfaceContainer).copy(alpha = settings.folders.backgroundOpacity),
            modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.padding(24.dp).widthIn(max = 420.dp),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    folder.title,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                val columns = settings.folders.columns.coerceAtLeast(2)
                folder.children.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { child ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                StaticItem(child, settings.icons.folder, settings, labelColor = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun firstFolder(layout: HomeLayout): FolderItem? =
    (layout.desktop.values.flatten() + layout.dock).filterIsInstance<FolderItem>().firstOrNull()

private fun sampleFolder(apps: List<AppInfo>): FolderItem? {
    val sample = apps.filter { !it.isHidden }.take(6)
    if (sample.isEmpty()) return null
    val children = sample.mapIndexed { i, app ->
        AppItem(id = -2L - i, container = -1L, screenId = 0, rect = CellRect(0, 0, GridSpec.SUBGRID, GridSpec.SUBGRID), rank = i, key = app.key)
    }
    return FolderItem(-1L, Containers.DESKTOP, 0, CellRect(0, 0, GridSpec.SUBGRID, GridSpec.SUBGRID), 0, "Folder", children.toImmutableList())
}

/** A folder icon drawn with the real FolderIcon in a given [preview] style (settings picker swatch). */
@Composable
fun FolderPreviewSwatch(preview: FolderPreview, apps: List<AppInfo>, size: Dp = 48.dp) {
    val settings = LocalLauncherSettings.current
    val folder = remember(apps) { sampleFolder(apps) } ?: return
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    CompositionLocalProvider(
        LocalLauncherSettings provides settings.copy(folders = settings.folders.copy(preview = preview)),
        LocalAppLookup provides { appsByKey[it] },
    ) {
        FolderIcon(folder, IconStyle(sizePercent = (size / BaseIconSize * 100).roundToInt(), showLabels = false), emptyMap())
    }
}

// ---- Drawer --------------------------------------------------------------------------------------

/** Mirrors AppDrawer at full open: background, search field, tabs and the chosen layout. */
@Composable
private fun DrawerContent(settings: LauncherSettings, apps: List<AppInfo>, suggestions: List<AppInfo>, statusTop: Dp, navBottom: Dp) {
    val drawer = settings.drawer
    val background = drawer.backgroundColor.toColorOr(MaterialTheme.colorScheme.surface)
    val visible = remember(apps) { apps.filter { !it.isHidden } }
    Box(Modifier.fillMaxSize().background(background.copy(alpha = drawer.backgroundOpacity))) {
        Column(Modifier.fillMaxSize().padding(top = statusTop, bottom = navBottom)) {
            val header: @Composable () -> Unit = {
                Column {
                    SearchFieldMock(settings, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (drawer.manualTabs) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = true, onClick = {}, label = { Text("All") })
                            if (drawer.showWorkTab && apps.any { it.isWorkProfile }) FilterChip(selected = false, onClick = {}, label = { Text("Work") })
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            if (!drawer.searchBarBottom) header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (drawer.style) {
                    DrawerStyle.VerticalGrid -> GridMock(settings, visible, if (drawer.showSuggestions) suggestions.take(drawer.columns) else emptyList())
                    DrawerStyle.HorizontalPaged -> GridMock(settings, visible.take(drawer.columns * drawer.pageRows), emptyList(), paged = true)
                    DrawerStyle.List -> ListMock(visible)
                }
            }
            if (drawer.searchBarBottom) header()
        }
    }
}

@Composable
private fun SearchFieldMock(settings: LauncherSettings, modifier: Modifier) {
    Surface(
        shape = settings.home.searchBarStyle.shape(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Search apps and more", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun GridMock(settings: LauncherSettings, apps: List<AppInfo>, suggestions: List<AppInfo>, paged: Boolean = false) {
    val columns = settings.drawer.columns
    val style = settings.icons.drawer
    // Same paddings as AppDrawer's LazyVerticalGrid: 8 dp content padding, 14 dp row spacing.
    Column(
        Modifier.fillMaxSize().padding(PaddingValues(8.dp)),
        verticalArrangement = if (paged) Arrangement.SpaceEvenly else Arrangement.spacedBy(14.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            DrawerRowMock(suggestions, columns, style)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        apps.chunked(columns).forEach { row -> DrawerRowMock(row, columns, style) }
    }
}

@Composable
private fun DrawerRowMock(row: List<AppInfo>, columns: Int, style: IconStyle) {
    Row(Modifier.fillMaxWidth()) {
        row.forEach { app ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconTile(
                    icon = rememberAppIcon(app.key, app.lastUpdateTime),
                    label = app.label,
                    style = style,
                    defaultLabelColor = MaterialTheme.colorScheme.onSurface,
                    isWork = app.isWorkProfile,
                    isLocked = app.isLocked,
                )
            }
        }
        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
    }
}

@Composable
private fun ListMock(apps: List<AppInfo>) {
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        apps.take(24).forEach { app ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
                Text(app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---- System chrome -------------------------------------------------------------------------------

@Composable
private fun StatusBarMock(height: Dp, color: Color) {
    if (height <= 0.dp) return
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) }
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = color, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Wifi, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.SignalCellular4Bar, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.BatteryFull, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NavHandleMock(height: Dp, color: Color) {
    if (height <= 0.dp) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.height(height), contentAlignment = Alignment.Center) {
            Box(Modifier.width(108.dp).height(4.dp).clip(CircleShape).background(color.copy(alpha = 0.8f)))
        }
    }
}
