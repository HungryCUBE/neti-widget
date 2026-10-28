package io.github.hungrycube.lessonwidget.render

import io.github.hungrycube.lessonwidget.render.theme.AdaptSpec
import kotlin.math.max
import kotlin.math.min

/**
 * Как виджет выглядит при данном размере. Раскладка рисует в «виртуальных» dp ([width]×[height]),
 * а холст увеличен в [scale] раз — так шрифты, отступы, скругления, кольца, точки и иконки растут вместе
 * с виджетом во всех стилях сразу.
 */
data class SizeClass(
    val scale: Float,
    val width: Float,
    val height: Float,
    /** Компактный вид (2×2): раскладки показывают меньше деталей. */
    val compact: Boolean,
    /** Низкий виджет: второстепенные строки убираются. */
    val short: Boolean,
    /** Высокий виджет: сверху карточка высотой [mainHeight], под ней список пар дня. */
    val large: Boolean,
    val mainHeight: Float,
) {
    /** Формы для вариантов стиля (variants.short / compact / large). */
    val forms: List<String> get() = listOfNotNull("short".takeIf { short }, "compact".takeIf { compact }, "large".takeIf { large })
}

/**
 * Один механизм адаптивности для всех стилей.
 *
 * 1. Масштаб. Стиль описан для 4×2 (316×168 dp) и для 2×2 (150×150). Если виджет больше, весь рисунок
 *    увеличивается так, чтобы по ширине оставалось не меньше [AdaptSpec.minWidth] (или compactWidth),
 *    а по высоте влезала карточка — но не больше maxScale. На стандартном 4×2 масштаб ровно 1.
 *    Если виджет уже 2×2, рисунок слегка ужимается (не меньше minScale), дальше раскладки убирают детали.
 * 2. Форма по виртуальному размеру: compact (узкий), short (низкий), large (высокий, со списком пар).
 * 3. Лишнее место на высоком виджете забирает список пар дня (Agenda): строки крупнее и подробнее.
 */
object Adaptive {
    val DEFAULT = AdaptSpec()

    fun classify(widthDp: Float, heightDp: Float, a: AdaptSpec, sizes: Set<String>): SizeClass {
        // Размера ещё нет (виджет только что добавили) — стандартный вид.
        val sized = widthDp > 0f && heightDp > 0f
        val w = if (sized) widthDp else a.baseWidth
        val h = if (sized) heightDp else a.baseHeight
        val canCompact = "compact" in sizes
        val canLarge = "large" in sizes
        val s = if (w >= a.compactBelow || !canCompact) {
            val up = min(w / a.minWidth, h / a.baseHeight)
            when {
                up > 1f -> min(up, a.maxScale)
                // Стиль без компактного вида: ужимаем обычный, чтобы он поместился по ширине.
                w < a.compactBelow -> max(w / a.compactBelow, a.minScale)
                else -> 1f
            }
        } else {
            val up = min(w / a.compactWidth, h / a.compactHeight)
            when {
                up > 1f -> min(up, a.maxScale)
                w < a.compactWidth -> max(w / a.compactWidth, a.minScale)
                else -> 1f
            }
        }
        val vw = w / s
        val vh = h / s
        val large = canLarge && vh >= a.largeFrom
        val mainH = if (large) a.mainHeight else vh
        return SizeClass(
            scale = s,
            width = vw,
            height = vh,
            compact = canCompact && vw < a.compactBelow,
            short = mainH < a.shortBelow,
            large = large,
            mainHeight = mainH,
        )
    }
}
