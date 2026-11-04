package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.PhotoFrame
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.ui.AppClock
import io.github.hungrycube.lessonwidget.ui.AppTheme
import io.github.hungrycube.lessonwidget.ui.GroupPickerScreen
import io.github.hungrycube.lessonwidget.ui.ScheduleScreen
import io.github.hungrycube.lessonwidget.ui.SettingsScreen
import io.github.hungrycube.lessonwidget.ui.StyleDraft
import io.github.hungrycube.lessonwidget.ui.StyleSetupScreen
import io.github.hungrycube.lessonwidget.ui.previewDisplay
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Экраны приложения в светлой и тёмной теме (app/build/screenshots/app). Данные — настоящие страницы ЭМ-63 из samples,
 * время зафиксировано, поэтому картинки не зависят от дня сборки. Сравнивать с design/mockups/app-*.html.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots", "app").apply { mkdirs() }
    private val samples = File(System.getProperty("samples.dir") ?: "../samples")

    private fun nsk(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, NOVOSIBIRSK).toInstant().toEpochMilli()

    private fun setNow(millis: Long) {
        AppClock.now = { millis }
        val s = ScheduleAssembler.assemble(
            group = "ЭМ-63",
            semester = NstuParser.parseSchedulePage(File(samples, "schedule_EM-63_semester.html").readText()),
            week = NstuParser.parseSchedulePage(File(samples, "schedule_EM-63_week.html").readText()),
            fetchedAt = millis - 18 * 60_000L,
            today = LocalDate.of(2026, 10, 26),
        )
        ScheduleRepository(context).save(s)
    }

    @Before
    fun setUp() {
        GroupsRepository(context).store(NstuParser.parseGroups(File(samples, "groups.html").readText()))
        Settings(context).apply {
            defaultGroup = "ЭМ-63"
            // Без сети: автоматическая проверка версии в тестах не нужна.
            lastUpdateCheck = System.currentTimeMillis()
            recentGroups = listOf("ЭМ-63", "АВТ-308", "ПМ-41")
            defaultStyle = "glass"
        }
        setNow(nsk(2026, 10, 28, 10, 50))
    }

    @After
    fun tearDown() {
        AppClock.now = System::currentTimeMillis
    }

    private fun idle(times: Int) = repeat(times) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
    }

    private fun shot(name: String, dark: Boolean, wait: Int = 10, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { AppTheme(dark = dark) { content() } }
        idle(wait)
        val view = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home(name: String, dark: Boolean, mode: Int = 0, group: String? = "ЭМ-63") =
        shot(name, dark) { ScheduleScreen(group, onSettings = {}, onPickGroup = {}, onConfigureWidget = null, initialMode = mode) }

    @Test
    fun homeDuringLesson() {
        home("home_light", dark = false)
        home("home_dark", dark = true)
    }

    @Test
    fun homeEveningShowsTomorrow() {
        setNow(nsk(2026, 10, 27, 20, 30))
        home("home_evening_light", dark = false)
        home("home_evening_dark", dark = true)
    }

    @Test
    fun homeWeekAndEmpty() {
        home("home_week_light", dark = false, mode = 1)
        home("home_week_dark", dark = true, mode = 1)
        home("home_nogroup_light", dark = false, group = null)
        setNow(nsk(2026, 11, 1, 12, 0))
        home("home_sunday_dark", dark = true)
    }

    @Test
    @Config(qualifiers = "w390dp-h3000dp-xhdpi")
    fun settings() {
        shot("settings_light", dark = false, wait = 60) { SettingsScreen(onBack = {}, onChangeGroup = {}) }
        shot("settings_dark", dark = true, wait = 60) { SettingsScreen(onBack = {}, onChangeGroup = {}) }
    }

    @Test
    fun groupPicker() {
        shot("picker_search_light", dark = false) { GroupPickerScreen("Выбор группы", "ЭМ-63", onBack = {}, onPicked = {}, initialQuery = "эм6") }
        shot("picker_search_dark", dark = true) { GroupPickerScreen("Выбор группы", "ЭМ-63", onBack = {}, onPicked = {}, initialQuery = "ЭМ") }
        shot("picker_recent_light", dark = false) { GroupPickerScreen("Группа для виджета", "ЭМ-63", onBack = {}, onPicked = {}) }
        shot("picker_nothing_dark", dark = true) { GroupPickerScreen("Выбор группы", null, onBack = {}, onPicked = {}, initialQuery = "ЯЯ-999") }
    }

    @Test
    fun photoSetupPlaceholder() {
        val style = Styles.byId(context, "photo")
        val draft = WidgetPhotos.draft(context, "test")
        shot("photo_setup_light", dark = false, wait = 30) {
            StyleSetupScreen(
                style = style,
                initial = StyleDraft(emptyMap(), PhotoFrame()),
                currentPhoto = null,
                draftFile = draft,
                widthDp = 316f,
                heightDp = 168f,
                display = { now -> previewDisplay(context, "ЭМ-63", null, now) },
                onDone = {},
                onBack = {},
            )
        }
    }
}
