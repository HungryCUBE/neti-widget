package io.github.hungrycube.lessonwidget.render

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.palette.graphics.Palette
import io.github.hungrycube.lessonwidget.render.theme.ColorMath
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Своё фото виджета: картинка (уже уменьшенная при выборе), точка кадра и отражение.
 * Точка кадра — центр видимой части в долях фото, как его видит человек (уже с отражением).
 * Кадр считается под пропорции виджета при каждой отрисовке: после изменения размера точка остаётся той же.
 */
class PhotoInput(val bitmap: Bitmap, val focusX: Float = 0.5f, val focusY: Float = 0.5f, val flip: Boolean = false) {

    /** Видимая часть фото (в пикселях исходной картинки) для прямоугольника с пропорцией [aspect], как object-fit: cover. */
    fun crop(aspect: Float): Rect {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        var cw = bw
        var ch = cw / aspect
        if (ch > bh) {
            ch = bh
            cw = ch * aspect
        }
        val fx = if (flip) 1f - focusX else focusX
        val cx = (fx * bw).coerceIn(cw / 2f, bw - cw / 2f)
        val cy = (focusY * bh).coerceIn(ch / 2f, bh - ch / 2f)
        return Rect((cx - cw / 2f).roundToInt(), (cy - ch / 2f).roundToInt(), (cx + cw / 2f).roundToInt(), (cy + ch / 2f).roundToInt())
    }

    /** Точка кадра, до которой его можно сдвинуть при этой пропорции (чтобы перетаскивание не упиралось в «мёртвую зону»). */
    fun clampFocus(aspect: Float, x: Float, y: Float): Pair<Float, Float> {
        val c = crop(aspect)
        val hx = c.width() / 2f / bitmap.width
        val hy = c.height() / 2f / bitmap.height
        return x.coerceIn(hx, 1f - hx) to y.coerceIn(hy, 1f - hy)
    }

    private val small: Bitmap by lazy {
        val k = 128f / max(bitmap.width, bitmap.height)
        if (k >= 1f) bitmap else Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * k).roundToInt()), max(1, (bitmap.height * k).roundToInt()), true)
    }

    private val palette: Palette? by lazy { runCatching { Palette.from(small).maximumColorCount(16).generate() }.getOrNull() }

    /** Главный цвет фото. */
    val dominant: Int? by lazy { palette?.dominantSwatch?.rgb ?: ColorMath.average(small) }

    /** Самый заметный яркий цвет — для «Акцента из фото». */
    val accent: Int? by lazy { palette?.let { it.vibrantSwatch ?: it.lightVibrantSwatch ?: it.darkVibrantSwatch ?: it.dominantSwatch }?.rgb }

    /**
     * Цвет части кадра от доли высоты [fromY] до [toY]: пиксель, темнее которого доля [q] этой части.
     * Не среднее: яркое небо и тёмные перила вместе дают обманчивую «середину», а читаемость решают крайние части.
     */
    private fun quantile(aspect: Float, fromY: Float, toY: Float, q: Float): Int? = runCatching {
        val c = crop(aspect)
        val k = small.width.toFloat() / bitmap.width
        val l = (c.left * k).toInt().coerceIn(0, small.width - 1)
        val r = (c.right * k).toInt().coerceIn(l + 1, small.width)
        val t = ((c.top + c.height() * fromY) * k).toInt().coerceIn(0, small.height - 1)
        val b = ((c.top + c.height() * toY) * k).toInt().coerceIn(t + 1, small.height)
        val px = IntArray((r - l) * (b - t))
        small.getPixels(px, 0, r - l, l, t, r - l, b - t)
        val sorted = px.sortedBy { ColorMath.luminance(it) }
        sorted[((sorted.size - 1) * q).roundToInt()] or 0xFF000000.toInt()
    }.getOrNull()

    /** Тёмная четверть нижней половины кадра (под текстом): если светлая и она — нужен тёмный текст. */
    fun under(aspect: Float): Int? = quantile(aspect, 0.5f, 1f, 0.25f)

    /** Светлая часть нижней половины кадра: по ней подбирается подложка под светлый текст. */
    fun underBright(aspect: Float): Int? = quantile(aspect, 0.5f, 1f, 0.8f)

    /** Светлая часть верха кадра, где шапка. */
    fun topBright(aspect: Float): Int? = quantile(aspect, 0f, 0.3f, 0.8f)
}

/** Гауссово размытие картинки тремя проходами «коробочного» фильтра, с учётом прозрачности. */
internal object Blur {

    fun gaussian(bmp: Bitmap, sigma: Float) {
        if (sigma < 0.3f || bmp.width < 2 || bmp.height < 2) return
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Каналы с умноженной прозрачностью, иначе по краям прозрачного проступает чёрный.
        val ch = Array(4) { IntArray(w * h) }
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            ch[0][i] = a
            ch[1][i] = ((c shr 16) and 0xFF) * a / 255
            ch[2][i] = ((c shr 8) and 0xFF) * a / 255
            ch[3][i] = (c and 0xFF) * a / 255
        }
        val tmp = IntArray(w * h)
        for (size in boxes(sigma)) {
            val radius = (size - 1) / 2
            for (c in ch) {
                boxH(c, tmp, w, h, radius)
                boxV(tmp, c, w, h, radius)
            }
        }
        for (i in px.indices) {
            val a = ch[0][i].coerceIn(0, 255)
            px[i] = if (a == 0) 0 else (a shl 24) or
                ((ch[1][i] * 255 / a).coerceIn(0, 255) shl 16) or
                ((ch[2][i] * 255 / a).coerceIn(0, 255) shl 8) or
                (ch[3][i] * 255 / a).coerceIn(0, 255)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Ширины трёх коробок, дающих гаусс с сигмой [sigma]. */
    private fun boxes(sigma: Float, n: Int = 3): IntArray {
        val ideal = sqrt(12f * sigma * sigma / n + 1f)
        var wl = ideal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val m = ((12f * sigma * sigma - n * wl * wl - 4f * n * wl - 3f * n) / (-4f * wl - 4f)).roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        if (r <= 0) {
            src.copyInto(dst)
            return
        }
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        if (r <= 0) {
            src.copyInto(dst)
            return
        }
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }
}
