package com.anda.launcher.core.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Callbacks for an icon/folder/widget on the home screen or in the drawer. */
interface ItemGestureCallbacks {
    fun onTap(position: Offset) {}

    /** Long-press: typically shows the popup menu. A drag may follow. */
    fun onLongPress(position: Offset) {}

    /** Movement past touch slop after a long press: start dragging (dismiss the menu). */
    fun onDragStart(position: Offset) {}

    /**
     * [position] is the pointer in the item's *current* local space. Prefer converting it to
     * root coordinates over accumulating [delta]: the item may move underneath the finger
     * (drawer closing, page scrolling) and deltas would then include that movement.
     */
    fun onDrag(delta: Offset, position: Offset) {}

    /**
     * The long press ended without a drag (finger lifted, menu stays). Once a drag has started,
     * this is NOT called: the drop belongs to the root tracker (`Modifier.trackDrags`), which sees
     * the real finger-up even when this item was removed mid-drag.
     */
    fun onRelease(dragged: Boolean) {}

    /** Nova per-icon swipe up. Return false to let the gesture fall through to the home screen. */
    fun onSwipeUp(): Boolean = false
}

private enum class Outcome { Tap, SwipeUp, Moved, Cancelled }

/**
 * One state machine for tap / long-press / drag / swipe-up so the gestures never fight each
 * other. Movement before the long-press timeout is NOT consumed, so the pager and home
 * gestures still receive it. [feedback] adds the press-in scale and the long-press haptic.
 */
fun Modifier.launcherItemGestures(key: Any?, callbacks: ItemGestureCallbacks, feedback: ItemFeedback? = null): Modifier = pointerInput(key, feedback) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        feedback?.press()
        val slop = viewConfiguration.touchSlop
        val outcome: Outcome? = try {
            pressPhase(down, slop, callbacks)
        } catch (e: CancellationException) {
            // The pointer input restarted mid-press: never leave the icon pressed in.
            feedback?.release()
            throw e
        }
        if (outcome != null) feedback?.release()
        when (outcome) {
            Outcome.Tap -> callbacks.onTap(down.position)
            Outcome.SwipeUp -> {
                // Swallow the rest of the gesture so the home swipe-up doesn't also fire.
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.all { !it.pressed }) break
                }
            }
            Outcome.Moved, Outcome.Cancelled -> Unit
            null -> {
                // Long press reached.
                feedback?.longPress()
                callbacks.onLongPress(down.position)
                var dragging = false
                var dragTotal = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp() || !change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    change.consume()
                    dragTotal += delta
                    if (!dragging && dragTotal.getDistance() > slop) {
                        dragging = true
                        callbacks.onDragStart(change.position)
                    }
                    if (dragging) callbacks.onDrag(delta, change.position)
                }
                // Not after a drag: when an item leaves composition mid-drag (e.g. a search
                // result once the drawer clears its query), Compose sends it a synthetic cancel
                // that looks exactly like a finger-up. Dropping here ended such drags instantly,
                // on the spot. The root tracker drops on the real finger-up instead.
                if (!dragging) callbacks.onRelease(false)
            }
        }
    }
}

/** Waits for tap, swipe-up or movement; returns null when the long-press timeout elapses first. */
private suspend fun AwaitPointerEventScope.pressPhase(down: PointerInputChange, slop: Float, callbacks: ItemGestureCallbacks): Outcome? {
    var total = Offset.Zero
    return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@withTimeoutOrNull Outcome.Cancelled
            val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull Outcome.Cancelled
            if (change.isConsumed) return@withTimeoutOrNull Outcome.Cancelled
            if (change.changedToUp()) {
                change.consume()
                return@withTimeoutOrNull Outcome.Tap
            }
            total += change.positionChange()
            if (total.getDistance() > slop) {
                val vertical = abs(total.y) > abs(total.x) * 1.5f
                if (vertical && total.y < 0 && callbacks.onSwipeUp()) {
                    change.consume()
                    return@withTimeoutOrNull Outcome.SwipeUp
                }
                return@withTimeoutOrNull Outcome.Moved
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Outcome.Cancelled
    }
}
