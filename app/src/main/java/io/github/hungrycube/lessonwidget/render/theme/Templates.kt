package io.github.hungrycube.lessonwidget.render.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Шаблон текста из strings. Виды в JSON:
 * - "Далее · {next}" — строка; если хоть одного значения нет (нет аудитории и т. п.), шаблона нет целиком;
 * - ["{room}", "{type}"] — части через « · », отсутствующие пропускаются;
 * - {"parts": […], "sep": " "} — то же со своим разделителем;
 * - {"first": […]} — первый вариант, для которого есть все значения;
 * - {"one": "{n} пара", "few": "{n} пары", "many": "{n} пар"} — по числу {n}.
 * В строке: {имя}, {имя|lower|upper|cap|short} (short — до первой запятой), {@ключ} — другой шаблон.
 */
sealed interface Tpl {
    data class Text(val text: String) : Tpl
    data class Join(val parts: List<Tpl>, val sep: String) : Tpl
    data class First(val options: List<Tpl>) : Tpl
    data class Plural(val one: Tpl, val few: Tpl, val many: Tpl) : Tpl
}

object Templates {
    val PLACEHOLDER = Regex("\\{([^{}]+)\\}")
    val FILTERS = setOf("lower", "upper", "cap", "short")

    /** Разбирает шаблон; ошибки дописывает в [errors] с путём [path]. JSON null — шаблона нет. */
    fun parse(e: JsonElement, path: String, errors: MutableList<String>): Tpl? = when (e) {
        JsonNull -> null
        is JsonPrimitive -> if (e.isString) Tpl.Text(e.content) else null.also { errors += "$path: нужна строка в кавычках" }
        is JsonArray -> Tpl.Join(e.mapIndexedNotNull { i, it -> parse(it, "$path[$i]", errors) }, " · ")
        is JsonObject -> when {
            "parts" in e -> {
                val parts = e["parts"] as? JsonArray
                if (parts == null) {
                    errors += "$path.parts: нужен список"
                    null
                } else {
                    val sep = (e["sep"] as? JsonPrimitive)?.content ?: " · "
                    Tpl.Join(parts.mapIndexedNotNull { i, it -> parse(it, "$path.parts[$i]", errors) }, sep)
                }
            }
            "first" in e -> {
                val options = e["first"] as? JsonArray
                if (options == null) {
                    errors += "$path.first: нужен список"
                    null
                } else {
                    Tpl.First(options.mapIndexedNotNull { i, it -> parse(it, "$path.first[$i]", errors) })
                }
            }
            "one" in e || "few" in e || "many" in e -> {
                fun form(k: String) = e[k]?.let { parse(it, "$path.$k", errors) }
                val many = form("many") ?: form("few") ?: form("one")
                if (many == null) {
                    errors += "$path: нужны формы one, few, many"
                    null
                } else {
                    Tpl.Plural(form("one") ?: many, form("few") ?: many, many)
                }
            }
            else -> null.also { errors += "$path: непонятный шаблон (нужна строка, список, parts, first или one/few/many)" }
        }
    }

    /** Все подстановки {…} шаблона. */
    fun placeholders(t: Tpl): Sequence<String> = when (t) {
        is Tpl.Text -> PLACEHOLDER.findAll(t.text).map { it.groupValues[1] }
        is Tpl.Join -> t.parts.asSequence().flatMap { placeholders(it) }
        is Tpl.First -> t.options.asSequence().flatMap { placeholders(it) }
        is Tpl.Plural -> sequenceOf(t.one, t.few, t.many).flatMap { placeholders(it) }
    }

    /** Строки-списки (меню и т. п.): только части без подстановок. */
    fun list(t: Tpl?): List<String> = when (t) {
        is Tpl.Join -> t.parts.mapNotNull { (it as? Tpl.Text)?.text }
        is Tpl.Text -> listOf(t.text)
        else -> emptyList()
    }

    fun plural(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "one"
            mod10 in 2..4 && mod100 !in 12..14 -> "few"
            else -> "many"
        }
    }

    fun filter(v: String, name: String): String = when (name) {
        "lower" -> v.lowercase()
        "upper" -> v.uppercase()
        "cap" -> v.replaceFirstChar { it.uppercase() }
        "short" -> v.substringBefore(',')
        else -> v
    }
}

/** Подставляет значения в шаблоны стиля. [vars] — значение по имени или null, если его нет. */
class TextRenderer(private val tpls: Map<String, Tpl>, private val vars: (String) -> String?) {

    fun get(key: String, extra: Map<String, String?> = emptyMap()): String? = tpls[key]?.let { render(it, extra, 0) }

    private fun render(t: Tpl, extra: Map<String, String?>, depth: Int): String? = when (t) {
        is Tpl.Text -> expand(t.text, extra, depth)
        is Tpl.Join -> t.parts.mapNotNull { render(it, extra, depth) }.joinToString(t.sep)
        is Tpl.First -> t.options.firstNotNullOfOrNull { render(it, extra, depth) }
        is Tpl.Plural -> value("n", extra)?.toIntOrNull()?.let { n ->
            render(
                when (Templates.plural(n)) {
                    "one" -> t.one
                    "few" -> t.few
                    else -> t.many
                },
                extra, depth,
            )
        }
    }

    private fun expand(s: String, extra: Map<String, String?>, depth: Int): String? {
        if ('{' !in s) return s
        val out = StringBuilder()
        var last = 0
        for (m in Templates.PLACEHOLDER.findAll(s)) {
            out.append(s, last, m.range.first)
            val parts = m.groupValues[1].split('|')
            val name = parts[0].trim()
            var v = if (name.startsWith("@")) {
                if (depth >= 6) null else tpls[name.substring(1)]?.let { render(it, extra, depth + 1) }
            } else {
                value(name, extra)
            }
            if (v == null) return null
            for (f in parts.drop(1)) v = Templates.filter(v!!, f.trim())
            out.append(v)
            last = m.range.last + 1
        }
        out.append(s, last, s.length)
        return out.toString()
    }

    private fun value(name: String, extra: Map<String, String?>): String? = if (name in extra) extra[name] else vars(name)
}
