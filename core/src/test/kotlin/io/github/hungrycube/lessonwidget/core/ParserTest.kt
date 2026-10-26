package io.github.hungrycube.lessonwidget.core

import io.github.hungrycube.lessonwidget.core.model.Parity
import io.github.hungrycube.lessonwidget.core.model.WeekFilter
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParserTest {

    private val week = NstuParser.parseSchedulePage(Samples.read("schedule_EM-63_week.html"))
    private val semester = NstuParser.parseSchedulePage(Samples.read("schedule_EM-63_semester.html"))

    @Test
    fun weekPageHeader() {
        assertTrue(week.hasTable)
        assertEquals("ФМА · ЭМ-63", week.title)
        assertEquals(LocalDate.of(2026, 10, 26), week.today)
        assertEquals(9, week.week)
        assertEquals(18, week.weeksTotal)
    }

    @Test
    fun mondayLessons() {
        val monday = week.lessons.filter { it.dayOfWeek == 1 }
        assertEquals(
            listOf("Физическая культура и спорт", "Линейная алгебра", "Математический анализ", "Математический анализ"),
            monday.map { it.title },
        )
        val pe = monday[0]
        assertEquals(10 * 60 + 15, pe.startMinute)
        assertEquals(11 * 60 + 45, pe.endMinute)
        assertNull(pe.type)
        assertEquals(listOf("спорткомплекс"), pe.rooms)
        assertTrue(pe.teachers.isEmpty())

        val algebra = monday[1]
        assertEquals(12 * 60, algebra.startMinute)
        assertEquals(13 * 60 + 30, algebra.endMinute)
        assertEquals("Лекция", algebra.type)
        assertEquals(listOf("1-402"), algebra.rooms)
        assertEquals(listOf("Чуриков Д. В."), algebra.teachers)
        assertTrue(algebra.weeks.isEveryWeek)
    }

    @Test
    fun weekPageHasOnlyThisWeeksSubgroup() {
        val itop = week.lessons.filter { it.dayOfWeek == 2 && it.title.startsWith("Информационные технологии") }
        assertTrue(itop.isNotEmpty())
        assertTrue(itop.all { it.subgroup == 2 })
        assertEquals("Информационные технологии и основы программирования", itop.first().title)
    }

    @Test
    fun semesterParityAndSubgroups() {
        assertNull(semester.week)
        val itop = semester.lessons.filter { it.dayOfWeek == 2 && it.title.startsWith("Информационные технологии") }
        assertEquals(4, itop.size)
        assertTrue(itop.filter { it.subgroup == 1 }.all { it.weeks.parity == Parity.EVEN })
        assertTrue(itop.filter { it.subgroup == 2 }.all { it.weeks.parity == Parity.ODD })
        assertTrue(itop.all { it.type == "Лабораторная" && it.rooms.isNotEmpty() })
    }

    @Test
    fun explicitWeekList() {
        val chem = semester.lessons.first { it.dayOfWeek == 3 && it.title == "Химия" }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 17), chem.weeks.weeks)
        assertTrue(chem.weeks.matches(17))
        assertFalse(chem.weeks.matches(10))
    }

    @Test
    fun lessonWithoutRoomAndType() {
        val project = semester.lessons.filter { it.title == "Основы проектной деятельности" }
        assertEquals(3, project.size)
        assertTrue(project.all { it.rooms.isEmpty() && it.type == null })
    }

    @Test
    fun electiveWithSubgroupAndWeeks() {
        val avt = NstuParser.parseSchedulePage(Samples.read("schedule_AVT-308_semester.html"))
        val elective = avt.lessons.first {
            it.title == "Теория случайных процессов (Дисциплина по выбору)" && it.subgroup == 1
        }
        assertEquals(listOf(2, 6, 10, 14, 18), elective.weeks.weeks)
        assertEquals("Лабораторная", elective.type)
    }

    @Test
    fun severalTeachersAndRooms() {
        val all = Samples.semesterFiles.flatMap { NstuParser.parseSchedulePage(Samples.read(it)).lessons }
        assertNotNull(all.firstOrNull { it.teachers.containsAll(listOf("Питько Я. А.", "Миллер В. В.")) })
        assertNotNull(all.firstOrNull { it.rooms == listOf("1-203а", "1-203б") })
        assertNotNull(all.firstOrNull { it.subgroup == 3 })
    }

    @Test
    fun allSamplesAreClean() {
        val bells = setOf(510, 615, 720, 840, 945, 1050, 1155, 1260)
        for (file in Samples.semesterFiles) {
            val page = NstuParser.parseSchedulePage(Samples.read(file))
            assertTrue(file, page.hasTable)
            assertTrue(file, page.lessons.size > 10)
            for (l in page.lessons) {
                assertTrue("$file: ${l.title}", l.title.isNotBlank())
                assertFalse("$file: ${l.title}", l.title.contains("п.гр") || l.title.contains("·") || l.title.contains("&"))
                assertTrue("$file: ${l.title}", l.startMinute in bells && l.endMinute > l.startMinute)
                assertTrue(l.dayOfWeek in 1..6)
            }
        }
    }

    @Test
    fun pageWithoutTable() {
        val page = NstuParser.parseSchedulePage("<html><body><main>Ничего нет</main></body></html>")
        assertFalse(page.hasTable)
        assertTrue(page.lessons.isEmpty())
    }

    @Test
    fun weekLabels() {
        assertEquals(WeekFilter(parity = Parity.EVEN), NstuParser.parseWeekLabel("по чётным"))
        assertEquals(WeekFilter(parity = Parity.ODD), NstuParser.parseWeekLabel("по нечётным"))
        assertEquals(WeekFilter(parity = Parity.ODD), NstuParser.parseWeekLabel("по нечетным неделям"))
        assertEquals(listOf(1, 2, 3, 4, 7), NstuParser.parseWeekLabel("недели 1-4 7").weeks)
        assertTrue(NstuParser.parseWeekLabel("").isEveryWeek)
    }

    @Test
    fun groupsList() {
        val groups = NstuParser.parseGroups(Samples.read("groups.html"))
        val expected = Samples.read("groups.txt").lines().filter { it.isNotBlank() }.toSet()
        assertEquals(expected, groups.map { it.name }.toSet())
        val em63 = groups.first { it.name == "ЭМ-63" }
        assertEquals(1, em63.course)
        assertTrue(em63.faculty.isNotEmpty())
        assertTrue(em63.form.isNotEmpty())
        assertTrue(groups.any { it.name == "EM-41" })
        assertFalse(groups.any { it.name == "СОП30-26" })
        assertTrue(groups.any { it.level == "Магистратура" })
    }
}
