package io.github.hungrycube.lessonwidget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.hungrycube.lessonwidget.data.GroupNotFoundException
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import io.github.hungrycube.lessonwidget.widget.Widgets
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Раз в сутки скачивает расписание всех используемых групп. Только при наличии сети. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = ScheduleRepository(ctx)
        val groups = Widgets.groupsInUse(ctx)
        var failed = false
        for ((index, group) in groups.withIndex()) {
            if (index > 0) delay(2_000) // не дёргать сайт пачкой запросов
            try {
                repo.refresh(group, minIntervalMs = MIN_INTERVAL_MS)
            } catch (e: GroupNotFoundException) {
                // Группа пропала с сайта — старые данные остаются, повтор не поможет.
            } catch (e: Exception) {
                failed = true
            }
        }
        val groupsRepo = GroupsRepository(ctx)
        if (groupsRepo.isStale()) runCatching { groupsRepo.refresh() }
        repo.deleteUnused(groups)
        WidgetUpdater.updateAll(ctx)
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        /** Ежедневное обновление не скачивает то, что обновлялось меньше часа назад. */
        private const val MIN_INTERVAL_MS = 3_600_000L
    }
}

object RefreshScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensureDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork("daily-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Обновить как можно скорее (как только будет сеть). */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, request)
    }
}
