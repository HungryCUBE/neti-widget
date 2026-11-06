package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.SizeF
import androidx.core.os.BundleCompat
import io.github.hungrycube.lessonwidget.BuildConfig
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.display.withShortTitles
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetConfig
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetStyle

/** Что показывает конкретный виджет. */
data class WidgetState(
    val appWidgetId: Int,
    val group: String?,
    val style: WidgetStyle,
    val subgroup: Int?,
    val display: WidgetDisplay,
    /** Настройки стиля и своё фото. */
    val custom: StyleCustom? = null,
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
        val style = Styles.byId(context, cfg?.style ?: settings.defaultStyle)
        val subgroup = (cfg?.subgroup ?: settings.subgroup).takeIf { it > 0 }
        val display = displayFor(context, group, subgroup, nowMillis, settings.shortTitles)
        return WidgetState(appWidgetId, group, style, subgroup, display, customFor(context, style, appWidgetId, cfg, settings))
    }

    /**
     * Настройки стиля и фото виджета: свои, а если их нет — по умолчанию из настроек приложения
     * (так новый виджет со «Своим фото» по умолчанию сразу получает это фото).
     */
    fun customFor(context: Context, style: WidgetStyle, appWidgetId: Int?, cfg: WidgetConfig?, settings: Settings = Settings(context)): StyleCustom? {
        if (style.options.isEmpty() && !style.usesPhoto) return null
        val photo = if (style.usesPhoto) {
            WidgetPhotos.input(WidgetPhotos.fileFor(context, appWidgetId), cfg?.photo ?: settings.defaultPhoto)
        } else {
            null
        }
        return StyleCustom(cfg?.options ?: settings.defaultOptions, photo)
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

    /** Что лаунчер сообщает о размерах: если виджет выглядит странно, по этому видно почему. */
    fun sizeReport(context: Context): String = buildString {
        val manager = AppWidgetManager.getInstance(context)
        val dm = context.resources.displayMetrics
        appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        appendLine("Приложение: ${BuildConfig.VERSION_NAME}")
        appendLine("Экран: ${dm.widthPixels}×${dm.heightPixels}, плотность ${dm.density}")
        for (id in ids(context)) {
            val o = manager.getAppWidgetOptions(id) ?: continue
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BundleCompat.getParcelableArrayList(o, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            } else {
                null
            }
            append("Виджет $id: ширина ${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}–${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)}")
            append(", высота ${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}–${o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)}")
            appendLine(", размеры ${list.orEmpty().joinToString()}")
        }
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
