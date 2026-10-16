package com.anda.launcher.core.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Touch feedback for icons and folders: a slight press-in while the finger is down and a haptic
 * tick when a long press lands. The scale is read in the draw phase only.
 */
@Stable
class ItemFeedback internal constructor(private val haptics: HapticFeedback, private val scope: CoroutineScope) {
    internal val scale = Animatable(1f)

    internal fun press() {
        scope.launch { scale.animateTo(PRESSED_SCALE, tween(durationMillis = 90)) }
    }

    internal fun release() {
        if (scale.targetValue == 1f) return
        scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    }

    internal fun longPress() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        release()
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
    }
}

@Composable
fun rememberItemFeedback(): ItemFeedback {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(haptics, scope) { ItemFeedback(haptics, scope) }
}

/** Applies the press-in scale from [feedback]. */
fun Modifier.pressScale(feedback: ItemFeedback): Modifier = graphicsLayer {
    val s = feedback.scale.value
    scaleX = s
    scaleY = s
}
