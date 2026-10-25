package com.anda.launcher.core.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.anda.launcher.core.model.IconBackground
import com.anda.launcher.core.model.IconColor
import com.anda.launcher.core.model.IconEdit
import com.anda.launcher.core.model.IconForeground
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Colors used for Material You themed icons. */
data class ThemedColors(val background: Int, val foreground: Int)

/**
 * Renders any drawable into a shaped, normalised icon bitmap. Runs on a background dispatcher.
 */
object IconRenderer {

    /** Legacy icons are drawn at this fraction of the adaptive canvas (Pixel uses ~0.7). */
    private const val LEGACY_SCALE = 0.72f
    private const val THEMED_LEGACY_SCALE = 0.6f

    /** Room kept around the shape for its shadow, as a fraction of the size. */
    private const val SHADOW_INSET = 0.05f

    @Suppress("LongParameterList")
    fun render(
        drawable: Drawable,
        size: Int,
        shape: Path,
        themed: ThemedColors?,
        themedFallback: Boolean,
        normalizeLegacy: Boolean,
        edit: IconEdit = IconEdit.Plain,
        colors: (IconColor) -> Int = { it.argb?.toInt() ?: Color.GRAY },
    ): Bitmap {
        if (!edit.isPlain) return renderEdited(drawable, size, shape, themed, themedFallback, normalizeLegacy, edit, colors, flat = false)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, size, size)
            canvas.withSave {
                clipPath(shape)
                val mono = drawable.monochrome
                when {
                    themed != null && mono != null -> drawThemed(this, mono, size, themed, expanded = true, isMonochrome = true)
                    themed != null && themedFallback && drawable.foreground != null ->
                        drawThemed(this, drawable.foreground, size, themed, expanded = true, isMonochrome = false)
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
                    drawThemed(this, drawable, size, themed, expanded = false, isMonochrome = false)
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

    /**
     * Icon-pack art: already a finished icon, so a plain edit draws it as it is. An edit recolors
     * and moves it; it's only cut to [shape] once the edit gives it a backdrop of its own.
     */
    fun renderFlat(drawable: Drawable, size: Int, shape: Path, edit: IconEdit, colors: (IconColor) -> Int): Bitmap =
        if (edit.isPlain) {
            renderPlain(drawable, size)
        } else {
            renderEdited(drawable, size, shape, null, themedFallback = false, normalizeLegacy = false, edit, colors, flat = true)
        }

    /**
     * The icon editor's pipeline: optional shadow → clip to the shape → background (the app's own,
     * a color, a gradient or none) → foreground (own colors through the color matrix, or one flat
     * color) moved / scaled / rotated inside the shape → outline.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    private fun renderEdited(
        drawable: Drawable,
        size: Int,
        shape: Path,
        themed: ThemedColors?,
        themedFallback: Boolean,
        normalizeLegacy: Boolean,
        edit: IconEdit,
        colors: (IconColor) -> Int,
        flat: Boolean,
    ): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val adaptive = drawable as? AdaptiveIconDrawable
        val mono = adaptive?.monochrome

        var background = edit.background
        var foreground = edit.foreground
        var backgroundColor = colors(edit.backgroundColor)
        var foregroundColor = colors(edit.foregroundColor)
        // Themed icons recolor only what the edit leaves original.
        val themeIt = themed != null && !flat && (mono != null || themedFallback)
        if (themeIt && themed != null) {
            if (background == IconBackground.Original) {
                background = IconBackground.Solid
                backgroundColor = themed.background
            }
            if (foreground == IconForeground.Original) {
                foreground = IconForeground.Monochrome
                foregroundColor = themed.foreground
            }
        }

        val shaped = when {
            adaptive != null -> true
            flat -> background != IconBackground.Original
            else -> normalizeLegacy || background != IconBackground.Original
        }
        val adjust: ColorFilter? = if (foreground == IconForeground.Original && edit.hasColorAdjustments) {
            ColorMatrixColorFilter(IconColorMatrix.of(edit))
        } else {
            null
        }
        val center = size / 2f

        canvas.withSave {
            val inset = if (edit.shadow && shaped) max(edit.padding, SHADOW_INSET) else edit.padding
            if (inset > 0f) scale(1 - 2 * inset, 1 - 2 * inset, center, center)
            if (edit.shadow && shaped) {
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    setShadowLayer(size * 0.04f, 0f, size * 0.02f, Color.argb(110, 0, 0, 0))
                }
                drawPath(shape, shadow)
            }
            if (shaped) clipPath(shape)

            when (background) {
                IconBackground.Original -> when {
                    adaptive != null -> adaptive.background?.let { drawLayer(this, it, size, adjust) }
                    !flat && normalizeLegacy -> drawColor(Color.WHITE)
                }
                IconBackground.Solid -> drawColor(backgroundColor)
                IconBackground.Gradient -> {
                    val angle = Math.toRadians(edit.gradientAngle.toDouble())
                    val dx = (cos(angle) * center).toFloat()
                    val dy = (sin(angle) * center).toFloat()
                    val paint = Paint().apply {
                        shader = LinearGradient(
                            center - dx, center - dy, center + dx, center + dy,
                            backgroundColor, colors(edit.backgroundColor2), Shader.TileMode.CLAMP,
                        )
                    }
                    drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                }
                IconBackground.None -> Unit
            }

            withSave {
                translate(edit.offsetX * size, edit.offsetY * size)
                if (edit.rotation != 0) rotate(edit.rotation.toFloat(), center, center)
                if (edit.scale != 1f || edit.flip) scale(if (edit.flip) -edit.scale else edit.scale, edit.scale, center, center)
                val filter = when {
                    foreground != IconForeground.Monochrome -> adjust
                    mono != null -> PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
                    else -> silhouette(foregroundColor)
                }
                when {
                    adaptive != null -> {
                        val layer = if (foreground == IconForeground.Monochrome) mono ?: adaptive.foreground else adaptive.foreground
                        layer?.let { drawLayer(this, it, size, filter) }
                    }
                    else -> {
                        val scale = when {
                            !shaped || flat -> 1f
                            foreground == IconForeground.Monochrome && themeIt -> THEMED_LEGACY_SCALE
                            else -> LEGACY_SCALE
                        }
                        val copy = drawable.copyForDrawing()
                        copy.colorFilter = filter
                        drawScaled(this, copy, size, scale)
                    }
                }
            }

            if (edit.outlineWidth > 0f && shaped) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    // Centred on the edge and clipped by the shape: half of it shows, inside.
                    strokeWidth = edit.outlineWidth * size * 2
                    color = colors(edit.outlineColor)
                }
                drawPath(shape, stroke)
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

    @Suppress("LongParameterList")
    private fun drawThemed(canvas: Canvas, layer: Drawable, size: Int, colors: ThemedColors, expanded: Boolean, isMonochrome: Boolean) {
        canvas.drawColor(colors.background)
        val tint = if (isMonochrome) PorterDuffColorFilter(colors.foreground, PorterDuff.Mode.SRC_IN) else silhouette(colors.foreground)
        if (expanded) {
            drawLayer(canvas, layer, size, tint)
        } else {
            val copy = layer.copyForDrawing()
            copy.colorFilter = tint
            drawScaled(canvas, copy, size, THEMED_LEGACY_SCALE)
        }
    }

    /**
     * One flat [color] from a full-color layer, for apps without a monochrome layer. Unlike a
     * plain tint it drops faint pixels (long shadows, glows), which would otherwise show as a pale
     * smear, and makes the rest solid; edges keep a short alpha ramp so they stay smooth.
     */
    private fun silhouette(color: Int): ColorFilter = ColorMatrixColorFilter(
        floatArrayOf(
            0f, 0f, 0f, 0f, Color.red(color).toFloat(),
            0f, 0f, 0f, 0f, Color.green(color).toFloat(),
            0f, 0f, 0f, 0f, Color.blue(color).toFloat(),
            0f, 0f, 0f, SILHOUETTE_GAIN, -SILHOUETTE_GAIN * SILHOUETTE_THRESHOLD * 255f,
        ),
    )

    /** Alpha below this (0..1) disappears; the next 1 / [SILHOUETTE_GAIN] of the range fades in. */
    private const val SILHOUETTE_THRESHOLD = 0.45f
    private const val SILHOUETTE_GAIN = 4f

    /** An adaptive layer: 108dp drawn into a 72dp viewport, so outset by 25% on each side. */
    private fun drawLayer(canvas: Canvas, layer: Drawable, size: Int, filter: ColorFilter?) {
        val copy = layer.copyForDrawing()
        copy.colorFilter = filter
        val outset = size / 4
        copy.setBounds(-outset, -outset, size + outset, size + outset)
        copy.draw(canvas)
    }

    /** A private copy, so a color filter never leaks into the shared drawable state. */
    private fun Drawable.copyForDrawing(): Drawable = constantState?.newDrawable()?.mutate() ?: mutate()

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
