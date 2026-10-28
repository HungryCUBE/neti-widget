package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.display.withShortTitles
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.render.Adaptive
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import io.github.hungrycube.lessonwidget.legacy.RenderMode as LegacyMode
import io.github.hungrycube.lessonwidget.legacy.Styles as LegacyStyles
import io.github.hungrycube.lessonwidget.legacy.WidgetRenderer as LegacyRenderer

/**
 * Стили из JSON рисуют стандартный виджет пиксель в пиксель как прежний код (копия в пакете legacy).
 * Проверяются все стили во всех состояниях на размерах обычной формы, где масштаб движка равен 1:
 * 4×2 (316×168), низкий, узкий и чуть выше обычного — в превью и на рабочем столе.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegacyParityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots", "parity")

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

    private fun states(): List<Triple<String, WidgetDisplay, Long>> {
        val schedule = em63()
        val moments = listOf(
            "lesson" to nsk(2026, 10, 26, 12, 30),
            "break" to nsk(2026, 10, 26, 11, 52),
            "before" to nsk(2026, 10, 26, 8, 10),
            "over" to nsk(2026, 10, 26, 22, 0),
            "dayoff" to nsk(2026, 11, 1, 12, 0),
            "holidays" to nsk(2027, 3, 15, 12, 0),
            "long" to nsk(2026, 10, 27, 9, 0),
        )
        val demoNow = nsk(2026, 10, 26, 11, 11)
        return listOf(Triple("demo", Demo.display(demoNow), demoNow)) +
            moments.map { (name, t) ->
                val d = DisplayBuilder.build(schedule, "ЭМ-63", null, t)
                Triple(name, if (name == "long") d else d.withShortTitles(), t)
            } +
            Triple("nodata", DisplayBuilder.build(null, "ЭМ-63", null, moments[0].second), moments[0].second)
    }

    @Test
    fun jsonStylesMatchOldRenderer() {
        val legacy = LegacyRenderer(context)
        val renderer = WidgetRenderer(context)
        // Только обычная форма 4×2: на 2×2 поведение намеренно улучшено (сначала убираются детали, а не название).
        val sizes = listOf(316f to 168f, 316f to 120f, 260f to 168f, 300f to 200f)
        val failures = ArrayList<String>()
        var compared = 0
        for (old in LegacyStyles.all) {
            val style = Styles.byId(context, old.id)
            assertTrue("нет стиля ${old.id}", style.id == old.id)
            for ((name, d, now) in states()) {
                for ((w, h) in sizes) {
                    for (mode in listOf(RenderMode.PREVIEW, RenderMode.WIDGET)) {
                        val preview = mode == RenderMode.PREVIEW
                        // Сравниваем только там, где движок ничего не масштабирует (иначе разница — это и есть адаптивность).
                        val inset = if (preview) 0f else style.adapt.widgetInset
                        val size = Adaptive.classify(w - 2 * inset, h - 2 * inset, style.adapt, style.sizes)
                        if (size.scale != 1f || size.large) continue
                        val a = legacy.render(
                            old, d, w, h, if (preview) LegacyMode.PREVIEW else LegacyMode.WIDGET,
                            nowMillis = now, density = 2f, dynamicColor = !preview,
                        ).bitmap
                        val b = renderer.render(style, d, w, h, mode, nowMillis = now, density = 2f, dynamicColor = !preview).bitmap
                        compared++
                        val diff = difference(a, b)
                        if (diff != null) {
                            val tag = "${old.id}_${name}_${w.toInt()}x${h.toInt()}_${mode.name.lowercase()}"
                            failures += "$tag: $diff"
                            out.mkdirs()
                            save("${tag}_old", a)
                            save("${tag}_new", b)
                        }
                    }
                }
            }
        }
        assertTrue("Отличаются ${failures.size} из $compared:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun difference(a: Bitmap, b: Bitmap): String? {
        if (a.width != b.width || a.height != b.height) return "размер ${a.width}×${a.height} ≠ ${b.width}×${b.height}"
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var count = 0
        var maxDelta = 0
        for (i in pa.indices) {
            if (pa[i] != pb[i]) {
                count++
                for (shift in intArrayOf(0, 8, 16, 24)) {
                    maxDelta = maxOf(maxDelta, kotlin.math.abs(((pa[i] ushr shift) and 0xFF) - ((pb[i] ushr shift) and 0xFF)))
                }
            }
        }
        return if (count == 0) null else "$count пикс. отличаются, до $maxDelta по каналу"
    }

    private fun save(name: String, bitmap: Bitmap) {
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
