package io.github.hungrycube.lessonwidget.render.layout

import io.github.hungrycube.lessonwidget.render.Frame

/**
 * Раскладки — расположение элементов карточки. Стиль (JSON) выбирает раскладку по id и задаёт
 * всё остальное: цвета, шрифты, фон, прогресс, тексты. Принципиально новая идея = новая раскладка:
 * файл с функцией `Frame.имя()` и строка здесь.
 */
object Layouts {
    private val all: Map<String, Frame.() -> Unit> = mapOf(
        "glass" to Frame::glass,
        "clean" to Frame::clean,
        "ring" to Frame::ring,
        "terminal" to Frame::terminal,
        "tonal" to Frame::tonal,
        "paper" to Frame::paper,
        "dots" to Frame::dots,
        "retro" to Frame::retro,
        "tags" to Frame::tags,
    )

    val ids: Set<String> get() = all.keys

    fun draw(id: String, frame: Frame) = (all[id] ?: all.getValue("clean")).invoke(frame)
}
