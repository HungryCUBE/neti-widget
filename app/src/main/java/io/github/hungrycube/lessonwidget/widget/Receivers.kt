package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Фоновая работа в ресивере: goAsync + корутина. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            // Виджет не должен падать из-за фоновой ошибки — следующий будильник попробует снова.
            android.util.Log.w("LessonWidget", "Фоновое обновление не удалось", e)
        } finally {
            pending.finish()
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshScheduler.ensureDaily(context)
        runAsync { WidgetUpdater.scheduleNext(context) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val configs = WidgetConfigs(context)
        appWidgetIds.forEach(configs::remove)
        runAsync {
            // Кэш группы удаляется, если её больше не использует ни один виджет и не приложение.
            ScheduleRepository(context).deleteUnused(Widgets.groupsInUse(context, excludeIds = appWidgetIds.toSet()))
            WidgetUpdater.scheduleNext(context)
        }
    }
}

/** Будильник на границе пары, перезагрузка, смена времени или часового пояса. */
class WidgetEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RefreshScheduler.ensureDaily(context)
        }
        runAsync { WidgetUpdater.updateAll(context) }
    }
}
