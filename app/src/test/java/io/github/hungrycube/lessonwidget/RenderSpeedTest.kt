package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Сколько стоит отрисовка: загрузка всех стилей и каждый стиль на 4×2 при плотности Pixel 6 (2.625).
 * Время — по System.nanoTime (часы Robolectric стоят). Цифры — в render_speed.txt рядом со скриншотами
 * (ветка ci-logs). На телефоне быстрее или медленнее, но тяжёлые места видно.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderSpeedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun renderTimes() {
        val lines = ArrayList<String>()
        Styles.reload()
        var t = System.nanoTime()
        val styles = Styles.all(context)
        lines += "Загрузка ${styles.size} стилей: ${(System.nanoTime() - t) / 1_000_000} мс"

        val dir = File(System.getProperty("samples.dir") ?: "../samples", "photos")
        val photo = PhotoInput(BitmapFactory.decodeFile(File(dir, "night_city.jpg").path))
        val now = System.currentTimeMillis()
        val demo = Demo.display(now)
        val renderer = WidgetRenderer(context)
        val cases = styles.flatMap { st ->
            if (!st.usesPhoto) {
                listOf(st.id to (st to null))
            } else {
                listOf("gradient", "glass", "none").map { b -> "${st.id} $b" to (st to StyleCustom(mapOf("backing" to b, "blur" to "60"), photo)) } +
                    ("${st.id} без фото" to (st to null))
            }
        }
        var worst = 0L
        for ((name, pair) in cases) {
            val (st, custom) = pair
            // Первый раз — «холодный» (шрифты, разбор вариантов), второй — как каждую минуту.
            val times = (1..2).map {
                t = System.nanoTime()
                renderer.render(st, demo, 316f, 168f, RenderMode.WIDGET, nowMillis = now, density = 2.625f, custom = custom)
                (System.nanoTime() - t) / 1_000_000
            }
            worst = maxOf(worst, times[1])
            lines += "$name: первый раз ${times[0]} мс, потом ${times[1]} мс"
        }
        val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(out, "render_speed.txt").writeText(lines.joinToString("\n") + "\n")
        println(lines.joinToString("\n"))
        assertTrue("Слишком долгая отрисовка:\n" + lines.joinToString("\n"), worst < 3_000L)
    }
}
