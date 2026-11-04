package com.anda.launcher.feature.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.HomeLayout
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.feature.home.EXTRA_CUSTOMIZE
import com.anda.launcher.feature.home.LauncherPreview
import com.anda.launcher.feature.home.PreviewMode
import com.anda.launcher.feature.settings.components.LocalSettingsHighlight
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.Update
import com.anda.launcher.feature.settings.screens.AboutScreen
import com.anda.launcher.feature.settings.screens.BackupScreen
import com.anda.launcher.feature.settings.screens.BadgeSettingsScreen
import com.anda.launcher.feature.settings.screens.DevScreen
import com.anda.launcher.feature.settings.screens.DockSettingsScreen
import com.anda.launcher.feature.settings.screens.DrawerSettingsScreen
import com.anda.launcher.feature.settings.screens.FolderSettingsScreen
import com.anda.launcher.feature.settings.screens.GestureSettingsScreen
import com.anda.launcher.feature.settings.screens.GlanceSettingsScreen
import com.anda.launcher.feature.settings.screens.HiddenAppsScreen
import com.anda.launcher.feature.settings.screens.HomeSettingsScreen
import com.anda.launcher.feature.settings.screens.IconEditorScreen
import com.anda.launcher.feature.settings.screens.IconSettingsScreen
import com.anda.launcher.feature.settings.screens.MainScreen
import com.anda.launcher.feature.settings.screens.PermissionsScreen
import com.anda.launcher.feature.settings.screens.QuickActionsScreen
import com.anda.launcher.feature.settings.screens.ShortcutAppsScreen
import com.anda.launcher.feature.settings.screens.SearchSettingsScreen
import com.anda.launcher.feature.settings.screens.ThemeSettingsScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(onExit: () -> Unit, initialScreen: String?, initialIconTarget: String? = null) {
    var screen by rememberSaveable { mutableStateOf(Screen.entries.firstOrNull { it.name == initialScreen } ?: Screen.Main) }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    // The app the icon editor works on (flattened key), or null for all apps.
    var iconTarget by rememberSaveable { mutableStateOf(initialIconTarget) }
    val go: (Screen, String?) -> Unit = { to, row ->
        screen = to
        highlight = row
    }
    val openIconEditor: (ComponentKey?) -> Unit = { key ->
        iconTarget = key?.flatten()
        go(Screen.IconEditor, null)
    }
    val back = { if (screen == Screen.Main) onExit() else go(screen.parent ?: Screen.Main, null) }
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
    // The icon editor shares the Icons page's state, so Save or Back returns to the same spot.
    val scroll = key(screen.parent ?: screen) { rememberScrollState() }

    // The editor is a full-screen tool with its own bar (Save, Reset) and unsaved-changes check.
    if (screen == Screen.IconEditor) {
        IconEditorScreen(
            target = iconTarget?.let(ComponentKey::parse),
            onTargetChange = { iconTarget = it?.flatten() },
            update = update,
            onClose = { go(Screen.Icons, null) },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (screen == Screen.Icons) {
                        IconButton(onClick = { openIconEditor(null) }) { Icon(Icons.Filled.FormatPaint, contentDescription = "Icon editor") }
                    }
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
                        Screen.Icons -> IconSettingsScreen(binder, openIconEditor)
                        Screen.Folders -> FolderSettingsScreen(binder)
                        Screen.Gestures -> GestureSettingsScreen(binder)
                        Screen.Search -> SearchSettingsScreen(
                            binder,
                            onQuickActions = { go(Screen.QuickActions, null) },
                            onShortcutApps = { go(Screen.ShortcutApps, null) },
                        )
                        Screen.QuickActions -> QuickActionsScreen(binder)
                        Screen.ShortcutApps -> ShortcutAppsScreen(binder)
                        Screen.Badges -> BadgeSettingsScreen(binder)
                        Screen.Theme -> ThemeSettingsScreen(binder)
                        Screen.Glance -> GlanceSettingsScreen(binder)
                        Screen.Hidden -> HiddenAppsScreen()
                        Screen.Permissions -> PermissionsScreen()
                        Screen.Backup -> BackupScreen(binder)
                        Screen.Dev -> DevScreen()
                        Screen.About -> AboutScreen()
                        Screen.IconEditor -> Unit
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
