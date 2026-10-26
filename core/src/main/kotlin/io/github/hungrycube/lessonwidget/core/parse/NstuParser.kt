package io.github.hungrycube.lessonwidget.core.parse

import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import io.github.hungrycube.lessonwidget.core.model.Lesson
import io.github.hungrycube.lessonwidget.core.model.Parity
import io.github.hungrycube.lessonwidget.core.model.WeekFilter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate

/** Результат разбора страницы расписания группы (недельной или «весь семестр»). */
data class ParsedSchedulePage(
    /** Есть ли на странице таблица расписания. Если нет — группа не найдена или расписание не готово. */
    val hasTable: Boolean,
    /** «ФМА · ЭМ-63». */
    val title: String?,
    /** «Сегодня 26 октября 2026» из шапки (только на недельной странице). */
    val today: LocalDate?,
    /** Номер учебной недели, которую показывает страница. */
    val week: Int?,
    /** Сколько недель в переключателе. */
    val weeksTotal: Int?,
    val lessons: List<Lesson>,
)

class ParseException(message: String) : Exception(message)

/**
 * Разбор HTML публичного расписания nstu.ru.
 * Разметка описана в samples/README.md.
 */
object NstuParser {

    fun parseSchedulePage(html: String): ParsedSchedulePage {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst(".schedule__title-h1")?.text()?.clean()
        val body = doc.selectFirst(".schedule__table-body")
            ?: return ParsedSchedulePage(false, title, null, null, null, emptyList())

        val lessons = ArrayList<Lesson>()
        for (dayRow in body.children()) {
            if (!dayRow.hasClass("schedule__table-row")) continue
            val cells = dayRow.children()
            val dayCell = cells.firstOrNull { it.attr("data-type") == "day" } ?: continue
            val dayEl = dayCell.selectFirst(".schedule__table-day") ?: continue
            val dayOfWeek = dayOfWeek(dayEl.ownText()) ?: continue
            val content = cells.firstOrNull { it !== dayCell } ?: continue
            for (timeRow in content.children()) {
                if (!timeRow.hasClass("schedule__table-row")) continue
                val timeCells = timeRow.children()
                val timeCell = timeCells.firstOrNull { it.attr("data-type") == "time" } ?: continue
                val (start, end) = parseTimeRange(timeCell.text()) ?: continue
                val itemsCell = timeCells.firstOrNull { it !== timeCell } ?: continue
                for (itemRow in itemsCell.children()) {
                    val item = itemRow.selectFirst(".schedule__table-item") ?: continue
                    parseItem(item, dayOfWeek, start, end)?.let(lessons::add)
                }
            }
        }

        val weekItems = doc.select(".schedule__weeks-item")
        val activeWeek = doc.selectFirst(".schedule__weeks-item.is-active")?.attr("data-week")?.toIntOrNull()
        val labelWeek = doc.selectFirst(".schedule__title-label")?.text()?.let(::parseWeekNumber)
        val weeksTotal = weekItems.mapNotNull { it.attr("data-week").toIntOrNull() }.maxOrNull()
        val today = doc.selectFirst(".schedule__title-content")?.text()?.let(::parseRussianDate)

        return ParsedSchedulePage(
            hasTable = true,
            title = title,
            today = today,
            week = labelWeek ?: activeWeek,
            weeksTotal = weeksTotal,
            lessons = lessons,
        )
    }

    private fun parseItem(item: Element, dayOfWeek: Int, start: Int, end: Int): Lesson? {
        val label = item.selectFirst(".schedule__table-label")?.text()?.clean()
        val type = item.selectFirst(".schedule__table-typework")?.text()?.clean()?.takeIf { it.isNotEmpty() }
        val roomText = item.selectFirst(".schedule__table-class")?.text()?.clean().orEmpty()
        val teachers = item.select("a").map { it.text().clean() }.filter { it.isNotEmpty() }

        val bare = item.clone()
        bare.select(".schedule__table-label, .schedule__table-typework, .schedule__table-class, a").remove()
        val raw = bare.text().clean().substringBefore('·').trim()
        if (raw.isEmpty()) return null

        val subgroupMatch = SUBGROUP.find(raw)
        val subgroup = subgroupMatch?.groupValues?.get(1)?.toIntOrNull()
        val title = (if (subgroupMatch != null) raw.removeRange(subgroupMatch.range) else raw)
            .trim().trimEnd(';', ',').trim()
        if (title.isEmpty()) return null

        return Lesson(
            dayOfWeek = dayOfWeek,
            startMinute = start,
            endMinute = end,
            title = title,
            type = type,
            subgroup = subgroup,
            rooms = roomText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            teachers = teachers,
            weeks = label?.let(::parseWeekLabel) ?: WeekFilter.EVERY,
        )
    }

    /** «по чётным», «по нечётным», «недели 1 3 5», «недели 1-9». */
    fun parseWeekLabel(text: String): WeekFilter {
        val t = text.lowercase().replace('ё', 'е')
        return when {
            "нечет" in t -> WeekFilter(parity = Parity.ODD)
            "чет" in t -> WeekFilter(parity = Parity.EVEN)
            "недел" in t -> {
                val weeks = sortedSetOf<Int>()
                for (m in WEEK_TOKEN.findAll(t)) {
                    val a = m.groupValues[1].toInt()
                    val b = m.groupValues[2].toIntOrNull()
                    if (b != null && b >= a && b - a < 60) weeks.addAll(a..b) else weeks.add(a)
                }
                if (weeks.isEmpty()) WeekFilter.EVERY else WeekFilter(weeks = weeks.toList())
            }
            else -> WeekFilter.EVERY
        }
    }

    /** «08:30-10:00» → минуты от полуночи. */
    fun parseTimeRange(text: String): Pair<Int, Int>? {
        val m = TIME_RANGE.find(text) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val start = h1.toInt() * 60 + m1.toInt()
        val end = h2.toInt() * 60 + m2.toInt()
        return if (end > start) start to end else null
    }

    fun parseWeekNumber(text: String): Int? =
        Regex("""(\d{1,2})\s*учебн""").find(text)?.groupValues?.get(1)?.toIntOrNull()

    /** «Сегодня 26 октября 2026, понедельник» → дата. */
    fun parseRussianDate(text: String): LocalDate? {
        val m = Regex("""(\d{1,2})\s+([а-яё]+)\s+(\d{4})""").find(text.lowercase()) ?: return null
        val month = MONTHS.indexOfFirst { m.groupValues[2].startsWith(it) }
        if (month < 0) return null
        return runCatching {
            LocalDate.of(m.groupValues[3].toInt(), month + 1, m.groupValues[1].toInt())
        }.getOrNull()
    }

    private fun dayOfWeek(text: String): Int? =
        when (text.clean().lowercase().take(2)) {
            "пн" -> 1
            "вт" -> 2
            "ср" -> 3
            "чт" -> 4
            "пт" -> 5
            "сб" -> 6
            "вс" -> 7
            else -> null
        }

    /** Список групп со страницы «Расписание занятий». Группы, у которых расписание ещё готовится, пропускаются. */
    fun parseGroups(html: String): List<GroupInfo> {
        val doc = Jsoup.parse(html)
        val result = ArrayList<GroupInfo>()
        val seen = HashSet<String>()
        for (faculty in doc.select(".schedule__faculty")) {
            val facultyName = faculty.selectFirst(".schedule__faculty-title")?.text()?.clean().orEmpty()
            for (type in faculty.select(".schedule__faculty-type")) {
                val form = type.selectFirst(".schedule__faculty-type__title")?.text()?.clean().orEmpty()
                var level = ""
                for (child in type.children()) {
                    when {
                        child.hasClass("schedule__faculty-type__subtitle") -> level = child.text().clean()
                        child.hasClass("schedule__faculty-courses") -> {
                            for (course in child.select(".schedule__faculty-course")) {
                                val courseNo = course.selectFirst(".schedule__faculty-course__title")
                                    ?.text()?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 0
                                for (a in course.select("a.schedule__faculty-groups__item")) {
                                    val name = a.text().clean()
                                    if (name.isNotEmpty() && seen.add(name)) {
                                        result += GroupInfo(name, facultyName, form, level, courseNo)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // На случай изменения вёрстки: любые ссылки на расписание группы.
        if (result.isEmpty()) {
            for (a in doc.select("a[href*=schedule?group=]")) {
                val name = a.text().clean()
                if (name.isNotEmpty() && seen.add(name)) result += GroupInfo(name, "", "", "", 0)
            }
        }
        return result
    }

    private fun String.clean(): String =
        replace("&middot;", "·").replace("&middot", "·").replace("&nbsp", " ")
            .replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

    private val SUBGROUP = Regex(""";?\s*(\d+)\s*п\.?\s*гр\.?""")
    private val WEEK_TOKEN = Regex("""(\d{1,2})(?:\s*[-–]\s*(\d{1,2}))?""")
    private val TIME_RANGE = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
    private val MONTHS = listOf(
        "январ", "феврал", "март", "апрел", "ма", "июн",
        "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
    )
}
