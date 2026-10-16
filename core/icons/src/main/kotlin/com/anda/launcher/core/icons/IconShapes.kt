package com.anda.launcher.core.icons

import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import com.anda.launcher.core.model.IconShapeKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Adaptive icon masks. All paths fill a `size × size` square. */
object IconShapes {

    fun path(kind: IconShapeKind, size: Float): Path {
        val rect = RectF(0f, 0f, size, size)
        return when (kind) {
            IconShapeKind.System -> systemMask(size)
            IconShapeKind.Circle -> Path().apply { addCircle(size / 2, size / 2, size / 2, Path.Direction.CW) }
            IconShapeKind.Square -> Path().apply { addRect(rect, Path.Direction.CW) }
            IconShapeKind.RoundedSquare -> Path().apply { addRoundRect(rect, size * 0.22f, size * 0.22f, Path.Direction.CW) }
            IconShapeKind.Squircle -> superellipse(size, exponent = 4.0)
            IconShapeKind.Teardrop -> Path().apply {
                val r = size / 2
                val small = size * 0.12f
                addRoundRect(rect, floatArrayOf(r, r, small, small, r, r, r, r), Path.Direction.CW)
            }
            IconShapeKind.Cylinder -> Path().apply { addRoundRect(rect, size / 2, size * 0.22f, Path.Direction.CW) }
            IconShapeKind.Hexagon -> polygon(size, sides = 6)
        }
    }

    private fun systemMask(size: Float): Path {
        val drawable = AdaptiveIconDrawable(ColorDrawable(), ColorDrawable())
        drawable.setBounds(0, 0, size.toInt(), size.toInt())
        return Path(drawable.iconMask)
    }

    private fun superellipse(size: Float, exponent: Double, samples: Int = 96): Path {
        val r = size / 2.0
        val path = Path()
        for (i in 0..samples) {
            val t = 2 * Math.PI * i / samples
            val c = cos(t)
            val s = sin(t)
            val x = (r + sign(c) * abs(c).pow(2.0 / exponent) * r).toFloat()
            val y = (r + sign(s) * abs(s).pow(2.0 / exponent) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun polygon(size: Float, sides: Int): Path {
        val r = size / 2.0
        val path = Path()
        for (i in 0 until sides) {
            val t = 2 * Math.PI * i / sides - Math.PI / 2
            val x = (r + r * cos(t)).toFloat()
            val y = (r + r * sin(t)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
