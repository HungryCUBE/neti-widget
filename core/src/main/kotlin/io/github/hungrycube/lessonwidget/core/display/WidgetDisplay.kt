package io.github.hungrycube.lessonwidget.core.display

import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.core.time.ScheduleCalendar
import io.github.hungrycube.lessonwidget.core.time.Slot
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

/** Что сейчас показывает виджет. */
enum class Phase {
    /** Идёт пара. */
    LESSON,
    /** Короткая перемена между парами (до 30 минут). */
    BREAK,
    /** «Окно» — свободная пара между занятиями. */
    WINDOW,
    /** Сегодня пары ещё не начались. */
    BEFORE_FIRST,
    /** Сегодняшние пары закончились, показываем следующий учебный день. */
    DAY_OVER,
    /** Сегодня пар нет, показываем следующий учебный день. */
    DAY_OFF,
    /** В ближайшие недели пар нет (каникулы, сессия). */
    NO_LESSONS,
    /** Расписание ещё не скачано. */
    NO_DATA,
}

/** Сведения о слоте, готовые для вывода. */
data class SlotInfo(
    val date: LocalDate,
    val number: Int?,
    val title: String,
    /** «практика» — строчными. */
    val type: String?,
    /** «пр», «лек», «лаб». */
    val typeShort: String?,
    val room: String?,
    val startMinute: Int,
    val endMinute: Int,
    /** «1 п.гр.», если пара только у одной подгруппы. */
    val subgroupNote: String?,
    /** Сколько записей в слоте (подгруппы, разные предметы). */
    val entryCount: Int,
)

enum class LineStatus { PAST, NOW, FUTURE }

data class DayLine(val info: SlotInfo, val status: LineStatus)

data class WidgetDisplay(
    val phase: Phase,
    val group: String,
    /** Номер учебной недели сегодня. */
    val week: Int?,
    /** Пара, о которой виджет говорит крупно: текущая или ближайшая. */
    val focus: SlotInfo?,
    /** Короткая метка состояния: «Сейчас», «Перемена», «Окно», «Завтра»… */
    val status: String,
    /** Строка для шапки: «2 пара · практика», «Перемена», «Завтра · 1 пара». */
    val headline: String,
    /** Крупный заголовок: название пары или «Пар нет». */
    val title: String,
    /** Момент, до которого идёт обратный отсчёт (конец пары или начало следующей), epoch millis. */
    val countdownTo: Long?,
    /** Сколько минут осталось до [countdownTo], округлено вверх. */
    val minutesLeft: Int?,
    /** Доля прошедшего времени пары или перемены, 0..1. */
    val progress: Float?,
    /** Пара после [focus] в тот же день. */
    val next: SlotInfo?,
    /** «Сегодня», «Завтра», «Пн, 2 ноя» — день, к которому относится [focus]. */
    val dayLabel: String,
    /** Все пары дня [focus] — для большого виджета. */
    val day: List<DayLine>,
    /** Когда состояние сменится и виджет нужно перерисовать, epoch millis. */
    val boundaryAt: Long?,
    /** Пояснение для пустых состояний. */
    val message: String?,
) {
    val hasCountdown: Boolean get() = countdownTo != null
}

object DisplayBuilder {

    private const val BREAK_MAX_MINUTES = 30

    fun build(schedule: GroupSchedule?, group: String, subgroup: Int?, nowMillis: Long): WidgetDisplay {
        val now = Instant.ofEpochMilli(nowMillis).atZone(NOVOSIBIRSK)
        val today = now.toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay(NOVOSIBIRSK).toInstant().toEpochMilli()

        if (schedule == null) {
            return empty(Phase.NO_DATA, group, null, "Нет данных", "Нажмите, чтобы обновить", null, today)
        }

        val cal = ScheduleCalendar(schedule, subgroup)
        val week = cal.weekOf(today)
        val slots = cal.slotsOn(today)

        fun millis(slotTime: java.time.ZonedDateTime) = slotTime.toInstant().toEpochMilli()

        val current = slots.firstOrNull { nowMillis >= millis(it.start) && nowMillis < millis(it.end) }
        if (current != null) {
            val start = millis(current.start)
            val end = millis(current.end)
            val next = slots.firstOrNull { it.startMinute >= current.endMinute }
            val info = current.info()
            return WidgetDisplay(
                phase = Phase.LESSON,
                group = group,
                week = week,
                focus = info,
                status = "Сейчас",
                headline = listOfNotNull(info.number?.let { "$it пара" } ?: "Пара", info.type).joinToString(" · "),
                title = info.title,
                countdownTo = end,
                minutesLeft = minutesUntil(nowMillis, end),
                progress = fraction(nowMillis, start, end),
                next = next?.info(),
                dayLabel = "Сегодня",
                day = dayLines(slots, nowMillis),
                boundaryAt = end,
                message = null,
            )
        }

        val upcoming = slots.firstOrNull { millis(it.start) > nowMillis }
        if (upcoming != null) {
            val start = millis(upcoming.start)
            val prev = slots.lastOrNull { millis(it.end) <= nowMillis }
            val info = upcoming.info()
            val numberText = info.number?.let { "$it пара" } ?: "пара"
            val phase: Phase
            val status: String
            val headline: String
            val progress: Float?
            if (prev == null) {
                phase = Phase.BEFORE_FIRST
                status = "Скоро"
                headline = "Сегодня · $numberText"
                progress = null
            } else {
                val gap = upcoming.startMinute - prev.endMinute
                phase = if (gap <= BREAK_MAX_MINUTES) Phase.BREAK else Phase.WINDOW
                status = if (phase == Phase.BREAK) "Перемена" else "Окно"
                headline = "$status · далее $numberText"
                progress = fraction(nowMillis, millis(prev.end), start)
            }
            return WidgetDisplay(
                phase = phase,
                group = group,
                week = week,
                focus = info,
                status = status,
                headline = headline,
                title = info.title,
                countdownTo = start,
                minutesLeft = minutesUntil(nowMillis, start),
                progress = progress,
                next = slots.firstOrNull { it.startMinute >= upcoming.endMinute }?.info(),
                dayLabel = "Сегодня",
                day = dayLines(slots, nowMillis),
                boundaryAt = start,
                message = null,
            )
        }

        val nextDay = cal.nextStudyDay(today)
        if (nextDay == null) {
            val reason = if (!cal.isTeachingWeek(today)) "Каникулы или сессия" else "В ближайшие 4 недели пар нет"
            return empty(Phase.NO_LESSONS, group, week, "Пар нет", reason, midnight, today)
        }
        val (date, daySlots) = nextDay
        val label = dayLabel(date, today)
        val first = daySlots.first().info()
        val phase = if (slots.isEmpty()) Phase.DAY_OFF else Phase.DAY_OVER
        return WidgetDisplay(
            phase = phase,
            group = group,
            week = week,
            focus = first,
            status = label,
            headline = "$label · " + (first.number?.let { "$it пара" } ?: "пара"),
            title = first.title,
            countdownTo = null,
            minutesLeft = null,
            progress = null,
            next = daySlots.getOrNull(1)?.info(),
            dayLabel = label,
            day = daySlots.map { DayLine(it.info(), LineStatus.FUTURE) },
            boundaryAt = midnight,
            message = if (phase == Phase.DAY_OFF) "Сегодня пар нет" else "Пары на сегодня закончились",
        )
    }

    private fun empty(
        phase: Phase, group: String, week: Int?, title: String, message: String, boundary: Long?, today: LocalDate,
    ) = WidgetDisplay(
        phase = phase, group = group, week = week, focus = null, status = title, headline = title,
        title = title, countdownTo = null, minutesLeft = null, progress = null, next = null,
        dayLabel = "Сегодня", day = emptyList(), boundaryAt = boundary, message = message,
    )

    private fun dayLines(slots: List<Slot>, nowMillis: Long): List<DayLine> = slots.map {
        val start = it.start.toInstant().toEpochMilli()
        val end = it.end.toInstant().toEpochMilli()
        val status = when {
            nowMillis >= end -> LineStatus.PAST
            nowMillis >= start -> LineStatus.NOW
            else -> LineStatus.FUTURE
        }
        DayLine(it.info(), status)
    }

    private fun fraction(now: Long, from: Long, to: Long): Float =
        if (to <= from) 0f else ((now - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    private fun minutesUntil(now: Long, target: Long): Int =
        ceil((target - now).coerceAtLeast(0) / 60_000.0).toInt()

    fun dayLabel(date: LocalDate, today: LocalDate): String = when {
        date.isEqual(today) -> "Сегодня"
        date.isEqual(today.plusDays(1)) -> "Завтра"
        else -> "${WEEKDAYS[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    }

    val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
}

fun Slot.info(): SlotInfo {
    val titles = entries.map { it.title }.distinct()
    val types = entries.mapNotNull { it.type?.lowercase() }.distinct()
    val withSubgroups = entries.count { it.subgroup != null }
    val room = if (entries.size > 1 && withSubgroups > 0) {
        entries.map { e ->
            val rooms = e.rooms.joinToString(", ")
            listOfNotNull(e.subgroup?.let { "$it пг" }, rooms.ifEmpty { null }).joinToString(" ")
        }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
    } else {
        entries.flatMap { it.rooms }.distinct().joinToString(", ")
    }
    return SlotInfo(
        date = date,
        number = number,
        title = titles.joinToString(" / "),
        type = types.joinToString("/").ifEmpty { null },
        typeShort = types.map(::shortType).distinct().joinToString("/").ifEmpty { null },
        room = room.ifEmpty { null },
        startMinute = startMinute,
        endMinute = endMinute,
        subgroupNote = entries.singleOrNull()?.subgroup?.let { "$it п.гр." },
        entryCount = entries.size,
    )
}

fun shortType(type: String): String = when {
    type.startsWith("лек") -> "лек"
    type.startsWith("пр") -> "пр"
    type.startsWith("лаб") -> "лаб"
    else -> type.take(3)
}
