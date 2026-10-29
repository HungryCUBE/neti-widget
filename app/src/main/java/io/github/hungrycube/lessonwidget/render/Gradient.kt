package io.github.hungrycube.lessonwidget.render

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Градиент из файла стиля, уже с готовыми цветами. Координаты считаются от прямоугольника,
 * который им заливают: угол как в CSS, или концы [from]/[to] в долях, или центр и радиус для radial.
 */
class Gradient(
    val radial: Boolean,
    val angle: Float?,
    val from: FloatArray?,
    val to: FloatArray?,
    val center: FloatArray?,
    val radius: Float,
    val radiusOf: String,
    val colors: IntArray,
    /** null — цвета равномерно. */
    val positions: FloatArray?,
    val opacity: Float,
    /** radial: эллипс по пропорциям прямоугольника (как radial-gradient(ellipse …) в CSS). */
    val ellipse: Boolean = false,
) {
    /** Прозрачность кисти, которой рисуют этот градиент. */
    val paintAlpha: Int get() = (opacity * 255f).roundToInt().coerceIn(0, 255)

    fun shader(l: Float, t: Float, r: Float, b: Float): Shader {
        val w = r - l
        val h = b - t
        if (radial) {
            val cx = l + (center?.getOrNull(0) ?: 0.5f) * w
            val cy = t + (center?.getOrNull(1) ?: 0.5f) * h
            // Радиусы по горизонтали и вертикали: как у CSS farthest-corner / closest-side или от размера прямоугольника.
            val (rx, ry) = when (radiusOf) {
                "farthest", "closest" -> {
                    val far = radiusOf == "farthest"
                    val dx = if (far) max(cx - l, r - cx) else min(cx - l, r - cx)
                    val dy = if (far) max(cy - t, b - cy) else min(cy - t, b - cy)
                    when {
                        ellipse && far -> dx * SQRT2 to dy * SQRT2
                        ellipse -> dx to dy
                        far -> sqrt(dx * dx + dy * dy).let { it to it }
                        else -> min(dx, dy).let { it to it }
                    }
                }
                else -> {
                    val base = when (radiusOf) {
                        "width" -> w
                        "height" -> h
                        "min" -> min(w, h)
                        else -> sqrt(w * w + h * h)
                    }
                    if (ellipse && w > 0f) base to base * h / w else base to base
                }
            }
            val rad = max(0.01f, radius * rx)
            val shader = if (positions == null && colors.size == 2) {
                RadialGradient(cx, cy, rad, colors[0], colors[1], Shader.TileMode.CLAMP)
            } else {
                RadialGradient(cx, cy, rad, colors, positions, Shader.TileMode.CLAMP)
            }
            if (ellipse && rx > 0f && ry != rx) shader.setLocalMatrix(Matrix().apply { setScale(1f, max(0.01f, ry) / rx, cx, cy) })
            return shader
        }
        val x0: Float
        val y0: Float
        val x1: Float
        val y1: Float
        if (from != null && to != null) {
            x0 = l + from[0] * w
            y0 = t + from[1] * h
            x1 = l + to[0] * w
            y1 = t + to[1] * h
        } else {
            // Как linear-gradient(<angle>deg) в CSS: линия через центр, длина — проекция прямоугольника.
            val a = Math.toRadians((angle ?: 180f).toDouble())
            val dx = sin(a).toFloat()
            val dy = -cos(a).toFloat()
            val len = abs(w * dx) + abs(h * dy)
            x0 = l + w / 2f - dx * len / 2f
            y0 = t + h / 2f - dy * len / 2f
            x1 = x0 + dx * len
            y1 = y0 + dy * len
        }
        return if (positions == null && colors.size == 2) {
            LinearGradient(x0, y0, x1, y1, colors[0], colors[1], Shader.TileMode.CLAMP)
        } else {
            LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
        }
    }
}

private const val SQRT2 = 1.4142135f

/** Цвет или градиент: заливка полос, рамок, капли счётчика. */
class Ink(val color: Int, val gradient: Gradient? = null) {
    /** Цвет кисти: при градиенте важна только его прозрачность. */
    val paintColor: Int get() = if (gradient != null) gradient.paintAlpha shl 24 else color
}
