package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.display.withShortTitles
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Рисует все стили во всех состояниях и размерах в PNG (app/build/screenshots).
 * CI выкладывает их в ветку ci-logs — так дизайн проверяется без телефона.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = WidgetRenderer(context)
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun nsk(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, NOVOSIBIRSK).toInstant().toEpochMilli()

    private fun em63(): GroupSchedule {
        val dir = File(System.getProperty("samples.dir") ?: "../samples")
        fun read(name: String) = File(dir, name).readText()
        return ScheduleAssembler.assemble(
            group = "ЭМ-63",
            semester = NstuParser.parseSchedulePage(read("schedule_EM-63_semester.html")),
            week = NstuParser.parseSchedulePage(read("schedule_EM-63_week.html")),
            fetchedAt = 0L,
            today = LocalDate.of(2026, 10, 26),
        )
    }

    private fun save(name: String, bitmap: Bitmap) {
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(st: WidgetStyle, d: WidgetDisplay, w: Float, h: Float, now: Long, density: Float = 2f) =
        renderer.render(st, d, w, h, RenderMode.PREVIEW, nowMillis = now, density = density, dynamicColor = false).bitmap

    @Test
    fun eachStyleLikeTheMockup() {
        val now = nsk(2026, 10, 26, 11, 11)
        val demo = Demo.display(now)
        val tiles = ArrayList<Pair<String, Bitmap>>()
        for (st in Styles.all) {
            val bmp = render(st, demo, 316f, 168f, now)
            save("style_${st.id}", bmp)
            tiles += st.title to bmp
        }
        save("overview", sheet(tiles, columns = 3))
    }

    @Test
    fun statesAndSizes() {
        val schedule = em63()
        val moments = listOf(
            "Идёт пара" to nsk(2026, 10, 26, 12, 30),
            "Перемена" to nsk(2026, 10, 26, 11, 52),
            "До первой пары" to nsk(2026, 10, 26, 8, 10),
            "Пары закончились" to nsk(2026, 10, 26, 22, 0),
            "Выходной" to nsk(2026, 11, 1, 12, 0),
            "Каникулы" to nsk(2027, 3, 15, 12, 0),
        )
        for (st in Styles.all) {
            val tiles = ArrayList<Pair<String, Bitmap>>()
            for ((label, t) in moments) {
                val d = DisplayBuilder.build(schedule, "ЭМ-63", null, t).withShortTitles()
                tiles += label to render(st, d, 316f, 168f, t, 1.5f)
            }
            val noData = DisplayBuilder.build(null, "ЭМ-63", null, moments[0].second)
            tiles += "Нет данных" to render(st, noData, 316f, 168f, moments[0].second, 1.5f)
            val lesson = DisplayBuilder.build(schedule, "ЭМ-63", null, moments[0].second).withShortTitles()
            val long = DisplayBuilder.build(schedule, "ЭМ-63", null, nsk(2026, 10, 27, 9, 0))
            tiles += "Длинное название" to render(st, long, 316f, 168f, nsk(2026, 10, 27, 9, 0), 1.5f)
            tiles += "2×2" to render(st, lesson, 150f, 150f, moments[0].second, 1.5f)
            tiles += "4×4" to render(st, lesson, 316f, 340f, moments[0].second, 1.5f)
            save("states_${st.id}", sheet(tiles, columns = 4))
        }
    }

    @Test
    fun widgetModeRendersEveryStyle() {
        val now = nsk(2026, 10, 26, 12, 30)
        val d = DisplayBuilder.build(em63(), "ЭМ-63", null, now)
        assertEquals(Phase.LESSON, d.phase)
        for (st in Styles.all) {
            val r = renderer.render(st, d, 316f, 168f, RenderMode.WIDGET, nowMillis = now)
            assert(r.bitmap.width > 0 && r.description.contains("Линейная алгебра")) { st.id }
        }
    }

    /** Сетка картинок с подписями. */
    private fun sheet(tiles: List<Pair<String, Bitmap>>, columns: Int): Bitmap {
        val cellW = tiles.maxOf { it.second.width }
        val cellH = tiles.maxOf { it.second.height } + 36
        val rows = (tiles.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(columns * cellW + (columns + 1) * 16, rows * cellH + (rows + 1) * 16, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.rgb(242, 241, 237))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.rgb(26, 26, 26) }
        tiles.forEachIndexed { i, (label, bmp) ->
            val x = 16 + (i % columns) * (cellW + 16)
            val y = 16 + (i / columns) * (cellH + 16)
            c.drawText(label, x.toFloat(), y + 24f, p)
            c.drawBitmap(bmp, x.toFloat(), y + 34f, null)
        }
        return sheet
    }
}
