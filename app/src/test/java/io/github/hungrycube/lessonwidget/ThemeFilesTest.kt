package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.render.Adaptive
import io.github.hungrycube.lessonwidget.render.FontCache
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.layout.Layouts
import io.github.hungrycube.lessonwidget.render.theme.AdaptSpec
import io.github.hungrycube.lessonwidget.render.theme.ThemeLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Файлы стилей: каждый читается, ссылается на существующие раскладку и шрифты; ошибки понятны. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        Styles.userDir(context).deleteRecursively()
        Styles.reload()
    }

    @Test
    fun everyAssetThemeLoadsWithoutProblems() {
        val files = context.assets.list("themes")!!.filter { it.endsWith(".json") }
        assertEquals(9, files.size)
        val styles = Styles.all(context)
        assertEquals("Проблемы в файлах стилей:\n" + Styles.problems(context).joinToString("\n"), emptyList<String>(), Styles.problems(context))
        assertEquals(listOf("glass", "minimal", "oled", "terminal", "material", "notebook", "nothing", "win95", "neti"), styles.map { it.id })
        for (st in styles) {
            assertTrue("${st.id}: нет раскладки ${st.layout}", st.layout in Layouts.ids)
            // Все сочетания форм: шрифты каждой роли существуют.
            for (forms in listOf(emptyList(), listOf("short"), listOf("compact"), listOf("large"), listOf("short", "compact"), listOf("compact", "large"))) {
                val spec = st.spec(forms)
                for ((role, t) in spec.typography) {
                    t.font?.let { assertTrue("${st.id} $forms: роль $role — нет шрифта $it", FontCache.exists(context, it)) }
                }
            }
        }
    }

    @Test
    fun brokenJsonGivesReadableErrorAndFallback() {
        val r1 = Styles.import(context, """{ "schemaVersion": 1, "id": "x", "name": "X", "layout": "clean", """)
        assertNull(r1.style)
        assertTrue(r1.errors.joinToString(), r1.errors.single().contains("ошибка в JSON"))

        val r2 = Styles.import(
            context,
            """{"schemaVersion": 1, "id": "bad", "name": "Плохой", "layout": "nope",
               "palette": {"text": "#GG0000", "muted": "@nothere"},
               "typography": {"title": {"size": 20, "colr": "#fff"}}}""",
        )
        assertNull(r2.style)
        assertTrue(r2.errors.joinToString(), r2.errors.any { "colr" in it })

        val r3 = Styles.import(
            context,
            """{"schemaVersion": 1, "id": "bad2", "name": "Плохой", "layout": "nope",
               "palette": {"text": "#GG0000", "muted": "@nothere"},
               "typography": {"title": {"size": 20}}}""",
        )
        val all = r3.errors.joinToString("\n")
        assertTrue(all, "layout" in all && "nope" in all)
        assertTrue(all, "#GG0000" in all)
        assertTrue(all, "nothere" in all)
        assertTrue(all, "нет роли «header»" in all)

        // Неизвестный id — стиль по умолчанию, а не падение.
        assertEquals(Styles.DEFAULT_ID, Styles.byId(context, "no-such-style").id)
        assertNotNull(Styles.fallback(context))
    }

    @Test
    fun importedThemeExtendsBuiltIn() {
        val r = Styles.import(
            context,
            """{"schemaVersion": 1, "id": "amber", "name": "Янтарный терминал", "extends": "terminal",
               "palette": {"text": "#FFC66D", "secondary": "#E0A040", "accent": "#FFC66D"}}""",
        )
        assertEquals(r.errors.joinToString(), emptyList<String>(), r.errors)
        val st = Styles.byId(context, "amber")
        assertEquals("amber", st.id)
        assertEquals("terminal", st.layout)
        val now = System.currentTimeMillis()
        val d = DisplayBuilder.build(null, "ЭМ-63", null, now)
        val bmp = WidgetRenderer(context).render(st, d, 316f, 168f, RenderMode.WIDGET, nowMillis = now, density = 1f).bitmap
        assertTrue(bmp.width > 0)
        // Встроенный id занять нельзя.
        assertTrue(Styles.import(context, """{"schemaVersion": 1, "id": "terminal", "name": "X", "extends": "terminal"}""").errors.isNotEmpty())
    }

    @Test
    fun adaptiveScaleIsOneOnStandardSizes() {
        val a = AdaptSpec()
        val all = ThemeLoader.SIZES
        val std = Adaptive.classify(316f, 168f, a, all)
        assertEquals(1f, std.scale)
        assertEquals(316f, std.width)
        assertTrue(!std.compact && !std.large && !std.short)
        // Без размера — стандартный вид.
        assertEquals(std, Adaptive.classify(0f, 0f, a, all))
        // 2×2 — компактный вид без масштаба.
        val small = Adaptive.classify(150f, 150f, a, all)
        assertTrue(small.compact && small.scale == 1f)
        // Большой 2×2 на HyperOS — тот же вид, крупнее.
        val big2 = Adaptive.classify(190f, 190f, a, all)
        assertTrue(big2.compact && big2.scale > 1.2f)
        // Растянутый 4×4 — крупнее и со списком пар.
        val tall = Adaptive.classify(350f, 390f, a, all)
        assertTrue(tall.large && tall.scale > 1.1f && tall.width >= a.minWidth - 0.01f)
        // 4×3: списку пар ещё мало места — карточка крупнее, без пустой полосы посередине.
        val mid = Adaptive.classify(330f, 260f, a, all)
        assertTrue(!mid.large && mid.scale > 1.2f && mid.height <= a.baseHeight * a.stretch + 0.5f)
        // Очень маленький — ужат, но не сильнее minScale.
        val tiny = Adaptive.classify(110f, 110f, a, all)
        assertTrue(tiny.compact && tiny.scale >= a.minScale && tiny.scale < 1f)
        // Огромный — не больше maxScale.
        assertTrue(Adaptive.classify(600f, 600f, a, all).scale <= a.maxScale)
    }
}
