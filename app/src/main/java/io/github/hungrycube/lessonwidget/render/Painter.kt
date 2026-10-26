package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

/** Текстовый стиль в dp. [tracking] — межбуквенный интервал в em, как letter-spacing в макете. */
data class Typo(
    val font: AppFont,
    val size: Float,
    val weight: Int = 400,
    val tracking: Float = 0f,
    val lineHeight: Float = 1.25f,
    val tabular: Boolean = false,
    val caps: Boolean = false,
) {
    fun sized(newSize: Float) = copy(size = newSize)
    fun weighted(newWeight: Int) = copy(weight = newWeight)
}

enum class Align { START, END, CENTER }

/** Результат подбора заголовка: размер шрифта и строки. */
data class FittedText(val typo: Typo, val lines: List<String>, val lineHeight: Float) {
    val height: Float get() = lines.size * lineHeight
}

/**
 * Обёртка над Canvas, где все координаты — в dp (холст заранее масштабирован).
 * Текст позиционируется по верху строки, как в вёрстке.
 */
class Painter(private val context: Context, val canvas: Canvas) {

    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Тень под текстом (для стиля «Стекло» поверх обоев). */
    var textShadowColor: Int = 0

    fun paint(t: Typo, color: Int, size: Float = t.size): TextPaint {
        tp.reset()
        tp.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        val base = FontCache.get(context, t.font)
        if (t.font == AppFont.SYSTEM) {
            tp.typeface = Typeface.create(base, if (t.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        } else {
            tp.typeface = base
            tp.setFontVariationSettings("'wght' ${t.weight}")
        }
        tp.textSize = size
        tp.color = color
        tp.letterSpacing = t.tracking
        tp.fontFeatureSettings = if (t.tabular) "tnum" else null
        if (textShadowColor != 0) tp.setShadowLayer(2.5f, 0f, 0.8f, textShadowColor) else tp.clearShadowLayer()
        return tp
    }

    private fun prepare(text: String, t: Typo) = if (t.caps) text.uppercase() else text

    fun width(text: String, t: Typo, size: Float = t.size): Float =
        paint(t, 0, size).measureText(prepare(text, t))

    fun lineHeight(t: Typo, size: Float = t.size): Float = size * t.lineHeight

    fun ellipsize(text: String, t: Typo, maxWidth: Float, size: Float = t.size): String {
        val p = paint(t, 0, size)
        val s = prepare(text, t)
        if (maxWidth <= 0f) return ""
        return TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    /**
     * Рисует одну строку. [x] — левый край (или правый для END, центр для CENTER), [top] — верх строки.
     * Возвращает ширину нарисованного текста.
     */
    fun text(
        text: String,
        x: Float,
        top: Float,
        t: Typo,
        color: Int,
        align: Align = Align.START,
        maxWidth: Float = Float.MAX_VALUE,
        size: Float = t.size,
        underline: Boolean = false,
    ): Float {
        if (text.isEmpty()) return 0f
        val s = if (maxWidth < Float.MAX_VALUE) ellipsize(text, t, maxWidth, size) else prepare(text, t)
        val p = paint(t, color, size)
        p.isUnderlineText = underline
        val w = p.measureText(s)
        val fm = p.fontMetrics
        val lh = lineHeight(t, size)
        val baseline = top + (lh - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val left = when (align) {
            Align.START -> x
            Align.END -> x - w
            Align.CENTER -> x - w / 2f
        }
        canvas.drawText(s, left, baseline, p)
        return w
    }

    /** Базовая линия строки, если её верх на [top] — для выравнивания разных шрифтов по низу. */
    fun baseline(top: Float, t: Typo, size: Float = t.size): Float {
        val p = paint(t, 0, size)
        val fm = p.fontMetrics
        return top + (lineHeight(t, size) - (fm.descent - fm.ascent)) / 2f - fm.ascent
    }

    /** Рисует текст по базовой линии. */
    fun textAtBaseline(text: String, x: Float, baseline: Float, t: Typo, color: Int, align: Align = Align.START, size: Float = t.size): Float {
        val s = prepare(text, t)
        val p = paint(t, color, size)
        val w = p.measureText(s)
        val left = when (align) {
            Align.START -> x
            Align.END -> x - w
            Align.CENTER -> x - w / 2f
        }
        canvas.drawText(s, left, baseline, p)
        return w
    }

    /**
     * Подбирает размер крупного заголовка: сначала одна строка с небольшим уменьшением,
     * потом две строки, в крайнем случае — одна строка с многоточием.
     */
    fun fit(text: String, t: Typo, maxWidth: Float, maxHeight: Float, maxLines: Int = 2): FittedText {
        val s = prepare(text, t)
        for (scale in floatArrayOf(1f, 0.93f, 0.86f)) {
            val size = t.size * scale
            if (width(s, t, size) <= maxWidth) return FittedText(t.sized(size), listOf(s), lineHeight(t, size))
        }
        if (maxLines >= 2) {
            for (scale in floatArrayOf(0.86f, 0.8f, 0.74f, 0.68f)) {
                val size = t.size * scale
                val lh = lineHeight(t, size)
                if (2 * lh > maxHeight) continue
                val lines = wrap(s, t, size, maxWidth)
                if (lines.size <= 2) return FittedText(t.sized(size), lines, lh)
            }
            val size = t.size * 0.68f
            val lh = lineHeight(t, size)
            if (2 * lh <= maxHeight) {
                val lines = wrap(s, t, size, maxWidth)
                val first = lines.first()
                val rest = s.removePrefix(first).trim()
                return FittedText(t.sized(size), listOf(first, ellipsize(rest, t.copy(caps = false), maxWidth, size)), lh)
            }
        }
        val size = t.size * 0.86f
        return FittedText(t.sized(size), listOf(ellipsize(s, t.copy(caps = false), maxWidth, size)), lineHeight(t, size))
    }

    fun drawFitted(f: FittedText, x: Float, top: Float, color: Int, align: Align = Align.START) {
        var y = top
        val plain = f.typo.copy(caps = false)
        for (line in f.lines) {
            text(line, x, y, plain, color, align)
            y += f.lineHeight
        }
    }

    private fun wrap(s: String, t: Typo, size: Float, maxWidth: Float): List<String> {
        val words = s.split(' ').filter { it.isNotEmpty() }
        val lines = ArrayList<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate, t.copy(caps = false), size) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.map { if (width(it, t.copy(caps = false), size) > maxWidth) ellipsize(it, t.copy(caps = false), maxWidth, size) else it }
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawRect(l, t, r, b, fill)
    }

    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, fill)
    }

    fun roundRectStroke(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float = 1f) {
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = width
        val h = width / 2f
        canvas.drawRoundRect(RectF(l + h, t + h, r - h, b - h), radius - h, radius - h, stroke)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawCircle(cx, cy, r, fill)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1f) {
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, stroke)
    }

    fun clipRound(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val p = Path()
        p.addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW)
        canvas.clipPath(p)
    }
}
