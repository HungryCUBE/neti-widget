package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.ringProgress
import kotlin.math.max

/**
 * Кольцо обратного отсчёта слева, название справа, внизу следующая пара.
 * Роли: unit («мин» под цифрами), time (время начала в кольце), placeholder (прочерк), nextTime.
 * Цвета: divider — линия над следующей парой, nextDot — точка. metrics: ringDiameter, ringGap.
 * Толщина кольца — progress.width. Флаг stack: статус справа от кольца, название — под ним (для 2×2).
 */
internal fun Frame.ring() {
    card()
    val ringD = m("ringDiameter", 76f)
    val ringW = th.progress.width
    val y = top
    val cx = l + ringD / 2f
    val cy = y + ringD / 2f
    val rr = ringD / 2f - ringW / 2f
    ringProgress(cx, cy, rr, d.progress)

    val unit = role("unit", "details")
    if (d.countdownTo != null) {
        // Как в макете: крупное число минут, под ним «мин».
        val size = role("counter").typo.size
        val boxW = ringD - 2 * ringW - 6f
        val boxH = size * 1.05f
        countdown(cx - boxW / 2f, cy - boxH / 2f - 5f, boxW, boxH, counterText(), size)
        p.text(sv("unitLabel"), cx, cy + boxH / 2f - 3f, unit.typo, unit.color, Align.CENTER)
    } else if (f != null) {
        val t = role("time", "title")
        p.text(sv("ringTime"), cx, cy - p.lineHeight(t.typo) / 2f - 4f, t.typo, t.color, Align.CENTER)
        p.text(sv("ringDay"), cx, cy + p.lineHeight(t.typo) / 2f - 4f, unit.typo, unit.color, Align.CENTER)
    } else {
        val e = role("placeholder", "title")
        p.text(sv("ringEmpty"), cx, cy - 10f, e.typo, e.color, Align.CENTER)
    }

    val header = role("header")
    val title = role("title")
    val details = role("details")
    val x0 = l + ringD + m("ringGap", 14f)
    val colW = r - x0
    val labelLh = p.lineHeight(header.typo)
    val bodyLh = p.lineHeight(details.typo)
    var contentBottom = y + ringD
    if (flag("stack")) {
        // Узкий виджет: справа от кольца — статус и аудитория, название — под кольцом.
        var ty = cy - (labelLh + bodyLh) / 2f
        p.text(sv("statusCompact"), x0, ty, header.typo, header.color, maxWidth = colW)
        ty += labelLh
        p.text(sv("subCompact"), x0, ty, details.typo, details.color, maxWidth = colW)
        val titleTop = y + ringD + 6f
        val fitted = p.fit(d.title, title.typo, r - l, mainBottom - titleTop)
        p.drawFitted(fitted, l, titleTop, title.color)
        contentBottom = titleTop + fitted.height
    } else {
        val fitted = p.fit(d.title, title.typo, colW, ringD + 16f - labelLh - bodyLh)
        val colH = labelLh + 2f + fitted.height + 2f + bodyLh
        var ty = max(y, cy - colH / 2f)
        p.text(sv("status"), x0, ty, header.typo, header.color, maxWidth = colW)
        ty += labelLh + 2f
        p.drawFitted(fitted, x0, ty, title.color)
        ty += fitted.height + 2f
        p.text(detailsText(), x0, ty, details.typo, details.color, maxWidth = colW)
        contentBottom = max(contentBottom, ty + bodyLh)
    }

    val next = role("next")
    val metaLh = p.lineHeight(next.typo)
    val rowTop = mainBottom - metaLh
    if (rowTop - 11f > contentBottom + 4f) {
        p.rect(l, rowTop - 10f, r, rowTop - 9f, c("divider"))
        val n = d.next
        if (n != null) {
            p.circle(l + 3f, rowTop + metaLh / 2f, 3f, c("nextDot"))
            var rx = r
            n.room?.let { rx -= p.text(it, rx, rowTop, next.typo, next.color, Align.END) + 8f }
            val time = role("nextTime")
            rx -= p.text(fmt(n.startMinute), rx, rowTop, time.typo, time.color, Align.END) + 8f
            p.text(sv("nextName"), l + 14f, rowTop, next.typo, next.color, maxWidth = rx - l - 14f)
        } else {
            p.text(nextParts().first, l, rowTop, next.typo, next.color, maxWidth = r - l)
        }
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
