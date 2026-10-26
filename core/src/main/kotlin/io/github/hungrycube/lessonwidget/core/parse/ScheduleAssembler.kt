package io.github.hungrycube.lessonwidget.core.parse

import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.model.WeekAnchor
import java.time.DayOfWeek
import java.time.LocalDate

/** Собирает кэш группы из двух страниц сайта: «весь семестр» и «текущая неделя». */
object ScheduleAssembler {

    /**
     * @param semester страница `?group=…&print=true` — все пары с пометками недель.
     * @param week страница `?group=…` — номер текущей недели и точное расписание на неё; может отсутствовать.
     * @param today сегодняшняя дата по Новосибирску — если сайт не указал дату в шапке.
     * @param previous старый кэш: из него берётся номер недели, если сайт его не показал.
     */
    fun assemble(
        group: String,
        semester: ParsedSchedulePage,
        week: ParsedSchedulePage?,
        fetchedAt: Long,
        today: LocalDate,
        previous: GroupSchedule? = null,
    ): GroupSchedule {
        if (!semester.hasTable) throw ParseException("На сайте нет расписания группы $group")

        val weekNumber = week?.week
        val anchor = if (week != null && week.hasTable && weekNumber != null) {
            val day = week.today ?: today
            WeekAnchor(day.with(DayOfWeek.MONDAY).toEpochDay(), weekNumber)
        } else {
            null
        }

        return GroupSchedule(
            group = group,
            title = semester.title ?: week?.title ?: group,
            fetchedAt = fetchedAt,
            anchor = anchor ?: previous?.anchor,
            semesterWeeks = week?.weeksTotal ?: previous?.semesterWeeks,
            lessons = semester.lessons,
            anchorWeekLessons = if (anchor != null) week?.lessons else null,
        )
    }
}
