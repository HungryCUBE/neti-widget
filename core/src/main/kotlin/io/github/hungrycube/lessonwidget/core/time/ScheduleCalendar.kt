package io.github.hungrycube.lessonwidget.core.time

import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.model.Lesson
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Всё время в приложении — новосибирское, независимо от пояса телефона. */
val NOVOSIBIRSK: ZoneId = ZoneId.of("Asia/Novosibirsk")

/** Начала пар по звонкам НГТУ (сайт, осень 2026). Время самих пар всегда берётся с сайта, это только для номера пары. */
private val BELL_STARTS = intArrayOf(
    8 * 60 + 30, 10 * 60 + 15, 12 * 60, 14 * 60,
    15 * 60 + 45, 17 * 60 + 30, 19 * 60 + 15, 21 * 60,
)

/** Номер пары по времени начала: точное совпадение со звонком или ближайший предыдущий звонок. */
fun lessonNumber(startMinute: Int): Int? {
    val exact = BELL_STARTS.indexOf(startMinute)
    if (exact >= 0) return exact + 1
    val before = BELL_STARTS.count { it <= startMinute + 5 }
    return before.takeIf { it > 0 }
}

/** Один временной слот дня: одна или несколько записей (подгруппы, разные недели). */
data class Slot(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val entries: List<Lesson>,
) {
    val number: Int? get() = lessonNumber(startMinute)
    val start: ZonedDateTime get() = date.atStartOfDay(NOVOSIBIRSK).plusMinutes(startMinute.toLong())
    val end: ZonedDateTime get() = date.atStartOfDay(NOVOSIBIRSK).plusMinutes(endMinute.toLong())
}

class ScheduleCalendar(
    private val schedule: GroupSchedule,
    /** Подгруппа пользователя или null — показывать все. */
    private val subgroup: Int? = null,
) {
    /** Номер учебной недели для даты или null, если сайт не сообщил номер. */
    fun weekOf(date: LocalDate): Int? {
        val anchor = schedule.anchor ?: return null
        val monday = date.with(DayOfWeek.MONDAY).toEpochDay()
        return anchor.week + Math.floorDiv(monday - anchor.mondayEpochDay, 7L).toInt()
    }

    /** Попадает ли дата в учебные недели семестра. */
    fun isTeachingWeek(date: LocalDate): Boolean {
        val week = weekOf(date) ?: return true
        val total = schedule.semesterWeeks
        return week >= 1 && (total == null || week <= total)
    }

    fun lessonsOn(date: LocalDate): List<Lesson> {
        if (!isTeachingWeek(date)) return emptyList()
        val dow = date.dayOfWeek.value
        val week = weekOf(date)
        val anchor = schedule.anchor
        val exact = schedule.anchorWeekLessons
        val source = if (anchor != null && exact != null && week == anchor.week) {
            exact.filter { it.dayOfWeek == dow }
        } else {
            schedule.lessons.filter { it.dayOfWeek == dow && (week == null || it.weeks.matches(week)) }
        }
        return source
            .filter { subgroup == null || it.subgroup == null || it.subgroup == subgroup }
            .sortedWith(compareBy<Lesson>({ it.startMinute }, { it.subgroup ?: 0 }, { it.title }))
    }

    fun slotsOn(date: LocalDate): List<Slot> =
        lessonsOn(date)
            .groupBy { it.startMinute to it.endMinute }
            .map { (time, entries) -> Slot(date, time.first, time.second, entries) }
            .sortedBy { it.startMinute }

    /** Первый день после [date] (не включая), в который есть пары, в пределах [horizonDays]. */
    fun nextStudyDay(date: LocalDate, horizonDays: Int = 28): Pair<LocalDate, List<Slot>>? {
        for (i in 1..horizonDays) {
            val d = date.plusDays(i.toLong())
            val slots = slotsOn(d)
            if (slots.isNotEmpty()) return d to slots
        }
        return null
    }
}

fun Instant.atNovosibirsk(): ZonedDateTime = atZone(NOVOSIBIRSK)

fun minuteOfDay(t: ZonedDateTime): Int = t.hour * 60 + t.minute

/** 510 → «8:30». */
fun formatMinute(minute: Int): String = "%d:%02d".format(minute / 60 % 24, minute % 60)
