package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight

/**
 * Крупные цифры (отсчёт или время начала), название рядом, ряд точек прогресса, «Далее».
 * Роли: hero — цифры без отсчёта, nextLabel — слово «Далее». Цвет statusDot — точка в шапке.
 * metrics: statusDot (радиус), heroWidth (доля ширины под цифры). Флаги: stack — название под цифрами,
 * nextShort — в строке «Далее» только время.
 */
internal fun Frame.dots() {
    card()
    var y = top
    val header = role("header")
    val labelT = header.typo
    val lh = p.lineHeight(labelT)
    val dot = m("statusDot", 3.5f)
    p.circle(l + dot, y + lh / 2f, dot, c("statusDot"))
    val status = if (nextDay) d.dayLabel else d.status
    val rangeText = f?.let { s("headerRight") }?.takeIf { it.isNotEmpty() }
    val rw = rangeText?.let { p.text(it, r, y, labelT, c("muted"), Align.END) + 10f } ?: 0f
    p.text(status, l + 13f, y, labelT, header.color, maxWidth = r - l - 13f - rw)
    y += lh + 8f

    // Крупные цифры: отсчёт, время начала или прочерк.
    val big = d.countdownTo != null
    val hero = role("hero", "counter")
    val numT = hero.typo
    val sample = if (big) counterText() else f?.let { fmt(it.startMinute) } ?: sv("heroNone")
    val maxNumW = (r - l) * m("heroWidth", 0.5f)
    var size = numT.size
    val wAt = p.width(sample, numT)
    if (wAt > maxNumW) size *= maxNumW / wAt
    val heroH = size * 0.85f
    val numW = p.width(sample, numT.sized(size)) + 4f
    if (big) {
        countdown(l, y, numW, heroH, sample, size)
    } else {
        p.text(sample, l, y, numT.sized(size), hero.color)
    }
    // Название — справа от цифр, а в узком виджете — под ними.
    val stack = flag("stack")
    val title = role("title")
    val details = role("details")
    val colX = if (stack) l else l + numW + 12f
    val colW = r - colX
    val subLh = p.lineHeight(details.typo)
    val fitted = p.fit(d.title, title.typo, colW, if (stack) p.lineHeight(title.typo) else heroH - subLh - 3f + 6f)
    val colH = fitted.height + 3f + subLh
    var cy = if (stack) y + heroH + 4f else y + heroH - colH
    p.drawFitted(fitted, colX, cy, title.color)
    cy += fitted.height + 3f
    p.text(if (f == null) sv("detailsNone") else sv("sub"), colX, cy, details.typo, details.color, maxWidth = colW)
    val contentBottom = if (stack) cy + subLh else y + heroH

    val next = role("next")
    val metaLh = p.lineHeight(next.typo)
    val bottomTop = mainBottom - metaLh
    val dotsY = bottomTop - m("progressGap", 10f) - progressHeight
    if (dotsY > contentBottom + 4f) progress(l, dotsY, r, d.progress)
    if (bottomTop > contentBottom + 2f) {
        val n = d.next
        if (n != null) {
            var x = l
            val label = role("nextLabel")
            x += p.text(sv("nextLabel"), x, bottomTop, label.typo, label.color)
            if (flag("nextShort")) {
                p.text(sv("nextTime"), x, bottomTop, next.typo, next.color, maxWidth = r - x)
            } else {
                val rw2 = p.text(sv("nextRight"), r, bottomTop, next.typo, next.color, Align.END) + 10f
                p.text(sv("nextTitle"), x, bottomTop, next.typo, c("text"), maxWidth = r - x - rw2)
            }
        } else {
            p.text(nextParts().first, l, bottomTop, next.typo, next.color, maxWidth = r - l)
        }
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
