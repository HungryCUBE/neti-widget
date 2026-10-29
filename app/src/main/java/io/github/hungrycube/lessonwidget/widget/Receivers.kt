package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Фоновая работа в ресивере: goAsync + корутина. Любая ошибка пишется в журнал и не роняет процесс.
 * goAsync() отдаёт результат только при первом вызове за onReceive, дальше — null.
 */
internal fun BroadcastReceiver.runAsync(context: Context, what: String, block: suspend () -> Unit): Job {
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    val app = context.applicationContext
    return CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Throwable) {
            runCatching { CrashLog.record(app, what, e) }
        } finally {
            try {
                pending?.finish()
            } catch (e: IllegalStateException) {
                // Некоторые прошивки сами завершают broadcast раньше.
            }
        }
    }
}

/**
 * Провайдер виджета. Сам рисует виджеты и ставит будильники — без Glance-сессий в WorkManager,
 * которые система откладывает на часы, если приложение давно не открывали.
 */
class ScheduleWidgetReceiver : AppWidgetProvider() {

    @VisibleForTesting
    internal var lastJob: Job? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        lastJob = runAsync(context, "Обновление виджета (onUpdate)") {
            RefreshScheduler.ensureDaily(context)
            WidgetUpdater.render(context, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        lastJob = runAsync(context, "Изменение размера виджета") {
            WidgetUpdater.render(context, intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val configs = WidgetConfigs(context)
        appWidgetIds.forEach(configs::remove)
        // Своё фото удаляется вместе с виджетом.
        appWidgetIds.forEach { WidgetPhotos.delete(context, "$it") }
        lastJob = runAsync(context, "Удаление виджета") {
            // Кэш группы удаляется, если её больше не использует ни один виджет и не приложение.
            ScheduleRepository(context).deleteUnused(Widgets.groupsInUse(context, excludeIds = appWidgetIds.toSet()))
            WidgetPhotos.cleanup(context, Widgets.ids(context).toSet() - appWidgetIds.toSet())
            WidgetUpdater.scheduleNext(context)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.scheduleNext(context)
    }
}

/** Будильник на границе пары или смене минуты, перезагрузка, обновление приложения, смена времени или пояса. */
class WidgetEventsReceiver : BroadcastReceiver() {

    @VisibleForTesting
    internal var lastJob: Job? = null

    override fun onReceive(context: Context, intent: Intent) {
        lastJob = runAsync(context, "Обновление виджета (${intent.action})") {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                RefreshScheduler.ensureDaily(context)
            }
            WidgetUpdater.updateAll(context)
        }
    }
}
