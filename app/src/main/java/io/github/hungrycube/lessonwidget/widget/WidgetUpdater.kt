package io.github.hungrycube.lessonwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.nextRedrawAt
import io.github.hungrycube.lessonwidget.data.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Перерисовка виджетов и будильники.
 *
 * Виджет рисуется прямо в процессе, который получил событие, и сразу отдаётся лаунчеру
 * (GlanceAppWidget.compose + AppWidgetManager.updateAppWidget). Обычный Glance update идёт через
 * WorkManager, а его система откладывает на часы — отсюда было «застывшее» время на виджете.
 *
 * Будильников два:
 * - «будящий» — граница пары и шаг прогресса раз в 5 минут во время пары, срабатывает и при выключенном экране;
 * - «не будящий» — смена минуты в отсчёте: пока экран выключен, его никто не видит,
 *   а при включении экрана система сразу его доставляет.
 */
object WidgetUpdater {

    /** Если ничего не запланировано — проверить через 6 часов. */
    private const val IDLE_MS = 6 * 3600_000L

    /** Во время пары полоска прогресса двигается хотя бы раз в столько. */
    private const val PROGRESS_STEP_MS = 5 * 60_000L

    /** Без разрешения на точные будильники — окно не больше минуты. */
    private const val WINDOW_MS = 60_000L

    /** Запас, чтобы будильник сработал уже после границы. */
    private const val SLACK_MS = 500L

    private const val RENDER_TIMEOUT_MS = 8_000L

    /** Размер, пока лаунчер не сообщил настоящий (виджет только что поставили): макет 4×2. */
    val DEFAULT_SIZE = DpSize(316.dp, 168.dp)

    const val ACTION_TICK = "io.github.hungrycube.lessonwidget.TICK"
    const val ACTION_WAKE = "io.github.hungrycube.lessonwidget.WAKE"

    /** Текущее время. Подменяется в тестах. */
    @VisibleForTesting
    internal var clock: () -> Long = System::currentTimeMillis

    fun now(): Long = clock()

    private val renderLock = Mutex()

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        render(app, Widgets.ids(app))
    }

    suspend fun update(context: Context, appWidgetId: Int) = render(context, intArrayOf(appWidgetId))

    /** Перерисовывает виджеты и переставляет будильники. Ошибка одного виджета не мешает остальным. */
    suspend fun render(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext
        try {
            for (id in appWidgetIds) renderNow(app, id)
        } finally {
            scheduleNext(app)
        }
    }

    /**
     * Рисует один виджет и сразу отдаёт картинку лаунчеру. При ошибке пишет её в журнал,
     * а на рабочем столе остаётся прошлая картинка.
     */
    suspend fun renderNow(context: Context, appWidgetId: Int): Boolean = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        try {
            val manager = AppWidgetManager.getInstance(app)
            val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
            val options = manager.getAppWidgetOptions(appWidgetId) ?: Bundle()
            val views = renderLock.withLock {
                withTimeout(RENDER_TIMEOUT_MS) {
                    if (hasSize(options)) {
                        ScheduleWidget().compose(app, glanceId, options = options)
                    } else {
                        ScheduleWidget().compose(app, glanceId, size = DEFAULT_SIZE)
                    }
                }
            }
            manager.updateAppWidget(appWidgetId, views)
            true
        } catch (e: TimeoutCancellationException) {
            CrashLog.record(app, "Отрисовка виджета $appWidgetId: не уложилась в ${RENDER_TIMEOUT_MS / 1000} с", e)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CrashLog.record(app, "Отрисовка виджета $appWidgetId", e)
            false
        }
    }

    /** Сообщил ли лаунчер размер виджета. Сразу после добавления его может ещё не быть. */
    private fun hasSize(options: Bundle): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            if (!sizes.isNullOrEmpty() && sizes.all { it.width > 0f && it.height > 0f }) return true
        }
        return options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0 &&
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) > 0 &&
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0 &&
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) > 0
    }

    /** Ставит будильники на ближайшие моменты, когда хоть один виджет должен измениться. */
    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val tick = alarmIntent(app, ACTION_TICK)
        val wake = alarmIntent(app, ACTION_WAKE)
        val ids = Widgets.ids(app)
        if (ids.isEmpty()) {
            am.cancel(tick)
            am.cancel(wake)
            return
        }
        val now = now()
        var nextTick = Long.MAX_VALUE
        var nextWake = now + IDLE_MS
        for (id in ids) {
            val d = runCatching { Widgets.state(app, id, now).display }.getOrNull() ?: continue
            // Смена минуты в отсчёте (только пока до события не больше 90 минут) или граница пары.
            d.nextRedrawAt(now)?.let { if (it > now) nextTick = minOf(nextTick, it) }
            d.boundaryAt?.let { if (it > now) nextWake = minOf(nextWake, it) }
            if (d.phase == Phase.LESSON) nextWake = minOf(nextWake, now + PROGRESS_STEP_MS)
        }
        set(am, AlarmManager.RTC_WAKEUP, nextWake + SLACK_MS, wake)
        // Если «будящий» сработает раньше или одновременно, он сам переставит и минутный.
        if (nextTick < nextWake) set(am, AlarmManager.RTC, nextTick + SLACK_MS, tick) else am.cancel(tick)
    }

    private fun set(am: AlarmManager, type: Int, at: Long, pending: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            try {
                if (type == AlarmManager.RTC_WAKEUP) am.setExactAndAllowWhileIdle(type, at, pending) else am.setExact(type, at, pending)
                return
            } catch (e: SecurityException) {
                // Разрешение могли отозвать между проверкой и вызовом — ставим неточный.
            }
        }
        am.setWindow(type, at, WINDOW_MS, pending)
    }

    private fun alarmIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetEventsReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, if (action == ACTION_WAKE) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
