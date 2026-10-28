package io.github.hungrycube.lessonwidget.render.theme

/** Встроенные иконки Lucide (ISC), сетка 24×24, обводка 2. Стиль может добавить свои в icons.custom. */
object Icons {
    val BUILT_IN: Map<String, IconSpec> = mapOf(
        "book" to IconSpec(
            listOf("M4 19.5A2.5 2.5 0 0 1 6.5 17H20", "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"),
        ),
        "pencil" to IconSpec(listOf("M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z", "m15 5 4 4")),
        "flask" to IconSpec(
            listOf(
                "M10 2v7.527a2 2 0 0 1-.211.896L4.72 20.55a1 1 0 0 0 .9 1.45h12.76a1 1 0 0 0 .9-1.45l-5.069-10.127A2 2 0 0 1 14 9.527V2",
                "M8.5 2h7",
                "M7 16h10",
            ),
        ),
        "coffee" to IconSpec(
            listOf(
                "M10 2v2", "M14 2v2", "M6 2v2",
                "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
            ),
        ),
        "calendar" to IconSpec(
            listOf(
                "M8 2v4", "M16 2v4",
                "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
                "M3 10h18",
            ),
        ),
    )
}
