package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.progress

/**
 * Строки консоли: приглашение с командой, «▸ Название [тип]», детали, прогресс, «next → …» с курсором.
 * Все тексты — в strings (prompt, cmd*, titlePrefix, titleTag, nextLine, cursor…).
 * Роль tag — тип пары после названия. Цвет cursor — курсор.
 */
internal fun Frame.terminal() {
    card()
    val header = role("header")
    val title = role("title")
    val details = role("details")
    val next = role("next")
    val mono = header.typo
    val big = title.typo
    val monoLh = p.lineHeight(mono)
    var y = top
    val cmd = when {
        d.phase == Phase.NO_DATA -> "cmdSync"
        isLesson -> "cmdNow"
        upcoming -> "cmdNext"
        nextDay -> "cmdNextDay"
        else -> "cmdNow"
    }
    p.text(sv("prompt", "cmd" to sv(cmd)), l, y, mono, header.color, maxWidth = r - l)
    y += monoLh

    // ▸ Матанализ [пр]
    val tagRole = role("tag", "title")
    var tag = s("titleTag") ?: ""
    val titleText = if (f == null) sv("titleNone") else d.title
    val prefix = sv("titlePrefix")
    val prefixW = p.width(prefix, big)
    // Флаг tagOptional: если название не влезает целиком, сначала убирается тип пары.
    if (flag("tagOptional") && tag.isNotEmpty() && prefixW + p.width(titleText, big) + p.width(tag, tagRole.typo) > r - l) tag = ""
    val tagW = if (tag.isNotEmpty()) p.width(tag, tagRole.typo) else 0f
    var x = l
    x += p.text(prefix, x, y, big, title.color)
    x += p.text(titleText, x, y, big, title.color, maxWidth = r - l - prefixW - tagW)
    if (tag.isNotEmpty()) p.text(tag, x, y, tagRole.typo, tagRole.color)
    y += p.lineHeight(big)

    val lastTop = mainBottom - monoLh
    // Детали
    if (y + monoLh <= lastTop) {
        p.text(detailsText(), l, y, details.typo, details.color, maxWidth = r - l)
        y += monoLh
    }
    // Прогресс или подпись
    if (y + monoLh <= lastTop) {
        val prog = d.progress
        if (prog != null) {
            progress(l, y, r, prog)
        } else if (upcoming && d.minutesLeft != null) {
            p.text(sv("upcomingNote"), l, y, mono, header.color, maxWidth = r - l)
        } else if (nextDay && d.message != null) {
            p.text(sv("nextDayNote"), l, y, mono, header.color, maxWidth = r - l)
        }
    }

    // next → 12:00 Физика [лек] 1-224 ▍
    if (lastTop >= top + monoLh + p.lineHeight(big) - 1f) {
        val nextText = when {
            d.next != null -> sv("nextLine")
            d.phase == Phase.NO_DATA -> sv("nextNoData")
            else -> sv("nextNone")
        }
        val cursor = sv("cursor")
        val cursorW = p.width(cursor, next.typo)
        val w = p.text(nextText, l, lastTop, next.typo, next.color, maxWidth = r - l - cursorW)
        p.text(cursor, l + w, lastTop, next.typo, c("cursor"))
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
