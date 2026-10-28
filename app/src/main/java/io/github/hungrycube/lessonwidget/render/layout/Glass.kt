package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight

/**
 * Плашка с номером пары и отсчёт справа, крупное название, полоса прогресса и строка «Далее».
 * Цвета: pill, pillBorder — плашка. metrics: pillPadX, pillPadY, pillGap, headerGap, progressGap.
 */
internal fun Frame.glass() {
    card()
    var y = top
    val header = role("header")
    val next = role("next")
    val counter = role("counter")
    val title = role("title")
    val details = role("details")
    val labelT = header.typo
    val metaT = next.typo
    val padX = m("pillPadX", 9f)
    val padY = m("pillPadY", 3f)
    val pillH = p.lineHeight(labelT) + padY * 2f

    val rightText: String? = when {
        d.countdownTo != null -> counterText()
        !isLesson && f != null -> s("atStart")
        else -> null
    }
    val rightW = rightText?.let { p.width(it, metaT) + 4f } ?: 0f
    val pillMax = r - l - rightW - m("pillGap", 8f) - padX * 2f
    val pillText = when {
        p.width(d.headline, labelT) <= pillMax -> d.headline
        else -> p.ellipsize(shortHeadline(), labelT, pillMax)
    }
    val pw = p.width(pillText, labelT) + padX * 2f
    p.roundRect(l, y, l + pw, y + pillH, pillH / 2f, c("pill"))
    p.roundRectStroke(l, y, l + pw, y + pillH, pillH / 2f, c("pillBorder"))
    p.text(pillText, l + padX, y + padY, labelT, header.color)

    if (d.countdownTo != null) {
        countdown(r - rightW, y, rightW, pillH, rightText!!, metaT.size)
    } else if (rightText != null) {
        p.text(rightText, r, y + (pillH - p.lineHeight(metaT)) / 2f, metaT, counter.color, Align.END)
    }
    y += pillH + m("headerGap", 8f)

    val metaLh = p.lineHeight(metaT)
    val bodyLh = p.lineHeight(details.typo)
    val hasBar = d.progress != null
    val ph = progressHeight
    val gap = m("progressGap", 9f)
    val avail = mainBottom - y
    val titleMin = p.lineHeight(title.typo) * 0.86f
    val fullBottom = (if (hasBar) gap + ph else 0f) + metaLh
    val showNext = avail >= titleMin + bodyLh + fullBottom + 4f
    val bottomBlock = if (showNext) fullBottom else if (hasBar) ph else 0f
    val showDetails = avail >= titleMin + bodyLh + bottomBlock + 4f
    val titleMaxH = avail - bottomBlock - (if (showDetails) bodyLh else 0f) - 4f

    val fitted = p.fit(d.title, title.typo, r - l, titleMaxH)
    p.drawFitted(fitted, l, y, title.color)
    y += fitted.height
    if (showDetails) p.text(detailsText(), l, y, details.typo, details.color, maxWidth = r - l)

    var by = mainBottom
    if (showNext) {
        by -= metaLh
        val (left, right) = nextParts()
        val rw = if (right.isNotEmpty()) p.text(right, r, by, metaT, next.color, Align.END) + 10f else 0f
        p.text(left, l, by, metaT, next.color, maxWidth = r - l - rw)
        by -= gap
    }
    if (hasBar) {
        by -= ph
        progress(l, by, r, d.progress)
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
