package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import io.github.hungrycube.lessonwidget.R
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.render.CountdownSlot
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.ui.MainActivity
import io.github.hungrycube.lessonwidget.work.RefreshScheduler

/**
 * Виджет на Glance. Вся картинка рисуется в Bitmap (шрифты, кольца, точки и градиенты
 * в RemoteViews иначе не передать), а обратный отсчёт — системный Chronometer поверх неё.
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
    val state = Widgets.state(context, appWidgetId, now)
    val rendered = WidgetRenderer(context).render(
        style = state.style,
        display = state.display,
        widthDp = size.width.value,
        heightDp = size.height.value,
        mode = RenderMode.WIDGET,
        nowMillis = now,
    )
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
        val slot = rendered.countdown
        val target = state.display.countdownTo
        if (slot != null && target != null) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(start = slot.x.dp, top = slot.y.dp)) {
                AndroidRemoteViews(
                    remoteViews = countdownViews(context, slot, target),
                    modifier = GlanceModifier.size(slot.width.dp, slot.height.dp),
                )
            }
        }
    }
}

/** Системный обратный отсчёт: тикает сам, виджет для этого не обновляется. */
private fun countdownViews(context: Context, slot: CountdownSlot, target: Long): RemoteViews {
    val rv = RemoteViews(context.packageName, slot.layout.layoutRes)
    val base = SystemClock.elapsedRealtime() + (target - System.currentTimeMillis())
    rv.setChronometer(R.id.countdown, base, slot.format, true)
    rv.setChronometerCountDown(R.id.countdown, true)
    rv.setTextColor(R.id.countdown, slot.color)
    rv.setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_DIP, slot.textSize)
    return rv
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
