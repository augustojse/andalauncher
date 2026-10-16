package com.anda.launcher.core.ui.drag

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.HomeItem
import kotlin.math.roundToInt

/** What is being dragged. */
sealed interface DragPayload {
    /** An app from the drawer or search (not yet on the home screen). */
    data class NewApp(val app: AppInfo) : DragPayload

    /** An item already placed on the desktop, dock, folder or stack. */
    data class Existing(val item: HomeItem) : DragPayload

    /** A widget from the picker. [spanX]/[spanY] are whole cells. */
    data class NewWidget(val provider: ComponentKey, val spanX: Int, val spanY: Int, val label: String) : DragPayload

    /** A deep shortcut dragged out of a long-press menu. */
    data class NewShortcut(val packageName: String, val shortcutId: String, val user: Long, val label: String) : DragPayload
}

/** A region that accepts drops (delete/uninstall/app-info bar, page edges, folders). */
fun interface DropTarget {
    /** Returns true if the drop was consumed. */
    fun onDrop(payload: DragPayload, position: Offset): Boolean
}

/**
 * Global drag state shared by drawer, desktop, dock and folders. Position is kept in root
 * coordinates; readers should read it inside layout/draw lambdas to avoid recomposition.
 */
@Stable
class DragController {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    var icon by mutableStateOf<ImageBitmap?>(null)
        private set
    var iconSize by mutableStateOf(Dp.Unspecified)
        private set

    /** Current pointer position in root coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Offset from the pointer to the dragged view's top-left, so the icon doesn't jump. */
    var touchOffset by mutableStateOf(Offset.Zero)
        private set

    val isDragging: Boolean get() = payload != null

    /** Counts completed drops; the drag layer ticks a haptic on each. */
    var dropCount by mutableIntStateOf(0)
        private set

    private val targets = LinkedHashMap<Any, Pair<() -> Rect, DropTarget>>()
    private var fallback: DropTarget? = null

    fun start(payload: DragPayload, icon: ImageBitmap?, iconSize: Dp, rootPosition: Offset, touchOffset: Offset) {
        this.payload = payload
        this.icon = icon
        this.iconSize = iconSize
        this.position = rootPosition
        this.touchOffset = touchOffset
    }

    fun moveBy(delta: Offset) {
        position += delta
    }

    fun moveTo(rootPosition: Offset) {
        position = rootPosition
    }

    /** Drops at the current position: the first registered target containing it wins, else the fallback. */
    fun drop() {
        val p = payload ?: return
        val pos = position
        val consumed = targets.values.reversed().any { (bounds, target) -> bounds().contains(pos) && target.onDrop(p, pos) }
        if (!consumed) fallback?.onDrop(p, pos)
        clear()
        dropCount++
    }

    fun cancel() = clear()

    private fun clear() {
        payload = null
        icon = null
    }

    fun register(key: Any, bounds: () -> Rect, target: DropTarget) {
        targets[key] = bounds to target
    }

    fun unregister(key: Any) {
        targets.remove(key)
    }

    /** The workspace: receives every drop no other target claimed. */
    fun setFallback(target: DropTarget?) {
        fallback = target
    }
}

val LocalDragController = staticCompositionLocalOf { DragController() }

/**
 * Keeps a drag alive after the item that started it is gone. Put on the root layout (an ancestor
 * of every draggable item, at window origin).
 *
 * The item that started a drag can leave composition mid-drag: an app dragged out of drawer
 * search results (the drawer closes and the results go away), an icon dragged two pages away, a
 * package removed while dragging. Its gesture handler then gets a synthetic cancel that looks
 * like a finger-up, so items never drop (see `launcherItemGestures`); this tracker owns the drop.
 * Pointer events keep reaching the ancestors on the original hit path, so it moves the payload
 * and drops it on the real finger-up. It runs in the Final pass, after the item (while alive)
 * has handled the same event with the same position.
 */
fun Modifier.trackDrags(controller: DragController): Modifier = pointerInput(controller) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (!controller.isDragging) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed) controller.moveTo(change.position) else controller.drop()
        }
    }
}

/** Draws the dragged icon above everything. Place once at the root. */
@Composable
fun DragLayer(controller: DragController) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(controller.dropCount) {
        if (controller.dropCount > 0) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }
    val icon = controller.icon ?: return
    if (!controller.isDragging) return
    val size = controller.iconSize
    Box(
        Modifier
            .offset {
                val p = controller.position - controller.touchOffset
                IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .size(size)
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                shadowElevation = 8.dp.toPx()
            },
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size))
    }
}
