package io.github.hungrycube.lessonwidget.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки в стиле Lucide (ISC): контур 2 на сетке 24×24, скруглённые концы.
 * Цвет задаёт tint у Icon, поэтому контур здесь просто чёрный.
 */
object LucideIcons {
    val back = icon("back", "M15 18l-6-6 6-6")
    val chevronRight = icon("chevron-right", "M9 18l6-6-6-6")
    val chevronLeft = icon("chevron-left", "M15 18l-6-6 6-6")
    val refresh = icon("refresh", "M21 12a9 9 0 1 1-2.64-6.36", "M21 3v6h-6")
    val settings = icon(
        "settings",
        "M12.22 2h-0.44a2 2 0 0 0 -2 2v0.18a2 2 0 0 1 -1 1.73l-0.43 0.25a2 2 0 0 1 -2 0l-0.15 -0.08" +
            "a2 2 0 0 0 -2.73 0.73l-0.22 0.38a2 2 0 0 0 0.73 2.73l0.15 0.1a2 2 0 0 1 1 1.72v0.51" +
            "a2 2 0 0 1 -1 1.74l-0.15 0.09a2 2 0 0 0 -0.73 2.73l0.22 0.38a2 2 0 0 0 2.73 0.73l0.15 -0.08" +
            "a2 2 0 0 1 2 0l0.43 0.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h0.44a2 2 0 0 0 2 -2v-0.18" +
            "a2 2 0 0 1 1 -1.73l0.43 -0.25a2 2 0 0 1 2 0l0.15 0.08a2 2 0 0 0 2.73 -0.73l0.22 -0.39" +
            "a2 2 0 0 0 -0.73 -2.73l-0.15 -0.08a2 2 0 0 1 -1 -1.74v-0.5a2 2 0 0 1 1 -1.74l0.15 -0.09" +
            "a2 2 0 0 0 0.73 -2.73l-0.22 -0.38a2 2 0 0 0 -2.73 -0.73l-0.15 0.08a2 2 0 0 1 -2 0l-0.43 -0.25" +
            "a2 2 0 0 1 -1 -1.73V4a2 2 0 0 0 -2 -2z",
        circle(12f, 12f, 3f),
    )
    val search = icon("search", circle(11f, 11f, 8f), "M21 21l-4.3 -4.3")
    val close = icon("close", "M18 6L6 18", "M6 6l12 12")
    val check = icon("check", "M20 6L9 17l-5 -5")
    val send = icon("send", "M22 2L11 13", "M22 2l-7 20 -4 -9 -9 -4 20 -7z")
    val external = icon("external", "M7 17L17 7", "M7 7h10v10")
    val image = icon("image", roundRect(3f, 3f, 18f, 18f, 3f), circle(8.5f, 8.5f, 1.5f), "M21 15l-5 -5L5 21")
    val flip = icon("flip", "M12 3v18", "M8 7L3 12l5 5V7z", "M16 7l5 5 -5 5V7z")
    val pencil = icon(
        "pencil",
        "M21.174 6.812a1 1 0 0 0 -3.986 -3.987L3.842 16.174a2 2 0 0 0 -0.5 0.83l-1.321 4.352" +
            "a0.5 0.5 0 0 0 0.623 0.622l4.353 -1.32a2 2 0 0 0 0.83 -0.497z",
        "M15 5l4 4",
    )
    val coffee = icon(
        "coffee",
        "M10 2v2", "M14 2v2", "M6 2v2",
        "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1 -4 4H7a4 4 0 0 1 -4 -4V9a1 1 0 0 1 1 -1h14a4 4 0 1 1 0 8h-1",
    )
    val sun = icon(
        "sun",
        circle(12f, 12f, 4f),
        "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
        "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41",
    )
    val wifiOff = icon(
        "wifi-off",
        "M12 20h0.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17 -2.69",
        "M19 12.859a10 10 0 0 0 -2.007 -1.523", "M2 8.82a15 15 0 0 1 4.177 -2.643",
        "M22 8.82a15 15 0 0 0 -11.288 -3.764", "M2 2l20 20",
    )
    val users = icon(
        "users",
        "M16 21v-2a4 4 0 0 0 -4 -4H6a4 4 0 0 0 -4 4v2", circle(9f, 7f, 4f),
        "M22 21v-2a4 4 0 0 0 -3 -3.87", "M16 3.13a4 4 0 0 1 0 7.75",
    )
    val calendar = icon("calendar", "M8 2v4", "M16 2v4", roundRect(3f, 4f, 18f, 18f, 2f), "M3 10h18")
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

private fun icon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (d in paths) {
        b.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}
