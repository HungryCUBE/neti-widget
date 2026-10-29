package io.github.hungrycube.lessonwidget.data

import android.content.Context
import androidx.core.content.edit
import io.github.hungrycube.lessonwidget.render.Styles
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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

    /** Настройки стиля по умолчанию (подложка, затемнение…) — для виджетов без своих. */
    var defaultOptions: Map<String, String>
        get() = prefs.getString("default_options", null)
            ?.let { runCatching { json.decodeFromString(optionsSerializer, it) }.getOrNull() }.orEmpty()
        set(value) = prefs.edit { putString("default_options", json.encodeToString(optionsSerializer, value)) }

    /** Кадр фото по умолчанию (само фото — WidgetPhotos.DEFAULT). */
    var defaultPhoto: PhotoFrame
        get() = prefs.getString("default_photo", null)
            ?.let { runCatching { json.decodeFromString(PhotoFrame.serializer(), it) }.getOrNull() } ?: PhotoFrame()
        set(value) = prefs.edit { putString("default_photo", json.encodeToString(PhotoFrame.serializer(), value)) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val optionsSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}

/** Настройки одного виджета. null в поле — «как в приложении». */
@Serializable
data class WidgetConfig(
    val group: String? = null,
    val style: String? = null,
    /** 0 — все подгруппы. */
    val subgroup: Int? = null,
    /** Настройки стиля (подложка, затемнение…). */
    val options: Map<String, String>? = null,
    /** Кадр своего фото; само фото — WidgetPhotos.file(<appWidgetId>). */
    val photo: PhotoFrame? = null,
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
