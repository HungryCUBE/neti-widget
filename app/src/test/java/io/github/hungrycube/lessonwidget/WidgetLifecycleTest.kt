package io.github.hungrycube.lessonwidget

import android.app.AlarmManager
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetConfig
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.widget.ScheduleWidgetReceiver
import io.github.hungrycube.lessonwidget.widget.WidgetEventsReceiver
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import io.github.hungrycube.lessonwidget.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Жизненный цикл виджета без телефона: добавление без размера и настроек, без разрешения
 * на точные будильники, срабатывание будильника после того, как процесс был убит.
 * Ни одна ошибка не должна попасть в журнал сбоев.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun nsk(h: Int, min: Int) =
        ZonedDateTime.of(2026, 10, 26, h, min, 0, 0, NOVOSIBIRSK).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        CrashLog.clear(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        WidgetUpdater.clock = System::currentTimeMillis
    }

    /** Лаунчер привязал виджет; размеры (options) он мог ещё не прислать. */
    private fun bindWidget(id: Int, options: Bundle? = null) {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ScheduleWidgetReceiver::class.java)
            minWidth = 250
            minHeight = 110
        }
        shadowOf(manager).addBoundWidget(id, info)
        options?.let { manager.updateAppWidgetOptions(id, it) }
    }

    /** Первый onUpdate, как при добавлении виджета на рабочий стол. */
    private fun launcherAdds(vararg ids: Int) {
        val receiver = ScheduleWidgetReceiver()
        receiver.onUpdate(context, manager, ids)
        runBlocking { receiver.lastJob!!.join() }
    }

    private fun assertNoErrors() {
        val errors = CrashLog.entries(context)
        assertTrue("Ошибки в журнале:\n" + errors.joinToString("\n\n"), errors.isEmpty())
    }

    private fun View.descriptions(): List<String> {
        val own = listOfNotNull(contentDescription?.toString())
        return if (this is ViewGroup) own + (0 until childCount).flatMap { getChildAt(it).descriptions() } else own
    }

    @Test
    fun addWithoutSizeAndConfig() {
        bindWidget(1)
        launcherAdds(1)
        assertNoErrors()
        assertEquals("Выберите группу", Widgets.state(context, 1).display.title)
        val view = shadowOf(manager).getViewFor(1)
        assertTrue(view.descriptions().any { "Выберите группу" in it })
    }

    @Test
    fun addWithoutExactAlarmPermission() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        bindWidget(2)
        launcherAdds(2)
        assertNoErrors()
        val scheduled = alarms.scheduledAlarms
        assertTrue(scheduled.isNotEmpty())
        // Без разрешения — неточный будильник с окном не больше минуты.
        assertTrue(scheduled.all { it.windowLengthMs in 1..60_000L })
    }

    @Test
    fun alarmAfterProcessDeathShowsCurrentLesson() {
        val dir = File(System.getProperty("samples.dir") ?: "../samples")
        fun read(name: String) = File(dir, name).readText()
        val schedule = ScheduleAssembler.assemble(
            group = "ЭМ-63",
            semester = NstuParser.parseSchedulePage(read("schedule_EM-63_semester.html")),
            week = NstuParser.parseSchedulePage(read("schedule_EM-63_week.html")),
            fetchedAt = 0L,
            today = LocalDate.of(2026, 10, 26),
        )
        ScheduleRepository(context).save(schedule)
        Settings(context).defaultGroup = "ЭМ-63"
        Settings(context).shortTitles = false
        WidgetConfigs(context).set(3, WidgetConfig(style = "terminal"))
        bindWidget(3, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 316)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 316)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 168)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 168)
        })

        // Перемена перед третьей парой: виджет нарисован, будильники поставлены.
        WidgetUpdater.clock = { nsk(11, 50) }
        launcherAdds(3)
        assertEquals(Phase.BREAK, Widgets.state(context, 3, nsk(11, 50)).display.phase)

        // Процесс убит, в памяти ничего нет. Через 40 минут срабатывает будильник.
        val later = nsk(12, 30)
        WidgetUpdater.clock = { later }
        val receiver = WidgetEventsReceiver()
        receiver.onReceive(context, Intent(WidgetUpdater.ACTION_WAKE))
        runBlocking { receiver.lastJob!!.join() }
        assertNoErrors()

        val d = Widgets.state(context, 3, later).display
        assertEquals(Phase.LESSON, d.phase)
        assertEquals("Линейная алгебра", d.focus?.title)
        assertEquals(nsk(13, 30), d.countdownTo)
        val view = shadowOf(manager).getViewFor(3)
        assertTrue(view.descriptions().any { "Линейная алгебра" in it })

        // Минутный будильник — на смену минуты (12:31), будящий — на шаг прогресса (12:35).
        val byType = alarms.scheduledAlarms.associateBy { it.type }
        assertEquals(nsk(12, 31) + 500, byType.getValue(AlarmManager.RTC).triggerAtMs)
        val wake = byType.getValue(AlarmManager.RTC_WAKEUP)
        assertEquals(nsk(12, 35) + 500, wake.triggerAtMs)
        assertTrue(wake.isAllowWhileIdle)
    }
}
