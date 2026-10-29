package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
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

    /** Фото из samples/photos: снимки автора, уже уменьшенные, как это делает приложение. */
    private fun photo(name: String): PhotoInput {
        val dir = File(System.getProperty("samples.dir") ?: "../samples", "photos")
        return PhotoInput(BitmapFactory.decodeFile(File(dir, name).path) ?: error("нет фото $name"))
    }

    private val nightCity by lazy { photo("night_city.jpg") }
    private val riverSunset by lazy { photo("river_sunset.jpg") }
    private val fieldLight by lazy { photo("field_light.jpg") }

    // Стиль со своим фото во всех состояниях и размерах — с настоящим снимком, а не с примером из файла.
    private fun render(st: WidgetStyle, d: WidgetDisplay, w: Float, h: Float, now: Long, density: Float = 2f, custom: StyleCustom? = null) =
        renderer.render(
            st, d, w, h, RenderMode.PREVIEW, nowMillis = now, density = density, dynamicColor = false,
            custom = custom ?: StyleCustom(photo = nightCity).takeIf { st.usesPhoto },
        ).bitmap

    @Test
    fun eachStyleLikeTheMockup() {
        val now = nsk(2026, 10, 26, 11, 11)
        val demo = Demo.display(now)
        val tiles = ArrayList<Pair<String, Bitmap>>()
        for (st in Styles.all(context)) {
            val bmp = render(st, demo, 316f, 168f, now)
            save("style_${st.id}", bmp)
            tiles += st.title to bmp
        }
        save("overview", sheet(tiles, columns = 3))
        // «Своё фото» для README: другие снимки и подложки.
        val ph = Styles.byId(context, "photo")
        save("style_photo_glass", render(ph, demo, 316f, 168f, now, custom = StyleCustom(mapOf("backing" to "glass"), riverSunset)))
        save("style_photo_light", render(ph, demo, 316f, 168f, now, custom = StyleCustom(photo = fieldLight)))
    }

    /** Новые стили как на макетах: обои трёх видов, своё фото на всех подложках, вид на рабочем столе. */
    @Test
    fun newStylesLikeTheMockups() {
        val now = nsk(2026, 10, 26, 11, 11)
        val demo = Demo.display(now)
        val tiles = ArrayList<Pair<String, Bitmap>>()
        fun shot(st: WidgetStyle, mode: RenderMode = RenderMode.PREVIEW, custom: StyleCustom? = null, wallpaper: List<Int?>? = null) =
            renderer.render(st, demo, 316f, 168f, mode, nowMillis = now, density = 2f, dynamicColor = false, custom = custom, wallpaper = wallpaper).bitmap

        val wp = Styles.byId(context, "wallpaper")
        tiles += "Обои: светлые" to shot(wp)
        tiles += "Обои: тёмные" to shot(wp, wallpaper = listOf(0xFF0C1122.toInt(), 0xFF33478A.toInt(), 0xFF1D2B55.toInt()))
        tiles += "Обои: яркие" to shot(wp, wallpaper = listOf(0xFFE8457F.toInt(), 0xFFFF8A5C.toInt(), 0xFF7A3CFF.toInt()))
        tiles += "Обои: серые" to shot(wp, wallpaper = listOf(0xFF8A8A8A.toInt(), null, null))
        tiles += "Обои: жёлтые" to shot(wp, wallpaper = listOf(0xFFE0C020.toInt(), null, null))

        val ph = Styles.byId(context, "photo")
        tiles += "Фото: пример без фото" to shot(ph)
        for (backing in listOf("gradient", "glass", "none")) {
            tiles += "Фото: $backing, город" to shot(ph, custom = StyleCustom(mapOf("backing" to backing), nightCity))
            tiles += "Фото: $backing, закат" to shot(ph, custom = StyleCustom(mapOf("backing" to backing), riverSunset))
            tiles += "Фото: $backing, поле" to shot(ph, custom = StyleCustom(mapOf("backing" to backing), fieldLight))
        }
        tiles += "Фото: размытие 60, без акцента" to shot(ph, custom = StyleCustom(mapOf("blur" to "60", "accent" to "off"), nightCity))
        tiles += "Фото: светлый текст, затемнение 80" to shot(ph, custom = StyleCustom(mapOf("text" to "light", "dim" to "80"), fieldLight))
        tiles += "Фото: отражено, кадр вправо" to
            shot(ph, custom = StyleCustom(photo = PhotoInput(riverSunset.bitmap, focusX = 1f, flip = true)))
        tiles += "Фото: стекло на рабочем столе" to shot(ph, RenderMode.WIDGET, StyleCustom(mapOf("backing" to "glass"), nightCity))
        for (id in listOf("frutiger", "y2k")) {
            val st = Styles.byId(context, id)
            tiles += st.title to shot(st)
            tiles += "${st.title}: рабочий стол" to shot(st, RenderMode.WIDGET)
        }
        save("new_styles", sheet(tiles, columns = 3))
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
        for (st in Styles.all(context)) {
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
        for (st in Styles.all(context)) {
            val r = renderer.render(st, d, 316f, 168f, RenderMode.WIDGET, nowMillis = now)
            assert(r.bitmap.width > 0 && r.description.contains("Линейная алгебра")) { st.id }
        }
    }

    /** Каждый стиль на разных размерах виджета — проверка адаптивности. Пропорции между размерами сохранены. */
    @Test
    fun adaptiveSizes() {
        val now = nsk(2026, 10, 26, 12, 30)
        val d = DisplayBuilder.build(em63(), "ЭМ-63", null, now).withShortTitles()
        val sizes = listOf(
            "2×2 мелкий 120×120" to (120f to 120f),
            "2×2 150×150" to (150f to 150f),
            "2×2 HyperOS 190×190" to (190f to 190f),
            "2×4 150×340" to (150f to 340f),
            "3×2 240×168" to (240f to 168f),
            "4×1 316×110" to (316f to 110f),
            "4×2 316×168" to (316f to 168f),
            "4×2 крупный 360×190" to (360f to 190f),
            "4×2 ландшафт 520×130" to (520f to 130f),
            "4×3 330×260" to (330f to 260f),
            "4×4 316×340" to (316f to 340f),
            "4×4 350×390" to (350f to 390f),
            "4×5 340×480" to (340f to 480f),
            "планшет 600×300" to (600f to 300f),
        )
        for (st in Styles.all(context)) {
            val tiles = sizes.map { (label, wh) -> label to render(st, d, wh.first, wh.second, now, 1.25f) }
            save("sizes_${st.id}", flow(tiles, 2300))
        }
    }

    /** Как было и как стало на растянутом виджете 4×4 (350×390 dp, как на скриншоте пользователя). */
    @Test
    fun beforeAfter() {
        val now = nsk(2026, 10, 26, 12, 30)
        val d = DisplayBuilder.build(em63(), "ЭМ-63", null, now).withShortTitles()
        val legacy = io.github.hungrycube.lessonwidget.legacy.WidgetRenderer(context)
        val tiles = ArrayList<Pair<String, Bitmap>>()
        for (st in Styles.all(context)) {
            // У новых стилей старого кода нет.
            val old = io.github.hungrycube.lessonwidget.legacy.Styles.all.firstOrNull { it.id == st.id } ?: continue
            tiles += "${st.title}: было" to legacy.render(
                old, d, 350f, 390f, io.github.hungrycube.lessonwidget.legacy.RenderMode.PREVIEW,
                nowMillis = now, density = 1f, dynamicColor = false,
            ).bitmap
            tiles += "${st.title}: стало" to render(st, d, 350f, 390f, now, 1f)
        }
        save("before_after_4x4", flow(tiles, 2500))
    }

    /** Картинки подряд с переносом строк, каждая в своём размере. */
    private fun flow(tiles: List<Pair<String, Bitmap>>, maxWidth: Int): Bitmap {
        data class Placed(val label: String, val bmp: Bitmap, val x: Int, val y: Int)
        val placed = ArrayList<Placed>()
        var x = 16
        var y = 16
        var rowH = 0
        var width = 0
        for ((label, bmp) in tiles) {
            if (x > 16 && x + bmp.width > maxWidth) {
                x = 16
                y += rowH + 16
                rowH = 0
            }
            placed += Placed(label, bmp, x, y)
            x += bmp.width + 16
            width = maxOf(width, x)
            rowH = maxOf(rowH, bmp.height + 34)
        }
        val sheet = Bitmap.createBitmap(width, y + rowH + 16, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.rgb(242, 241, 237))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = Color.rgb(26, 26, 26) }
        for (t in placed) {
            c.drawText(t.label, t.x.toFloat(), t.y + 22f, p)
            c.drawBitmap(t.bmp, t.x.toFloat(), t.y + 30f, null)
        }
        return sheet
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
