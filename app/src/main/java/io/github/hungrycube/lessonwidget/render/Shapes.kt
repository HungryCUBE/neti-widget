package io.github.hungrycube.lessonwidget.render

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Длина из файла стиля: dp или доля стороны ("50%"). */
class Dim(val value: Float, val percent: Boolean) {
    fun of(base: Float) = if (percent) value / 100f * base else value

    companion object {
        fun parse(raw: String): Dim? {
            val s = raw.trim().removeSuffix("px").removeSuffix("dp")
            return if (s.endsWith("%")) s.dropLast(1).toFloatOrNull()?.let { Dim(it, true) } else s.toFloatOrNull()?.let { Dim(it, false) }
        }
    }
}

/**
 * Скругления углов как border-radius в CSS: "24", "50%", "24px 24px 50% 50% / 24px 24px 16px 16px".
 * Углы по порядку: левый верхний, правый верхний, правый нижний, левый нижний.
 */
class Corners private constructor(private val rx: List<Dim>, private val ry: List<Dim>) {

    /** 8 радиусов для Path.addRoundRect; слишком большие уменьшаются все вместе, как в CSS. */
    fun radii(w: Float, h: Float): FloatArray {
        val x = rx.map { it.of(w) }
        val y = ry.map { it.of(h) }
        var f = 1f
        fun limit(side: Float, a: Float, b: Float) {
            if (a + b > side && a + b > 0f) f = min(f, side / (a + b))
        }
        limit(w, x[0], x[1])
        limit(h, y[1], y[2])
        limit(w, x[2], x[3])
        limit(h, y[3], y[0])
        return floatArrayOf(x[0] * f, y[0] * f, x[1] * f, y[1] * f, x[2] * f, y[2] * f, x[3] * f, y[3] * f)
    }

    companion object {
        /** null — если строка неправильная (см. [error]). */
        fun parse(s: String): Corners? {
            val parts = s.split('/')
            if (parts.size > 2) return null
            val x = expand(parts[0]) ?: return null
            val y = if (parts.size == 2) expand(parts[1]) ?: return null else x
            return Corners(x, y)
        }

        fun error(s: String): String? =
            if (parse(s) == null) "«$s»: нужно как border-radius в CSS — «24», «50%» или «24px 24px 50% 50% / 24px 24px 16px 16px»" else null

        private fun expand(side: String): List<Dim>? {
            val v = side.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Dim.parse(it) ?: return null }
            return when (v.size) {
                1 -> listOf(v[0], v[0], v[0], v[0])
                2 -> listOf(v[0], v[1], v[0], v[1])
                3 -> listOf(v[0], v[1], v[2], v[1])
                4 -> v
                else -> null
            }
        }
    }
}

/** Скруглённый прямоугольник: фигура карточки, подложки, капли. Радиусы — 8 чисел, как в Path.addRoundRect. */
class Shape(val l: Float, val t: Float, val r: Float, val b: Float, val radii: FloatArray) {
    val w get() = r - l
    val h get() = b - t

    fun path(): Path = Path().apply { addRoundRect(RectF(l, t, r, b), radii, Path.Direction.CW) }

    /** Сжать на [d] (отрицательное — расширить), радиусы меняются вместе с краем, как у spread в CSS. */
    fun inset(d: Float) = Shape(l + d, t + d, r - d, b - d, FloatArray(8) { max(0f, radii[it] - d) })

    fun offset(dx: Float, dy: Float) = Shape(l + dx, t + dy, r + dx, b + dy, radii)

    companion object {
        fun of(l: Float, t: Float, r: Float, b: Float, radius: Float, corners: Corners?): Shape =
            Shape(l, t, r, b, corners?.radii(r - l, b - t) ?: FloatArray(8) { radius })
    }
}

/** Тень как box-shadow / text-shadow в CSS. [blur] — как в CSS (сигма гаусса = blur / 2). */
data class Shadow(val dx: Float, val dy: Float, val blur: Float, val spread: Float, val color: Int, val inset: Boolean)

internal object Shadows {

    /** Фильтр размытия для blur из CSS; null — без размытия. Радиус масштабируется вместе с холстом (dp). */
    fun blurFilter(cssBlur: Float): BlurMaskFilter? {
        if (cssBlur < 0.5f) return null
        // Android переводит радиус в сигму как radius * 0.57735 + 0.5.
        val radius = max(0.1f, (cssBlur / 2f - 0.5f) / 0.57735f)
        return BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }

    /** Наружные тени фигуры (под ней, сама фигура не затемняется), первая в списке — сверху. */
    fun outer(p: Painter, shape: Shape, shadows: List<Shadow>) {
        val list = shadows.filter { !it.inset }
        if (list.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        p.canvas.withSave {
            clipOutPath(shape.path())
            for (s in list.asReversed()) {
                paint.color = s.color
                paint.maskFilter = blurFilter(s.blur)
                drawPath(shape.inset(-s.spread).offset(s.dx, s.dy).path(), paint)
            }
        }
    }

    /** Внутренние тени (inset), первая в списке — сверху. */
    fun inner(p: Painter, shape: Shape, shadows: List<Shadow>) {
        val list = shadows.filter { it.inset }
        if (list.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        p.canvas.withSave {
            clipPath(shape.path())
            for (s in list.asReversed()) {
                val pad = s.blur * 2f + abs(s.dx) + abs(s.dy) + abs(s.spread) + 4f
                val frame = Path().apply {
                    addRect(shape.l - pad, shape.t - pad, shape.r + pad, shape.b + pad, Path.Direction.CW)
                    op(shape.inset(s.spread).offset(s.dx, s.dy).path(), Path.Op.DIFFERENCE)
                }
                paint.color = s.color
                paint.maskFilter = blurFilter(s.blur)
                drawPath(frame, paint)
            }
        }
    }
}
