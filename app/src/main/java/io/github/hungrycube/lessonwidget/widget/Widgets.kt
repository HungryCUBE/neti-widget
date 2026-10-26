package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.display.withShortTitles
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetStyle

/** Что показывает конкретный виджет. */
data class WidgetState(
    val appWidgetId: Int,
    val group: String?,
    val style: WidgetStyle,
    val subgroup: Int?,
    val display: WidgetDisplay,
)

object Widgets {

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ScheduleWidgetReceiver::class.java))

    /** Группа, стиль и подгруппа виджета с учётом настроек приложения. */
    fun state(context: Context, appWidgetId: Int, nowMillis: Long = System.currentTimeMillis()): WidgetState {
        val settings = Settings(context)
        val cfg = WidgetConfigs(context).get(appWidgetId)
        val group = cfg?.group ?: settings.defaultGroup
        val style = Styles.byId(cfg?.style ?: settings.defaultStyle)
        val subgroup = (cfg?.subgroup ?: settings.subgroup).takeIf { it > 0 }
        val display = displayFor(context, group, subgroup, nowMillis, settings.shortTitles)
        return WidgetState(appWidgetId, group, style, subgroup, display)
    }

    fun displayFor(context: Context, group: String?, subgroup: Int?, nowMillis: Long, shortTitles: Boolean): WidgetDisplay {
        if (group == null) {
            return DisplayBuilder.build(null, "—", null, nowMillis).copy(
                title = "Выберите группу",
                headline = "Нет группы",
                status = "Нет группы",
                message = "Нажмите, чтобы настроить",
            )
        }
        val schedule = ScheduleRepository(context).load(group)
        val display = DisplayBuilder.build(schedule, group, subgroup, nowMillis)
        return if (shortTitles && display.phase != Phase.NO_DATA) display.withShortTitles() else display
    }

    /** Группы, которые нужны приложению и виджетам — их кэш обновляется, остальные удаляются. */
    fun groupsInUse(context: Context, excludeIds: Set<Int> = emptySet()): Set<String> {
        val settings = Settings(context)
        val configs = WidgetConfigs(context)
        val result = LinkedHashSet<String>()
        settings.defaultGroup?.let(result::add)
        for (id in ids(context)) {
            if (id in excludeIds) continue
            configs.get(id)?.group?.let(result::add)
        }
        return result
    }
}
