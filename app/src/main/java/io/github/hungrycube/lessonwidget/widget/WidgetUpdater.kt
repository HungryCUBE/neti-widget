package io.github.hungrycube.lessonwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Перерисовка виджетов и будильник на ближайшую границу пары.
 *
 * Будильник один на все виджеты и «не будящий» (RTC): если экран выключен, телефон не просыпается,
 * а обновление придёт, как только экран включат. Поэтому батарея не тратится, а виджет
 * переключается ровно на границе пары, когда на него смотрят.
 */
object WidgetUpdater {

    /** Пока идёт пара или перемена, полоса прогресса подвигается раз в 3 минуты. Минуты отсчёта тикают сами. */
    private const val PROGRESS_TICK_MS = 3 * 60_000L

    /** Если ничего не запланировано — проверить через 6 часов. */
    private const val IDLE_MS = 6 * 3600_000L

    const val ACTION_TICK = "io.github.hungrycube.lessonwidget.TICK"

    private val _version = MutableStateFlow(0L)

    /** Меняется при каждом обновлении — Glance перерисовывает открытые сессии виджетов. */
    val version: StateFlow<Long> = _version

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        _version.value = System.currentTimeMillis()
        ScheduleWidget().updateAll(app)
        scheduleNext(app)
    }

    suspend fun update(context: Context, appWidgetId: Int) {
        val app = context.applicationContext
        _version.value = System.currentTimeMillis()
        val glanceId = runCatching { GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId) }.getOrNull()
        if (glanceId != null) ScheduleWidget().update(app, glanceId) else ScheduleWidget().updateAll(app)
        scheduleNext(app)
    }

    /** Ставит будильник на ближайший момент, когда хоть один виджет должен измениться. */
    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = tickIntent(app)
        val ids = Widgets.ids(app)
        if (ids.isEmpty()) {
            am.cancel(pending)
            return
        }
        val now = System.currentTimeMillis()
        var next = now + IDLE_MS
        for (id in ids) {
            val d = Widgets.state(app, id, now).display
            d.boundaryAt?.let { if (it > now) next = minOf(next, it) }
            if (d.countdownTo != null) next = minOf(next, now + PROGRESS_TICK_MS)
        }
        // Небольшой запас, чтобы будильник сработал уже после границы.
        val at = next + 500L
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExact(AlarmManager.RTC, at, pending)
        } else {
            // Без разрешения на точные будильники — системный «примерно вовремя».
            am.set(AlarmManager.RTC, at, pending)
        }
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetEventsReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
