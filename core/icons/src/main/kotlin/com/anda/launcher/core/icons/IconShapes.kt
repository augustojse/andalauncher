package com.anda.launcher.core.icons

import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import com.anda.launcher.core.model.CornerStyle
import com.anda.launcher.core.model.CustomShape
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.ShapeCorner
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Adaptive icon masks. All paths fill a `size × size` square. */
object IconShapes {

    fun path(kind: IconShapeKind, size: Float, custom: CustomShape = CustomShape()): Path {
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
            // A soft, slightly lopsided blob.
            IconShapeKind.Pebble -> polar(size) { t -> 1 + 0.05 * sin(2 * t + 0.6) + 0.03 * cos(3 * t) }
            IconShapeKind.Clover -> polar(size) { t -> 1 + 0.16 * cos(4 * t) }
            IconShapeKind.Flower -> polar(size) { t -> 1 + 0.09 * cos(8 * t) }
            IconShapeKind.Sunny -> polar(size) { t -> 1 + 0.045 * cos(12 * t) }
            IconShapeKind.Arch -> Path().apply {
                val r = size / 2
                val small = size * 0.14f
                addRoundRect(rect, floatArrayOf(r, r, r, r, small, small, small, small), Path.Direction.CW)
            }
            IconShapeKind.Diamond -> roundedPolygon(size, sides = 4, rounding = 0.3f, startAngle = -Math.PI / 2)
            IconShapeKind.Octagon -> roundedPolygon(size, sides = 8, rounding = 0.2f, startAngle = -Math.PI / 2 + Math.PI / 8)
            IconShapeKind.Heart -> heart(size)
            IconShapeKind.Leaf -> Path().apply {
                val big = size * 0.5f
                val small = size * 0.1f
                addRoundRect(rect, floatArrayOf(big, big, small, small, big, big, small, small), Path.Direction.CW)
            }
            // Wider at the bottom.
            IconShapeKind.Egg -> parametric(size) { t -> cos(t) * (1 + 0.13 * sin(t)) to sin(t) }
            IconShapeKind.Custom -> custom(size, custom)
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

    /** A regular polygon whose corners are rounded off by [rounding] (0..0.5 of each side). */
    private fun roundedPolygon(size: Float, sides: Int, rounding: Float, startAngle: Double): Path {
        val r = size / 2.0
        val points = (0 until sides).map { i ->
            val t = startAngle + 2 * Math.PI * i / sides
            (r + r * cos(t)).toFloat() to (r + r * sin(t)).toFloat()
        }
        fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, f: Float) =
            (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f)
        val path = Path()
        points.forEachIndexed { i, vertex ->
            val prev = points[(i - 1 + sides) % sides]
            val next = points[(i + 1) % sides]
            val enter = lerp(vertex, prev, rounding)
            val exit = lerp(vertex, next, rounding)
            if (i == 0) path.moveTo(enter.first, enter.second) else path.lineTo(enter.first, enter.second)
            path.quadTo(vertex.first, vertex.second, exit.first, exit.second)
        }
        path.close()
        return path.fitted(size)
    }

    /** A closed curve from a radius function of the angle, stretched to fill the square. */
    private fun polar(size: Float, radius: (Double) -> Double): Path =
        parametric(size) { t -> radius(t).let { r -> r * cos(t) to r * sin(t) } }

    private fun parametric(size: Float, samples: Int = 180, point: (Double) -> Pair<Double, Double>): Path {
        val path = Path()
        for (i in 0..samples) {
            val (x, y) = point(2 * Math.PI * i / samples)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path.fitted(size)
    }

    private fun heart(size: Float): Path = parametric(size) { t ->
        val s = sin(t)
        val x = 16 * s * s * s
        val y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))
        x to y
    }

    /** Scales and moves a path so its bounds fill `0..size` on both axes. */
    private fun Path.fitted(size: Float): Path {
        val bounds = RectF()
        @Suppress("DEPRECATION") // computeBounds(exact) is the only bounds API on minSdk 34.
        computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return this
        val matrix = android.graphics.Matrix()
        matrix.setRectToRect(bounds, RectF(0f, 0f, size, size), android.graphics.Matrix.ScaleToFit.FILL)
        transform(matrix)
        return this
    }

    /**
     * A shape built corner by corner, clockwise from the top left. Each corner takes up to half a
     * side, so two full corners meet in the middle (a circle when all four are round and full).
     */
    fun custom(size: Float, shape: CustomShape): Path {
        val half = size / 2
        val (tl, tr, br, bl) = shape.corners.map { it.size.coerceIn(0f, 1f) * half }
        val path = Path()
        path.moveTo(tl, 0f)
        path.lineTo(size - tr, 0f)
        corner(path, shape.topRight, size - tr, 0f, size, 0f, size, tr)
        path.lineTo(size, size - br)
        corner(path, shape.bottomRight, size, size - br, size, size, size - br, size)
        path.lineTo(bl, size)
        corner(path, shape.bottomLeft, bl, size, 0f, size, 0f, size - bl)
        path.lineTo(0f, tl)
        corner(path, shape.topLeft, 0f, tl, 0f, 0f, tl, 0f)
        path.close()
        return path
    }

    /** From (x0,y0) around the corner vertex (vx,vy) to (x1,y1); the path is already at (x0,y0). */
    @Suppress("LongParameterList")
    private fun corner(path: Path, corner: ShapeCorner, x0: Float, y0: Float, vx: Float, vy: Float, x1: Float, y1: Float) {
        // How far the curve's control points sit toward the vertex: a circle's quarter, or tighter.
        val k = when (corner.style) {
            CornerStyle.Round -> CIRCLE_CONTROL
            CornerStyle.Squircle -> SQUIRCLE_CONTROL
            CornerStyle.Cut -> {
                path.lineTo(x1, y1)
                return
            }
            CornerStyle.Sharp -> {
                path.lineTo(vx, vy)
                path.lineTo(x1, y1)
                return
            }
        }
        path.cubicTo(x0 + (vx - x0) * k, y0 + (vy - y0) * k, x1 + (vx - x1) * k, y1 + (vy - y1) * k, x1, y1)
    }

    private const val CIRCLE_CONTROL = 0.5523f
    private const val SQUIRCLE_CONTROL = 0.82f
}
