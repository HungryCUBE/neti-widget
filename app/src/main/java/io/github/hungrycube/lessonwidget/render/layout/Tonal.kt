package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.icon
import io.github.hungrycube.lessonwidget.render.lessonIcon
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight
import kotlin.math.max

/**
 * Тональный контейнер Material 3: иконка, название, чип аудитории, толстый прогресс, пилюля следующей пары.
 * Цвета: container, iconBg, chip, pill. Роли: chip, nextAside (аудитория в пилюле), nextNone.
 * metrics: gap, pillHeight, containerRadius, innerPadX, innerPadTop, innerPadBottom, iconBox, iconRadius,
 * chipPadX, chipPadY, chipMax, pillPadX. Флаг chip — показывать чип аудитории. Пилюля прячется на низком виджете.
 */
internal fun Frame.tonal() {
    card()
    val gap = m("gap", 7f)
    val pillH = m("pillHeight", 34f)
    val showPill = !short
    val innerL = pad.side
    val innerR = W - pad.side
    val innerT = pad.top
    val innerB = if (showPill) mainH - pad.bottom - pillH - gap else mainH - pad.bottom
    p.roundRect(innerL, innerT, innerR, innerB, m("containerRadius", 24f), c("container"))

    val padX = m("innerPadX", 14f)
    val il = innerL + padX
    val ir = innerR - padX
    val it = innerT + m("innerPadTop", 12f)
    val ib = innerB - m("innerPadBottom", 13f)

    // Иконка в тональной плашке
    val iconBox = m("iconBox", 36f)
    p.roundRect(il, it, il + iconBox, it + iconBox, m("iconRadius", 12f), c("iconBg"))
    val iconSize = iconBox / 2f
    val icx = il + iconBox / 2f
    val icy = it + iconBox / 2f
    icon(lessonIcon(), icx - iconSize / 2f, icy - iconSize / 2f, iconSize)

    // Чип аудитории (в узком виджете аудитория уходит в подпись)
    val chip = role("chip", "header")
    val chipT = chip.typo
    val room = f?.room
    var chipW = 0f
    if (room != null && flag("chip")) {
        val chipPadX = m("chipPadX", 10f)
        val chipPadY = m("chipPadY", 4f)
        val maxChip = (ir - il) * m("chipMax", 0.38f)
        val text = p.ellipsize(room, chipT, maxChip - chipPadX * 2f)
        chipW = p.width(text, chipT) + chipPadX * 2f
        val chipH = p.lineHeight(chipT) + chipPadY * 2f
        val cy = it + (iconBox - chipH) / 2f
        p.roundRect(ir - chipW, cy, ir, cy + chipH, chipH / 2f, c("chip"))
        p.text(text, ir - chipW + chipPadX, cy + chipPadY, chipT, chip.color)
    }

    val title = role("title")
    val details = role("details")
    val colX = il + iconBox + 10f
    val colW = ir - (if (chipW > 0f) chipW + 8f else 0f) - colX
    val rowH = p.lineHeight(details.typo)
    val subLh = p.lineHeight(details.typo)
    val fitted = p.fit(d.title, title.typo, colW, ib - it - rowH - 6f - subLh)
    val colH = fitted.height + subLh
    var cy = it + max(0f, (iconBox - colH) / 2f)
    p.drawFitted(fitted, colX, cy, title.color)
    cy += fitted.height
    val sub = when {
        f == null -> sv("detailsNone")
        nextDay -> sv("subNextDay")
        else -> sv("sub")
    }
    p.text(sub, colX, cy, details.typo, details.color, maxWidth = colW)

    // Прогресс и отсчёт
    val rowTop = ib - rowH
    if (rowTop > cy + subLh) {
        val counter = role("counter")
        val cdT = details.typo.weighted(counter.typo.weight)
        if (d.progress != null || d.countdownTo != null) {
            val cdW = if (d.countdownTo != null) p.width(counterText(), cdT) + 4f else 0f
            val barR = if (cdW > 0f) ir - cdW - 10f else ir
            val by = rowTop + rowH / 2f - progressHeight / 2f
            progress(il, by, barR, d.progress ?: 0f)
            countdown(ir - cdW, rowTop, cdW, rowH, counterText(), cdT.size)
        }
    }

    if (showPill) {
        val pt = mainH - pad.bottom - pillH
        p.roundRect(innerL, pt, innerR, pt + pillH, pillH / 2f, c("pill"))
        val next = role("next")
        val n = d.next
        val pillPad = m("pillPadX", 14f)
        val ty = pt + (pillH - p.lineHeight(next.typo)) / 2f
        if (n != null) {
            val aside = role("nextAside")
            val room = n.room?.takeIf { !flag("hidePillRoom") }
            val rw = room?.let { p.text(it, innerR - pillPad, ty, aside.typo, aside.color, Align.END) + 10f } ?: 0f
            p.text(sv("pillText"), innerL + pillPad, ty, next.typo, next.color, maxWidth = innerR - innerL - pillPad * 2f - rw)
        } else {
            val none = role("nextNone")
            p.text(nextParts().first, innerL + pillPad, ty, none.typo, none.color, maxWidth = innerR - innerL - pillPad * 2f)
        }
    }
    if (large) agenda(mainH + 4f, H - pad.bottom - 6f, left = innerL + padX, right = innerR - padX)
}
