package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.SlotInfo
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.display.withVisibleCountdown
import io.github.hungrycube.lessonwidget.core.time.formatMinute
import io.github.hungrycube.lessonwidget.render.layout.Layouts
import io.github.hungrycube.lessonwidget.render.theme.ColorSources
import io.github.hungrycube.lessonwidget.render.theme.Role
import io.github.hungrycube.lessonwidget.render.theme.TextRenderer
import io.github.hungrycube.lessonwidget.render.theme.Theme
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class RenderMode {
    /** Для виджета: фон прозрачный (под карточкой — обои). */
    WIDGET,
    /** Для превью в приложении и скриншотов: с фоном-подложкой, отсчёт нарисован в картинке. */
    PREVIEW,
}

class Rendered(val bitmap: Bitmap, val description: String)

/** Что у виджета своё, кроме стиля: значения настроек стиля (options) и своё фото. */
class StyleCustom(val options: Map<String, String> = emptyMap(), val photo: PhotoInput? = null)

/** Рисует виджет целиком в Bitmap. Один код и для рабочего стола, и для превью. */
class WidgetRenderer(private val context: Context) {

    companion object {
        const val MAX_PIXELS = 1_500_000f
    }

    fun render(
        style: WidgetStyle,
        display: WidgetDisplay,
        widthDp: Float,
        heightDp: Float,
        mode: RenderMode,
        nowMillis: Long = System.currentTimeMillis(),
        density: Float = context.resources.displayMetrics.density,
        /** Цвета из обоев и Material You; false — палитра из файла стиля. */
        dynamicColor: Boolean = true,
        /** Предел размера картинки в пикселях: у виджетов есть лимит на память под картинки. */
        maxPixels: Float = MAX_PIXELS,
        /** Настройки стиля и своё фото этого виджета. */
        custom: StyleCustom? = null,
        /** Подмена цветов обоев (primary, secondary, tertiary) — для скриншотов на разных обоях. */
        wallpaper: List<Int?>? = null,
    ): Rendered {
        // Размера нет (виджет только что добавили) — стандартный 4×2.
        val sized = widthDp > 0f && heightDp > 0f
        val w = if (sized) widthDp.coerceAtLeast(80f) else style.adapt.baseWidth
        val h = if (sized) heightDp.coerceAtLeast(80f) else style.adapt.baseHeight
        val margin = if (mode == RenderMode.PREVIEW) 20f else 0f
        val totalW = w + 2 * margin
        val totalH = h + 2 * margin

        // Плотность пикселей ограничена: у виджетов есть лимит на размер картинок.
        var wanted = min(density, 3f)
        if (totalW * totalH * wanted * wanted > maxPixels) wanted = sqrt(maxPixels / (totalW * totalH))
        val (bitmap, scale) = allocate(totalW, totalH, wanted)

        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val painter = Painter(context, canvas, bitmap)
        // Прозрачный отступ на рабочем столе (Android 12+ скругляет углы виджетов — Win95 отодвигаем от краёв).
        val inset = if (mode == RenderMode.WIDGET) style.adapt.widgetInset else 0f
        val size = Adaptive.classify(w - 2 * inset, h - 2 * inset, style.adapt, style.sizes)
        val photo = custom?.photo
        val sources = ColorSources(context, dynamicColor, photo, size.width / size.height, wallpaper)
        val theme = style.theme(size.forms, sources, widget = mode == RenderMode.WIDGET, custom?.options.orEmpty(), photo)
        if (mode == RenderMode.PREVIEW) theme.backdrop?.let { Backgrounds.draw(painter, it, 0f, 0f, totalW, totalH, mode) }
        canvas.translate(margin + inset, margin + inset)
        if (size.scale != 1f) canvas.scale(size.scale, size.scale)
        Frame(painter, theme, display, size.width, size.height, mode, nowMillis, size).draw()
        return Rendered(bitmap, describe(display))
    }

    /** Картинка нужного размера; если памяти не хватает — с меньшим разрешением, а не падение. */
    private fun allocate(w: Float, h: Float, scale: Float): Pair<Bitmap, Float> {
        var s = scale
        while (true) {
            try {
                val bitmap = Bitmap.createBitmap(ceil(w * s).toInt().coerceAtLeast(1), ceil(h * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                return bitmap to s
            } catch (e: OutOfMemoryError) {
                if (s <= 0.5f) throw e
                s *= 0.7f
            }
        }
    }

    private fun describe(d: WidgetDisplay): String =
        listOfNotNull(d.headline, d.title, d.focus?.room?.let { "аудитория $it" }, d.message).joinToString(". ")
}

/**
 * Отрисовка одного виджета. Все размеры — в виртуальных dp ([W]×[H], см. [SizeClass]).
 * Здесь — общее для всех раскладок: состояние, тексты из шаблонов стиля, роли и числа стиля.
 * Сами раскладки — в пакете layout, элементы (фон, прогресс, отсчёт, список пар) — в Elements.kt.
 */
class Frame internal constructor(
    val p: Painter,
    val th: Theme,
    display: WidgetDisplay,
    val W: Float,
    val H: Float,
    val mode: RenderMode,
    val now: Long,
    val size: SizeClass,
) {
    // Отсчёт показываем, только если до события не больше 90 минут, иначе — время начала.
    val d: WidgetDisplay = display.withVisibleCountdown()
    val pad = th.spacing

    /** Большой виджет (4×4): сверху обычная карточка, ниже список пар дня. */
    val large = size.large
    val narrow = size.compact
    val short = size.short
    val mainH = if (large) size.mainHeight else H
    val l = pad.side
    val r = W - pad.side
    val top = pad.top
    val mainBottom = mainH - pad.bottom

    val f: SlotInfo? = d.focus
    val isLesson = d.phase == Phase.LESSON
    val upcoming = d.phase == Phase.BREAK || d.phase == Phase.WINDOW || d.phase == Phase.BEFORE_FIRST
    val nextDay = d.phase == Phase.DAY_OVER || d.phase == Phase.DAY_OFF

    private val texts = TextRenderer(th.strings, ::variable)

    fun draw() = Layouts.draw(th.layout, this)

    // ---------- Значения стиля ----------

    /** Число раскладки из metrics или значение по умолчанию. */
    fun m(name: String, default: Float): Float = th.metric(name, default)

    fun c(name: String): Int = th.palette[name]

    fun flag(name: String): Boolean = th.flag(name)

    fun role(name: String, fallback: String = "next"): Role = th.role(name, fallback)

    /** Текст по шаблону из strings; null — если для шаблона нет данных. */
    fun s(key: String, vararg extra: Pair<String, String?>): String? = texts.get(key, extra.toMap())

    fun sv(key: String, vararg extra: Pair<String, String?>): String = s(key, *extra) ?: ""

    // ---------- Общие тексты ----------

    fun fmt(minute: Int) = formatMinute(minute)

    private fun variable(name: String): String? = when (name) {
        "group" -> d.group
        "week" -> d.week?.toString()
        "status" -> d.status
        "headline" -> d.headline
        "title" -> d.title
        "dayLabel" -> d.dayLabel
        "message" -> d.message
        "num" -> f?.number?.toString()
        "num2" -> f?.number?.let { "%02d".format(it) }
        "numLabel" -> s("lessonNumber")
        "type" -> f?.type
        "typeShort" -> f?.typeShort
        "room" -> f?.room
        "subgroup" -> f?.subgroupNote
        "start" -> f?.let { fmt(it.startMinute) }
        "end" -> f?.let { fmt(it.endMinute) }
        "until" -> f?.let { s(if (isLesson) "untilLesson" else "untilUpcoming") }
        "next" -> d.next?.title
        "nextStart" -> d.next?.let { fmt(it.startMinute) }
        "nextEnd" -> d.next?.let { fmt(it.endMinute) }
        "nextRoom" -> d.next?.room
        "nextType" -> d.next?.type
        "nextTypeShort" -> d.next?.typeShort
        "nextNum" -> d.next?.number?.toString()
        "m" -> "${d.minutesLeft ?: 0}"
        "minutes" -> s("minutes")
        "prefix" -> s(if (isLesson) "prefixLesson" else "prefixUpcoming")
        "duration" -> d.minutesLeft?.let { duration(it) }
        "percent" -> d.progress?.let { "${(it * 100).roundToInt()}" }
        "cdUnit" -> if (d.countdownTo != null) s("unitLabel") else null
        "phaseTitle" -> s("phase" + d.phase.name.split('_').joinToString("") { w -> w.lowercase().replaceFirstChar { it.uppercase() } })
        else -> null
    }

    /** Текст отсчёта по шаблону стиля counterFormat: «34 мин», «ещё 34 мин», «34». */
    fun counterText(): String = sv("counterFormat")

    /** Левая и правая части строки «Далее». В пустых состояниях — группа и неделя. */
    fun nextParts(): Pair<String, String> {
        val n = d.next
        return when {
            n != null -> sv("nextLeft") to sv("nextRight")
            f == null -> d.group.takeIf { it != "—" }.orEmpty() to (s("weekLabel") ?: "")
            nextDay -> (d.message ?: "") to ""
            else -> sv("noMore") to ""
        }
    }

    /** Строка деталей под названием: для пустых состояний, следующего дня и обычная. */
    fun detailsText(): String = when {
        f == null -> sv("detailsNone")
        nextDay -> sv("detailsNextDay")
        else -> sv("details")
    }

    /** 125 → «2 ч 05 мин». */
    fun duration(minutes: Int): String =
        if (minutes >= 60) sv("durationLong", "h" to "${minutes / 60}", "mm" to "%02d".format(minutes % 60))
        else sv("durationShort", "min" to "$minutes")

    /** Короткая метка состояния для узких виджетов: «3 пара», «Перемена», «Завтра». */
    fun shortHeadline(): String = when {
        isLesson -> s("lessonNumber") ?: d.status
        nextDay -> d.dayLabel
        else -> d.status
    }
}
