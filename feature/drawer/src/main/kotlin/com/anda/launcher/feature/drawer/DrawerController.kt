package com.anda.launcher.feature.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LauncherGraph
import com.anda.launcher.core.ui.drag.DragController
import com.anda.launcher.core.ui.menu.ItemMenuState
import com.anda.launcher.core.ui.menu.MenuAction
import com.anda.launcher.core.ui.menu.MenuShortcut
import com.anda.launcher.core.ui.menu.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Activity-provided hooks the drawer can't implement itself. */
interface DrawerCallbacks {
    fun addToHome(app: AppInfo)
    fun authenticate(reason: String, onSuccess: () -> Unit)
    fun editApp(app: AppInfo)
    fun editGroup(group: DrawerGroup?)

    /** [screen] deep-links into a settings page. */
    fun openSettings(screen: String?)
}

sealed interface DrawerTab {
    data object All : DrawerTab
    data object Work : DrawerTab
    data class Group(val id: Long, val name: String) : DrawerTab
    data class Category(val category: AppCategory) : DrawerTab
}

/** Drawer open state (0 = closed, 1 = open), search query and app actions. */
@Stable
class DrawerController(
    val graph: LauncherGraph,
    val overlay: OverlayController,
    val drag: DragController,
    private val scope: CoroutineScope,
    private val callbacks: DrawerCallbacks,
) {
    val progress = Animatable(0f)
    var query by mutableStateOf("")
    var tab by mutableStateOf<DrawerTab>(DrawerTab.All)

    /** Incremented to request keyboard focus on the search field. */
    var focusRequests by mutableIntStateOf(0)
        private set

    /** Set when hidden apps were unlocked with biometrics for this drawer session. */
    var hiddenUnlocked by mutableStateOf(false)
    var openGroupId by mutableStateOf<Long?>(null)

    val isOpen: Boolean get() = progress.targetValue > 0.5f

    private val openSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /**
     * Animations need a MonotonicFrameClock; the activity's lifecycleScope has none, so every
     * progress change runs on Compose's UI dispatcher (which carries the Choreographer clock).
     */
    private fun animate(block: suspend () -> Unit) {
        scope.launch(AndroidUiDispatcher.Main) { block() }
    }

    /** Tracks drag position synchronously; Animatable.value lags behind queued snapTo calls. */
    private var dragPosition = 0f

    fun open(focusSearch: Boolean = false) {
        dragPosition = 1f
        animate { progress.animateTo(1f, openSpec) }
        if (focusSearch) focusRequests++
    }

    fun close(animate: Boolean = true) {
        query = ""
        openGroupId = null
        hiddenUnlocked = false
        dragPosition = 0f
        animate { if (animate) progress.animateTo(0f, openSpec) else progress.snapTo(0f) }
    }

    /** Interactive drag from home or within the drawer. [fraction] > 0 opens. */
    fun dragBy(fraction: Float) {
        if (!progress.isRunning) dragPosition = progress.value
        dragPosition = (dragPosition + fraction).coerceIn(0f, 1f)
        val target = dragPosition
        animate { progress.snapTo(target) }
    }

    /** Finishes an interactive drag. [velocity] in fraction/second, positive = opening. */
    fun settle(velocity: Float) {
        val target = when {
            velocity > 1.2f -> 1f
            velocity < -1.2f -> 0f
            else -> dragPosition.roundToInt().toFloat()
        }
        if (target == 0f) close() else open()
    }

    fun launch(app: AppInfo, bounds: Rect) {
        val go = {
            graph.appLauncher.launch(app.key, bounds.toAndroid())
            scope.launch { kotlinx.coroutines.delay(350); close(animate = false) }
            Unit
        }
        if (app.isLocked && graph.settingsRepository.settings.value?.drawer?.authForHiddenApps != false) {
            callbacks.authenticate("Open ${app.label}") { go() }
        } else {
            go()
        }
    }

    fun onDragOut() {
        // Keep composed (gesture continues) but get out of the way immediately.
        animate { progress.snapTo(0f) }
        query = ""
    }

    fun unlockHidden() {
        callbacks.authenticate("Show hidden apps") { hiddenUnlocked = true }
    }

    fun showMenu(app: AppInfo, anchor: Rect) {
        val token = overlay.requestMenu()
        scope.launch {
            val shortcuts = withContext(Dispatchers.IO) {
                graph.shortcutRepository.shortcutsFor(app.key).map { info ->
                    val icon = graph.shortcutRepository.icon(info)?.let { graph.iconRepository.render(it).asImageBitmap() }
                    MenuShortcut((info.shortLabel ?: info.longLabel ?: "").toString(), icon) {
                        graph.appLauncher.launchShortcut(info.`package`, info.id, app.key.user)
                    }
                }
            }
            val actions = listOf(
                MenuAction("Add to home", Icons.Filled.AddToHomeScreen) { callbacks.addToHome(app) },
                MenuAction("App info", Icons.Filled.Info) { graph.appLauncher.openAppInfo(app.key, anchor.toAndroid()) },
                MenuAction("Edit", Icons.Filled.Edit) { callbacks.editApp(app) },
                if (app.isHidden) {
                    MenuAction("Unhide", Icons.Filled.Visibility) { setHidden(app, false) }
                } else {
                    MenuAction("Hide", Icons.Filled.VisibilityOff) { setHidden(app, true) }
                },
                MenuAction("Uninstall", Icons.Filled.DeleteForever) { graph.appLauncher.uninstall(app.key) },
            )
            overlay.showMenu(token, ItemMenuState(anchor, shortcuts, actions))
        }
    }

    fun setHidden(app: AppInfo, hidden: Boolean) {
        scope.launch { graph.overridesRepository.update(app.key) { it.copy(hidden = hidden) } }
    }

    fun editGroup(group: DrawerGroup?) = callbacks.editGroup(group)

    fun openSettings() = callbacks.openSettings("Drawer")

    /** Quick tweaks from the drawer's own menu; the drawer re-renders as soon as they're saved. */
    fun updateSettings(block: (LauncherSettings) -> LauncherSettings) {
        scope.launch { graph.settingsRepository.update(block) }
    }
}

internal fun Rect.toAndroid(): android.graphics.Rect =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
