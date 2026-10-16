package com.anda.launcher.core.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures

@Immutable
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Immutable
data class MenuShortcut(val label: String, val icon: ImageBitmap?, val onClick: () -> Unit)

/** Long-press popup content anchored to an item's bounds (root coordinates). */
@Immutable
data class ItemMenuState(
    val anchor: Rect,
    val shortcuts: List<MenuShortcut>,
    val actions: List<MenuAction>,
)

/** Shared overlay state for menus/dialogs so any surface (home, drawer, folder) can open one. */
@Stable
class OverlayController {
    var menu by mutableStateOf<ItemMenuState?>(null)
        private set

    /** A modal dialog rendered at the root (edit app, rename folder, pickers). */
    var dialog by mutableStateOf<(@Composable () -> Unit)?>(null)

    /**
     * Menus load shortcuts asynchronously. Each request gets a token; dismissing (or starting a
     * drag) invalidates it so a late-arriving menu never pops up over a drag.
     */
    private var menuToken = 0

    fun requestMenu(): Int = ++menuToken

    fun showMenu(token: Int, state: ItemMenuState) {
        if (token == menuToken) menu = state
    }

    fun dismissMenu() {
        menuToken++
        menu = null
    }

    fun showDialog(content: @Composable () -> Unit) {
        dialog = content
    }

    fun dismissDialog() {
        dialog = null
    }
}

val LocalOverlayController = staticCompositionLocalOf { OverlayController() }

/** Shows a "are you sure?" dialog for destructive actions. */
fun OverlayController.confirm(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
    showDialog {
        AlertDialog(
            onDismissRequest = { dismissDialog() },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    dismissDialog()
                    onConfirm()
                }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dismissDialog() }) { Text("Cancel") } },
        )
    }
}

/**
 * Renders the menu in the main window (not a Popup window), so a drag that started on the icon
 * keeps flowing to the icon's gesture handler while the menu is visible.
 */
@Composable
fun ItemMenuOverlay(controller: OverlayController) {
    val state = controller.menu ?: return
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    Box(
        Modifier.fillMaxSize().pointerInput(state) { detectTapGestures { controller.dismissMenu() } },
    ) {
        Layout(content = { MenuCard(state, onAction = { controller.dismissMenu(); it() }) }) { measurables, constraints ->
            val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val anchor = state.anchor
                val x = (anchor.center.x - placeable.width / 2f).toInt().coerceIn(gap, (constraints.maxWidth - placeable.width - gap).coerceAtLeast(gap))
                val above = anchor.top.toInt() - placeable.height - gap
                val y = if (above > gap) above else (anchor.bottom.toInt() + gap).coerceAtMost(constraints.maxHeight - placeable.height - gap)
                placeable.place(IntOffset(x, y))
            }
        }
    }
}

@Composable
private fun MenuCard(state: ItemMenuState, onAction: (() -> Unit) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.width(260.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            state.shortcuts.forEach { shortcut ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAction(shortcut.onClick) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (shortcut.icon != null) Image(shortcut.icon, null, Modifier.size(28.dp)) else Box(Modifier.size(28.dp))
                    Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.shortcuts.isNotEmpty() && state.actions.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            // Vertical rows: labels never truncate, whatever the action count or font scale.
            state.actions.forEach { action ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAction(action.onClick) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(action.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }
        }
    }
}
