package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.hungrycube.lessonwidget.R
import java.io.File

/** Загруженный шрифт. [system] — системный: жирность задаётся стилем Typeface, а не осью wght. */
class LoadedFont(val typeface: Typeface, val system: Boolean)

/**
 * Шрифты по имени из файла стиля. Ищутся по порядку: res/font/<имя>.ttf, assets/fonts/<имя>.ttf|otf,
 * папка fonts рядом с импортированными стилями. «system», «serif», «monospace» и неизвестные имена —
 * системные шрифты. Шрифты из Google Fonts (OFL) вариативные, жирность задаётся осью wght.
 */
object FontCache {
    const val SYSTEM = "system"

    private val systemFonts = mapOf(
        SYSTEM to Typeface.SANS_SERIF,
        "serif" to Typeface.SERIF,
        "monospace" to Typeface.MONOSPACE,
    )
    private val cache = HashMap<String, LoadedFont>()

    /** Папки со шрифтами импортированных стилей. */
    @Volatile
    var extraDirs: List<File> = emptyList()

    fun get(context: Context, name: String): LoadedFont = synchronized(cache) {
        cache.getOrPut(name) { load(context, name) ?: LoadedFont(systemFonts[name] ?: Typeface.SANS_SERIF, true) }
    }

    /** Есть ли такой шрифт (системные имена тоже считаются). */
    fun exists(context: Context, name: String): Boolean = name in systemFonts || get(context, name).let { !it.system }

    private fun load(context: Context, name: String): LoadedFont? {
        if (name in systemFonts || !name.matches(Regex("[a-z0-9_]+"))) return null
        val res = context.resources
        val id = res.getIdentifier(name, "font", context.packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "font", R::class.java.name.substringBeforeLast('.')).takeIf { it != 0 }
        if (id != null) {
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()?.let { return LoadedFont(it, false) }
        }
        val assets = runCatching { context.assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        for (ext in listOf("ttf", "otf")) {
            if ("$name.$ext" in assets) {
                runCatching { Typeface.createFromAsset(context.assets, "fonts/$name.$ext") }.getOrNull()?.let { return LoadedFont(it, false) }
            }
        }
        for (dir in extraDirs) {
            for (ext in listOf("ttf", "otf")) {
                val file = File(dir, "$name.$ext")
                if (file.isFile) runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { return LoadedFont(it, false) }
            }
        }
        return null
    }

    /** Сбросить кэш, например после импорта стиля со своим шрифтом. */
    fun clear() = synchronized(cache) { cache.clear() }
}
