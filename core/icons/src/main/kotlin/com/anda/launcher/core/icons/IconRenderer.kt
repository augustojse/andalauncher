package com.anda.launcher.core.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave

/** Colors used for Material You themed icons. */
data class ThemedColors(val background: Int, val foreground: Int)

/**
 * Renders any drawable into a shaped, normalised icon bitmap. Runs on a background dispatcher.
 */
object IconRenderer {

    /** Legacy icons are drawn at this fraction of the adaptive canvas (Pixel uses ~0.7). */
    private const val LEGACY_SCALE = 0.72f
    private const val THEMED_LEGACY_SCALE = 0.6f

    fun render(
        drawable: Drawable,
        size: Int,
        shape: Path,
        themed: ThemedColors?,
        themedFallback: Boolean,
        normalizeLegacy: Boolean,
    ): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, size, size)
            canvas.withSave {
                clipPath(shape)
                val mono = drawable.monochrome
                when {
                    themed != null && mono != null -> drawThemed(this, mono, size, themed, expanded = true)
                    themed != null && themedFallback && drawable.foreground != null ->
                        drawThemed(this, drawable.foreground, size, themed, expanded = true)
                    else -> {
                        drawable.background?.draw(this)
                        drawable.foreground?.draw(this)
                    }
                }
            }
        } else {
            when {
                themed != null && themedFallback -> canvas.withSave {
                    clipPath(shape)
                    drawThemed(this, drawable, size, themed, expanded = false)
                }
                normalizeLegacy -> canvas.withSave {
                    clipPath(shape)
                    drawColor(Color.WHITE)
                    drawScaled(this, drawable, size, LEGACY_SCALE)
                }
                else -> drawScaled(canvas, drawable, size, 1f)
            }
        }
        return bitmap
    }

    /** Applies an ADW/Nova icon pack's back/mask/upon to an icon the pack doesn't theme. */
    fun renderWithPackMask(
        original: Drawable,
        size: Int,
        back: Drawable?,
        mask: Drawable?,
        upon: Drawable?,
        scale: Float,
    ): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        back?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
        val layer = createBitmap(size, size)
        val layerCanvas = Canvas(layer)
        if (original is AdaptiveIconDrawable) {
            original.setBounds(0, 0, size, size)
            layerCanvas.withSave {
                clipPath(original.iconMask)
                original.background?.draw(this)
                original.foreground?.draw(this)
            }
            val scaled = createBitmap(size, size)
            Canvas(scaled).apply {
                val inset = size * (1 - scale) / 2
                translate(inset, inset)
                scale(scale, scale)
                drawBitmap(layer, 0f, 0f, null)
            }
            layer.eraseColor(Color.TRANSPARENT)
            layerCanvas.drawBitmap(scaled, 0f, 0f, null)
            scaled.recycle()
        } else {
            drawScaled(layerCanvas, original, size, scale)
        }
        mask?.let {
            it.setBounds(0, 0, size, size)
            val maskBitmap = createBitmap(size, size)
            it.draw(Canvas(maskBitmap))
            layerCanvas.drawBitmap(maskBitmap, 0f, 0f, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            maskBitmap.recycle()
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        layer.recycle()
        upon?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
        return bitmap
    }

    fun renderPlain(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        drawScaled(Canvas(bitmap), drawable, size, 1f)
        return bitmap
    }

    private fun drawThemed(canvas: Canvas, layer: Drawable, size: Int, colors: ThemedColors, expanded: Boolean) {
        canvas.drawColor(colors.background)
        val copy = layer.constantState?.newDrawable()?.mutate() ?: layer.mutate()
        copy.colorFilter = PorterDuffColorFilter(colors.foreground, PorterDuff.Mode.SRC_IN)
        if (expanded) {
            // Adaptive layers are 108dp drawn into a 72dp viewport: outset by 25% on each side.
            val outset = size / 4
            copy.setBounds(-outset, -outset, size + outset, size + outset)
            copy.draw(canvas)
        } else {
            drawScaled(canvas, copy, size, THEMED_LEGACY_SCALE)
        }
    }

    private fun drawScaled(canvas: Canvas, drawable: Drawable, size: Int, scale: Float) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val target = size * scale
        val ratio = minOf(target / w, target / h)
        val dw = (w * ratio).toInt()
        val dh = (h * ratio).toInt()
        val left = (size - dw) / 2
        val top = (size - dh) / 2
        val old = drawable.copyBounds()
        drawable.setBounds(left, top, left + dw, top + dh)
        drawable.draw(canvas)
        drawable.bounds = old
    }
}
