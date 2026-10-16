package com.anda.launcher.feature.home

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import com.anda.launcher.core.model.PageTransition
import kotlin.math.abs

/**
 * Nova-style scroll effects. [offset] is the page's signed distance from the settled position
 * (0 = current, -1 = one page to the left). Applied inside `graphicsLayer {}` so they only
 * touch the draw phase.
 */
internal fun GraphicsLayerScope.applyPageTransition(transition: PageTransition, offset: Float) {
    val o = offset.coerceIn(-1f, 1f)
    val a = abs(o)
    when (transition) {
        PageTransition.Slide -> Unit
        PageTransition.Cube -> {
            cameraDistance = 12f * density
            // The leaving page hinges on its right edge, the incoming page on its left edge.
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
            rotationY = o * -90f * 0.5f
        }
        PageTransition.Depth -> if (o > 0) {
            // The page being left recedes in place.
            translationX = o * size.width
            val scale = 1f - 0.25f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - a
        }
        PageTransition.Zoom -> {
            val scale = 1f - 0.2f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.5f * a
        }
        PageTransition.Stack -> if (o > 0) {
            translationX = o * size.width
            alpha = 1f - a * 0.6f
        }
        PageTransition.Fade -> {
            translationX = o * size.width
            alpha = 1f - a
        }
        PageTransition.Rotate -> {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = o * -12f
        }
    }
}
