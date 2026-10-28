package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.countdown
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight
import kotlin.math.max

/**
 * Плашка недели и группа, крупное название с минутами справа, цветные теги, линия и «Далее».
 * Цвета: badge — плашка недели, tag1–tag3 — теги типа, аудитории и времени, rule — линия над «Далее».
 * Роли: group (ссылка на группу), unit («мин»). Флаг badgeCounter — минуты в строке плашки вместо группы (2×2).
 */
internal fun Frame.tags() {
    card()
    var y = top
    val header = role("header")
    val badgeT = header.typo
    val badgeH = p.lineHeight(badgeT) + 6f
    val cdT = role("counter").typo
    val text = c("text")
    val rightText: String? = when {
        d.countdownTo != null -> counterText()
        !isLesson && f != null -> sv("atTime")
        else -> null
    }
    val compactRow = flag("badgeCounter")
    val badge = s("badge") ?: if (f == null) d.status else d.dayLabel
    val gw: Float
    if (compactRow) {
        // Узкий виджет: минуты — в строке с плашкой, вместо названия группы.
        val w = rightText?.let { p.width(it, cdT) + 4f } ?: 0f
        if (rightText != null) {
            if (d.countdownTo != null) {
                countdown(r - w, y, w, badgeH, rightText, cdT.size)
            } else {
                p.text(rightText, r, y + (badgeH - p.lineHeight(cdT)) / 2f, cdT, text, Align.END)
            }
        }
        gw = w + 6f
    } else {
        val group = role("group", "header")
        gw = p.text(d.group, r, y + (badgeH - p.lineHeight(group.typo)) / 2f, group.typo, group.color, Align.END) + 10f
    }
    val bt = p.ellipsize(badge, badgeT, r - l - gw - 14f)
    val bw = p.width(bt, badgeT) + 14f
    p.rect(l, y, l + bw, y + badgeH, c("badge"))
    p.text(bt, l + 7f, y + 3f, badgeT, header.color)
    y += badgeH + 8f

    // Название и минуты справа. Как в макете: крупное «34» и маленькое «мин» рядом.
    val unit = role("unit", "next")
    val title = role("title")
    val details = role("details")
    val next = role("next")
    val minW = if (d.countdownTo != null) p.width(sv("unitLabel"), unit.typo) + 4f else 0f
    val numW = rightText?.let { p.width(it, cdT) } ?: 0f
    val rightW = if (compactRow || rightText == null) 0f else numW + minW + 2f
    val tagsH = p.lineHeight(details.typo) + 6f
    val metaLh = p.lineHeight(next.typo)
    val ph = progressHeight
    val bottomBlock = ph + 9f + 2f + 6f + metaLh
    val showBottom = mainBottom - y >= p.lineHeight(title.typo) * 0.86f + 8f + tagsH + bottomBlock
    val titleMaxH = mainBottom - y - (if (showBottom) bottomBlock + 6f else 0f) - 8f - tagsH
    val fitted = p.fit(d.title, title.typo, r - l - rightW - (if (rightW > 0f) 12f else 0f), titleMaxH)
    p.drawFitted(fitted, l, y, title.color)
    val rowH = if (compactRow) fitted.height else max(fitted.height, cdT.size * 1.0f)
    if (rightText != null && !compactRow) {
        val cy = y + fitted.height - cdT.size * 1.0f
        if (d.countdownTo != null) {
            countdown(r - minW - numW - 2f, cy, numW + 2f, cdT.size * 1.0f, rightText, cdT.size)
            p.textAtBaseline(sv("unitLabel"), r, p.baseline(cy, cdT), unit.typo, unit.color, Align.END)
        } else {
            p.text(rightText, r, cy, cdT, text, Align.END)
        }
    }
    y += rowH + 8f

    // Теги
    val tags = buildList {
        s("tagType")?.let { add(it to c("tag1")) }
        s("tagRoom")?.let { add(it to c("tag2")) }
        when {
            f == null -> s("tagWhenNone")
            nextDay -> s("tagWhenNextDay")
            else -> s("tagWhen")
        }?.let { add(it to c("tag3")) }
    }
    var tx = l
    for ((label, color) in tags) {
        val w = p.width(label, details.typo) + 12f
        if (tx + w > r) break
        p.rect(tx, y, tx + w, y + tagsH, color)
        p.text(label, tx + 6f, y + 3f, details.typo, details.color)
        tx += w + 4f
    }

    if (showBottom) {
        val rowTop = mainBottom - metaLh
        val lineTop = rowTop - 6f - 2f
        p.rect(l, lineTop, r, lineTop + 2f, c("rule"))
        val prog = d.progress
        if (prog != null) progress(l, lineTop - 9f - ph, r, prog)
        val n = d.next
        if (n != null) {
            val rw = p.text(sv("nextRight"), r, rowTop, next.typo, next.color, Align.END) + 10f
            p.text(sv("nextLeft"), l, rowTop, next.typo, next.color, maxWidth = r - l - rw)
        } else {
            p.text(nextParts().first, l, rowTop, next.typo, next.color, maxWidth = r - l)
        }
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
