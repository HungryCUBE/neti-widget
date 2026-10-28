package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight

/**
 * Время пары и номер в шапке, крупное название, тонкая линия прогресса с отсчётом, строка «Далее».
 * Цвета: number — номер пары, nextTitle — название следующей пары.
 */
internal fun Frame.clean() {
    card()
    var y = top
    val header = role("header")
    val next = role("next")
    val title = role("title")
    val details = role("details")
    val counter = role("counter")
    val labelT = header.typo
    val metaT = next.typo
    val headerLeft = when {
        f == null -> sv("headerNone")
        isLesson -> sv("headerLesson")
        upcoming -> sv("headerUpcoming")
        else -> sv("headerNextDay")
    }
    val number = s("numberBadge")
    val nw = number?.let { p.text(it, r, y, labelT, c("number"), Align.END) + 12f } ?: 0f
    p.text(headerLeft, l, y, labelT, header.color, maxWidth = r - l - nw)
    y += p.lineHeight(labelT) + 2f

    val metaLh = p.lineHeight(metaT)
    val bodyLh = p.lineHeight(details.typo)
    val rowH = metaLh
    val hasRow = d.progress != null || d.countdownTo != null || (nextDay && d.message != null)
    val avail = mainBottom - y
    val titleMin = p.lineHeight(title.typo) * 0.86f
    val fullBottom = (if (hasRow) rowH + 8f else 0f) + metaLh
    val showNext = avail >= titleMin + bodyLh + fullBottom + 4f
    val bottomBlock = if (showNext) fullBottom else if (hasRow) rowH else 0f
    val showDetails = avail >= titleMin + bodyLh + bottomBlock + 4f
    val titleMaxH = avail - bottomBlock - (if (showDetails) bodyLh else 0f) - 4f

    val fitted = p.fit(d.title, title.typo, r - l, titleMaxH)
    p.drawFitted(fitted, l, y, title.color)
    y += fitted.height
    if (showDetails) p.text(detailsText(), l, y, details.typo, details.color, maxWidth = r - l)

    var by = mainBottom
    if (showNext) {
        by -= metaLh
        val n = d.next
        if (n != null) {
            val prefix = sv("nextPrefix")
            val suffix = sv("nextSuffix")
            var x = l
            x += p.text(prefix, x, by, metaT, next.color)
            val suffixW = p.width(suffix, metaT)
            x += p.text(n.title, x, by, metaT, c("nextTitle"), maxWidth = r - x - suffixW)
            p.text(suffix, x, by, metaT, next.color)
        } else {
            p.text(nextParts().first, l, by, metaT, next.color, maxWidth = r - l)
        }
        by -= 8f
    }
    if (hasRow) {
        by -= rowH
        if (d.countdownTo != null || d.progress != null) {
            val cdW = if (d.countdownTo != null) p.width(counterText(), metaT.weighted(counter.typo.weight)) + 4f else 0f
            val lineR = if (cdW > 0f) r - cdW - 12f else r
            val ly = by + rowH / 2f - progressHeight / 2f
            progress(l, ly, lineR, d.progress ?: 0f)
            countdown(r - cdW, by, cdW, rowH, counterText(), metaT.size)
        } else {
            p.text(d.message ?: "", l, by, metaT, next.color, maxWidth = r - l)
        }
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
