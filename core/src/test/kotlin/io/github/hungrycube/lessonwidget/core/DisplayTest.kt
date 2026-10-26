package io.github.hungrycube.lessonwidget.core

import io.github.hungrycube.lessonwidget.core.Samples.nsk
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.LineStatus
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.model.Lesson
import io.github.hungrycube.lessonwidget.core.model.WeekAnchor
import io.github.hungrycube.lessonwidget.core.time.ScheduleCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class DisplayTest {

    private val em63 = Samples.em63()

    private fun display(millis: Long, schedule: GroupSchedule? = em63, subgroup: Int? = null) =
        DisplayBuilder.build(schedule, "ЭМ-63", subgroup, millis)

    @Test
    fun weekNumbers() {
        val cal = ScheduleCalendar(em63)
        assertEquals(9, cal.weekOf(LocalDate.of(2026, 10, 26)))
        assertEquals(9, cal.weekOf(LocalDate.of(2026, 11, 1)))
        assertEquals(10, cal.weekOf(LocalDate.of(2026, 11, 2)))
        assertEquals(1, cal.weekOf(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun parityPicksRightSubgroupEachWeek() {
        val cal = ScheduleCalendar(em63)
        val odd = cal.lessonsOn(LocalDate.of(2026, 10, 27)).filter { it.title.startsWith("Информационные") }
        val even = cal.lessonsOn(LocalDate.of(2026, 11, 3)).filter { it.title.startsWith("Информационные") }
        assertTrue(odd.isNotEmpty() && odd.all { it.subgroup == 2 })
        assertTrue(even.isNotEmpty() && even.all { it.subgroup == 1 })
        val onlyFirst = ScheduleCalendar(em63, subgroup = 1).lessonsOn(LocalDate.of(2026, 10, 27))
        assertTrue(onlyFirst.none { it.title.startsWith("Информационные") })
    }

    @Test
    fun explicitWeeksOutsideAnchorWeek() {
        val cal = ScheduleCalendar(em63)
        // Химия-лекция по средам только в недели 1–9 и 17.
        assertTrue(cal.lessonsOn(LocalDate.of(2026, 10, 28)).any { it.title == "Химия" })
        assertTrue(cal.lessonsOn(LocalDate.of(2026, 11, 4)).none { it.title == "Химия" && it.type == "Лекция" })
        assertTrue(cal.lessonsOn(LocalDate.of(2026, 12, 23)).any { it.title == "Химия" && it.type == "Лекция" })
    }

    @Test
    fun lessonInProgress() {
        val d = display(nsk(2026, 10, 26, 12, 30))
        assertEquals(Phase.LESSON, d.phase)
        assertEquals("Линейная алгебра", d.title)
        assertEquals(3, d.focus?.number)
        assertEquals("лекция", d.focus?.type)
        assertEquals("лек", d.focus?.typeShort)
        assertEquals("1-402", d.focus?.room)
        assertEquals("3 пара · лекция", d.headline)
        assertEquals(60, d.minutesLeft)
        assertEquals(nsk(2026, 10, 26, 13, 30), d.countdownTo)
        assertEquals(nsk(2026, 10, 26, 13, 30), d.boundaryAt)
        assertEquals(1f / 3f, d.progress!!, 0.01f)
        assertEquals("Математический анализ", d.next?.title)
        assertEquals(9, d.week)
        assertEquals(LineStatus.PAST, d.day.first().status)
        assertEquals(LineStatus.NOW, d.day[1].status)
    }

    @Test
    fun breakBetweenLessons() {
        val d = display(nsk(2026, 10, 26, 11, 50))
        assertEquals(Phase.BREAK, d.phase)
        assertEquals("Линейная алгебра", d.title)
        assertEquals(10, d.minutesLeft)
        assertEquals(nsk(2026, 10, 26, 12, 0), d.boundaryAt)
        assertEquals(1f / 3f, d.progress!!, 0.01f)
    }

    @Test
    fun beforeFirstLesson() {
        val d = display(nsk(2026, 10, 26, 7, 0))
        assertEquals(Phase.BEFORE_FIRST, d.phase)
        assertEquals("Физическая культура и спорт", d.title)
        assertEquals(2, d.focus?.number)
        assertNull(d.progress)
    }

    @Test
    fun dayOverShowsTomorrow() {
        val d = display(nsk(2026, 10, 26, 23, 0))
        assertEquals(Phase.DAY_OVER, d.phase)
        assertEquals("Завтра", d.dayLabel)
        assertEquals(LocalDate.of(2026, 10, 27), d.focus?.date)
        assertEquals(nsk(2026, 10, 27, 0, 0), d.boundaryAt)
        assertNull(d.countdownTo)
    }

    @Test
    fun sundayIsDayOff() {
        val d = display(nsk(2026, 11, 1, 12, 0))
        assertEquals(Phase.DAY_OFF, d.phase)
        assertEquals("Завтра", d.dayLabel)
        assertEquals("Физическая культура и спорт", d.title)
    }

    @Test
    fun saturdayEveningShowsMondayWithWeekday() {
        val d = display(nsk(2026, 10, 31, 22, 0))
        assertEquals(Phase.DAY_OVER, d.phase)
        assertEquals("Пн, 2 ноя", d.dayLabel)
    }

    @Test
    fun afterSemesterNoLessons() {
        val d = display(nsk(2027, 3, 15, 12, 0))
        assertEquals(Phase.NO_LESSONS, d.phase)
        assertEquals("Пар нет", d.title)
        assertEquals("Каникулы или сессия", d.message)
    }

    @Test
    fun noData() {
        val d = display(nsk(2026, 10, 26, 12, 0), schedule = null)
        assertEquals(Phase.NO_DATA, d.phase)
        assertNull(d.boundaryAt)
    }

    @Test
    fun windowBetweenLessons() {
        val s = GroupSchedule(
            group = "Т-1", title = "Т-1", fetchedAt = 0,
            anchor = WeekAnchor(LocalDate.of(2026, 10, 26).toEpochDay(), 9), semesterWeeks = 18,
            lessons = listOf(
                Lesson(1, 510, 600, "Первая"),
                Lesson(1, 840, 930, "Вторая"),
            ),
        )
        val d = display(nsk(2026, 10, 26, 11, 0), schedule = s)
        assertEquals(Phase.WINDOW, d.phase)
        assertEquals("Вторая", d.title)
        assertEquals(180, d.minutesLeft)
    }

    @Test
    fun severalEntriesInOneSlotAreKept() {
        val s = GroupSchedule(
            group = "Т-1", title = "Т-1", fetchedAt = 0,
            anchor = WeekAnchor(LocalDate.of(2026, 10, 26).toEpochDay(), 9), semesterWeeks = 18,
            lessons = listOf(
                Lesson(1, 510, 600, "Физика", type = "Лабораторная", subgroup = 1, rooms = listOf("1-224")),
                Lesson(1, 510, 600, "Химия", type = "Лабораторная", subgroup = 2, rooms = listOf("1-407")),
            ),
        )
        val d = display(nsk(2026, 10, 26, 9, 0), schedule = s)
        assertEquals("Физика / Химия", d.title)
        assertEquals("1 пг 1-224 · 2 пг 1-407", d.focus?.room)
        assertEquals(2, d.focus?.entryCount)
        val only2 = display(nsk(2026, 10, 26, 9, 0), schedule = s, subgroup = 2)
        assertEquals("Химия", only2.title)
    }

    @Test
    fun phoneTimezoneDoesNotMatter() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val d = display(nsk(2026, 10, 26, 12, 30))
            assertEquals(Phase.LESSON, d.phase)
            assertEquals("Линейная алгебра", d.title)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
