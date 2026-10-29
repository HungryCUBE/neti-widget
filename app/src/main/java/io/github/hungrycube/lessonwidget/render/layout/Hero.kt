package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Backgrounds
import io.github.hungrycube.lessonwidget.render.CounterBox
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.counterBox
import io.github.hungrycube.lessonwidget.render.icon
import io.github.hungrycube.lessonwidget.render.iconFor
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight
import io.github.hungrycube.lessonwidget.render.theme.Role
import kotlin.math.max

/**
 * Шапка (слева — номер пары или состояние, справа — время, иконка или отсчёт), крупное название с отсчётом,
 * строка деталей, прогресс и строка «Далее» внизу.
 *
 * Где отсчёт — counter.placement: inline — справа от названия по базовой линии, side — справа от названия и деталей,
 * aside — справа от всего блока вместе с шапкой, header — в шапке справа; counter.valign — по центру или по низу.
 * Если отсчёт по высоте не помещается, он становится текстом в шапке справа.
 *
 * flags: chip — левая часть шапки на подложке surfaces.chip; anchorBottom — название и всё под ним прижаты к низу.
 * Цвета: divider и dividerLight — линия и светлая линия под ней над строкой «Далее» (если divider есть в палитре).
 * Роли: headerRight (справа в шапке), unit («мин»), nextTitle (название следующей пары после nextPrefix).
 * metrics: headerGap 6, detailsGap 0, counterGap 12, progressGap 9 (при anchorBottom — от деталей до прогресса),
 * nextGap 10, dividerGap 7 (от линии до текста), chipPadX 9, chipPadY 3, headerIcon 13 (иконка слота header),
 * counterMargin 0 (отступ отсчёта от правого края).
 * strings: heroHeaderLesson (шапка во время пары, иначе — headline), heroHeaderRight, nextPrefix.
 */
internal fun Frame.hero() {
    card()
    val header = role("header")
    val headerRight = role("headerRight", "header")
    val title = role("title")
    val details = role("details")
    val next = role("next")
    val nextTitle = role("nextTitle", "next")
    val cs = th.counter
    val cGap = m("counterGap", 12f)
    val headerGap = m("headerGap", 6f)
    val detailsGap = m("detailsGap", 0f)
    val chip = flag("chip")
    val chipPadX = m("chipPadX", 9f)
    val chipPadY = m("chipPadY", 3f)
    val headLh = p.lineHeight(header.typo)
    val leftH = if (chip) headLh + 2 * chipPadY else headLh
    val iconSize = m("headerIcon", 13f)

    var box: CounterBox? = counterBox()
    var place = if (box == null) "none" else cs.placement
    var rightText = if (place == "header") null else s("heroHeaderRight")

    val hasBar = d.progress != null
    val ph = progressHeight
    val metaLh = p.lineHeight(next.typo)
    val divider = th.palette.getOrNull("divider") != null
    val dividerH = if (divider) 1f + m("dividerGap", 7f) else 0f
    val nextGap = m("nextGap", 10f)
    val anchor = flag("anchorBottom")
    val progressGap = if (anchor) m("progressGap", 9f) else 0f
    val minSpace = 4f

    // Отсчёт выше, чем есть места рядом с названием, — текстом в шапке.
    fun rowH(): Float {
        val rh = when {
            place == "header" -> box!!.h
            rightText != null -> p.lineHeight(headerRight.typo)
            iconFor("header") != null -> iconSize
            else -> 0f
        }
        return max(leftH, rh)
    }
    if (place == "side" || place == "aside") {
        val room = mainBottom - top - (if (place == "side") rowH() + headerGap else 0f) - (if (hasBar) ph + progressGap else 0f)
        if (box!!.h > room) {
            box = null
            place = "none"
            rightText = counterText()
        }
    }
    val inColumn = place == "aside"
    val iconName = if (place == "header" || rightText != null) null else iconFor("header")
    val headerRowH = rowH()
    // Ширина, которую отсчёт забирает справа, с его отступом от края.
    val cm = m("counterMargin", 0f)
    val bw = box?.let { it.w + cm } ?: 0f

    // ---------- Сколько чего помещается ----------
    val detailsLh = p.lineHeight(details.typo)
    val headerPart = if (inColumn) leftH + headerGap else headerRowH + headerGap
    val avail = mainBottom - top - headerPart
    val titleMin = p.lineHeight(title.typo) * 0.86f
    val counterMin = if (place == "side" || place == "aside") box!!.h - (if (inColumn) leftH + headerGap else 0f) else 0f
    val bodyMin = max(titleMin + detailsLh + detailsGap, counterMin)
    val fullBottom = (if (hasBar) ph + progressGap + nextGap else 0f) + metaLh + dividerH
    val showNext = avail >= bodyMin + fullBottom + minSpace
    val bottomBlock = if (showNext) fullBottom else if (hasBar) ph + progressGap else 0f
    val showDetails = avail >= max(titleMin + detailsLh + detailsGap, counterMin) + bottomBlock + minSpace
    val titleMaxH = avail - bottomBlock - (if (showDetails) detailsLh + detailsGap else 0f) - minSpace

    val textR = if (place == "side" || place == "aside") r - bw - cGap else r
    val titleW = if (place == "inline") textR - l - bw - cGap else textR - l
    val fitted = p.fit(d.title, title.typo, titleW, titleMaxH)

    // Высота блока под шапкой: название (с отсчётом) и детали.
    val titleBaseline = p.baseline(0f, fitted.typo)
    val inlineAbove = if (place == "inline") max(titleBaseline, box!!.baseline) else titleBaseline
    val titleRowH = if (place == "inline") max(inlineAbove - titleBaseline + fitted.height, inlineAbove - box!!.baseline + box!!.h) else fitted.height
    val colH = (if (inColumn) leftH + headerGap else 0f) + titleRowH + (if (showDetails) detailsGap + detailsLh else 0f)
    val bodyH = if (place == "side" || place == "aside") max(colH, box!!.h) else colH

    // ---------- Низ: прогресс и «Далее» ----------
    var by = mainBottom
    if (showNext) {
        by -= metaLh
        drawNext(by, next, nextTitle)
        if (divider) {
            val dy = by - m("dividerGap", 7f) - 1f
            p.rect(l, dy, r, dy + 1f, c("divider"))
            th.palette.getOrNull("dividerLight")?.let { p.rect(l, dy + 1f, r, dy + 2f, it) }
        }
        by -= dividerH
        if (hasBar) by -= nextGap
    }
    if (hasBar) {
        by -= ph
        progress(l, by, r, d.progress)
        by -= progressGap
    }

    // ---------- Шапка и название ----------
    val bodyTop = if (anchor) by - bodyH else top + (if (inColumn) 0f else headerRowH + headerGap)
    val headerTop = top
    if (!inColumn) {
        val leftMax = r - l - (when {
            place == "header" -> bw + 8f
            rightText != null -> p.width(rightText!!, headerRight.typo) + 8f
            iconName != null -> iconSize + 8f
            else -> 0f
        })
        headerLeft(l, headerTop + (headerRowH - leftH) / 2f, leftMax, chip, chipPadX, chipPadY)
        when {
            place == "header" -> box!!.draw(r - bw, headerTop + (headerRowH - box!!.h) / 2f)
            rightText != null -> {
                val rl = p.lineHeight(headerRight.typo)
                p.text(rightText!!, r, headerTop + (headerRowH - rl) / 2f, headerRight.typo, headerRight.color, Align.END)
            }
            iconName != null -> icon(iconName, r - iconSize, headerTop + (headerRowH - iconSize) / 2f, iconSize)
        }
    }

    val colTop = when (place) {
        "side", "aside" -> bodyTop + if (cs.valign == "end") bodyH - colH else (bodyH - colH) / 2f
        else -> bodyTop
    }
    var y = colTop
    if (inColumn) {
        headerLeft(l, y, textR - l, chip, chipPadX, chipPadY)
        y += leftH + headerGap
    }
    if (place == "inline") {
        p.drawFitted(fitted, l, y + inlineAbove - titleBaseline, title.color)
        box!!.draw(r - bw, y + inlineAbove - box!!.baseline)
    } else {
        p.drawFitted(fitted, l, y, title.color)
    }
    y += titleRowH
    if (showDetails) p.text(detailsText(), l, y + detailsGap, details.typo, details.color, maxWidth = textR - l)
    if (place == "side" || place == "aside") {
        val bt = bodyTop + if (cs.valign == "end") bodyH - box!!.h else (bodyH - box!!.h) / 2f
        box!!.draw(r - bw, bt)
    }
    if (large) agenda(mainH + 2f, H - pad.bottom)
}

/** Левая часть шапки: строка состояния, при chip — на подложке surfaces.chip. Если не влезает — короткая метка. */
private fun Frame.headerLeft(x: Float, y: Float, maxW: Float, chip: Boolean, padX: Float, padY: Float) {
    val header = role("header")
    val full = (if (isLesson) s("heroHeaderLesson") else null) ?: d.headline
    val avail = maxW - if (chip) 2 * padX else 0f
    val text = if (p.width(full, header.typo) <= avail) full else p.ellipsize(shortHeadline(), header.typo, avail)
    if (!chip) {
        p.text(text, x, y, header.typo, header.color, maxWidth = avail)
        return
    }
    val w = p.width(text, header.typo) + 2 * padX
    val h = p.lineHeight(header.typo) + 2 * padY
    th.surface("chip")?.let { Backgrounds.draw(p, it, x, y, x + w, y + h, mode) }
    p.text(text, x + padX, y + padY, header.typo, header.color)
}

/** Строка «Далее»: «Далее · » + название (роль nextTitle) слева, время и аудитория справа. В пустых состояниях — как у всех. */
private fun Frame.drawNext(y: Float, next: Role, nextTitle: Role) {
    val n = d.next
    val prefix = s("nextPrefix")
    val (left, right) = nextParts()
    val rw = if (right.isNotEmpty()) p.text(right, r, y, next.typo, next.color, Align.END) + 10f else 0f
    if (n != null && prefix != null) {
        var x = l
        x += p.text(prefix, x, y, next.typo, next.color, maxWidth = r - l - rw)
        p.text(n.title, x, y, nextTitle.typo, nextTitle.color, maxWidth = r - rw - x)
    } else {
        p.text(left, l, y, next.typo, next.color, maxWidth = r - l - rw)
    }
}
