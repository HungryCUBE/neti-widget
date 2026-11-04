package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Backgrounds
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.icon
import kotlin.math.min

/**
 * Заглушка вместо расписания, пока у стиля нет нужного (например, своего фото): по центру круг с плюсом,
 * под ним заголовок и подсказка. Включается вариантом photo:none.
 * Подложка круга — surfaces.plus, иконка — слот plus (по умолчанию встроенная plus), цвет иконки — icons.color.
 * Роли: title — заголовок, details — подсказка. strings: placeholderTitle, placeholderHint.
 * metrics: plusSize 44, plusIcon 20, plusGap 12, hintGap 2. Если места мало — всё уменьшается вместе, подсказка пропадает первой.
 */
internal fun Frame.placeholder() {
    card()
    val title = role("title")
    val details = role("details")
    val titleText = sv("placeholderTitle")
    val hintText = s("placeholderHint")
    val plus = m("plusSize", 44f)
    val gap = m("plusGap", 12f)
    val hintGap = m("hintGap", 2f)
    val titleLh = p.lineHeight(title.typo)
    val hintLh = p.lineHeight(details.typo)
    val avail = H - pad.top - pad.bottom
    val full = plus + gap + titleLh + (if (hintText != null) hintGap + hintLh else 0f)
    val showHint = hintText != null && full <= avail
    val need = plus + gap + titleLh + (if (showHint) hintGap + hintLh else 0f)
    val k = min(1f, avail / need)
    val size = plus * k
    val titleT = title.typo.sized(title.typo.size * k)
    val width = r - l
    var y = pad.top + (avail - need * k) / 2f
    val cx = W / 2f
    th.surface("plus")?.let { Backgrounds.draw(p, it, cx - size / 2f, y, cx + size / 2f, y + size, mode) }
    val iconSize = m("plusIcon", 20f) * k
    icon(th.icons.slots["plus"] ?: "plus", cx - iconSize / 2f, y + (size - iconSize) / 2f, iconSize)
    y += size + gap * k
    p.text(titleText, cx, y, titleT, title.color, Align.CENTER, maxWidth = width)
    y += titleLh * k
    if (showHint) p.text(hintText!!, cx, y + hintGap * k, details.typo.sized(details.typo.size * k), details.color, Align.CENTER, maxWidth = width)
}
