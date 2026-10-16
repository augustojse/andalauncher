package com.anda.launcher.feature.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.Gesture
import kotlin.math.abs
import kotlin.math.hypot

/** Continuous vertical drag for interactive panels (drawer follows the finger, Pixel-style). */
interface VerticalDragHandler {
    /** Return true to take over this swipe (e.g. swipe up when it's bound to the drawer). */
    fun canStart(up: Boolean): Boolean
    fun onStart()
    fun onDrag(deltaY: Float)
    fun onEnd(velocityY: Float)
}

/**
 * Home-screen gestures: swipe up/down, double tap, two-finger swipes and pinch. Runs in the Main
 * pass *after* children, so anything an icon or the pager consumed is ignored here.
 */
fun Modifier.homeGestures(
    key: Any?,
    dragHandler: VerticalDragHandler?,
    onGesture: (Gesture) -> Unit,
): Modifier = pointerInput(key) {
    val slop = viewConfiguration.touchSlop
    val swipeThreshold = 40.dp.toPx()
    val doubleTapSlop = 64.dp.toPx()
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var fired = false
        var dragging = false
        var multiTouch = false
        var consumedByChild = down.isConsumed
        var total = Offset.Zero
        var startSpan = 0f
        var startCentroid = Offset.Zero
        var upTime = down.uptimeMillis

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                upTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: upTime
                break
            }
            if (!dragging && event.changes.any { it.isConsumed }) consumedByChild = true

            if (pressed.size >= 2) {
                if (dragging) continue
                if (!multiTouch) {
                    multiTouch = true
                    startSpan = span(pressed)
                    startCentroid = centroid(pressed)
                }
                if (!fired && startSpan > 0f) {
                    val ratio = span(pressed) / startSpan
                    val move = centroid(pressed) - startCentroid
                    val gesture = when {
                        ratio < 0.65f -> Gesture.PinchIn
                        ratio > 1.5f -> Gesture.PinchOut
                        abs(ratio - 1f) < 0.2f && abs(move.y) > swipeThreshold && abs(move.y) > abs(move.x) ->
                            if (move.y < 0) Gesture.TwoFingerSwipeUp else Gesture.TwoFingerSwipeDown
                        else -> null
                    }
                    if (gesture != null) {
                        fired = true
                        pressed.forEach { it.consume() }
                        onGesture(gesture)
                    }
                }
                continue
            }

            val change = pressed.first()
            velocity.addPosition(change.uptimeMillis, change.position)
            if (multiTouch || consumedByChild) continue
            val delta = change.positionChange()
            if (dragging) {
                change.consume()
                dragHandler?.onDrag(delta.y)
                continue
            }
            if (fired) continue
            total += delta
            val vertical = abs(total.y) > abs(total.x) * 1.5f
            if (vertical && abs(total.y) > slop && dragHandler?.canStart(total.y < 0) == true) {
                dragging = true
                change.consume()
                dragHandler.onStart()
                dragHandler.onDrag(total.y)
                continue
            }
            if (vertical && abs(total.y) > swipeThreshold) {
                fired = true
                change.consume()
                onGesture(if (total.y < 0) Gesture.SwipeUp else Gesture.SwipeDown)
            }
        }

        if (dragging) {
            dragHandler?.onEnd(velocity.calculateVelocity().y)
            return@awaitEachGesture
        }
        val isTap = !fired && !multiTouch && !consumedByChild && total.getDistance() < slop
        if (isTap) {
            if (upTime - lastTapTime < doubleTapTimeout && (down.position - lastTapPosition).getDistance() < doubleTapSlop) {
                lastTapTime = 0L
                onGesture(Gesture.DoubleTap)
            } else {
                lastTapTime = upTime
                lastTapPosition = down.position
            }
        }
    }
}

private fun span(changes: List<PointerInputChange>): Float {
    val a = changes[0].position
    val b = changes[1].position
    return hypot(a.x - b.x, a.y - b.y)
}

private fun centroid(changes: List<PointerInputChange>): Offset {
    var sum = Offset.Zero
    changes.forEach { sum += it.position }
    return sum / changes.size.toFloat()
}
