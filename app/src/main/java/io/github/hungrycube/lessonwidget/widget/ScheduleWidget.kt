package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.action.ActionParameters
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.ui.MainActivity
import io.github.hungrycube.lessonwidget.work.RefreshScheduler

/**
 * Виджет на Glance. Вся картинка рисуется в Bitmap (шрифты, кольца, точки и градиенты
 * в RemoteViews иначе не передать). Виджет перерисовывается на границах пар и раз в минуту,
 * пока идёт отсчёт, — только при включённом экране.
 */
class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val version by WidgetUpdater.version.collectAsState()
            WidgetContent(context, appWidgetId, version)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int, version: Long) {
    val size = LocalSize.current
    // version читается, чтобы открытая сессия перерисовывалась при каждом обновлении.
    val now = maxOf(System.currentTimeMillis(), version)
    val renderer = WidgetRenderer(context)
    // Любая ошибка (битый кэш и т.п.) — показываем «нет данных», а не «Не удалось загрузить виджет».
    val (state, rendered) = runCatching {
        val s = Widgets.state(context, appWidgetId, now)
        s to renderer.render(s.style, s.display, size.width.value, size.height.value, RenderMode.WIDGET, nowMillis = now)
    }.getOrElse {
        Log.w("LessonWidget", "Не удалось нарисовать виджет", it)
        val s = WidgetState(appWidgetId, null, Styles.MINIMAL, null, DisplayBuilder.build(null, "—", null, now))
        s to renderer.render(s.style, s.display, size.width.value, size.height.value, RenderMode.WIDGET, nowMillis = now)
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
