package io.github.hungrycube.lessonwidget.render.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import kotlin.math.roundToInt

/**
 * Откуда берутся «живые» цвета: Material You (@dynamic), обои (@wallpaper), своё фото (@photo).
 * [live] = false — только запасные цвета из файла: так рисуются превью и скриншоты, чтобы совпадать с макетом.
 */
class ColorSources(
    private val context: Context?,
    private val live: Boolean,
    private val photo: () -> Bitmap? = { null },
) {
    private val wallpaperColors by lazy {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            null
        } else {
            runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        }
    }

    private val photoColor by lazy { photo()?.let { average(it) } }

    /** Системные цвета Material You: accent1_600, neutral2_50 и т. п. (Android 12+). */
    fun dynamic(name: String): Int? {
        if (!live || context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val id = context.resources.getIdentifier("system_$name", "color", "android")
        return if (id == 0) null else runCatching { context.getColor(id) }.getOrNull()
    }

    /** Цвета обоев: primary, secondary, tertiary (Android 8.1+). */
    fun wallpaper(name: String): Int? {
        if (!live) return null
        val wc = wallpaperColors ?: return null
        val c = when (name) {
            "primary" -> wc.primaryColor
            "secondary" -> wc.secondaryColor
            "tertiary" -> wc.tertiaryColor
            else -> null
        }
        return c?.toArgb()
    }

    /** Цвета своего фото: dominant, light, dark. */
    fun photo(name: String): Int? {
        if (!live) return null
        val c = photoColor ?: return null
        return when (name) {
            "dominant" -> c
            "light" -> mix(c, Color.WHITE, 0.55f)
            "dark" -> mix(c, Color.BLACK, 0.55f)
            else -> null
        }
    }

    private fun average(b: Bitmap): Int? =
        runCatching { Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt() }.getOrNull()

    private fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * Цвет в файле стиля: "#RGB", "#RRGGBB", "#AARRGGBB", "transparent", ссылка на палитру "@text" (или "@palette.text"),
 * "@dynamic.accent1_600", "@wallpaper.primary", "@photo.dominant". Прозрачность — после косой черты: "@text/50%", "#FFFFFF/0.2".
 * Несколько вариантов через «|» — берётся первый доступный: "@wallpaper.primary | @dynamic.accent1_500 | #4B5BFF".
 */
object ColorExpr {
    private val DYNAMIC = Regex("(accent[123]|neutral[12])_(0|10|50|100|200|300|400|500|600|700|800|900|1000)")
    private val WALLPAPER = setOf("primary", "secondary", "tertiary")
    private val PHOTO = setOf("dominant", "light", "dark")

    /** Текст ошибки или null, если выражение правильное. [palette] — имена цветов палитры. */
    fun check(expr: String, palette: Set<String>): String? {
        val options = expr.split('|').map { it.trim() }
        if (options.any { it.isEmpty() }) return "пустой вариант цвета в «$expr»"
        for (opt in options) {
            val (atom, alpha) = splitAlpha(opt)
            if (alpha != null && alpha.isNaN()) return "неверная прозрачность в «$opt» (нужно, например, /50% или /0.5)"
            val err = when {
                atom == "transparent" -> null
                atom.startsWith("#") -> if (parseHex(atom) == null) "неверный цвет «$atom» (нужно #RRGGBB или #AARRGGBB)" else null
                atom.startsWith("@dynamic.") -> if (!DYNAMIC.matches(atom.removePrefix("@dynamic."))) "нет такого системного цвета «$atom» (пример: @dynamic.accent1_600)" else null
                atom.startsWith("@wallpaper.") -> if (atom.removePrefix("@wallpaper.") !in WALLPAPER) "у обоев есть только primary, secondary, tertiary: «$atom»" else null
                atom.startsWith("@photo.") -> if (atom.removePrefix("@photo.") !in PHOTO) "у фото есть только dominant, light, dark: «$atom»" else null
                atom.startsWith("@") -> {
                    val key = atom.removePrefix("@").removePrefix("palette.")
                    if (key !in palette) "в palette нет цвета «$key»" else null
                }
                else -> "непонятный цвет «$atom»"
            }
            if (err != null) return err
        }
        // Последний вариант должен быть всегда доступен, иначе на старом Android цвета не будет.
        val last = splitAlpha(options.last()).first
        if (last.startsWith("@dynamic.") || last.startsWith("@wallpaper.") || last.startsWith("@photo.")) {
            return "у «$expr» нет запасного цвета: добавьте в конце, например, | #6750A4"
        }
        return null
    }

    /** Цвет или null, если ни один вариант недоступен. [palette] — уже готовые цвета палитры по имени. */
    fun resolve(expr: String, palette: (String) -> Int?, sources: ColorSources): Int? {
        for (opt in expr.split('|')) {
            val (atom, alpha) = splitAlpha(opt.trim())
            val base = when {
                atom == "transparent" -> 0
                atom.startsWith("#") -> parseHex(atom)
                atom.startsWith("@dynamic.") -> sources.dynamic(atom.removePrefix("@dynamic."))
                atom.startsWith("@wallpaper.") -> sources.wallpaper(atom.removePrefix("@wallpaper."))
                atom.startsWith("@photo.") -> sources.photo(atom.removePrefix("@photo."))
                atom.startsWith("@") -> palette(atom.removePrefix("@").removePrefix("palette."))
                else -> null
            } ?: continue
            return if (alpha == null) base else withAlpha(base, alpha)
        }
        return null
    }

    /** Имена цветов палитры, на которые ссылается выражение. */
    fun references(expr: String): List<String> = expr.split('|').map { splitAlpha(it.trim()).first }
        .filter { it.startsWith("@") && !it.startsWith("@dynamic.") && !it.startsWith("@wallpaper.") && !it.startsWith("@photo.") }
        .map { it.removePrefix("@").removePrefix("palette.") }

    fun parseHex(s: String): Int? {
        if (!s.startsWith("#")) return null
        val h = s.substring(1)
        if (h.isEmpty() || h.any { it.digitToIntOrNull(16) == null }) return null
        return when (h.length) {
            3 -> (0xFF000000L or h.map { "$it$it" }.joinToString("").toLong(16)).toInt()
            6 -> (0xFF000000L or h.toLong(16)).toInt()
            8 -> h.toLong(16).toInt()
            else -> null
        }
    }

    private fun splitAlpha(opt: String): Pair<String, Float?> {
        val i = opt.lastIndexOf('/')
        if (i < 0) return opt to null
        val raw = opt.substring(i + 1).trim()
        val k = if (raw.endsWith("%")) raw.dropLast(1).trim().toFloatOrNull()?.div(100f) else raw.toFloatOrNull()
        return opt.substring(0, i).trim() to (k?.takeIf { it in 0f..1f } ?: Float.NaN)
    }

    private fun withAlpha(c: Int, k: Float): Int {
        val a = ((c ushr 24) * k).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (c and 0x00FFFFFF)
    }
}
