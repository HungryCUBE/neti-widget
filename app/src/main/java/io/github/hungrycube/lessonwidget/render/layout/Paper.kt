package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import kotlin.math.max

/**
 * Лист: шапка, крупное название, подпись курсивом, внизу «время — следующая пара». Без прогресса.
 * Линейки и поле рисует фон стиля (слои rules и line). metrics: textLeft — где начинается текст (правее поля).
 * Роль nextTitle — название следующей пары, next — время и аудитория. Цвет number — номер пары.
 */
internal fun Frame.paper() {
    card()
    val header = role("header")
    val title = role("title")
    val details = role("details")
    val next = role("next")
    val left = m("textLeft", 40f)
    var y = top
    val headerLeft = when {
        f == null -> sv("headerNone")
        isLesson -> sv("headerLesson")
        upcoming -> sv("headerUpcoming")
        else -> sv("headerNextDay")
    }
    val num = s("numberBadge")
    val nw = num?.let { p.text(it, r, y, header.typo, c("number"), Align.END) + 10f } ?: 0f
    p.text(headerLeft, left, y, header.typo, header.color, maxWidth = r - left - nw)
    y += p.lineHeight(header.typo) + 6f

    val bodyLh = p.lineHeight(details.typo)
    val nextTitle = role("nextTitle", "details")
    val nextT = nextTitle.typo
    val bottomLh = p.lineHeight(nextT)
    val avail = mainBottom - y
    val titleMin = p.lineHeight(title.typo) * 0.86f
    val showNext = avail >= titleMin + bodyLh + bottomLh + 4f
    val showDetails = avail >= titleMin + bodyLh + (if (showNext) bottomLh else 0f) + 2f
    val fitted = p.fit(d.title, title.typo, r - left, avail - (if (showNext) bottomLh else 0f) - (if (showDetails) bodyLh else 0f) - 4f)
    p.drawFitted(fitted, left, y, title.color)
    y += fitted.height
    if (showDetails) p.text(detailsText(), left, y, details.typo, details.color, maxWidth = r - left)

    if (showNext) {
        val rowTop = mainBottom - bottomLh
        val base = p.baseline(rowTop, nextT)
        val n = d.next
        if (n != null) {
            var rx = r
            n.room?.let { rx -= p.textAtBaseline(it, rx, base, next.typo, next.color, Align.END) + 10f }
            var x = left
            x += p.textAtBaseline(sv("nextPrefix"), x, base, next.typo, next.color)
            val name = p.ellipsize(n.title, nextT, max(0f, rx - x))
            p.textAtBaseline(name, x, base, nextT, nextTitle.color)
        } else {
            p.textAtBaseline(p.ellipsize(nextParts().first, nextT, r - left), left, base, nextT, c("secondary"))
        }
    }
    if (large) agenda(mainH + 2f, H - pad.bottom, left = left)
}
