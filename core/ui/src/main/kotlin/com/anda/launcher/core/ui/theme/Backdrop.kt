package com.anda.launcher.core.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize

/**
 * Frosted glass for cards drawn over the home screen (Theme → Card blur). Home records itself into
 * [layer] while a card is showing and leaves a [hole] where the card is; the card fills that hole
 * with its own patch of the layer, blurred. So a card can be blurrier or sharper than the
 * home-screen blur around it. The wallpaper is not in the layer; the window blur behind the
 * launcher covers it.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)

    /** Recording costs a layer redraw every frame, so it only runs while some card uses it. */
    internal var users by mutableIntStateOf(0)

    /** The card's outline in source coordinates. Without it the sharp home shows through the glass. */
    internal var hole by mutableStateOf<Path?>(null)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Marks the content a [BackdropBlur] shows through. Place it inside any blur of that content. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        if (backdrop.users > 0) {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            val hole = backdrop.hole
            if (hole != null) clipPath(hole, ClipOp.Difference) { drawLayer(backdrop.layer) } else drawLayer(backdrop.layer)
        } else {
            drawContent()
        }
    }

/**
 * The [LocalBackdrop] content behind this box, blurred by [radius]. Put it under a translucent
 * card with the card's [shape].
 */
@Composable
fun BackdropBlur(radius: Dp, modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    val backdrop = LocalBackdrop.current ?: return
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    DisposableEffect(backdrop) {
        backdrop.users++
        onDispose {
            backdrop.users--
            backdrop.hole = null
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { coords ->
                origin = coords.positionInRoot()
                val outline = shape.createOutline(coords.size.toSize(), layoutDirection, density)
                backdrop.hole = Path().apply {
                    addOutline(outline)
                    translate(origin - backdrop.origin)
                }
            }
            .graphicsLayer {
                val px = radius.toPx()
                renderEffect = if (px > 0f) BlurEffect(px, px, TileMode.Clamp) else null
                clip = true
            }
            .drawBehind {
                val offset = origin - backdrop.origin
                translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
            },
    )
}
