package com.anda.launcher.core.icons

import com.anda.launcher.core.model.IconEdit
import kotlin.math.cos
import kotlin.math.sin

/**
 * The editor's hue / saturation / brightness / contrast / invert as one 4×5 color matrix, in the
 * layout `android.graphics.ColorMatrix` takes (offsets in 0..255). Plain math, so it's JVM-testable.
 */
object IconColorMatrix {

    val Identity: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    // Rec. 709 luma, as used for saturation and hue rotation.
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    fun of(edit: IconEdit): FloatArray {
        var m = Identity
        if (edit.saturation != 1f) m = concat(saturation(edit.saturation), m)
        if (edit.hue != 0) m = concat(hue(edit.hue.toFloat()), m)
        if (edit.contrast != 1f) m = concat(contrast(edit.contrast), m)
        if (edit.brightness != 0f) m = concat(brightness(edit.brightness), m)
        if (edit.invert) m = concat(invert(), m)
        return m
    }

    fun saturation(s: Float): FloatArray {
        val inv = 1 - s
        return floatArrayOf(
            LR * inv + s, LG * inv, LB * inv, 0f, 0f,
            LR * inv, LG * inv + s, LB * inv, 0f, 0f,
            LR * inv, LG * inv, LB * inv + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Rotates hue by [degrees] around the luma axis, keeping brightness. */
    fun hue(degrees: Float): FloatArray {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return floatArrayOf(
            LR + c * (1 - LR) + s * -LR, LG + c * -LG + s * -LG, LB + c * -LB + s * (1 - LB), 0f, 0f,
            LR + c * -LR + s * 0.143f, LG + c * (1 - LG) + s * 0.140f, LB + c * -LB + s * -0.283f, 0f, 0f,
            LR + c * -LR + s * -(1 - LR), LG + c * -LG + s * LG, LB + c * (1 - LB) + s * LB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** [c] around mid gray: 1 = unchanged. */
    fun contrast(c: Float): FloatArray {
        val t = 127.5f * (1 - c)
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** -1..1 → up to half the range darker or lighter. */
    fun brightness(b: Float): FloatArray {
        val t = b * 127.5f
        return floatArrayOf(
            1f, 0f, 0f, 0f, t,
            0f, 1f, 0f, 0f, t,
            0f, 0f, 1f, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun invert(): FloatArray = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** `a ∘ b`: applies [b] first, then [a]. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = if (col == 4) a[row * 5 + 4] else 0f
                for (k in 0 until 4) v += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    /** Applies [m] to one opaque RGB color (0..255 each), clamped. For tests and swatches. */
    fun apply(m: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * 255 + m[row * 5 + 4])
            .toInt().coerceIn(0, 255)
        return Triple(ch(0), ch(1), ch(2))
    }
}
