package com.anda.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InterpreterMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anda.launcher.core.data.BackupManager
import com.anda.launcher.core.data.DevNote
import com.anda.launcher.core.icons.IconPack
import com.anda.launcher.core.icons.IconPackInfo
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.BadgeMode
import com.anda.launcher.core.model.ColorSource
import com.anda.launcher.core.model.DarkMode
import com.anda.launcher.core.model.DockShape
import com.anda.launcher.core.model.DrawerSort
import com.anda.launcher.core.model.DrawerStyle
import com.anda.launcher.core.model.FolderOpenStyle
import com.anda.launcher.core.model.FolderPreview
import com.anda.launcher.core.model.FontChoice
import com.anda.launcher.core.model.Gesture
import com.anda.launcher.core.model.GestureSettings
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.PageIndicatorStyle
import com.anda.launcher.core.model.PageTransition
import com.anda.launcher.core.model.SearchBarPosition
import com.anda.launcher.core.model.SearchBarStyle
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.icon.rememberShapePreview
import com.anda.launcher.feature.badges.BadgeRepository
import com.anda.launcher.feature.gestures.ActionPickerDialog
import com.anda.launcher.feature.gestures.SystemActions
import com.anda.launcher.feature.gestures.displayName
import com.anda.launcher.feature.home.EXTRA_CUSTOMIZE
import com.anda.launcher.feature.home.FolderPreviewSwatch
import com.anda.launcher.feature.home.LauncherPreview
import com.anda.launcher.feature.home.PreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(onExit: () -> Unit, initialScreen: String?) {
    var screen by rememberSaveable { mutableStateOf(Screen.entries.firstOrNull { it.name == initialScreen } ?: Screen.Main) }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    val go: (Screen, String?) -> Unit = { to, row ->
        screen = to
        highlight = row
    }
    val back = { if (screen == Screen.Main) onExit() else go(Screen.Main, null) }
    BackHandler(onBack = back)
    // The flash and auto-expanded Advanced section only need to last until the row was seen.
    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(2500)
            highlight = null
        }
    }

    val graph = LocalLauncherGraph.current
    val scope = rememberCoroutineScope()
    val update: Update = { block -> scope.launch { graph.settingsRepository.update(block) } }
    val stored = LocalLauncherSettings.current
    // A slider mid-drag; dropped as soon as the stored settings change (or the screen does).
    var draft by remember(stored, screen) { mutableStateOf<LauncherSettings?>(null) }
    val binder = remember(stored, screen) { SettingsBinder(stored, update) { draft = it } }
    // One scroll position per screen: sub-screens always open at the top.
    val scroll = key(screen) { rememberScrollState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            previewModeFor(screen)?.let { mode -> StickyPreview(draft ?: stored, mode, scroll, onExit) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                CompositionLocalProvider(LocalSettingsHighlight provides highlight) {
                    when (screen) {
                        Screen.Main -> MainScreen(stored, go)
                        Screen.Home -> HomeSettingsScreen(binder)
                        Screen.Dock -> DockSettingsScreen(binder)
                        Screen.Drawer -> DrawerSettingsScreen(binder) { go(Screen.Hidden, null) }
                        Screen.Icons -> IconSettingsScreen(binder)
                        Screen.Folders -> FolderSettingsScreen(binder)
                        Screen.Gestures -> GestureSettingsScreen(binder)
                        Screen.Search -> SearchSettingsScreen(binder)
                        Screen.Badges -> BadgeSettingsScreen(binder)
                        Screen.Theme -> ThemeSettingsScreen(binder)
                        Screen.Glance -> GlanceSettingsScreen(binder)
                        Screen.Hidden -> HiddenAppsScreen()
                        Screen.Permissions -> PermissionsScreen()
                        Screen.Backup -> BackupScreen(binder)
                        Screen.Dev -> DevScreen()
                        Screen.About -> AboutScreen()
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun previewModeFor(screen: Screen): PreviewMode? = when (screen) {
    Screen.Home, Screen.Dock, Screen.Icons, Screen.Theme, Screen.Glance -> PreviewMode.Home
    Screen.Folders -> PreviewMode.Folder
    Screen.Drawer -> PreviewMode.Drawer
    else -> null
}

// ---- Live preview --------------------------------------------------------------------------------

private val PreviewMax = 320.dp
private val PreviewMin = 150.dp

/**
 * The launcher in miniature, pinned above the options. It shrinks as the list scrolls (in the
 * layout phase, so scrolling never recomposes it) and follows sliders while they're dragged.
 */
@Composable
private fun StickyPreview(settings: LauncherSettings, mode: PreviewMode, scroll: ScrollState, onExit: () -> Unit) {
    val graph = LocalLauncherGraph.current
    val context = LocalContext.current
    val homeLayout by graph.layoutRepository.layout.collectAsState(HomeLayout())
    val apps by graph.appsRepository.apps.collectAsState()
    val stats by graph.launchStatsRepository.stats.collectAsState(emptyMap())
    val suggestions = remember(apps, stats) {
        val now = System.currentTimeMillis()
        apps.filter { !it.isHidden && stats.containsKey(it.key) }
            .sortedByDescending { app -> stats.getValue(app.key).let { s -> s.count / (1.0 + (now - s.lastLaunch).coerceAtLeast(0) / 86_400_000.0) } }
            .take(8)
    }
    val density = LocalDensity.current
    val maxPx = with(density) { PreviewMax.roundToPx() }
    val minPx = with(density) { PreviewMin.roundToPx() }
    val range = (maxPx - minPx).toFloat()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Box(
            Modifier.fillMaxWidth().layout { measurable, constraints ->
                val f = (scroll.value / range).coerceIn(0f, 1f)
                val h = (maxPx - (maxPx - minPx) * f).roundToInt()
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            },
        ) {
            LauncherPreview(
                settings = settings,
                layout = homeLayout,
                apps = apps,
                mode = mode,
                suggestions = suggestions,
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            )
            if (mode == PreviewMode.Home) {
                FilledTonalButton(
                    onClick = {
                        // The exact preview: the real home with the Customize panel open.
                        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .setPackage(context.packageName).putExtra(EXTRA_CUSTOMIZE, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(home) }.onSuccess { onExit() }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Try on home")
                }
            }
        }
    }
}

// ---- Main ----------------------------------------------------------------------------------------

@Composable
private fun MainScreen(s: LauncherSettings, go: (Screen, String?) -> Unit) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TestTags.SETTINGS_SEARCH),
    )
    if (query.isNotBlank()) {
        val results = remember(query) { SettingsIndex.search(query) }
        SettingsGroup {
            if (results.isEmpty()) {
                Text("No matching settings", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            results.forEach { entry ->
                NavRow(entry.title, entry.screen.title) { go(entry.screen, entry.title) }
            }
        }
        return
    }

    SettingsGroup("Look") {
        NavRow("Home screen", homeSummary(s), Icons.Filled.GridView) { go(Screen.Home, null) }
        NavRow("Dock", dockSummary(s), Icons.Filled.Dock) { go(Screen.Dock, null) }
        NavRow("Icons", iconSummary(s), Icons.Filled.Widgets) { go(Screen.Icons, null) }
        NavRow("Folders", "${humanize(s.folders.preview.name)} preview · opens as ${humanize(s.folders.openStyle.name).lowercase()}", Icons.Filled.Folder) {
            go(Screen.Folders, null)
        }
        NavRow("Look & feel", themeSummary(s), Icons.Filled.Palette) { go(Screen.Theme, null) }
    }
    SettingsGroup("Behaviour") {
        NavRow("App drawer", drawerSummary(s), Icons.Filled.Apps) { go(Screen.Drawer, null) }
        NavRow("Gestures", gestureSummary(s), Icons.Filled.Gesture) { go(Screen.Gestures, null) }
        NavRow("Search", searchSummary(s), Icons.Filled.Search) { go(Screen.Search, null) }
        NavRow("At a glance", glanceSummary(s), Icons.Filled.Today) { go(Screen.Glance, null) }
        NavRow("Notification dots", humanize(s.badges.mode.name), Icons.Filled.Notifications) { go(Screen.Badges, null) }
    }
    SettingsGroup("Privacy & system") {
        val hidden = apps.count { it.isHidden }
        NavRow("Hidden apps", if (hidden == 0) "None" else "$hidden hidden", Icons.Filled.VisibilityOff) { go(Screen.Hidden, null) }
        NavRow("Permissions", "Default home, gestures, dots, contacts", Icons.Filled.Security) { go(Screen.Permissions, null) }
        NavRow("Backup & restore", "Daily automatic backups", Icons.Filled.Backup) { go(Screen.Backup, null) }
        NavRow("Dev", "Notes and screenshots about problems", Icons.Filled.BugReport) { go(Screen.Dev, null) }
        NavRow("About", null, Icons.Filled.Info) { go(Screen.About, null) }
    }
    Hint("A dot marks a setting you changed. Long-press it to reset it to the default.")
}

private fun homeSummary(s: LauncherSettings) =
    "${s.home.columns}×${s.home.rows} grid · ${humanize(s.home.pageTransition.name)} · ${humanize(s.home.pageIndicator.name).lowercase()} indicator"

private fun dockSummary(s: LauncherSettings) = if (!s.dock.enabled) {
    "Off"
} else {
    val pages = if (s.dock.pages > 1) " · ${s.dock.pages} pages" else ""
    "${s.dock.columns} icons$pages" + if (s.dock.showBackground) " · ${humanize(s.dock.backgroundShape.name).lowercase()} background" else ""
}

private fun iconSummary(s: LauncherSettings) = buildList {
    add(if (s.icons.shape == IconShapeKind.System) "System shape" else humanize(s.icons.shape.name))
    if (s.icons.iconPack != null) add("icon pack")
    if (s.icons.themedIcons) add("themed")
    add("${s.icons.home.sizePercent}%")
}.joinToString(" · ")

private fun themeSummary(s: LauncherSettings) = when (s.theme.colorSource) {
    ColorSource.Dynamic -> "Material You"
    ColorSource.Wallpaper -> "Wallpaper colors"
    ColorSource.Custom -> "Custom accent"
} + " · " + when (s.theme.darkMode) {
    DarkMode.System -> "follows system"
    DarkMode.Light -> "light"
    DarkMode.Dark -> "dark"
}

private fun drawerSummary(s: LauncherSettings) =
    "${drawerStyleLabel(s.drawer.style)} · ${sortLabel(s.drawer.sort)} · ${s.drawer.columns} columns"

private fun gestureSummary(s: LauncherSettings) =
    "Swipe up: ${s.gestures.actionFor(Gesture.SwipeUp).displayName()} · double tap: ${s.gestures.actionFor(Gesture.DoubleTap).displayName().lowercase()}"

private fun searchSummary(s: LauncherSettings): String {
    val q = s.search
    val on = listOf(q.apps, q.shortcuts, q.contacts, q.calculator, q.unitConverter, q.systemSettings, q.webSearch).count { it }
    return "$on of 7 sources" + if (q.fuzzyMatching) " · fuzzy" else ""
}

private fun glanceSummary(s: LauncherSettings): String {
    val g = s.glance
    if (!g.enabled) return "Off"
    return listOfNotNull("date".takeIf { g.showDate }, "alarm".takeIf { g.showNextAlarm }, "battery".takeIf { g.showBattery }, "calendar".takeIf { g.showCalendar })
        .joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Nothing shown" }
}

private fun drawerStyleLabel(style: DrawerStyle) = when (style) {
    DrawerStyle.VerticalGrid -> "Grid"
    DrawerStyle.HorizontalPaged -> "Pages"
    DrawerStyle.List -> "List"
}

private fun sortLabel(sort: DrawerSort) = when (sort) {
    DrawerSort.Alphabetical -> "A–Z"
    DrawerSort.InstallDate -> "Newest"
    DrawerSort.MostUsed -> "Most used"
}

private fun searchBarLabel(p: SearchBarPosition) = when (p) {
    SearchBarPosition.None -> "None"
    SearchBarPosition.Top -> "Top"
    SearchBarPosition.AboveDock -> "Above dock"
    SearchBarPosition.BelowDock -> "Below dock"
}

// ---- Home ----------------------------------------------------------------------------------------

@Composable
private fun HomeSettingsScreen(b: SettingsBinder) {
    SettingsGroup("Grid") {
        SliderRow("Columns", b.bind({ it.home.columns }) { copy(home = home.copy(columns = it)) }, 3..10)
        SliderRow("Rows", b.bind({ it.home.rows }) { copy(home = home.copy(rows = it)) }, 3..12)
    }
    SettingsGroup("Search bar") {
        SegmentedRow("Search bar position", b.bind({ it.home.searchBar }) { copy(home = home.copy(searchBar = it)) }, SearchBarPosition.entries, ::searchBarLabel)
        SegmentedRow("Search bar style", b.bind({ it.home.searchBarStyle }) { copy(home = home.copy(searchBarStyle = it)) }, SearchBarStyle.entries)
    }
    SettingsGroup("Pages") {
        ChoiceRow("Scroll effect", b.bind({ it.home.pageTransition }) { copy(home = home.copy(pageTransition = it)) }, PageTransition.entries)
        SegmentedRow("Page indicator", b.bind({ it.home.pageIndicator }) { copy(home = home.copy(pageIndicator = it)) }, PageIndicatorStyle.entries)
    }
    AdvancedSection("home", 11) {
        SwitchRow("Subgrid positioning", b.bind({ it.home.subgridPositioning }) { copy(home = home.copy(subgridPositioning = it)) }, "Place items between cells")
        SliderRow("Horizontal padding", b.bind({ it.home.horizontalPaddingDp }) { copy(home = home.copy(horizontalPaddingDp = it)) }, 0..48, " dp")
        SliderRow("Vertical padding", b.bind({ it.home.verticalPaddingDp }) { copy(home = home.copy(verticalPaddingDp = it)) }, 0..48, " dp")
        SliderRow("Widget padding", b.bind({ it.home.widgetPaddingDp }) { copy(home = home.copy(widgetPaddingDp = it)) }, 0..24, " dp")
        SwitchRow("Infinite scroll", b.bind({ it.home.infiniteScroll }) { copy(home = home.copy(infiniteScroll = it)) }, "Swipe past the last page to reach the first")
        SwitchRow("Wallpaper scrolling", b.bind({ it.home.wallpaperScrolling }) { copy(home = home.copy(wallpaperScrolling = it)) }, "Parallax as you change pages")
        SwitchRow(
            "Two pages side by side",
            b.bind({ it.home.dualPanelOnLargeScreens }) { copy(home = home.copy(dualPanelOnLargeScreens = it)) },
            "On foldables and tablets",
        )
        SwitchRow("Show status bar", b.bind({ it.home.showStatusBar }) { copy(home = home.copy(showStatusBar = it)) })
        FloatSliderRow("Wallpaper dimming", b.bind({ it.home.wallpaperDim }) { copy(home = home.copy(wallpaperDim = it)) }, 0f..0.8f)
        SwitchRow("Lock layout", b.bind({ it.home.lockLayout }) { copy(home = home.copy(lockLayout = it)) }, "Prevent moving or removing items")
        SwitchRow("Add new apps to home screen", b.bind({ it.home.addNewAppsToHome }) { copy(home = home.copy(addNewAppsToHome = it)) })
    }
}

// ---- Dock ----------------------------------------------------------------------------------------

@Composable
private fun DockSettingsScreen(b: SettingsBinder) {
    val d = b.stored.dock
    SettingsGroup {
        SwitchRow("Show dock", b.bind({ it.dock.enabled }) { copy(dock = dock.copy(enabled = it)) })
        SliderRow("Icons per page", b.bind({ it.dock.columns }) { copy(dock = dock.copy(columns = it)) }, 3..8)
        SliderRow("Dock pages", b.bind({ it.dock.pages }) { copy(dock = dock.copy(pages = it)) }, 1..3)
    }
    SettingsGroup("Background") {
        SwitchRow("Background", b.bind({ it.dock.showBackground }) { copy(dock = dock.copy(showBackground = it)) })
        if (d.showBackground) {
            VisualChoiceRow("Background shape", b.bind({ it.dock.backgroundShape }) { copy(dock = dock.copy(backgroundShape = it)) }, DockShape.entries) { shape ->
                val clip = when (shape) {
                    DockShape.Rectangle -> RoundedCornerShape(0.dp)
                    DockShape.Rounded -> RoundedCornerShape(6.dp)
                    DockShape.Pill -> CircleShape
                }
                Box(Modifier.size(48.dp, 18.dp).clip(clip).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            }
            ColorRow("Background color", b.bind({ it.dock.backgroundColor }) { copy(dock = dock.copy(backgroundColor = it)) })
            FloatSliderRow("Background opacity", b.bind({ it.dock.backgroundOpacity }) { copy(dock = dock.copy(backgroundOpacity = it)) })
        }
    }
    IconStyleGroups("Dock icons", "dock", b, { it.icons.dock }) { copy(icons = icons.copy(dock = it)) }
}

// ---- Icons ---------------------------------------------------------------------------------------

/**
 * Size and labels up front; label typography folded into Advanced. Used by every icon area.
 */
@Composable
private fun IconStyleGroups(
    title: String,
    key: String,
    b: SettingsBinder,
    get: (LauncherSettings) -> IconStyle,
    put: LauncherSettings.(IconStyle) -> LauncherSettings,
) {
    fun <T> part(read: (IconStyle) -> T, write: IconStyle.(T) -> IconStyle) =
        b.bind({ read(get(it)) }) { v -> this.put(get(this).write(v)) }
    val style = get(b.stored)
    SettingsGroup(title) {
        SliderRow("Icon size", part({ it.sizePercent }) { copy(sizePercent = it) }, 50..150, "%")
        SwitchRow("Labels", part({ it.showLabels }) { copy(showLabels = it) })
    }
    if (style.showLabels) {
        AdvancedSection("labels-$key", 5) {
            SliderRow("Label size", part({ it.labelSizeSp }) { copy(labelSizeSp = it) }, 8..18, " sp")
            SliderRow("Label lines", part({ it.labelLines }) { copy(labelLines = it) }, 1..2)
            ColorRow("Label color", part({ it.labelColor }) { copy(labelColor = it) })
            SwitchRow("Label shadow", part({ it.labelShadow }) { copy(labelShadow = it) })
            ChoiceRow("Label font", part({ it.labelFont }) { copy(labelFont = it) }, FontChoice.entries)
        }
    }
}

@Composable
private fun IconSettingsScreen(b: SettingsBinder) {
    val context = LocalContext.current
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    val i = b.stored.icons
    val packs by produceState(emptyList<IconPackInfo>()) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    // A recognisable app for the shape swatches.
    val sampleKey = remember(apps) { apps.firstOrNull { !it.isHidden }?.key }
    SettingsGroup("Style") {
        val pack = b.bind({ it.icons.iconPack }) { copy(icons = icons.copy(iconPack = it)) }
        ChoiceRow(
            "Icon pack",
            packs.firstOrNull { it.packageName == i.iconPack },
            listOf<IconPackInfo?>(null) + packs,
            label = { it?.label ?: "System icons" },
            modified = pack.modified,
            onReset = pack::reset,
        ) { v -> pack.set(v?.packageName) }
        VisualChoiceRow("Shape", b.bind({ it.icons.shape }) { copy(icons = icons.copy(shape = it)) }, IconShapeKind.entries) { shape ->
            val icon = rememberShapePreview(sampleKey, shape, 44.dp)
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        SwitchRow("Themed icons", b.bind({ it.icons.themedIcons }) { copy(icons = icons.copy(themedIcons = it)) }, "Tint icons with your wallpaper colors")
    }
    IconStyleGroups("Home screen icons", "home", b, { it.icons.home }) { copy(icons = icons.copy(home = it)) }
    AdvancedSection("icons", 2) {
        SwitchRow(
            "Theme apps without a monochrome icon",
            b.bind({ it.icons.themedFallback }) { copy(icons = icons.copy(themedFallback = it)) },
            enabled = i.themedIcons,
        )
        SwitchRow(
            "Normalize legacy icons",
            b.bind({ it.icons.normalizeLegacy }) { copy(icons = icons.copy(normalizeLegacy = it)) },
            "Put old square icons on a shaped background",
        )
    }
    Hint("Dock, drawer and folder icons have their own size and label options on those pages.")
}

// ---- Drawer --------------------------------------------------------------------------------------

@Composable
private fun DrawerSettingsScreen(b: SettingsBinder, openHidden: () -> Unit) {
    val d = b.stored.drawer
    SettingsGroup("Layout") {
        SegmentedRow("Style", b.bind({ it.drawer.style }) { copy(drawer = drawer.copy(style = it)) }, DrawerStyle.entries, ::drawerStyleLabel)
        if (d.style != DrawerStyle.List) SliderRow("Columns", b.bind({ it.drawer.columns }) { copy(drawer = drawer.copy(columns = it)) }, 3..8)
        SegmentedRow("Sort", b.bind({ it.drawer.sort }) { copy(drawer = drawer.copy(sort = it)) }, DrawerSort.entries, ::sortLabel)
        SwitchRow("Search bar at the bottom", b.bind({ it.drawer.searchBarBottom }) { copy(drawer = drawer.copy(searchBarBottom = it)) }, "Easier to reach one-handed")
    }
    SettingsGroup("Organisation") {
        SwitchRow("Custom tabs", b.bind({ it.drawer.manualTabs }) { copy(drawer = drawer.copy(manualTabs = it)) }, "Create tabs from the drawer's + button")
        SwitchRow("Automatic category tabs", b.bind({ it.drawer.autoCategoryTabs }) { copy(drawer = drawer.copy(autoCategoryTabs = it)) }, "Social, Media, Games, …")
        SwitchRow("Separate work apps", b.bind({ it.drawer.showWorkTab }) { copy(drawer = drawer.copy(showWorkTab = it)) })
        SwitchRow("Drawer folders", b.bind({ it.drawer.showDrawerFolders }) { copy(drawer = drawer.copy(showDrawerFolders = it)) })
    }
    SettingsGroup("Privacy") {
        SwitchRow(
            "Require fingerprint for hidden & locked apps",
            b.bind({ it.drawer.authForHiddenApps }) { copy(drawer = drawer.copy(authForHiddenApps = it)) },
        )
        SwitchRow("Show hidden apps in search", b.bind({ it.drawer.hiddenAppsInSearch }) { copy(drawer = drawer.copy(hiddenAppsInSearch = it)) })
        NavRow("Manage hidden apps", null, Icons.Filled.VisibilityOff, openHidden)
    }
    AdvancedSection("drawer", 7) {
        if (d.style == DrawerStyle.HorizontalPaged) {
            SliderRow("Rows per page", b.bind({ it.drawer.pageRows }) { copy(drawer = drawer.copy(pageRows = it)) }, 3..10)
        }
        SwitchRow("Fast scroller", b.bind({ it.drawer.fastScroller }) { copy(drawer = drawer.copy(fastScroller = it)) }, "A–Z strip; shown when sorted A–Z")
        SwitchRow("Suggested apps row", b.bind({ it.drawer.showSuggestions }) { copy(drawer = drawer.copy(showSuggestions = it)) })
        SwitchRow("Open keyboard automatically", b.bind({ it.drawer.openKeyboard }) { copy(drawer = drawer.copy(openKeyboard = it)) })
        ColorRow("Background color", b.bind({ it.drawer.backgroundColor }) { copy(drawer = drawer.copy(backgroundColor = it)) })
        FloatSliderRow("Background opacity", b.bind({ it.drawer.backgroundOpacity }) { copy(drawer = drawer.copy(backgroundOpacity = it)) })
        SwitchRow("Blur wallpaper", b.bind({ it.drawer.blur }) { copy(drawer = drawer.copy(blur = it)) })
    }
    IconStyleGroups("Drawer icons", "drawer", b, { it.icons.drawer }) { copy(icons = icons.copy(drawer = it)) }
}

// ---- Folders -------------------------------------------------------------------------------------

@Composable
private fun FolderSettingsScreen(b: SettingsBinder) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    SettingsGroup {
        VisualChoiceRow("Preview", b.bind({ it.folders.preview }) { copy(folders = folders.copy(preview = it)) }, FolderPreview.entries) { preview ->
            FolderPreviewSwatch(preview, apps, 48.dp)
        }
        SegmentedRow("Open as", b.bind({ it.folders.openStyle }) { copy(folders = folders.copy(openStyle = it)) }, FolderOpenStyle.entries)
        SliderRow("Columns", b.bind({ it.folders.columns }) { copy(folders = folders.copy(columns = it)) }, 3..6)
    }
    AdvancedSection("folders", 4) {
        SwitchRow("Icon background", b.bind({ it.folders.showIconBackground }) { copy(folders = folders.copy(showIconBackground = it)) })
        ColorRow("Background color", b.bind({ it.folders.backgroundColor }) { copy(folders = folders.copy(backgroundColor = it)) })
        FloatSliderRow("Background opacity", b.bind({ it.folders.backgroundOpacity }) { copy(folders = folders.copy(backgroundOpacity = it)) })
        SwitchRow("Blur behind folders", b.bind({ it.folders.blur }) { copy(folders = folders.copy(blur = it)) })
    }
    IconStyleGroups("Folder icons", "folder", b, { it.icons.folder }) { copy(icons = icons.copy(folder = it)) }
}

// ---- Gestures ------------------------------------------------------------------------------------

private val gestureNames = mapOf(
    Gesture.SwipeUp to "Swipe up",
    Gesture.SwipeDown to "Swipe down",
    Gesture.DoubleTap to "Double tap",
    Gesture.TwoFingerSwipeUp to "Two-finger swipe up",
    Gesture.TwoFingerSwipeDown to "Two-finger swipe down",
    Gesture.PinchIn to "Pinch in",
    Gesture.PinchOut to "Pinch out",
    Gesture.HomePress to "Home button (while on home)",
)

@Composable
private fun GestureSettingsScreen(b: SettingsBinder) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    val gestures = b.stored.gestures
    val defaults = DefaultSettings.gestures
    if (!SystemActions.isAccessibilityEnabled) {
        val context = LocalContext.current
        SettingsGroup {
            NavRow("Enable gesture service", "Needed for lock screen, recents, screenshot and power menu actions", Icons.Filled.InterpreterMode) {
                context.startActivitySafely(SystemActions.accessibilitySettingsIntent(context), SystemSettingsIntent)
            }
        }
    }
    SettingsGroup("Home screen gestures") {
        Gesture.entries.forEach { gesture ->
            val name = gestureNames.getValue(gesture)
            val modified = gestures.actionFor(gesture) != defaults.actionFor(gesture)
            SettingFrame(
                name,
                modified = modified,
                onReset = { b.update { it.copy(gestures = it.gestures.copy(bindings = it.gestures.bindings + (gesture to defaults.actionFor(gesture)))) } },
                onClick = { editing = gesture },
            ) {
                SettingTitle(name, modified)
                Text(gestures.actionFor(gesture).displayName(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SettingsGroup {
        SwitchRow(
            "Icon swipe actions",
            b.bind({ it.gestures.iconSwipeActions }) { copy(gestures = gestures.copy(iconSwipeActions = it)) },
            "Swipe up on an icon or folder to run its action",
        )
        NavRow("Reset gestures to defaults", null, Icons.Filled.RestartAlt) {
            b.update { it.copy(gestures = GestureSettings()) }
        }
    }
    editing?.let { gesture ->
        ActionPickerDialog(
            title = gestureNames.getValue(gesture),
            current = gestures.actionFor(gesture),
            apps = apps,
            onPick = { action ->
                b.update { it.copy(gestures = it.gestures.copy(bindings = it.gestures.bindings + (gesture to action))) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ---- Search --------------------------------------------------------------------------------------

@Composable
private fun SearchSettingsScreen(b: SettingsBinder) {
    val context = LocalContext.current
    val contacts = b.bind({ it.search.contacts }) { copy(search = search.copy(contacts = it)) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contacts.set(true)
    }
    SettingsGroup("Sources") {
        SwitchRow("Apps", b.bind({ it.search.apps }) { copy(search = search.copy(apps = it)) })
        SwitchRow("App shortcuts", b.bind({ it.search.shortcuts }) { copy(search = search.copy(shortcuts = it)) })
        SwitchRow("Contacts", contacts.value, modified = contacts.modified, onReset = contacts::reset) { v ->
            if (v && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            } else {
                contacts.set(v)
            }
        }
        SwitchRow("Calculator", b.bind({ it.search.calculator }) { copy(search = search.copy(calculator = it)) }, "12*7, 15% of 80")
        SwitchRow("Unit converter", b.bind({ it.search.unitConverter }) { copy(search = search.copy(unitConverter = it)) }, "5 km in miles")
        SwitchRow("Android settings", b.bind({ it.search.systemSettings }) { copy(search = search.copy(systemSettings = it)) })
        SwitchRow("Web search suggestion", b.bind({ it.search.webSearch }) { copy(search = search.copy(webSearch = it)) })
    }
    AdvancedSection("search", 2) {
        SwitchRow("Fuzzy matching", b.bind({ it.search.fuzzyMatching }) { copy(search = search.copy(fuzzyMatching = it)) }, "\"ggl\" finds Google")
        SwitchRow("Enter launches top result", b.bind({ it.search.launchTopOnEnter }) { copy(search = search.copy(launchTopOnEnter = it)) })
    }
}

// ---- Dots ----------------------------------------------------------------------------------------

@Composable
private fun BadgeSettingsScreen(b: SettingsBinder) {
    val context = LocalContext.current
    if (!BadgeRepository.isAccessGranted(context)) {
        SettingsGroup {
            NavRow("Grant notification access", "Required to show dots", Icons.Filled.Notifications) {
                context.startActivitySafely(BadgeRepository.accessSettingsIntent(context), BadgeRepository.accessSettingsFallback, SystemSettingsIntent)
            }
        }
    }
    SettingsGroup {
        SegmentedRow("Dot style", b.bind({ it.badges.mode }) { copy(badges = badges.copy(mode = it)) }, BadgeMode.entries)
        ColorRow("Color", b.bind({ it.badges.color }) { copy(badges = badges.copy(color = it)) })
    }
}

// ---- Look & feel ---------------------------------------------------------------------------------

@Composable
private fun ThemeSettingsScreen(b: SettingsBinder) {
    val t = b.stored.theme
    SettingsGroup {
        SegmentedRow("Colors", b.bind({ it.theme.colorSource }) { copy(theme = theme.copy(colorSource = it)) }, ColorSource.entries) {
            when (it) {
                ColorSource.Dynamic -> "Material You"
                ColorSource.Wallpaper -> "Wallpaper"
                ColorSource.Custom -> "Custom"
            }
        }
        if (t.colorSource == ColorSource.Custom) {
            val accent = b.bind({ it.theme.customAccent }) { copy(theme = theme.copy(customAccent = it)) }
            ColorRow("Accent", accent.value, accent.modified, accent::reset) { v -> v?.let(accent::set) }
        }
        SegmentedRow("Dark mode", b.bind({ it.theme.darkMode }) { copy(theme = theme.copy(darkMode = it)) }, DarkMode.entries) {
            if (it == DarkMode.System) "Auto" else humanize(it.name)
        }
        SliderRow("Corner radius", b.bind({ it.theme.cornerRadiusDp }) { copy(theme = theme.copy(cornerRadiusDp = it)) }, 0..40, " dp")
    }
    AdvancedSection("theme", 2) {
        ChoiceRow("Font", b.bind({ it.theme.font }) { copy(theme = theme.copy(font = it)) }, FontChoice.entries)
        SwitchRow("Blur effects", b.bind({ it.theme.blur }) { copy(theme = theme.copy(blur = it)) }, "Blur the wallpaper behind the drawer and folders")
    }
}

// ---- At a glance ---------------------------------------------------------------------------------

@Composable
private fun GlanceSettingsScreen(b: SettingsBinder) {
    val g = b.stored.glance
    val context = LocalContext.current
    val calendar = b.bind({ it.glance.showCalendar }) { copy(glance = glance.copy(showCalendar = it)) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) calendar.set(true)
    }
    SettingsGroup {
        SwitchRow("Show At a glance", b.bind({ it.glance.enabled }) { copy(glance = glance.copy(enabled = it)) })
        SwitchRow("Date", b.bind({ it.glance.showDate }) { copy(glance = glance.copy(showDate = it)) }, enabled = g.enabled)
        SwitchRow("Next alarm", b.bind({ it.glance.showNextAlarm }) { copy(glance = glance.copy(showNextAlarm = it)) }, "Within the next 24 hours", enabled = g.enabled)
        SwitchRow(
            "Battery when charging or low",
            b.bind({ it.glance.showBattery }) { copy(glance = glance.copy(showBattery = it)) },
            enabled = g.enabled,
        )
        SwitchRow("Next calendar event", calendar.value, enabled = g.enabled, modified = calendar.modified, onReset = calendar::reset) { v ->
            if (v && context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            } else {
                calendar.set(v)
            }
        }
    }
}

// ---- Hidden apps ---------------------------------------------------------------------------------

@Composable
private fun HiddenAppsScreen() {
    val graph = LocalLauncherGraph.current
    val scope = rememberCoroutineScope()
    val apps by graph.appsRepository.apps.collectAsState()
    val hidden = apps.filter { it.isHidden }
    if (hidden.isEmpty()) {
        Hint("No hidden apps. Long-press an app in the drawer and choose Hide.")
        return
    }
    SettingsGroup {
        hidden.forEach { app -> HiddenAppRow(app) { scope.launch { graph.overridesRepository.update(app.key) { it.copy(hidden = false) } } } }
    }
}

@Composable
private fun HiddenAppRow(app: AppInfo, onUnhide: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = rememberAppIcon(app.key, app.lastUpdateTime)
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
        Text(app.label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onUnhide) { Text("Unhide") }
    }
}

// ---- Permissions ---------------------------------------------------------------------------------

@Composable
private fun PermissionsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val roleManager = context.getSystemService(RoleManager::class.java)
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    // Re-check every time the user comes back from a system settings page.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val isHome = remember(refresh) { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    val accessibility = remember(refresh) { SystemActions.isAccessibilityEnabled }
    val notifications = remember(refresh) { BadgeRepository.isAccessGranted(context) }
    val contacts = remember(refresh) { context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED }
    SettingsGroup {
        StatusRow("Default home app", isHome, "Required for app shortcuts and pinning") {
            runCatching { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { context.startActivitySafely(Intent(android.provider.Settings.ACTION_HOME_SETTINGS), SystemSettingsIntent) }
        }
        StatusRow("Gesture service", accessibility, "Lock screen, recents, screenshot, power menu") {
            context.startActivitySafely(SystemActions.accessibilitySettingsIntent(context), SystemSettingsIntent)
        }
        StatusRow("Notification access", notifications, "Notification dots") {
            context.startActivitySafely(BadgeRepository.accessSettingsIntent(context), BadgeRepository.accessSettingsFallback, SystemSettingsIntent)
        }
        StatusRow("Contacts", contacts, "Contact search (optional)") {
            context.startActivitySafely(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                SystemSettingsIntent,
            )
        }
    }
    Hint("Anda Launcher has no internet permission: nothing you do here leaves your phone.")
}

@Composable
private fun StatusRow(title: String, granted: Boolean, summary: String, onClick: () -> Unit) {
    NavRow(title, (if (granted) "✓ Enabled · " else "Off · ") + summary, null, onClick)
}

// ---- Backup --------------------------------------------------------------------------------------

@Composable
private fun BackupScreen(b: SettingsBinder) {
    val context = LocalContext.current
    val graph = LocalLauncherGraph.current
    val scope = rememberCoroutineScope()
    var autoBackups by remember { mutableStateOf(graph.backupManager.autoBackups()) }
    var confirmReset by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { graph.backupManager.export(it) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { graph.backupManager.import(it) } } == true
            }.getOrDefault(false)
            Toast.makeText(context, if (ok) "Backup restored" else "Not an Anda Launcher backup", Toast.LENGTH_SHORT).show()
        }
    }
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    SettingsGroup {
        NavRow("Back up now", "Layout, settings, hidden apps, tabs and custom icons", Icons.Filled.Backup) {
            export.launch("anda-launcher-$stamp.${BackupManager.EXTENSION}")
        }
        NavRow("Restore from file", null, null) { import.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    }
    SettingsGroup("Automatic backups (daily, last 7)") {
        autoBackups.forEach { file ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getDateTimeInstance().format(Date(file.lastModified())), Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch {
                        val ok = runCatching {
                            withContext(Dispatchers.IO) { file.inputStream().use { graph.backupManager.import(it) } }
                        }.getOrDefault(false)
                        Toast.makeText(context, if (ok) "Restored" else "This backup couldn't be restored", Toast.LENGTH_SHORT).show()
                        autoBackups = graph.backupManager.autoBackups()
                    }
                }) { Text("Restore") }
            }
        }
        if (autoBackups.isEmpty()) {
            Text("None yet. One is made each day you use the launcher.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    SettingsGroup {
        NavRow("Reset all settings", "Back to defaults. Your home layout and hidden apps stay.", Icons.Filled.RestartAlt) { confirmReset = true }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Grid, icons, dock, drawer, gestures and theme go back to their defaults. Your apps and home layout are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    b.update { LauncherSettings(onboardingDone = true) }
                    Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

// ---- Dev -----------------------------------------------------------------------------------------

/**
 * Notes and screenshots about anything misbehaving, for whoever works on the launcher. Kept on the
 * phone, in backups, and in Downloads/Anda Launcher/dev (see [DevNotes]).
 */
@Composable
private fun DevScreen() {
    val context = LocalContext.current
    val notes = LocalLauncherGraph.current.devNotes
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val saved by produceState(emptyList<DevNote>(), refresh) { value = withContext(Dispatchers.IO) { notes.list() } }
    var text by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DevNote?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = (picked + uris.map { it.toString() }).distinct()
    }

    SettingsGroup("New note") {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("What's misbehaving? What did you do, and what did you expect?") },
            minLines = 4,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        picked.forEach { uri ->
            PickedFileRow(uri) { picked = picked - uri }
        }
        NavRow(
            "Add screenshots or files",
            if (picked.isEmpty()) "Screenshots are resized to keep backups small" else "${picked.size} attached",
            Icons.Filled.AttachFile,
        ) { pick.launch(arrayOf("image/*", "text/*", "*/*")) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                enabled = !saving && (text.isNotBlank() || picked.isNotEmpty()),
                onClick = {
                    saving = true
                    scope.launch {
                        val result = runCatching { notes.add(text, picked.map { it.toUri() }) }
                        saving = false
                        result.onSuccess { added ->
                            text = ""
                            picked = emptyList()
                            refresh++
                            val skipped = if (added.skipped.isEmpty()) "" else " (skipped ${added.skipped.joinToString()})"
                            Toast.makeText(context, "Note saved$skipped", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Couldn't save the note", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save note") }
        }
    }
    SettingsGroup(if (saved.isEmpty()) "Saved notes" else "Saved notes (${saved.size})") {
        if (saved.isEmpty()) {
            Text("None yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved.forEach { note -> key(note.id) { DevNoteRow(note) { confirmDelete = note } } }
    }
    Hint("Saved on this phone, included in backups, and copied to Downloads/Anda Launcher/dev. Nothing is sent anywhere.")

    confirmDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this note?") },
            text = { Text("The note, its files and its copy in Downloads are deleted. Older backups still contain it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        notes.delete(note)
                        refresh++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PickedFileRow(uri: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val name by produceState(uri.substringAfterLast('/'), uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(uri.toUri(), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: value
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove $name") }
    }
}

@Composable
private fun DevNoteRow(note: DevNote, onDelete: () -> Unit) {
    val notes = LocalLauncherGraph.current.devNotes
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.createdAt)), style = MaterialTheme.typography.titleSmall)
                Text("Version ${note.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note") }
        }
        if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        if (note.attachments.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.attachments.forEach { name ->
                    val thumb by produceState<ImageBitmap?>(null, note.id, name) {
                        value = withContext(Dispatchers.IO) { runCatching { notes.thumbnail(notes.attachment(note, name), 256)?.asImageBitmap() }.getOrNull() }
                    }
                    val shape = RoundedCornerShape(8.dp)
                    val current = thumb
                    if (current != null) {
                        Image(current, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(shape))
                    } else {
                        Box(Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(6.dp), contentAlignment = Alignment.Center) {
                            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}

// ---- About ---------------------------------------------------------------------------------------

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Anda Launcher ${info.versionName}", style = MaterialTheme.typography.titleLarge)
        Text("Build ${info.longVersionCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("A fast, deeply customizable home screen for Pixel phones. Inspired by Nova Launcher and Smart Launcher.")
        Text(
            "No internet permission, no analytics. Settings and layout stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    VersionHistoryGroup()
    CrashReportsGroup()
    SettingsGroup("Tips") {
        Tip(Icons.Filled.Widgets, "Long-press empty space on home, then Customize, to change the grid live.")
        Tip(Icons.Filled.Search, "Search settings from the top of the main settings page.")
        Tip(Icons.Filled.Folder, "Drop an icon on another icon to make a folder.")
    }
}

/** What changed in each version, from the CHANGELOG.md the build ships as an asset. */
@Composable
private fun VersionHistoryGroup() {
    val context = LocalContext.current
    val releases by produceState(emptyList<Release>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(Changelog.ASSET).bufferedReader().use { Changelog.parse(it.readText()) } }.getOrDefault(emptyList())
        }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val latest = releases.firstOrNull() ?: return
    SettingsGroup("What's new in ${latest.version}") {
        ReleaseNotes(latest, showVersion = false)
        if (releases.size > 1) {
            NavRow(
                "Version history",
                if (showAll) "Hide older versions" else "Show all ${releases.size} versions",
                Icons.Filled.History,
            ) { showAll = !showAll }
            if (showAll) releases.drop(1).forEach { ReleaseNotes(it, showVersion = true) }
        }
    }
}

@Composable
private fun ReleaseNotes(release: Release, showVersion: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val date = runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(release.date)!!) }
            .getOrDefault(release.date)
        Text(
            if (showVersion) "${release.version} · $date" else date,
            style = if (showVersion) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = if (showVersion) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        release.notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Crash reports stay on the phone (no INTERNET permission); the user decides whether to share
 * them, through the normal share sheet.
 */
@Composable
private fun CrashReportsGroup() {
    val context = LocalContext.current
    val reports = LocalLauncherGraph.current.crashReports
    var refresh by remember { mutableIntStateOf(0) }
    val count by produceState(0, refresh) { value = withContext(Dispatchers.IO) { reports.list().size } }
    SettingsGroup("Problem reports") {
        if (count == 0) {
            Text(
                "No crashes recorded.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SettingsGroup
        }
        NavRow("Share crash reports", "$count saved on this phone. Nothing is sent unless you share it.", Icons.Filled.Share) {
            val text = reports.combined()
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Anda Launcher crash reports")
                .putExtra(Intent.EXTRA_TEXT, text)
            runCatching { context.startActivity(Intent.createChooser(send, "Share crash reports")) }
        }
        NavRow("Delete crash reports", null, Icons.Filled.DeleteOutline) {
            reports.clear()
            refresh++
        }
    }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
