package io.github.hungrycube.lessonwidget.core.model

import kotlinx.serialization.Serializable

/** Чётность учебной недели. */
@Serializable
enum class Parity { EVEN, ODD }

/**
 * На каких учебных неделях проходит занятие.
 * Пустой фильтр (оба поля null) — каждую неделю.
 */
@Serializable
data class WeekFilter(
    val parity: Parity? = null,
    val weeks: List<Int>? = null,
) {
    val isEveryWeek: Boolean get() = parity == null && weeks == null

    fun matches(week: Int): Boolean = when {
        weeks != null -> week in weeks
        parity == Parity.EVEN -> week % 2 == 0
        parity == Parity.ODD -> week % 2 != 0
        else -> true
    }

    /** Короткая подпись для списка: «по чётным», «нед. 2, 6, 10». */
    fun label(): String? = when {
        weeks != null -> "нед. " + weeks.joinToString(", ")
        parity == Parity.EVEN -> "по чётным"
        parity == Parity.ODD -> "по нечётным"
        else -> null
    }

    companion object {
        val EVERY = WeekFilter()
    }
}

/** Одна запись расписания: предмет в конкретном слоте дня недели. */
@Serializable
data class Lesson(
    /** 1 — понедельник … 7 — воскресенье. */
    val dayOfWeek: Int,
    /** Начало и конец в минутах от полуночи по Новосибирску. */
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    /** «Лекция», «Практика», «Лабораторная» или null, если на сайте не указано. */
    val type: String? = null,
    val subgroup: Int? = null,
    val rooms: List<String> = emptyList(),
    val teachers: List<String> = emptyList(),
    val weeks: WeekFilter = WeekFilter.EVERY,
)

/** Привязка номера учебной недели к календарю: неделя [week] начинается в понедельник [mondayEpochDay]. */
@Serializable
data class WeekAnchor(
    val mondayEpochDay: Long,
    val week: Int,
)

/** Кэш расписания одной группы. */
@Serializable
data class GroupSchedule(
    val group: String,
    /** Заголовок с сайта, например «ФМА · ЭМ-63». */
    val title: String,
    /** Когда данные были скачаны, epoch millis. */
    val fetchedAt: Long,
    val anchor: WeekAnchor? = null,
    /** Сколько учебных недель в семестре (по переключателю недель на сайте). */
    val semesterWeeks: Int? = null,
    /** Все занятия семестра с пометками недель. */
    val lessons: List<Lesson>,
    /** Точное расписание недели [anchor] — со страницы текущей недели. */
    val anchorWeekLessons: List<Lesson>? = null,
)

/** Группа из общего списка на сайте. */
@Serializable
data class GroupInfo(
    /** Имя, как в адресе страницы расписания: «ИСТР-КН-61». */
    val name: String,
    val faculty: String,
    /** «Очное отделение», «Заочное отделение» … */
    val form: String,
    /** «Бакалавриат, специалитет», «Магистратура» … */
    val level: String,
    /** Номер курса или 0, если не указан. */
    val course: Int,
    /** Подпись на сайте, бывает короче имени: «КН-61». */
    val label: String = name,
)
