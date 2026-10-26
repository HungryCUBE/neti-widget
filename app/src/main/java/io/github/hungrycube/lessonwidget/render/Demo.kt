package io.github.hungrycube.lessonwidget.render

import io.github.hungrycube.lessonwidget.core.display.DayLine
import io.github.hungrycube.lessonwidget.core.display.LineStatus
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.SlotInfo
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import java.time.LocalDate

/** Момент из макета: идёт вторая пара, до конца 34 минуты. Для превью, пока расписание не скачано. */
object Demo {
    fun display(nowMillis: Long): WidgetDisplay {
        val today = LocalDate.now()
        fun slot(n: Int, title: String, type: String, short: String, room: String, start: Int, end: Int) =
            SlotInfo(today, n, title, type, short, room, start, end, null, 1)
        val first = slot(1, "Линейная алгебра", "лекция", "лек", "1-402", 510, 600)
        val current = slot(2, "Матанализ", "практика", "пр", "7-305", 615, 705)
        val next = slot(3, "Физика", "лекция", "лек", "1-224", 720, 810)
        val last = slot(4, "Химия", "лабораторная", "лаб", "1-407", 840, 930)
        return WidgetDisplay(
            phase = Phase.LESSON,
            group = "ЭМ-63",
            week = 9,
            focus = current,
            status = "Сейчас",
            headline = "2 пара · практика",
            title = current.title,
            countdownTo = nowMillis + 34 * 60_000L,
            minutesLeft = 34,
            progress = 0.62f,
            next = next,
            dayLabel = "Сегодня",
            day = listOf(
                DayLine(first, LineStatus.PAST),
                DayLine(current, LineStatus.NOW),
                DayLine(next, LineStatus.FUTURE),
                DayLine(last, LineStatus.FUTURE),
            ),
            boundaryAt = nowMillis + 34 * 60_000L,
            message = null,
        )
    }
}
