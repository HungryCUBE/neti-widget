package io.github.hungrycube.lessonwidget.core

import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/** Сохранённые страницы nstu.ru из папки samples/. Скачаны в понедельник 26.10.2026, 9-я учебная неделя. */
object Samples {
    private val dir = File(System.getProperty("samples.dir") ?: "../samples")

    fun read(name: String): String = File(dir, name).readText(Charsets.UTF_8)

    val semesterFiles = listOf(
        "schedule_EM-63_semester.html",
        "schedule_AVT-308_semester.html",
        "schedule_PM-41_semester.html",
        "schedule_AAM-25_semester.html",
        "schedule_EM-41-latin_semester.html",
        "schedule_ABs-322_semester.html",
    )

    val SAMPLE_DAY: LocalDate = LocalDate.of(2026, 10, 26)

    fun em63(): GroupSchedule = ScheduleAssembler.assemble(
        group = "ЭМ-63",
        semester = NstuParser.parseSchedulePage(read("schedule_EM-63_semester.html")),
        week = NstuParser.parseSchedulePage(read("schedule_EM-63_week.html")),
        fetchedAt = 0L,
        today = SAMPLE_DAY,
    )

    fun nsk(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, NOVOSIBIRSK).toInstant().toEpochMilli()
}
