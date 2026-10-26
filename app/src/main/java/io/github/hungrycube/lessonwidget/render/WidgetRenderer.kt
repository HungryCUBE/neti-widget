package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import androidx.core.graphics.withSave
import io.github.hungrycube.lessonwidget.core.display.LineStatus
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.SlotInfo
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.time.formatMinute
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class RenderMode {
    /** Для виджета: фон прозрачный, обратный отсчёт — системным Chronometer поверх картинки. */
    WIDGET,
    /** Для превью в приложении и скриншотов: с фоном-подложкой, отсчёт нарисован в картинке. */
    PREVIEW,
}

/** Место для системного обратного отсчёта поверх картинки, в dp от левого верхнего угла виджета. */
data class CountdownSlot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val layout: CountdownLayout,
    /** Формат Chronometer: «ещё %s». */
    val format: String,
    val color: Int,
    val textSize: Float,
)

class Rendered(val bitmap: Bitmap, val countdown: CountdownSlot?, val description: String)

/** Рисует виджет целиком в Bitmap. Один код и для рабочего стола, и для превью. */
class WidgetRenderer(private val context: Context) {

    fun render(
        style: WidgetStyle,
        display: WidgetDisplay,
        widthDp: Float,
        heightDp: Float,
        mode: RenderMode,
        nowMillis: Long = System.currentTimeMillis(),
        density: Float = context.resources.displayMetrics.density,
    ): Rendered {
        val st = style.resolved(context)
        val w = widthDp.coerceAtLeast(80f)
        val h = heightDp.coerceAtLeast(80f)
        val margin = if (mode == RenderMode.PREVIEW) 20f else 0f
        val totalW = w + 2 * margin
        val totalH = h + 2 * margin

        // Плотность пикселей ограничена: у виджетов есть лимит на размер картинок.
        var scale = min(density, 3f)
        val maxPixels = 1_500_000f
        if (totalW * totalH * scale * scale > maxPixels) scale = sqrt(maxPixels / (totalW * totalH))

        val bitmap = Bitmap.createBitmap(
            ceil(totalW * scale).toInt().coerceAtLeast(1),
            ceil(totalH * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val painter = Painter(context, canvas)
        if (mode == RenderMode.PREVIEW) drawBackdrop(painter, st.backdrop, totalW, totalH)
        canvas.translate(margin, margin)
        val slot = Frame(painter, st, display, w, h, mode, nowMillis).draw()
        return Rendered(bitmap, slot, describe(display))
    }

    private fun describe(d: WidgetDisplay): String =
        listOfNotNull(d.headline, d.title, d.focus?.room?.let { "аудитория $it" }, d.message).joinToString(". ")

    private fun drawBackdrop(p: Painter, b: Backdrop, w: Float, h: Float) {
        p.rect(0f, 0f, w, h, b.base)
        val diag = sqrt(w * w + h * h)
        for (blob in b.blobs) {
            val cx = blob.x * w
            val cy = blob.y * h
            val r = blob.radius * diag
            p.fill.shader = RadialGradient(cx, cy, r, blob.color, blob.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            p.canvas.drawRect(0f, 0f, w, h, p.fill)
            p.fill.shader = null
        }
        b.stripes?.let { color ->
            p.canvas.withSave {
                rotate(-28f, w * 0.78f, h * 0.5f)
                p.rect(w * 0.70f, -h, w * 0.70f + 3f, 2 * h, color)
                p.rect(w * 0.76f, -h, w * 0.76f + 2f, 2 * h, color)
            }
        }
    }
}

/** Отрисовка одного виджета. Все размеры — в dp. */
private class Frame(
    val p: Painter,
    val st: WidgetStyle,
    val d: WidgetDisplay,
    val W: Float,
    val H: Float,
    val mode: RenderMode,
    val now: Long,
) {
    val pal = st.palette
    val pad = st.padding

    /** Большой виджет (4×4): сверху обычная карточка, ниже список пар дня. */
    val large = H >= 250f
    val narrow = W < 230f
    val mainH = if (large) 168f else H
    val l = pad.side
    val r = W - pad.side
    val top = pad.top
    val mainBottom = mainH - pad.bottom

    val f: SlotInfo? = d.focus
    val isLesson = d.phase == Phase.LESSON
    val upcoming = d.phase == Phase.BREAK || d.phase == Phase.WINDOW || d.phase == Phase.BEFORE_FIRST
    val nextDay = d.phase == Phase.DAY_OVER || d.phase == Phase.DAY_OFF

    fun draw(): CountdownSlot? = when (st.layout) {
        CardLayout.GLASS -> glass()
        CardLayout.CLEAN -> clean()
        CardLayout.RING -> ring()
        CardLayout.TERMINAL -> terminal()
        CardLayout.TONAL -> tonal()
        CardLayout.PAPER -> paper()
        CardLayout.DOTS -> dots()
        CardLayout.RETRO -> retro()
        CardLayout.TAGS -> tags()
    }

    // ---------- Общие тексты ----------

    fun fmt(minute: Int) = formatMinute(minute)

    fun range(s: SlotInfo, sep: String) = fmt(s.startMinute) + sep + fmt(s.endMinute)

    /** «до 11:40» для идущей пары, «в 12:00» для следующей. */
    fun until(): String? = f?.let { if (isLesson) "до ${fmt(it.endMinute)}" else "в ${fmt(it.startMinute)}" }

    fun roomAndType(): String = listOfNotNull(f?.room, f?.type, f?.subgroupNote).joinToString(" · ")

    fun countdownPrefix() = if (isLesson) "ещё" else "через"

    /** Пример текста отсчёта максимальной ширины — чтобы зарезервировать место. */
    fun countdownSample(): String {
        val left = (d.countdownTo ?: now) - now
        return if (left >= 3_600_000L) "0:00:00" else "00:00"
    }

    fun countdownText(): String {
        val total = (((d.countdownTo ?: now) - now).coerceAtLeast(0L) / 1000L)
        val hh = total / 3600
        val mm = total % 3600 / 60
        val ss = total % 60
        return if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)
    }

    /**
     * Обратный отсчёт. На виджете возвращает место для системного Chronometer,
     * в превью рисует то же самое статично.
     */
    fun countdown(x: Float, y: Float, w: Float, h: Float, layout: CountdownLayout, format: String, color: Int, size: Float): CountdownSlot? {
        if (d.countdownTo == null) return null
        if (mode == RenderMode.PREVIEW) {
            val t = layout.typo(size)
            val text = format.replace("%s", countdownText())
            val ty = y + (h - p.lineHeight(t)) / 2f
            when (layout.align) {
                Align.START -> p.text(text, x, ty, t, color)
                Align.END -> p.text(text, x + w, ty, t, color, Align.END)
                Align.CENTER -> p.text(text, x + w / 2f, ty, t, color, Align.CENTER)
            }
            return null
        }
        return CountdownSlot(x, y, w, h, layout, format, color, size)
    }

    /** Левая и правая части строки «Далее». */
    fun nextParts(): Pair<String, String> {
        val n = d.next
        return when {
            n != null -> "Далее · ${n.title}" to listOfNotNull(fmt(n.startMinute), n.room).joinToString(" · ")
            f == null -> "" to ""
            nextDay -> (d.message ?: "") to ""
            else -> "Дальше пар нет" to ""
        }
    }

    fun roundPath(l: Float, t: Float, r: Float, b: Float, radius: Float) =
        Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) }

    /** Заливка карточки с обводкой. */
    fun card(color: Int, border: Int?, radius: Float = st.radius) {
        p.roundRect(0f, 0f, W, H, radius, color)
        if (border != null) p.roundRectStroke(0f, 0f, W, H, radius, border)
    }

    // ---------- Список пар дня (большой виджет) ----------

    fun dayList(top: Float, bottom: Float, left: Float = l, right: Float = r, text: Int = pal.text, muted: Int = pal.muted, accent: Int = pal.accent) {
        if (d.day.isEmpty() || bottom - top < 24f) return
        val t = st.meta.copy(size = min(st.meta.size, 12f), lineHeight = 1.25f)
        val head = st.label.copy(size = min(st.label.size, 11f), lineHeight = 1.25f)
        var y = top
        p.text("${d.dayLabel} · ${pairsWord(d.day.size)}", left, y, head, muted, maxWidth = right - left)
        y += p.lineHeight(head) + 6f
        val rowH = max(22f, p.lineHeight(t) + 6f)
        val timeW = p.width("00:00", t) + 10f
        val markerW = 12f
        for (line in d.day) {
            if (y + rowH > bottom) break
            val past = line.status == LineStatus.PAST
            val c = if (past) (text and 0x00FFFFFF) or 0x73000000 else text
            val m = if (past) (muted and 0x00FFFFFF) or 0x73000000 else muted
            val ty = y + (rowH - p.lineHeight(t)) / 2f
            if (line.status == LineStatus.NOW) {
                val marker = st.listMarker
                if (marker != null) p.text(marker, left, ty, t, accent) else p.circle(left + 3f, y + rowH / 2f, 3f, accent)
            }
            val info = line.info
            p.text(fmt(info.startMinute), left + markerW, ty, t, m)
            val roomW = info.room?.let { p.width(it, t) + 8f } ?: 0f
            info.room?.let { p.text(it, right, ty, t, m, Align.END, maxWidth = (right - left) / 3f) }
            val titleText = info.title + (info.typeShort?.let { " · $it" } ?: "")
            p.text(titleText, left + markerW + timeW, ty, t, c, maxWidth = right - left - markerW - timeW - min(roomW, (right - left) / 3f))
            y += rowH
        }
    }

    fun pairsWord(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        val word = when {
            mod10 == 1 && mod100 != 11 -> "пара"
            mod10 in 2..4 && mod100 !in 12..14 -> "пары"
            else -> "пар"
        }
        return "$n $word"
    }

    // ---------- 1. Стекло ----------

    fun glass(): CountdownSlot? {
        val path = roundPath(0f, 0f, W, H, st.radius)
        // На рабочем столе под стеклом — лёгкое затемнение, иначе белый текст пропадает на светлых обоях.
        if (mode == RenderMode.WIDGET) {
            p.fill.shader = null
            p.fill.color = 0x2E120C34
            p.canvas.drawPath(path, p.fill)
        }
        p.fill.color = pal.surface
        p.canvas.drawPath(path, p.fill)
        // Блик по диагонали (linear-gradient 140deg, белый 22% → 0 на 38%).
        val a = Math.toRadians(140.0)
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val len = kotlin.math.abs(W * dx) + kotlin.math.abs(H * dy)
        val sx = W / 2f - dx * len / 2f
        val sy = H / 2f - dy * len / 2f
        p.fill.shader = LinearGradient(
            sx, sy, sx + dx * len, sy + dy * len,
            intArrayOf(0x38FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.38f), Shader.TileMode.CLAMP,
        )
        p.canvas.drawPath(path, p.fill)
        p.fill.shader = null
        // Обводка: сверху ярче (внутренний блик), снизу слабее.
        p.stroke.strokeWidth = 1f
        p.stroke.shader = LinearGradient(
            0f, 0f, 0f, H, intArrayOf(0x8CFFFFFF.toInt(), pal.border ?: 0x52FFFFFF, 0x26FFFFFF),
            floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP,
        )
        p.canvas.drawRoundRect(RectF(0.5f, 0.5f, W - 0.5f, H - 0.5f), st.radius - 0.5f, st.radius - 0.5f, p.stroke)
        p.stroke.shader = null

        if (mode == RenderMode.WIDGET) p.textShadowColor = 0x40000000
        var y = top
        val labelT = st.label
        val metaT = st.meta
        val pillH = p.lineHeight(labelT) + 6f

        val cdColor = 0xD9FFFFFF.toInt()
        val rightText: String? = when {
            d.countdownTo != null -> "${countdownPrefix()} ${countdownSample()}"
            nextDay && f != null -> "в ${fmt(f.startMinute)}"
            else -> null
        }
        val rightW = rightText?.let { p.width(it, metaT) + 4f } ?: 0f
        val pillMax = r - l - rightW - 8f - 18f
        val pillText = p.ellipsize(d.headline, labelT, pillMax)
        val pw = p.width(pillText, labelT) + 18f
        p.roundRect(l, y, l + pw, y + pillH, pillH / 2f, 0x33FFFFFF)
        p.roundRectStroke(l, y, l + pw, y + pillH, pillH / 2f, 0x40FFFFFF)
        p.text(pillText, l + 9f, y + 3f, labelT, pal.text)

        var slot: CountdownSlot? = null
        if (d.countdownTo != null) {
            slot = countdown(r - rightW, y, rightW, pillH, CountdownLayout.MANROPE_END, "${countdownPrefix()} %s", cdColor, metaT.size)
        } else if (rightText != null) {
            p.text(rightText, r, y + (pillH - p.lineHeight(metaT)) / 2f, metaT, cdColor, Align.END)
        }
        y += pillH + 8f

        val metaLh = p.lineHeight(metaT)
        val bodyLh = p.lineHeight(st.body)
        val hasBar = d.progress != null
        val avail = mainBottom - y
        val titleMin = p.lineHeight(st.heading) * 0.86f
        val fullBottom = (if (hasBar) 13f else 0f) + metaLh
        val showNext = avail >= titleMin + bodyLh + fullBottom + 4f
        val bottomBlock = if (showNext) fullBottom else if (hasBar) 4f else 0f
        val showDetails = avail >= titleMin + bodyLh + bottomBlock + 4f
        val titleMaxH = avail - bottomBlock - (if (showDetails) bodyLh else 0f) - 4f

        val fitted = p.fit(d.title, st.heading, r - l, titleMaxH)
        p.drawFitted(fitted, l, y, pal.text)
        y += fitted.height
        if (showDetails) {
            val details = when {
                f == null -> d.message ?: ""
                nextDay -> listOfNotNull(f.room, f.type).joinToString(" · ")
                else -> listOfNotNull(f.room, until()).joinToString(" · ")
            }
            p.text(details, l, y, st.body, pal.secondary, maxWidth = r - l)
        }

        var by = mainBottom
        if (showNext) {
            by -= metaLh
            val (left, right) = nextParts()
            val rw = if (right.isNotEmpty()) p.text(right, r, by, metaT, pal.muted, Align.END) + 10f else 0f
            p.text(left, l, by, metaT, pal.muted, maxWidth = r - l - rw)
            by -= 9f
        }
        if (hasBar) {
            by -= 4f
            p.roundRect(l, by, r, by + 4f, 2f, pal.track)
            val fw = (r - l) * (d.progress ?: 0f)
            if (fw > 0.5f) {
                p.fill.setShadowLayer(5f, 0f, 0f, 0xB3FFFFFF.toInt())
                p.roundRect(l, by, l + max(fw, 4f), by + 4f, 2f, pal.accent)
                p.fill.clearShadowLayer()
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        p.textShadowColor = 0
        return slot
    }

    // ---------- 2. Минимализм ----------

    fun clean(): CountdownSlot? {
        card(pal.surface, pal.border)
        var y = top
        val labelT = st.label
        val metaT = st.meta
        val headerLeft = when {
            f == null -> d.status
            isLesson -> range(f, " — ")
            upcoming -> "${d.status} · ${range(f, " — ")}"
            else -> "${d.dayLabel} · ${range(f, " — ")}"
        }
        val number = f?.number?.let { "%02d".format(it) }
        val nw = number?.let { p.text(it, r, y, labelT, pal.container, Align.END) + 12f } ?: 0f
        p.text(headerLeft, l, y, labelT, pal.muted, maxWidth = r - l - nw)
        y += p.lineHeight(labelT) + 2f

        val metaLh = p.lineHeight(metaT)
        val bodyLh = p.lineHeight(st.body)
        val rowH = metaLh
        val hasRow = d.progress != null || d.countdownTo != null || (nextDay && d.message != null)
        val avail = mainBottom - y
        val titleMin = p.lineHeight(st.heading) * 0.86f
        val fullBottom = (if (hasRow) rowH + 8f else 0f) + metaLh
        val showNext = avail >= titleMin + bodyLh + fullBottom + 4f
        val bottomBlock = if (showNext) fullBottom else if (hasRow) rowH else 0f
        val showDetails = avail >= titleMin + bodyLh + bottomBlock + 4f
        val titleMaxH = avail - bottomBlock - (if (showDetails) bodyLh else 0f) - 4f

        val fitted = p.fit(d.title, st.heading, r - l, titleMaxH)
        p.drawFitted(fitted, l, y, pal.text)
        y += fitted.height
        if (showDetails) {
            p.text(if (f == null) d.message ?: "" else roomAndType(), l, y, st.body, pal.secondary, maxWidth = r - l)
        }

        var by = mainBottom
        if (showNext) {
            by -= metaLh
            val n = d.next
            if (n != null) {
                val prefix = "Далее — "
                val suffix = ", ${fmt(n.startMinute)}"
                var x = l
                x += p.text(prefix, x, by, metaT, pal.muted)
                val suffixW = p.width(suffix, metaT)
                x += p.text(n.title, x, by, metaT, pal.onContainer, maxWidth = r - x - suffixW)
                p.text(suffix, x, by, metaT, pal.muted)
            } else {
                p.text(nextParts().first, l, by, metaT, pal.muted, maxWidth = r - l)
            }
            by -= 8f
        }
        var slot: CountdownSlot? = null
        if (hasRow) {
            by -= rowH
            if (d.countdownTo != null || d.progress != null) {
                val cdW = if (d.countdownTo != null) p.width(countdownSample(), metaT.weighted(600)) + 4f else 0f
                val lineR = if (cdW > 0f) r - cdW - 12f else r
                val ly = by + rowH / 2f - 1f
                p.rect(l, ly, lineR, ly + 2f, pal.track)
                p.rect(l, ly, l + (lineR - l) * (d.progress ?: 0f), ly + 2f, pal.accent)
                slot = countdown(r - cdW, by, cdW, rowH, CountdownLayout.INTER_TIGHT_END, "%s", pal.text, metaT.size)
            } else {
                p.text(d.message ?: "", l, by, metaT, pal.muted, maxWidth = r - l)
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        return slot
    }

    // ---------- 3. Тёмный OLED ----------

    fun ring(): CountdownSlot? {
        card(pal.surface, pal.border)
        val ringD = if (narrow || mainH < 140f) 60f else 76f
        val ringW = if (ringD > 70f) 5f else 4.5f
        val y = top
        val cx = l + ringD / 2f
        val cy = y + ringD / 2f
        val rr = ringD / 2f - ringW / 2f
        p.stroke.shader = null
        p.stroke.strokeCap = Paint.Cap.BUTT
        p.stroke.strokeWidth = ringW
        p.stroke.color = pal.track
        p.canvas.drawCircle(cx, cy, rr, p.stroke)
        d.progress?.let { prog ->
            p.stroke.color = pal.accent
            p.canvas.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90f, 360f * prog, false, p.stroke)
        }

        val small = Typo(AppFont.INTER_TIGHT, 9f, 500, lineHeight = 1.2f)
        var slot: CountdownSlot? = null
        if (d.countdownTo != null) {
            val size = if (countdownSample().length > 5) 12f else if (ringD > 70f) 16f else 13f
            val boxW = ringD - 2 * ringW - 6f
            val boxH = size * 1.2f
            slot = countdown(cx - boxW / 2f, cy - boxH / 2f - 4f, boxW, boxH, CountdownLayout.INTER_TIGHT_CENTER, "%s", pal.text, size)
            p.text(if (isLesson) "осталось" else "до пары", cx, cy + boxH / 2f - 3f, small, pal.muted, Align.CENTER)
        } else if (f != null) {
            val t = Typo(AppFont.INTER_TIGHT, if (ringD > 70f) 18f else 15f, 600, tracking = -0.03f, lineHeight = 1.1f, tabular = true)
            p.text(fmt(f.startMinute), cx, cy - p.lineHeight(t) / 2f - 4f, t, pal.text, Align.CENTER)
            p.text(d.dayLabel.lowercase().substringBefore(','), cx, cy + p.lineHeight(t) / 2f - 4f, small, pal.muted, Align.CENTER)
        } else {
            p.text("—", cx, cy - 10f, Typo(AppFont.INTER_TIGHT, 18f, 600, lineHeight = 1.1f), pal.muted, Align.CENTER)
        }

        val x0 = l + ringD + 14f
        val colW = r - x0
        val status = listOfNotNull(d.status, f?.number?.let { "$it пара" }).joinToString(" · ")
        val labelLh = p.lineHeight(st.label)
        val bodyLh = p.lineHeight(st.body)
        val fitted = p.fit(d.title, st.heading, colW, ringD + 16f - labelLh - bodyLh)
        val colH = labelLh + 2f + fitted.height + 2f + bodyLh
        var ty = max(y, cy - colH / 2f)
        p.text(status, x0, ty, st.label, pal.muted, maxWidth = colW)
        ty += labelLh + 2f
        p.drawFitted(fitted, x0, ty, pal.text)
        ty += fitted.height + 2f
        val details = if (f == null) d.message ?: "" else roomAndType()
        p.text(details, x0, ty, st.body, pal.muted, maxWidth = colW)

        val metaLh = p.lineHeight(st.meta)
        val rowTop = mainBottom - metaLh
        if (rowTop - 11f > y + ringD + 4f) {
            p.rect(l, rowTop - 10f, r, rowTop - 9f, pal.container)
            val n = d.next
            if (n != null) {
                p.circle(l + 3f, rowTop + metaLh / 2f, 3f, pal.accent)
                var rx = r
                n.room?.let { rx -= p.text(it, rx, rowTop, st.meta, pal.secondary, Align.END) + 8f }
                rx -= p.text(fmt(n.startMinute), rx, rowTop, st.meta.weighted(500), pal.text, Align.END) + 8f
                val name = listOfNotNull(n.title, n.type).joinToString(" · ")
                p.text(name, l + 14f, rowTop, st.meta, pal.secondary, maxWidth = rx - l - 14f)
            } else {
                p.text(nextParts().first, l, rowTop, st.meta, pal.secondary, maxWidth = r - l)
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        return slot
    }

    // ---------- 4. Терминал ----------

    fun terminal(): CountdownSlot? {
        card(pal.surface, pal.border)
        val mono = st.label
        val big = st.heading
        val monoLh = p.lineHeight(mono)
        var y = top
        val cmd = when {
            d.phase == Phase.NO_DATA -> "sync"
            isLesson -> "now"
            upcoming -> "next"
            nextDay -> "next --day"
            else -> "now"
        }
        p.text("~/${d.group.lowercase()} \$ $cmd", l, y, mono, pal.muted, maxWidth = r - l)
        y += monoLh

        // ▸ Матанализ [пр]
        val tag = f?.typeShort?.let { " [$it]" } ?: ""
        val titleText = if (f == null) d.title.lowercase() else d.title
        val prefixW = p.width("▸ ", big)
        val tagW = if (tag.isNotEmpty()) p.width(tag, big.weighted(400)) else 0f
        var x = l
        x += p.text("▸ ", x, y, big, pal.text)
        x += p.text(titleText, x, y, big, pal.text, maxWidth = r - l - prefixW - tagW)
        if (tag.isNotEmpty()) p.text(tag, x, y, big.weighted(400), pal.secondary)
        y += p.lineHeight(big)

        val lastTop = mainBottom - monoLh
        // Детали
        if (y + monoLh <= lastTop) {
            val details = when {
                f == null -> (d.message ?: "").lowercase()
                nextDay -> listOfNotNull(d.dayLabel.lowercase(), range(f, "–"), f.room).joinToString(" · ")
                else -> listOfNotNull(f.room, range(f, "–")).joinToString(" · ")
            }
            p.text(details, l, y, mono, pal.secondary, maxWidth = r - l)
            y += monoLh
        }
        // Прогресс-бар из блоков или подпись
        if (y + monoLh <= lastTop) {
            val prog = d.progress
            if (prog != null) {
                val pct = " ${(prog * 100).roundToInt()}%"
                val cw = p.width("█", mono)
                val n = min(19, floor((r - l - p.width(" 100%", mono)) / cw).toInt()).coerceAtLeast(4)
                val filled = (prog * n).roundToInt().coerceIn(0, n)
                var bx = l
                bx += p.text("█".repeat(filled), bx, y, mono, pal.text)
                bx += p.text("█".repeat(n - filled), bx, y, mono, pal.track)
                p.text(pct, bx, y, mono, pal.secondary)
            } else if (nextDay && d.message != null) {
                p.text("# " + d.message!!.lowercase(), l, y, mono, pal.muted, maxWidth = r - l)
            }
        }

        // next → 12:00 Физика [лек] 1-224 ▍
        if (lastTop >= top + monoLh + p.lineHeight(big) - 1f) {
            val n = d.next
            val nextText = when {
                n != null -> "next → " + listOfNotNull(fmt(n.startMinute), n.title, n.typeShort?.let { "[$it]" }, n.room).joinToString(" ")
                upcoming && d.minutesLeft != null -> "через ${d.minutesLeft} мин"
                f == null -> "нажми, чтобы обновить".takeIf { d.phase == Phase.NO_DATA } ?: ""
                else -> "next → —"
            }
            val cursor = " ▍"
            val cursorW = p.width(cursor, mono)
            val w = p.text(nextText, l, lastTop, mono, pal.muted, maxWidth = r - l - cursorW)
            p.text(cursor, l + w, lastTop, mono, pal.secondary)
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        return null
    }

    // ---------- 5. Material You ----------

    fun tonal(): CountdownSlot? {
        card(pal.surface, pal.border)
        val gap = 7f
        val pillH = 34f
        val showPill = mainH >= 140f
        val innerL = pad.side
        val innerR = W - pad.side
        val innerT = pad.top
        val innerB = if (showPill) mainH - pad.bottom - pillH - gap else mainH - pad.bottom
        p.roundRect(innerL, innerT, innerR, innerB, 24f, pal.container)

        val il = innerL + 14f
        val ir = innerR - 14f
        val it = innerT + 12f
        val ib = innerB - 13f

        // Иконка в тональной плашке
        val iconBox = 36f
        p.roundRect(il, it, il + iconBox, it + iconBox, 12f, pal.accent)
        drawIcon(iconFor(), il + iconBox / 2f, it + iconBox / 2f, 18f, 0xFFFFFFFF.toInt())

        // Чип аудитории
        val chipT = st.label
        val room = f?.room
        var chipW = 0f
        if (room != null) {
            val maxChip = (ir - il) * 0.38f
            val text = p.ellipsize(room, chipT, maxChip - 20f)
            chipW = p.width(text, chipT) + 20f
            val chipH = p.lineHeight(chipT) + 8f
            val cy = it + (iconBox - chipH) / 2f
            p.roundRect(ir - chipW, cy, ir, cy + chipH, chipH / 2f, 0xFFFFFFFF.toInt())
            p.text(text, ir - chipW + 10f, cy + 4f, chipT, pal.secondary)
        }

        val colX = il + iconBox + 10f
        val colW = ir - (if (chipW > 0f) chipW + 8f else 0f) - colX
        val rowH = p.lineHeight(st.body)
        val subLh = p.lineHeight(st.body)
        val fitted = p.fit(d.title, st.heading, colW, ib - it - rowH - 6f - subLh)
        val colH = fitted.height + subLh
        var cy = it + max(0f, (iconBox - colH) / 2f)
        p.drawFitted(fitted, colX, cy, pal.onContainer)
        cy += fitted.height
        val sub = when {
            f == null -> d.message ?: ""
            nextDay -> listOfNotNull(f.type?.replaceFirstChar { c -> c.uppercase() }, "${d.dayLabel} в ${fmt(f.startMinute)}").joinToString(" · ")
            else -> listOfNotNull(f.type?.replaceFirstChar { c -> c.uppercase() } ?: d.status, until()).joinToString(" · ")
        }
        p.text(sub, colX, cy, st.body, pal.secondary, maxWidth = colW)

        // Прогресс и отсчёт
        var slot: CountdownSlot? = null
        val rowTop = ib - rowH
        if (rowTop > cy + subLh) {
            val cdT = st.body.weighted(600)
            if (d.progress != null || d.countdownTo != null) {
                val cdW = if (d.countdownTo != null) p.width(countdownSample(), cdT) + 4f else 0f
                val barR = if (cdW > 0f) ir - cdW - 10f else ir
                val by = rowTop + rowH / 2f - 4f
                p.roundRect(il, by, barR, by + 8f, 4f, pal.track)
                val fw = (barR - il) * (d.progress ?: 0f)
                if (fw > 0.5f) p.roundRect(il, by, il + max(fw, 8f), by + 8f, 4f, pal.accent)
                slot = countdown(ir - cdW, rowTop, cdW, rowH, CountdownLayout.ROBOTO_FLEX_END, "%s", pal.secondary, cdT.size)
            }
        }

        if (showPill) {
            val pt = mainH - pad.bottom - pillH
            p.roundRect(innerL, pt, innerR, pt + pillH, pillH / 2f, pal.tag1)
            val n = d.next
            val ty = pt + (pillH - p.lineHeight(st.meta)) / 2f
            if (n != null) {
                val rw = n.room?.let { p.text(it, innerR - 14f, ty, st.meta.weighted(400), pal.muted, Align.END) + 10f } ?: 0f
                p.text("${fmt(n.startMinute)} · ${n.title}", innerL + 14f, ty, st.meta, pal.text, maxWidth = innerR - innerL - 28f - rw)
            } else {
                p.text(nextParts().first, innerL + 14f, ty, st.meta.weighted(500), pal.muted, maxWidth = innerR - innerL - 28f)
            }
        }
        if (large) dayList(mainH + 4f, H - pad.bottom - 6f, left = innerL + 14f, right = innerR - 14f)
        return slot
    }

    fun iconFor(): Icon = when {
        d.phase == Phase.BREAK || d.phase == Phase.WINDOW -> Icon.COFFEE
        nextDay || f == null -> Icon.CALENDAR
        f.typeShort?.startsWith("лаб") == true -> Icon.FLASK
        f.typeShort?.startsWith("пр") == true -> Icon.PENCIL
        else -> Icon.BOOK
    }

    /** Иконки Lucide (ISC), сетка 24×24, обводка 2. */
    fun drawIcon(icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        p.canvas.withSave {
            translate(cx - size / 2f, cy - size / 2f)
            scale(size / 24f, size / 24f)
            p.stroke.shader = null
            p.stroke.color = color
            p.stroke.strokeWidth = 2f
            p.stroke.strokeCap = Paint.Cap.ROUND
            p.stroke.strokeJoin = Paint.Join.ROUND
            for (data in icon.paths) drawPath(PathParser.createPathFromPathData(data), p.stroke)
            p.stroke.strokeCap = Paint.Cap.BUTT
            p.stroke.strokeJoin = Paint.Join.MITER
        }
    }

    // ---------- 6. Тетрадь ----------

    fun paper(): CountdownSlot? {
        p.canvas.withSave {
            p.clipRound(0f, 0f, W, H, st.radius)
            p.rect(0f, 0f, W, H, pal.surface)
            var ly = 23f
            while (ly < H) {
                p.rect(0f, ly, W, ly + 1f, pal.track)
                ly += 24f
            }
            p.rect(28f, 0f, 29f, H, pal.accent)
        }
        pal.border?.let { p.roundRectStroke(0f, 0f, W, H, st.radius, it) }

        val left = 40f
        var y = top
        val headerLeft = when {
            f == null -> d.status
            isLesson -> "Сейчас · до ${fmt(f.endMinute)}"
            upcoming -> "${d.status} · в ${fmt(f.startMinute)}"
            else -> "${d.dayLabel} · в ${fmt(f.startMinute)}"
        }
        val num = f?.number?.let { "№ $it" }
        val nw = num?.let { p.text(it, r, y, st.label, pal.muted, Align.END) + 10f } ?: 0f
        p.text(headerLeft, left, y, st.label, pal.muted, maxWidth = r - left - nw)
        y += p.lineHeight(st.label) + 6f

        val bodyLh = p.lineHeight(st.body)
        val nextT = st.body.copy(size = 15f)
        val bottomLh = p.lineHeight(nextT)
        val avail = mainBottom - y
        val titleMin = p.lineHeight(st.heading) * 0.86f
        val showNext = avail >= titleMin + bodyLh + bottomLh + 4f
        val showDetails = avail >= titleMin + bodyLh + (if (showNext) bottomLh else 0f) + 2f
        val fitted = p.fit(d.title, st.heading, r - left, avail - (if (showNext) bottomLh else 0f) - (if (showDetails) bodyLh else 0f) - 4f)
        p.drawFitted(fitted, left, y, pal.text)
        y += fitted.height
        if (showDetails) {
            val details = when {
                f == null -> d.message ?: ""
                f.room != null && f.type != null -> "${f.type}, аудитория ${f.room}"
                f.room != null -> "аудитория ${f.room}"
                else -> f.type ?: ""
            }
            p.text(details, left, y, st.body, pal.secondary, maxWidth = r - left)
        }

        if (showNext) {
            val rowTop = mainBottom - bottomLh
            val base = p.baseline(rowTop, nextT)
            val n = d.next
            if (n != null) {
                var rx = r
                n.room?.let { rx -= p.textAtBaseline(it, rx, base, st.meta, pal.container, Align.END) + 10f }
                var x = left
                x += p.textAtBaseline("${fmt(n.startMinute)} — ", x, base, st.meta, pal.container)
                val name = p.ellipsize(n.title, nextT, rx - x)
                p.textAtBaseline(name, x, base, nextT, pal.text)
            } else {
                p.textAtBaseline(p.ellipsize(nextParts().first, nextT, r - left), left, base, nextT, pal.secondary)
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom, left = left)
        return null
    }

    // ---------- 7. Nothing ----------

    fun dots(): CountdownSlot? {
        card(pal.surface, pal.border)
        var y = top
        val labelT = st.label
        val lh = p.lineHeight(labelT)
        p.circle(l + 3.5f, y + lh / 2f, 3.5f, pal.accent)
        val status = if (nextDay) d.dayLabel else d.status
        val rangeText = f?.let { range(it, "—") }
        val rw = rangeText?.let { p.text(it, r, y, labelT.weighted(700), pal.muted, Align.END) + 10f } ?: 0f
        p.text(status, l + 13f, y, labelT, pal.text, maxWidth = r - l - 13f - rw)
        y += lh + 8f

        // Крупные точечные цифры: отсчёт, время начала или прочерк.
        val big = d.countdownTo != null
        val sample = if (big) countdownSample() else f?.let { fmt(it.startMinute) } ?: "--"
        val maxNumW = (r - l) * (if (narrow) 0.62f else 0.5f)
        var size = if (narrow) 34f else 44f
        val numT = Typo(AppFont.DOTO, size, 900, tracking = -0.03f, lineHeight = 1f, tabular = true)
        val wAt = p.width(sample, numT)
        if (wAt > maxNumW) size *= maxNumW / wAt
        val heroH = size * 1.0f
        val numW = p.width(sample, numT.sized(size)) + 4f
        var slot: CountdownSlot? = null
        if (big) {
            slot = countdown(l, y, numW, heroH, CountdownLayout.DOTO_START, "%s", pal.text, size)
        } else {
            p.text(sample, l, y, numT.sized(size), pal.text)
        }
        val colX = l + numW + 12f
        val colW = r - colX
        val subT = st.body
        val subLh = p.lineHeight(subT)
        val fitted = p.fit(d.title, st.heading, colW, heroH - subLh - 3f + 6f)
        val colH = fitted.height + 3f + subLh
        var cy = y + heroH - colH
        p.drawFitted(fitted, colX, cy, pal.text)
        cy += fitted.height + 3f
        val sub = when {
            f == null -> d.message ?: ""
            else -> listOfNotNull(f.typeShort, f.room).joinToString(" · ")
        }
        p.text(sub, colX, cy, subT, pal.muted, maxWidth = colW)

        val metaLh = p.lineHeight(st.meta)
        val bottomTop = mainBottom - metaLh
        val dotsY = bottomTop - 10f - 7f
        if (dotsY > y + heroH + 4f) {
            val count = 24
            val step = (r - l - 7f) / (count - 1)
            val prog = d.progress
            val current = prog?.let { floor(it * count).toInt().coerceIn(0, count - 1) } ?: -1
            for (i in 0 until count) {
                val c = when {
                    prog == null -> pal.track
                    i < current -> pal.text
                    i == current -> pal.accent
                    else -> pal.track
                }
                p.circle(l + 3.5f + i * step, dotsY + 3.5f, 3.5f, c)
            }
        }
        if (bottomTop > y + heroH + 2f) {
            val n = d.next
            if (n != null) {
                val right = listOfNotNull(fmt(n.startMinute), n.room).joinToString(" · ")
                val rw2 = p.text(right, r, bottomTop, st.meta, pal.muted, Align.END) + 10f
                var x = l
                x += p.text("Далее", x, bottomTop, st.meta.weighted(700), pal.accent)
                p.text(" ${n.title}", x, bottomTop, st.meta, pal.text, maxWidth = r - x - rw2)
            } else {
                p.text(nextParts().first, l, bottomTop, st.meta, pal.muted, maxWidth = r - l)
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        return slot
    }

    // ---------- 8. Win95 ----------

    fun bevel(l: Float, t: Float, r: Float, b: Float, raised: Boolean, thick: Boolean = true) {
        val light = if (raised) 0xFFFFFFFF.toInt() else 0xFF808080.toInt()
        val dark = if (raised) 0xFF808080.toInt() else 0xFFFFFFFF.toInt()
        val outerLight = if (raised) 0xFFDFDFDF.toInt() else 0xFF0A0A0A.toInt()
        val outerDark = if (raised) 0xFF0A0A0A.toInt() else 0xFFDFDFDF.toInt()
        if (thick) {
            p.rect(l, t, r, t + 2f, light)
            p.rect(l, t, l + 2f, b, light)
            p.rect(l, b - 2f, r, b, dark)
            p.rect(r - 2f, t, r, b, dark)
            p.rect(l, t, r, t + 1f, outerLight)
            p.rect(l, t, l + 1f, b, outerLight)
            p.rect(l, b - 1f, r, b, outerDark)
            p.rect(r - 1f, t, r, b, outerDark)
        } else {
            p.rect(l, t, r, t + 1f, light)
            p.rect(l, t, l + 1f, b, light)
            p.rect(l, b - 1f, r, b, dark)
            p.rect(r - 1f, t, r, b, dark)
        }
    }

    fun button(text: String, rRight: Float, t: Float, typo: Typo, minW: Float = 0f): Float {
        val w = max(minW, p.width(text, typo) + 20f)
        val h = p.lineHeight(typo) + 4f
        p.rect(rRight - w, t, rRight, t + h, pal.surface)
        bevel(rRight - w, t, rRight, t + h, raised = true)
        p.text(text, rRight - w / 2f, t + 2f, typo, pal.text, Align.CENTER)
        return w
    }

    fun retro(): CountdownSlot? {
        p.rect(0f, 0f, W, H, pal.surface)
        bevel(0f, 0f, W, H, raised = true)
        val small = st.body
        val bold = st.label

        // Заголовок окна
        val tbT = 3f
        val tbB = 21f
        p.fill.shader = LinearGradient(3f, 0f, W - 3f, 0f, pal.accent, pal.container, Shader.TileMode.CLAMP)
        p.canvas.drawRect(3f, tbT, W - 3f, tbB, p.fill)
        p.fill.shader = null
        pixelIcon(CALENDAR_PIXELS, 6f, tbT + 2f, 14f)
        val btnW = 16f
        val btnH = 14f
        val bTop = tbT + 2f
        val closeR = W - 3f - 2f
        val minR = closeR - btnW - 2f
        for ((right, label) in listOf(closeR to "×", minR to "_")) {
            p.rect(right - btnW, bTop, right, bTop + btnH, pal.surface)
            bevel(right - btnW, bTop, right, bTop + btnH, raised = true, thick = false)
            p.text(label, right - btnW / 2f, bTop + (btnH - p.lineHeight(bold.sized(10f))) / 2f, bold.sized(10f), pal.text, Align.CENTER)
        }
        val windowTitle = when (d.phase) {
            Phase.LESSON -> "Идёт пара"
            Phase.BREAK -> "Перемена"
            Phase.WINDOW -> "Окно"
            Phase.BEFORE_FIRST -> "Скоро пара"
            Phase.DAY_OVER -> "Пары закончились"
            Phase.DAY_OFF -> "Выходной"
            Phase.NO_LESSONS -> "Пар нет"
            Phase.NO_DATA -> "Нет данных"
        } + " — " + d.group
        p.text(windowTitle, 6f + 14f + 4f, tbT + (18f - p.lineHeight(bold)) / 2f, bold, 0xFFFFFFFF.toInt(), maxWidth = minR - btnW - 4f - 24f)

        // Меню
        var mx = 3f + 4f
        val my = tbB + 2f
        if (!narrow || W > 160f) {
            for (item in listOf("Файл", "Вид", "Неделя", "?")) {
                val w = p.text(item, mx, my, small, pal.text)
                val first = p.width(item.take(1), small)
                val base = p.baseline(my, small)
                p.line(mx, base + 1.5f, mx + first, base + 1.5f, pal.text, 1f)
                mx += w + 10f
                if (mx > W - 30f) break
            }
        }
        val menuB = my + p.lineHeight(small) + 2f

        // Тело окна
        val bl = 3f + 8f
        val br = W - 3f - 8f
        var y = menuB + 4f
        val iconSize = if (narrow) 24f else 32f
        pixelIcon(DOC_PIXELS, bl, y, iconSize)
        val cx = bl + iconSize + 10f
        val titleT = st.heading
        val typeText = f?.type?.let { " ($it)" } ?: ""
        val typeW = if (typeText.isNotEmpty()) p.width(typeText, small) else 0f
        val tw = p.text(d.title, cx, y, titleT, pal.text, maxWidth = br - cx - min(typeW, (br - cx) * 0.4f))
        if (typeText.isNotEmpty()) {
            p.text(typeText, cx + tw, y + (p.lineHeight(titleT) - p.lineHeight(small)) / 2f + 1f, small, pal.text, maxWidth = br - cx - tw)
        }
        val line2 = when {
            f == null -> d.message ?: ""
            nextDay -> listOfNotNull(f.room?.let { "Ауд. $it" }, "${d.dayLabel} · ${range(f, " → ")}").joinToString(" · ")
            else -> listOfNotNull(f.room?.let { "Ауд. $it" }, range(f, " → ")).joinToString(" · ")
        }
        p.text(line2, cx, y + p.lineHeight(titleT) + 1f, small, pal.text, maxWidth = br - cx)
        y += iconSize + 6f

        val statusH = 16f
        val statusT = H - 3f - 6f - statusH
        // Полоса прогресса
        val prog = d.progress
        if (prog != null && y + 16f < statusT - 4f) {
            p.rect(bl, y, br, y + 16f, pal.track)
            bevel(bl, y, br, y + 16f, raised = false)
            val il = bl + 2f
            val ir = br - 2f
            val fillR = il + (ir - il) * prog
            var sx = il + 1f
            while (sx < fillR - 0.5f) {
                p.rect(sx, y + 3f, min(sx + 8f, fillR), y + 13f, pal.accent)
                sx += 10f
            }
            y += 16f + 6f
        }
        // Текст и кнопка
        val rowH = p.lineHeight(small) + 4f
        if (y + rowH < statusT - 2f) {
            val btnText = when (d.phase) {
                Phase.LESSON -> "Прогулять"
                Phase.NO_DATA -> "Обновить"
                else -> "ОК"
            }
            val bw = button(btnText, br, y, small)
            val rowText = when {
                isLesson && d.minutesLeft != null -> "Осталось примерно ${d.minutesLeft} мин"
                upcoming && d.minutesLeft != null -> "До начала примерно ${d.minutesLeft} мин"
                nextDay && f != null -> "${d.dayLabel} в ${fmt(f.startMinute)}"
                else -> d.message ?: ""
            }
            p.text(rowText, bl, y + 2f, small, pal.text, maxWidth = br - bl - bw - 8f)
            y += rowH + 6f
        }
        if (large) dayList(y, statusT - 6f, left = bl, right = br, text = pal.text, muted = 0xFF404040.toInt(), accent = pal.accent)

        // Строка состояния
        p.rect(bl, statusT, br, statusT + statusH, pal.surface)
        bevel(bl, statusT, br, statusT + statusH, raised = false, thick = false)
        val n = d.next
        val statusText = when {
            n != null -> "Далее: " + listOfNotNull(n.title, fmt(n.startMinute), n.room).joinToString(" · ")
            else -> nextParts().first
        }
        p.text(statusText, bl + 4f, statusT + (statusH - p.lineHeight(small)) / 2f, small, pal.text, maxWidth = br - bl - 8f)
        return null
    }

    /** Пиксельная иконка: прямоугольники в сетке 16×16 (как в макете). */
    fun pixelIcon(rects: List<IntArray>, x: Float, y: Float, size: Float) {
        val k = size / 16f
        for (rc in rects) {
            p.rect(x + rc[0] * k, y + rc[1] * k, x + (rc[0] + rc[2]) * k, y + (rc[1] + rc[3]) * k, rc[4])
        }
    }

    // ---------- 9. НЭТИ ----------

    fun tags(): CountdownSlot? {
        card(pal.surface, pal.border)
        var y = top
        val badgeT = st.label
        val badge = d.week?.let { "$it учебная неделя" } ?: d.dayLabel
        val badgeH = p.lineHeight(badgeT) + 6f
        val groupT = Typo(AppFont.MONTSERRAT, 11f, 600)
        val gw = p.text(d.group, r, y + (badgeH - p.lineHeight(groupT)) / 2f, groupT, pal.text, Align.END, underline = true) + 10f
        val bt = p.ellipsize(badge, badgeT, r - l - gw - 14f)
        val bw = p.width(bt, badgeT) + 14f
        p.rect(l, y, l + bw, y + badgeH, pal.container)
        p.text(bt, l + 7f, y + 3f, badgeT, 0xFFFFFFFF.toInt())
        y += badgeH + 8f

        // Название и минуты справа
        val cdT = CountdownLayout.MONTSERRAT_END.typo(if (countdownSample().length > 5) 18f else 22f)
        val rightText: String? = when {
            d.countdownTo != null -> countdownSample()
            nextDay && f != null -> fmt(f.startMinute)
            else -> null
        }
        val rightW = rightText?.let { p.width(it, cdT) + 4f } ?: 0f
        val tagsH = p.lineHeight(st.body) + 6f
        val metaLh = p.lineHeight(st.meta)
        val bottomBlock = 5f + 9f + 2f + 6f + metaLh
        val showBottom = mainBottom - y >= p.lineHeight(st.heading) * 0.86f + 8f + tagsH + bottomBlock
        val titleMaxH = mainBottom - y - (if (showBottom) bottomBlock + 6f else 0f) - 8f - tagsH
        val fitted = p.fit(d.title, st.heading, r - l - rightW - 12f, titleMaxH)
        p.drawFitted(fitted, l, y, pal.text)
        var slot: CountdownSlot? = null
        val rowH = max(fitted.height, cdT.size * 1.0f)
        if (rightText != null) {
            val cy = y + fitted.height - cdT.size * 1.0f
            if (d.countdownTo != null) {
                slot = countdown(r - rightW, cy, rightW, cdT.size * 1.0f, CountdownLayout.MONTSERRAT_END, "%s", pal.text, cdT.size)
            } else {
                p.text(rightText, r, cy, cdT, pal.text, Align.END)
            }
        }
        y += rowH + 8f

        // Теги
        val tags = buildList {
            f?.type?.let { add(it to pal.tag1) }
            f?.room?.let { add("Ауд. $it" to pal.tag2) }
            when {
                f == null -> d.message?.let { add(it to pal.tag3) }
                nextDay -> add("${d.dayLabel} в ${fmt(f.startMinute)}" to pal.tag3)
                else -> until()?.let { add(it to pal.tag3) }
            }
        }
        var tx = l
        for ((text, color) in tags) {
            val w = p.width(text, st.body) + 12f
            if (tx + w > r) break
            p.rect(tx, y, tx + w, y + tagsH, color)
            p.text(text, tx + 6f, y + 3f, st.body, 0xFFFFFFFF.toInt())
            tx += w + 4f
        }

        if (showBottom) {
            val rowTop = mainBottom - metaLh
            val lineTop = rowTop - 6f - 2f
            p.rect(l, lineTop, r, lineTop + 2f, pal.text)
            val prog = d.progress
            if (prog != null) {
                val barTop = lineTop - 9f - 5f
                p.rect(l, barTop, r, barTop + 5f, pal.track)
                p.rect(l, barTop, l + (r - l) * prog, barTop + 5f, pal.accent)
            }
            val n = d.next
            if (n != null) {
                val right = listOfNotNull(fmt(n.startMinute), n.room).joinToString(" | ")
                val rw = p.text(right, r, rowTop, st.meta, pal.text, Align.END) + 10f
                p.text("Далее · ${n.title}", l, rowTop, st.meta, pal.text, maxWidth = r - l - rw)
            } else {
                p.text(nextParts().first, l, rowTop, st.meta, pal.text, maxWidth = r - l)
            }
        }
        if (large) dayList(mainH + 2f, H - pad.bottom)
        return slot
    }
}

enum class Icon(val paths: List<String>) {
    BOOK(listOf("M4 19.5A2.5 2.5 0 0 1 6.5 17H20", "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z")),
    PENCIL(listOf("M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z", "m15 5 4 4")),
    FLASK(
        listOf(
            "M10 2v7.527a2 2 0 0 1-.211.896L4.72 20.55a1 1 0 0 0 .9 1.45h12.76a1 1 0 0 0 .9-1.45l-5.069-10.127A2 2 0 0 1 14 9.527V2",
            "M8.5 2h7",
            "M7 16h10",
        ),
    ),
    COFFEE(
        listOf(
            "M10 2v2", "M14 2v2", "M6 2v2",
            "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
        ),
    ),
    CALENDAR(
        listOf(
            "M8 2v4", "M16 2v4",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M3 10h18",
        ),
    ),
}

private fun px(x: Int, y: Int, w: Int, h: Int, color: Long) = intArrayOf(x, y, w, h, color.toInt())

/** Иконка в заголовке окна Win95 — из макета. */
private val CALENDAR_PIXELS = listOf(
    px(1, 2, 14, 13, 0xFF000000), px(2, 3, 12, 11, 0xFFFFFFFF), px(2, 3, 12, 3, 0xFFC00000),
    px(4, 8, 2, 2, 0xFF808080), px(7, 8, 2, 2, 0xFF808080), px(10, 8, 2, 2, 0xFF000080), px(4, 11, 2, 2, 0xFF808080),
)

/** Иконка документа в окне Win95 — из макета. */
private val DOC_PIXELS = listOf(
    px(2, 1, 11, 14, 0xFF000000), px(3, 2, 9, 12, 0xFFFFFFFF), px(3, 2, 2, 12, 0xFF000080),
    px(6, 4, 5, 1, 0xFF808080), px(6, 6, 5, 1, 0xFF808080), px(6, 8, 3, 1, 0xFF808080), px(13, 3, 1, 11, 0xFF808080),
)
