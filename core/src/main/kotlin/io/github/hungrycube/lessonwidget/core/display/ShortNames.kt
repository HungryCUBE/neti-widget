package io.github.hungrycube.lessonwidget.core.display

/**
 * Короткие названия предметов для виджета: длинные официальные названия
 * не помещаются в 4×2. В приложении всегда показываются полные.
 */
object ShortNames {

    private val known = mapOf(
        "Математический анализ" to "Матанализ",
        "Физическая культура и спорт" to "Физкультура",
        "Элективные дисциплины по физической культуре и спорту" to "Физкультура",
        "Информационные технологии и основы программирования" to "ИТ и основы программирования",
        "Объектно-ориентированное программирование" to "ООП",
        "Теория вероятностей и математическая статистика" to "Теорвер и матстат",
        "Основы личностной и коммуникативной культуры" to "Коммуникативная культура",
        "Операционные системы и компьютерные сети" to "ОС и компьютерные сети",
        "Безопасность жизнедеятельности" to "БЖД",
        "Дифференциальные уравнения" to "Дифуры",
    )

    private val noise = Regex("""\s*\((?:[Дд]исциплина по выбору|[Фф]акультатив)\)\s*""")

    fun shorten(title: String): String =
        title.split(" / ").joinToString(" / ") { part ->
            known[part] ?: part.replace(noise, " ").trim()
        }
}

/** Та же картинка, но с короткими названиями предметов. */
fun WidgetDisplay.withShortTitles(): WidgetDisplay {
    fun SlotInfo.short() = copy(title = ShortNames.shorten(title))
    return copy(
        title = if (focus != null) ShortNames.shorten(title) else title,
        focus = focus?.short(),
        next = next?.short(),
        day = day.map { it.copy(info = it.info.short()) },
    )
}
