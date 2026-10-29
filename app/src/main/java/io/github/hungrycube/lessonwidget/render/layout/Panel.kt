package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Backgrounds
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.counterBox
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight
import kotlin.math.max

/**
 * Фон видно почти целиком: сверху по углам две плашки (номер пары и отсчёт), внизу панель с названием,
 * деталями справа от него, прогрессом и строкой «Далее». Для фото под настоящим стеклом.
 *
 * Подложки: surfaces.chip — плашки, surfaces.panel — панель. Отсчёт — counter (обычно type surface с той же плашкой).
 * metrics: chipInset 12, chipPadX 10, chipPadY 4, panelInset 8, panelPadX 13, panelPadTop 10, panelPadBottom 10,
 * progressGap 7, nextGap 6, detailsGap 10 (между названием и деталями справа).
 * strings: panelChip (левая плашка во время пары, иначе — короткая метка состояния), atStart (правая плашка без отсчёта).
 */
internal fun Frame.panel() {
    card()
    val header = role("header")
    val title = role("title")
    val details = role("details")
    val next = role("next")
    val chipSurface = th.surface("chip")
    val ci = m("chipInset", 12f)
    val padX = m("chipPadX", 10f)
    val padY = m("chipPadY", 4f)
    val chipH = p.lineHeight(header.typo) + 2 * padY

    // ---------- Плашки сверху ----------
    val box = counterBox()
    val rightText = if (box == null && !isLesson && f != null) s("atStart") else null
    val rightW = box?.w ?: rightText?.let { p.width(it, header.typo) + 2 * padX } ?: 0f
    val rowH = max(chipH, box?.h ?: 0f)
    val leftMax = W - 2 * ci - (if (rightW > 0f) rightW + 8f else 0f) - 2 * padX
    val full = (if (isLesson) s("panelChip") else null) ?: shortHeadline()
    val chipText = p.ellipsize(full, header.typo, leftMax)
    if (chipText.isNotEmpty()) {
        val cw = p.width(chipText, header.typo) + 2 * padX
        val cy = ci + (rowH - chipH) / 2f
        chipSurface?.let { Backgrounds.draw(p, it, ci, cy, ci + cw, cy + chipH, mode) }
        p.text(chipText, ci + padX, cy + padY, header.typo, header.color)
    }
    if (box != null) {
        box.draw(W - ci - box.w, ci + (rowH - box.h) / 2f)
    } else if (rightText != null) {
        val cy = ci + (rowH - chipH) / 2f
        chipSurface?.let { Backgrounds.draw(p, it, W - ci - rightW, cy, W - ci, cy + chipH, mode) }
        p.text(rightText, W - ci - padX, cy + padY, header.typo, header.color, Align.END)
    }

    // ---------- Панель снизу ----------
    val pi = m("panelInset", 8f)
    val px = m("panelPadX", 13f)
    val pt = m("panelPadTop", 10f)
    val pb = m("panelPadBottom", 10f)
    val left = pi + px
    val right = W - pi - px
    val cw = right - left
    val bottom = mainH - pi
    val avail = bottom - pb - pt - (ci + rowH + 6f)
    val metaLh = p.lineHeight(next.typo)
    val detailsLh = p.lineHeight(details.typo)
    val hasBar = d.progress != null
    val ph = progressHeight
    val barBlock = if (hasBar) m("progressGap", 7f) + ph else 0f
    val nextBlock = m("nextGap", 6f) + metaLh
    val titleMin = p.lineHeight(title.typo) * 0.86f

    // Детали справа от названия, пока название занимает хотя бы половину ширины; иначе — своей строкой под ним.
    val detailsText = detailsText()
    val gap = m("detailsGap", 10f)
    val dw = p.width(detailsText, details.typo)
    val side = detailsText.isNotEmpty() && p.width(d.title, title.typo) + gap + dw <= cw || dw + gap <= cw * 0.5f
    val showNext = avail >= titleMin + barBlock + nextBlock + (if (side) 0f else detailsLh)
    val showDetails = side || avail >= titleMin + barBlock + (if (showNext) nextBlock else 0f) + detailsLh
    val titleW = if (side && showDetails && detailsText.isNotEmpty()) cw - dw - gap else cw
    val bottomPart = barBlock + (if (showNext) nextBlock else 0f) + (if (showDetails && !side) detailsLh else 0f)
    val fitted = p.fit(d.title, title.typo, titleW, avail - bottomPart)
    val contentH = fitted.height + bottomPart
    val panelTop = bottom - pb - contentH - pt
    th.surface("panel")?.let { Backgrounds.draw(p, it, pi, panelTop, W - pi, bottom, mode) }

    var y = panelTop + pt
    p.drawFitted(fitted, left, y, title.color)
    if (showDetails && detailsText.isNotEmpty()) {
        if (side) {
            // По базовой линии первой строки названия, как align-items: baseline.
            val base = y + p.baseline(0f, fitted.typo)
            val dt = base - p.baseline(0f, details.typo)
            p.text(detailsText, right, dt, details.typo, details.color, Align.END, maxWidth = cw - titleW)
        } else {
            p.text(detailsText, left, y + fitted.height, details.typo, details.color, maxWidth = cw)
        }
    }
    y += fitted.height + if (showDetails && !side) detailsLh else 0f
    if (hasBar) {
        y += m("progressGap", 7f)
        progress(left, y, right, d.progress)
        y += ph
    }
    if (showNext) {
        y += m("nextGap", 6f)
        val (nl, nr) = nextParts()
        val rw = if (nr.isNotEmpty()) p.text(nr, right, y, next.typo, next.color, Align.END) + 10f else 0f
        p.text(nl, left, y, next.typo, next.color, maxWidth = cw - rw)
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}
