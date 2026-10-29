package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.withSave
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Тень под текстом или под заливкой. */
data class TextShadow(val color: Int, val blur: Float, val dx: Float, val dy: Float)

/**
 * Текстовый стиль в dp. [font] — имя шрифта (см. [FontCache]), [tracking] — межбуквенный интервал в em,
 * как letter-spacing в макете. [fill] — градиент вместо цвета.
 */
data class Typo(
    val font: String,
    val size: Float,
    val weight: Int = 400,
    val tracking: Float = 0f,
    val lineHeight: Float = 1.25f,
    val tabular: Boolean = false,
    val caps: Boolean = false,
    val shadow: TextShadow? = null,
    val fill: Gradient? = null,
    val underline: Boolean = false,
    /** Тени как text-shadow в CSS, первая — сверху. */
    val shadows: List<Shadow> = emptyList(),
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
 * Текст позиционируется по верху строки, как в вёрстке. [bitmap] — картинка под холстом, нужна для размытия под стеклом.
 */
class Painter(private val context: Context, val canvas: Canvas, val bitmap: Bitmap? = null) {

    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    /** Отдельная кисть для замеров «line-height: normal», чтобы не сбивать tp посреди рисования строки. */
    private val metricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun paint(t: Typo, color: Int, size: Float = t.size): TextPaint = setUp(tp, t, color, size)

    private fun setUp(tp: TextPaint, t: Typo, color: Int, size: Float): TextPaint {
        tp.reset()
        tp.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        val font = FontCache.get(context, t.font)
        if (font.system) {
            tp.typeface = Typeface.create(font.typeface, if (t.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        } else {
            tp.typeface = font.typeface
            tp.setFontVariationSettings("'wght' ${t.weight}")
        }
        tp.textSize = size
        tp.color = color
        tp.letterSpacing = t.tracking
        tp.fontFeatureSettings = if (t.tabular) "tnum" else null
        val sh = t.shadow
        if (sh != null) tp.setShadowLayer(sh.blur, sh.dx, sh.dy, sh.color) else tp.clearShadowLayer()
        return tp
    }

    /** Сколько пикселей картинки приходится сейчас на один dp (с учётом масштаба холста). */
    fun deviceScale(): Float {
        @Suppress("DEPRECATION")
        return canvas.matrix.mapRadius(1f)
    }

    /** Тени text-shadow под строкой: копии текста цветом тени со сдвигом и размытием. */
    private fun drawShadows(s: String, x: Float, baseline: Float, p: TextPaint, t: Typo) {
        if (t.shadows.isEmpty()) return
        val sp = TextPaint(p)
        sp.shader = null
        sp.clearShadowLayer()
        sp.isUnderlineText = false
        for (sh in t.shadows.asReversed()) {
            sp.color = sh.color
            sp.maskFilter = Shadows.blurFilter(sh.blur)
            canvas.drawText(s, x + sh.dx, baseline + sh.dy, sp)
        }
    }

    /**
     * Размывает уже нарисованное внутри [path] (прямоугольник [l]..[b] в dp), как backdrop-filter: blur() saturate() в CSS.
     * Работает только при рисовании в картинку ([bitmap]); [cssBlur] — как в CSS.
     */
    fun blurBehind(path: Path, l: Float, t: Float, r: Float, b: Float, cssBlur: Float, saturate: Float = 1f) {
        val bmp = bitmap ?: return
        if (cssBlur <= 0f && saturate == 1f) return
        @Suppress("DEPRECATION")
        val m = canvas.matrix
        val k = m.mapRadius(1f)
        val sigma = cssBlur / 2f * k
        val dev = RectF(l, t, r, b).also { m.mapRect(it) }
        val area = Rect(
            floor(dev.left - 2 * sigma).toInt().coerceAtLeast(0),
            floor(dev.top - 2 * sigma).toInt().coerceAtLeast(0),
            ceil(dev.right + 2 * sigma).toInt().coerceAtMost(bmp.width),
            ceil(dev.bottom + 2 * sigma).toInt().coerceAtMost(bmp.height),
        )
        if (area.width() < 2 || area.height() < 2) return
        // Сильное размытие считается на уменьшенной копии: на глаз то же, а быстрее в десятки раз.
        val down = max(1, (sigma / 3f).toInt())
        val part = Bitmap.createBitmap(bmp, area.left, area.top, area.width(), area.height())
        val small = Bitmap.createScaledBitmap(part, max(2, area.width() / down), max(2, area.height() / down), true)
        if (small !== part) part.recycle()
        Blur.gaussian(small, sigma / down)
        val inverse = Matrix().also { m.invert(it) }
        val dst = RectF(area).also { inverse.mapRect(it) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            if (saturate != 1f) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturate) })
        }
        canvas.withSave {
            clipPath(path)
            drawBitmap(small, null, dst, paint)
        }
        small.recycle()
    }

    /** Градиентная заливка текста по прямоугольнику строки. */
    private fun applyFill(p: TextPaint, t: Typo, left: Float, top: Float, w: Float, h: Float) {
        val fill = t.fill ?: return
        p.color = fill.paintAlpha shl 24
        p.shader = fill.shader(left, top, left + w, top + h)
    }

    private fun prepare(text: String, t: Typo) = if (t.caps) text.uppercase() else text

    fun width(text: String, t: Typo, size: Float = t.size): Float =
        paint(t, 0, size).measureText(prepare(text, t))

    /** Высота строки; lineHeight 0 — как normal в CSS: по метрикам шрифта. */
    fun lineHeight(t: Typo, size: Float = t.size): Float {
        if (t.lineHeight > 0f) return size * t.lineHeight
        val fm = setUp(metricsPaint, t, 0, size).fontMetrics
        return fm.descent - fm.ascent + fm.leading
    }

    /** Обрезает строку с многоточием по ширине (замером, без TextUtils — одинаково везде). */
    fun ellipsize(text: String, t: Typo, maxWidth: Float, size: Float = t.size): String {
        val s = prepare(text, t)
        if (maxWidth <= 0f) return ""
        val p = paint(t, 0, size)
        if (p.measureText(s) <= maxWidth) return s
        val ell = "…"
        val ellW = p.measureText(ell)
        if (ellW > maxWidth) return ""
        var lo = 0
        var hi = s.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (p.measureText(s, 0, mid) + ellW <= maxWidth) lo = mid else hi = mid - 1
        }
        return s.substring(0, lo).trimEnd() + ell
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
        p.isUnderlineText = underline || t.underline
        val w = p.measureText(s)
        val fm = p.fontMetrics
        val lh = lineHeight(t, size)
        val baseline = top + (lh - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val left = when (align) {
            Align.START -> x
            Align.END -> x - w
            Align.CENTER -> x - w / 2f
        }
        drawShadows(s, left, baseline, p, t)
        applyFill(p, t, left, top, w, lh)
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
        p.isUnderlineText = t.underline
        val w = p.measureText(s)
        val left = when (align) {
            Align.START -> x
            Align.END -> x - w
            Align.CENTER -> x - w / 2f
        }
        drawShadows(s, left, baseline, p, t)
        if (t.fill != null) {
            val fm = p.fontMetrics
            applyFill(p, t, left, baseline + fm.ascent, w, fm.descent - fm.ascent)
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

    /** Обводка внутри прямоугольника. [shader] — градиент вместо цвета (тогда от [color] берётся только прозрачность). */
    fun roundRectStroke(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float = 1f, shader: Shader? = null) {
        stroke.shader = shader
        stroke.color = color
        stroke.strokeWidth = width
        val h = width / 2f
        canvas.drawRoundRect(RectF(l + h, t + h, r - h, b - h), radius - h, radius - h, stroke)
        stroke.shader = null
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
