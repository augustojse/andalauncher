package com.anda.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Gesture
import com.anda.launcher.core.model.LauncherAction
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.drag.DragLayer
import com.anda.launcher.core.ui.drag.LocalDragController
import com.anda.launcher.core.ui.drag.trackDrags
import com.anda.launcher.core.ui.icon.rememberIconSpec
import com.anda.launcher.core.ui.menu.ItemMenuOverlay
import com.anda.launcher.core.ui.menu.LocalOverlayController
import com.anda.launcher.core.ui.theme.LauncherTheme
import com.anda.launcher.core.ui.theme.LocalBackdrop
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import com.anda.launcher.core.ui.theme.backdropSource
import com.anda.launcher.core.ui.theme.homeBlurRadius
import com.anda.launcher.core.ui.theme.isLauncherDark
import com.anda.launcher.core.ui.theme.rememberBackdrop
import com.anda.launcher.core.ui.theme.rememberLauncherColorScheme
import com.anda.launcher.core.ui.theme.wallpaperBlurPx
import com.anda.launcher.feature.badges.BadgeRepository
import com.anda.launcher.feature.drawer.AppDrawer
import com.anda.launcher.feature.gestures.VerticalDragHandler
import com.anda.launcher.feature.gestures.homeGestures
import com.anda.launcher.feature.home.CustomizeSheet
import com.anda.launcher.feature.home.FolderOverlay
import com.anda.launcher.feature.home.HomeScreen
import com.anda.launcher.feature.home.LocalAppLookup
import com.anda.launcher.feature.home.WidgetPicker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Root composition: home, drawer and every overlay share one window (see ItemMenuOverlay). */
@Composable
fun LauncherRoot(activity: MainActivity, graph: AppGraph) {
    val settingsState by graph.settingsRepository.settings.collectAsState()
    // Hold the first frame until settings are read, so the grid never flashes default sizes.
    val stored = settingsState ?: return
    val home = activity.home
    // A Customize slider mid-drag shows its value on the real home; dropped once it's saved.
    LaunchedEffect(stored) { home.draftSettings = null }
    val settings = home.draftSettings ?: stored
    val dark = isLauncherDark(settings.theme)
    // Icons follow the stored spec only: re-rendering every icon per slider tick would stutter.
    val iconsReady = rememberIconSpec(graph.iconRepository, stored, dark, rememberLauncherColorScheme(stored.theme))
    if (!iconsReady) return

    val apps by graph.appsRepository.apps.collectAsState()
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val lookup: (ComponentKey) -> com.anda.launcher.core.model.AppInfo? = remember(appsByKey) { { appsByKey[it] } }
    val groups by graph.drawerGroupsRepository.groups.collectAsState(emptyList())
    val stats by graph.launchStatsRepository.stats.collectAsState(emptyMap())
    val favoritesList by graph.favoritesRepository.favorites.collectAsState(emptyList())
    val favorites = remember(favoritesList) { favoritesList.toImmutableList() }
    val badges by BadgeRepository.counts.collectAsState()
    val drawer = activity.drawer
    val backdrop = rememberBackdrop()

    SideEffect {
        home.settings = settings
        home.apps = appsByKey
    }

    CompositionLocalProvider(
        LocalLauncherGraph provides graph,
        LocalLauncherSettings provides settings,
        LocalDragController provides activity.drag,
        LocalOverlayController provides activity.overlay,
        LocalAppLookup provides lookup,
        LocalBackdrop provides backdrop,
    ) {
        LauncherTheme(settings.theme) {
            WindowEffects(activity, settings, dark)
            Box(Modifier.fillMaxSize().trackDrags(activity.drag)) {
                // Registered first so every feature's BackHandler wins over it: a launcher never "exits".
                BackHandler { activity.closeTransientUi() }

                val heightPx = LocalWindowInfo.current.containerSize.height.toFloat().coerceAtLeast(1f)
                val dragHandler = remember(settings.gestures, heightPx) {
                    object : VerticalDragHandler {
                        override fun canStart(up: Boolean) =
                            up && !home.editMode && settings.gestures.actionFor(Gesture.SwipeUp) == LauncherAction.OpenDrawer
                        override fun onStart() = Unit
                        override fun onDrag(deltaY: Float) = drawer.dragBy(-deltaY / heightPx)
                        override fun onEnd(velocityY: Float) = drawer.settle(-velocityY / heightPx)
                    }
                }
                val gestureModifier = Modifier.homeGestures(settings.gestures, dragHandler) { gesture ->
                    if (home.editMode) {
                        if (gesture == Gesture.PinchOut || gesture == Gesture.DoubleTap) home.editMode = false
                    } else {
                        activity.dispatcher.dispatch(settings.gestures.actionFor(gesture))
                    }
                }
                val homeBlur = settings.theme.homeBlurRadius()
                val blurFolders = settings.folders.blur && home.openFolderId != null && homeBlur > 0.dp
                HomeScreen(
                    controller = home,
                    badges = badges,
                    host = activity,
                    gestureModifier = gestureModifier,
                    modifier = Modifier
                        .graphicsLayer {
                            val p = drawer.progress.value
                            alpha = 1f - p
                            val scale = 1f - 0.06f * p
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(if (blurFolders) Modifier.blur(homeBlur) else Modifier)
                        // Inside the blur: folder cards show the sharp home through their own card blur.
                        .backdropSource(backdrop),
                )
                AppDrawer(
                    controller = drawer,
                    engine = activity.searchEngine,
                    apps = apps,
                    groups = groups,
                    stats = stats,
                    badges = badges,
                    favorites = favorites,
                    searchActions = activity,
                )
                FolderOverlay(home, badges, activity)
                WidgetPicker(home)
                CustomizeSheet(home, activity)
                ItemMenuOverlay(activity.overlay)
                activity.overlay.dialog?.invoke()
                DragLayer(activity.drag)
                if (home.safeMode) SafeModeBanner(graph, Modifier.align(Alignment.TopCenter))
                if (!stored.onboardingDone && !home.customizeOpen) WelcomeCard(settings, home, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Wallpaper blur behind the drawer/folders and status/navigation bar styling. */
@Composable
private fun WindowEffects(activity: MainActivity, settings: LauncherSettings, dark: Boolean) {
    val drawer = activity.drawer
    val home = activity.home
    // Live: changing the wallpaper flips status-bar icons without a launcher restart.
    val wallpaperDark = LocalWallpaperTone.current.isDark
    val maxBlurPx = settings.theme.wallpaperBlurPx()
    LaunchedEffect(maxBlurPx, settings.drawer.blur, settings.folders.blur) {
        snapshotFlow {
            val drawerStep = if (settings.drawer.blur) (drawer.progress.value * 6).roundToInt() else 0
            val folderStep = if (settings.folders.blur && home.openFolderId != null) 6 else 0
            maxOf(drawerStep, folderStep)
        }.distinctUntilChanged().collect { step -> activity.setWallpaperBlur(maxBlurPx * step / 6) }
    }
    LaunchedEffect(settings.home.showStatusBar, dark, wallpaperDark) {
        // Full-screen launcher surfaces (drawer, widget picker) follow the theme; home follows the wallpaper.
        snapshotFlow { drawer.progress.value > 0.5f || home.widgetPickerOpen }.distinctUntilChanged().collect { onSurface ->
            activity.applySystemBars(settings, lightIcons = if (onSurface) dark else wallpaperDark)
        }
    }
}

@Composable
private fun SafeModeBanner(graph: AppGraph, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    if (!visible) return
    Surface(
        modifier = modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Safe mode: the launcher crashed repeatedly, widgets are paused until it restarts.",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val context = LocalContext.current
            val backups by produceState(emptyList<java.io.File>()) {
                value = withContext(Dispatchers.IO) { graph.backupManager.autoBackups() }
            }
            if (backups.isNotEmpty()) {
                TextButton(onClick = {
                    scope.launch {
                        // Newest first; skip any backup that turns out to be unreadable.
                        val restored = withContext(Dispatchers.IO) {
                            backups.any { file ->
                                runCatching { file.inputStream().use { graph.backupManager.import(it) } }.getOrDefault(false)
                            }
                        }
                        if (restored) {
                            graph.crashGuard.exitSafeMode()
                            visible = false
                        } else {
                            Toast.makeText(context, "None of the automatic backups could be restored", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Restore backup") }
            }
            TextButton(onClick = {
                graph.crashGuard.exitSafeMode()
                visible = false
            }) { Text("Dismiss") }
        }
    }
}
