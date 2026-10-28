package io.github.hungrycube.lessonwidget.render.layout

import android.graphics.LinearGradient
import android.graphics.Shader
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Frame
import io.github.hungrycube.lessonwidget.render.Typo
import io.github.hungrycube.lessonwidget.render.agenda
import io.github.hungrycube.lessonwidget.render.bevel
import io.github.hungrycube.lessonwidget.render.card
import io.github.hungrycube.lessonwidget.render.icon
import io.github.hungrycube.lessonwidget.render.iconFor
import io.github.hungrycube.lessonwidget.render.progress
import io.github.hungrycube.lessonwidget.render.progressHeight
import kotlin.math.max
import kotlin.math.min

/**
 * Окно Windows 95: заголовок с градиентом и кнопками, меню, иконка документа, прогресс из блоков,
 * строка с кнопкой и строка состояния. Цвета: bevelLight, bevelShadow, bevelOuterLight, bevelOuterDark,
 * titleBarStart, titleBarEnd, titleText. Иконки слотов window и document. metrics: iconSize, menuMinWidth.
 * Тексты: phase* (заголовок окна), menu, button*, row*, statusNext.
 */
internal fun Frame.retro() {
    card()
    bevel(0f, 0f, W, H, raised = true)
    val small = role("details").typo
    val bold = role("header").typo
    val text = c("text")

    // Заголовок окна
    val tbT = 3f
    val tbB = 21f
    p.fill.shader = LinearGradient(3f, 0f, W - 3f, 0f, c("titleBarStart"), c("titleBarEnd"), Shader.TileMode.CLAMP)
    p.canvas.drawRect(3f, tbT, W - 3f, tbB, p.fill)
    p.fill.shader = null
    iconFor("window")?.let { icon(it, 6f, tbT + 2f, 14f) }
    val btnW = 16f
    val btnH = 14f
    val bTop = tbT + 2f
    val closeR = W - 3f - 2f
    val minR = closeR - btnW - 2f
    val btnT = bold.sized(10f)
    for ((right, label) in listOf(closeR to sv("buttonClose"), minR to sv("buttonMinimize"))) {
        p.rect(right - btnW, bTop, right, bTop + btnH, c("surface"))
        bevel(right - btnW, bTop, right, bTop + btnH, raised = true, thick = false)
        p.text(label, right - btnW / 2f, bTop + (btnH - p.lineHeight(btnT)) / 2f, btnT, text, Align.CENTER)
    }
    p.text(sv("windowTitle"), 6f + 14f + 4f, tbT + (18f - p.lineHeight(bold)) / 2f, bold, c("titleText"), maxWidth = minR - btnW - 4f - 24f)

    // Меню
    var mx = 3f + 4f
    val my = tbB + 2f
    if (W > m("menuMinWidth", 160f)) {
        for (item in th.list("menu")) {
            val w = p.text(item, mx, my, small, text)
            val first = p.width(item.take(1), small)
            val base = p.baseline(my, small)
            p.line(mx, base + 1.5f, mx + first, base + 1.5f, text, 1f)
            mx += w + 10f
            if (mx > W - 30f) break
        }
    }
    val menuB = my + p.lineHeight(small) + 2f

    // Тело окна
    val bl = 3f + 8f
    val br = W - 3f - 8f
    var y = menuB + 4f
    val iconSize = m("iconSize", 32f)
    iconFor("document")?.let { icon(it, bl, y, iconSize) }
    val cx = bl + iconSize + 10f
    val titleT: Typo = role("title").typo
    var typeText = s("titleType") ?: ""
    // Флаг typeOptional: если название не влезает целиком, сначала убирается тип пары.
    if (flag("typeOptional") && typeText.isNotEmpty() && p.width(d.title, titleT) + p.width(typeText, small) > br - cx) typeText = ""
    val typeW = if (typeText.isNotEmpty()) p.width(typeText, small) else 0f
    val tw = p.text(d.title, cx, y, titleT, text, maxWidth = br - cx - min(typeW, (br - cx) * 0.4f))
    if (typeText.isNotEmpty()) {
        p.text(typeText, cx + tw, y + (p.lineHeight(titleT) - p.lineHeight(small)) / 2f + 1f, small, text, maxWidth = br - cx - tw)
    }
    p.text(detailsText(), cx, y + p.lineHeight(titleT) + 1f, small, text, maxWidth = br - cx)
    y += iconSize + 6f

    val statusH = 16f
    val statusT = H - 3f - 6f - statusH
    // Полоса прогресса
    val prog = d.progress
    val ph = progressHeight
    if (prog != null && y + ph < statusT - 4f) {
        progress(bl, y, br, prog)
        y += ph + 6f
    }
    // Текст и кнопка
    val rowH = p.lineHeight(small) + 4f
    if (y + rowH < statusT - 2f) {
        val btnText = when (d.phase) {
            Phase.LESSON -> sv("buttonLesson")
            Phase.NO_DATA -> sv("buttonNoData")
            else -> sv("buttonOk")
        }
        val bw = max(0f, p.width(btnText, small) + 20f)
        val bh = p.lineHeight(small) + 4f
        p.rect(br - bw, y, br, y + bh, c("surface"))
        bevel(br - bw, y, br, y + bh, raised = true)
        p.text(btnText, br - bw / 2f, y + 2f, small, text, Align.CENTER)
        val rowText = when {
            isLesson && d.minutesLeft != null -> sv("rowLesson")
            upcoming && d.minutesLeft != null -> sv("rowUpcoming")
            nextDay && f != null -> sv("rowNextDay")
            else -> d.message ?: ""
        }
        p.text(rowText, bl, y + 2f, small, text, maxWidth = br - bl - bw - 8f)
        y += rowH + 6f
    }
    if (large) agenda(y, statusT - 6f, left = bl, right = br)

    // Строка состояния
    p.rect(bl, statusT, br, statusT + statusH, c("surface"))
    bevel(bl, statusT, br, statusT + statusH, raised = false, thick = false)
    val statusText = if (d.next != null) sv("statusNext") else nextParts().first
    p.text(statusText, bl + 4f, statusT + (statusH - p.lineHeight(small)) / 2f, small, text, maxWidth = br - bl - 8f)
}
