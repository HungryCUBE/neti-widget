package io.github.hungrycube.lessonwidget.core.groups

import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import java.net.URLEncoder

/**
 * Поиск группы по тому, что ввёл человек: «эм63», «ЭМ-63», «эм 63»,
 * латиница вместо похожих русских букв, английская раскладка вместо русской.
 */
object GroupSearch {

    /** Канонический ключ: заглавные, без пробелов и дефисов, похожие латинские буквы → русские. */
    fun key(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.uppercase()) {
            val mapped = when (c) {
                'Ё' -> 'Е'
                else -> LOOKALIKE[c] ?: c
            }
            if (mapped.isLetterOrDigit()) sb.append(mapped)
        }
        return sb.toString()
    }

    /** Текст, набранный в английской раскладке вместо русской: «'v63» → «эм63». */
    fun swapLayout(text: String): String = buildString {
        for (c in text.lowercase()) append(LAYOUT[c] ?: c)
    }

    fun search(query: String, groups: List<GroupInfo>, limit: Int = 50): List<GroupInfo> {
        val keys = listOf(key(query), key(swapLayout(query))).filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return emptyList()
        return groups
            .mapNotNull { g ->
                val names = listOf(key(g.name), key(g.label)).distinct()
                val score = keys.minOf { k ->
                    names.minOf { gk ->
                        when {
                            gk == k -> 0
                            gk.startsWith(k) -> 1
                            gk.contains(k) -> 2
                            else -> 9
                        }
                    }
                }
                if (score < 9) g to score else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name }))
            .take(limit)
            .map { it.first }
    }

    /** Точное совпадение по ключу, если есть. */
    fun find(query: String, groups: List<GroupInfo>): GroupInfo? {
        val k = key(query)
        return groups.firstOrNull { key(it.name) == k }
            ?: key(swapLayout(query)).let { sk -> groups.firstOrNull { key(it.name) == sk } }
    }

    /**
     * Приводит ручной ввод к виду, как на сайте: заглавные, дефис между буквами и цифрами.
     * «эм63» → «ЭМ-63». Латиница остаётся латиницей (на сайте есть группы вроде EM-41).
     */
    fun normalizeInput(text: String): String {
        val t = text.trim().uppercase().replace('Ё', 'Е').replace(Regex("""\s+"""), "")
        if (t.contains('-')) return t
        val m = Regex("""^([^\d]+)(\d.*)$""").find(t) ?: return t
        return m.groupValues[1] + "-" + m.groupValues[2]
    }

    /** Имя группы для URL: кириллица в UTF-8 («ЭМ-63» → «%D0%AD%D0%9C-63»). */
    fun urlEncode(group: String): String = URLEncoder.encode(group.trim(), "UTF-8")

    private val LOOKALIKE = mapOf(
        'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
        'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
    )

    private val LAYOUT: Map<Char, Char> = run {
        val en = "qwertyuiop[]asdfghjkl;'zxcvbnm,."
        val ru = "йцукенгшщзхъфывапролджэячсмитьбю"
        en.indices.associate { en[it] to ru[it] }
    }
}
