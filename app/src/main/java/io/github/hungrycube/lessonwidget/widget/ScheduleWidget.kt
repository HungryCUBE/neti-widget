package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.core.os.BundleCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.ui.MainActivity
import io.github.hungrycube.lessonwidget.work.RefreshScheduler
import kotlin.math.max
import kotlin.math.min

/**
 * Виджет на Glance. Вся картинка рисуется в Bitmap (шрифты, кольца, точки и градиенты
 * в RemoteViews иначе не передать). Glance здесь только собирает RemoteViews — отрисовку
 * запускает [WidgetUpdater] через compose(), без сессий в WorkManager.
 */
class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    /** Своё состояние Glance не нужно: всё лежит в настройках и кэше расписания. */
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { WidgetContent(context, appWidgetId) }
    }
}

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int) {
    // Размера может не быть, если лаунчер ещё не сообщил его, — тогда макетный 4×2.
    val size = LocalSize.current.takeIf { it.width.value > 0f && it.height.value > 0f } ?: WidgetUpdater.DEFAULT_SIZE
    // Состояние всегда считается от текущего времени, а не от того, какой будильник сработал.
    val now = WidgetUpdater.now()
    val maxPixels = bitmapBudget(context, LocalAppWidgetOptions.current)
    val renderer = WidgetRenderer(context)
    fun draw(s: WidgetState) =
        renderer.render(s.style, s.display, size.width.value, size.height.value, RenderMode.WIDGET, nowMillis = now, maxPixels = maxPixels)
    // Любая ошибка (битый кэш и т.п.) — показываем «нет данных», а не «Не удалось загрузить виджет».
    val (state, rendered) = runCatching {
        val s = Widgets.state(context, appWidgetId, now)
        s to draw(s)
    }.getOrElse {
        CrashLog.record(context, "Отрисовка виджета $appWidgetId", it)
        val s = WidgetState(appWidgetId, null, Styles.MINIMAL, null, DisplayBuilder.build(null, "—", null, now))
        s to draw(s)
    }
    val action = if (state.group != null && state.display.phase == Phase.NO_DATA) {
        actionRunCallback<RefreshAction>()
    } else {
        actionStartActivity(openAppIntent(context, appWidgetId, state.group))
    }
    Box(modifier = GlanceModifier.fillMaxSize().clickable(action)) {
        Image(
            provider = ImageProvider(rendered.bitmap),
            contentDescription = rendered.description,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/**
 * Сколько пикселей можно на одну картинку. Система не принимает обновление виджета, если все его
 * картинки вместе больше полутора экранов (4 байта на пиксель), а в SizeMode.Exact картинок
 * столько, сколько размеров сообщил лаунчер (обычно портрет и ландшафт).
 */
private fun bitmapBudget(context: Context, options: Bundle): Float {
    val dm = context.resources.displayMetrics
    val screen = dm.widthPixels.toFloat() * dm.heightPixels
    val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)?.size ?: 0
    } else {
        2
    }
    return min(WidgetRenderer.MAX_PIXELS, screen * 1.5f * 0.9f / max(2, sizes))
}

fun openAppIntent(context: Context, appWidgetId: Int, group: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("lessonwidget://widget/$appWidgetId")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        group?.let { putExtra(MainActivity.EXTRA_GROUP, it) }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** Кнопка «обновить» в состоянии «нет данных». */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}
