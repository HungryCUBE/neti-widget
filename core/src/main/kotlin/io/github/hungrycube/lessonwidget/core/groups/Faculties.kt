package io.github.hungrycube.lessonwidget.core.groups

import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule

/**
 * Сокращения факультетов. В списке групп сайт пишет полное название («Факультет мехатроники и автоматизации»),
 * а в заголовке расписания — сокращение («ФМА · ЭМ-63»). Для незнакомых — первые буквы слов.
 */
object Faculties {
    private val KNOWN = mapOf(
        "факультет автоматики и вычислительной техники" to "АВТФ",
        "факультет летательных аппаратов" to "ФЛА",
        "механико-технологический факультет" to "МТФ",
        "факультет мехатроники и автоматизации" to "ФМА",
        "факультет прикладной математики и информатики" to "ФПМИ",
        "факультет радиотехники и электроники" to "РЭФ",
        "физико-технический факультет" to "ФТФ",
        "факультет энергетики" to "ФЭН",
        "факультет бизнеса" to "ФБ",
        "факультет гуманитарного образования" to "ФГО",
        "институт социальных технологий" to "ИСТ",
        "заочное отделение" to "ЗО",
    )
    private val SKIP = setOf("и", "по", "с", "для")

    fun short(name: String): String {
        val n = name.trim()
        if (n.isEmpty()) return ""
        KNOWN[n.lowercase()]?.let { return it }
        // Уже сокращение («ФМА»)?
        if (n.length <= 6 && !n.contains(' ')) return n
        val letters = n.split(' ', '-').filter { it.isNotBlank() && it.lowercase() !in SKIP }.map { it.first().uppercaseChar() }
        return if (letters.size in 2..5) letters.joinToString("") else n
    }

    /** Сокращение из заголовка расписания: «ФМА · ЭМ-63» → «ФМА». */
    fun fromTitle(title: String?, group: String): String? {
        val t = title?.trim() ?: return null
        val head = t.substringBefore('·', "").trim()
        return head.takeIf { it.isNotEmpty() && !it.equals(group, ignoreCase = true) }
    }
}

/** «ФМА · бакалавриат, специалитет» (+ «заочное», если форма не очная) — подпись группы в списке. */
fun GroupInfo.describe(): String {
    val parts = ArrayList<String>()
    Faculties.short(faculty).takeIf { it.isNotEmpty() }?.let(parts::add)
    level.trim().takeIf { it.isNotEmpty() }?.let { parts += it.lowercase() }
    val f = form.trim().lowercase()
    val formShort = f.removeSuffix(" отделение")
    if (f.isNotEmpty() && !f.startsWith("очн") && !faculty.lowercase().contains(formShort)) parts += formShort
    return parts.joinToString(" · ")
}

/** Сколько подгрупп у группы по её расписанию: 0 — пары не делятся на подгруппы. */
fun GroupSchedule.subgroupCount(): Int =
    (lessons.asSequence() + anchorWeekLessons.orEmpty().asSequence()).mapNotNull { it.subgroup }.maxOrNull() ?: 0
