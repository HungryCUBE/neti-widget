package io.github.hungrycube.lessonwidget.data

import android.content.Context
import androidx.core.content.edit
import io.github.hungrycube.lessonwidget.render.Styles
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Настройки приложения. Хранятся только на телефоне. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Группа по умолчанию — для приложения и виджетов без своей группы. */
    var defaultGroup: String?
        get() = prefs.getString("default_group", null)
        set(value) = prefs.edit { putString("default_group", value) }

    var defaultStyle: String
        get() = prefs.getString("default_style", Styles.DEFAULT_ID) ?: Styles.DEFAULT_ID
        set(value) = prefs.edit { putString("default_style", value) }

    /** Подгруппа: 0 — показывать все. */
    var subgroup: Int
        get() = prefs.getInt("subgroup", 0)
        set(value) = prefs.edit { putInt("subgroup", value) }

    /** Короткие названия предметов на виджете. */
    var shortTitles: Boolean
        get() = prefs.getBoolean("short_titles", true)
        set(value) = prefs.edit { putBoolean("short_titles", value) }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit { putLong("last_update_check", value) }
}

/** Настройки одного виджета. null в поле — «как в приложении». */
@Serializable
data class WidgetConfig(
    val group: String? = null,
    val style: String? = null,
    /** 0 — все подгруппы. */
    val subgroup: Int? = null,
)

class WidgetConfigs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(appWidgetId: Int): WidgetConfig? =
        prefs.getString(key(appWidgetId), null)?.let {
            runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull()
        }

    fun set(appWidgetId: Int, config: WidgetConfig) =
        prefs.edit { putString(key(appWidgetId), json.encodeToString(WidgetConfig.serializer(), config)) }

    fun remove(appWidgetId: Int) = prefs.edit { remove(key(appWidgetId)) }

    fun ids(): List<Int> = prefs.all.keys.mapNotNull { it.removePrefix("w_").toIntOrNull() }

    private fun key(id: Int) = "w_$id"
}
