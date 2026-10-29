package io.github.hungrycube.lessonwidget.render.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import io.github.hungrycube.lessonwidget.render.PhotoInput
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Откуда берутся «живые» цвета: Material You (@dynamic), обои (@wallpaper), своё фото (@photo).
 * [live] = false — только запасные цвета из файла: так рисуются превью и скриншоты, чтобы совпадать с макетом.
 * Цвета своего фото берутся всегда, когда фото есть: его выбрали именно для этого виджета.
 */
class ColorSources(
    private val context: Context?,
    private val live: Boolean,
    private val photo: PhotoInput? = null,
    /** Пропорция виджета (ширина / высота): какая часть фото видна. */
    private val aspect: Float = 316f / 168f,
    /** Подмена цветов обоев primary, secondary, tertiary — для скриншотов на разных обоях. */
    private val wallpaperOverride: List<Int?>? = null,
) {
    private val wallpaperColors: List<Int?>? by lazy {
        wallpaperOverride ?: run {
            val ctx = context
            if (!live || ctx == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                null
            } else {
                runCatching { WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
                    ?.let { listOf(it.primaryColor.toArgb(), it.secondaryColor?.toArgb(), it.tertiaryColor?.toArgb()) }
            }
        }
    }

    /** Системные цвета Material You: accent1_600, neutral2_50 и т. п. (Android 12+). */
    fun dynamic(name: String): Int? {
        val ctx = context
        if (!live || ctx == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val id = ctx.resources.getIdentifier("system_$name", "color", "android")
        return if (id == 0) null else runCatching { ctx.getColor(id) }.getOrNull()
    }

    /** Цвета обоев: primary, secondary, tertiary (Android 8.1+). */
    fun wallpaper(name: String): Int? {
        val wc = wallpaperColors ?: return null
        return when (name) {
            "primary" -> wc.getOrNull(0)
            "secondary" -> wc.getOrNull(1)
            "tertiary" -> wc.getOrNull(2)
            else -> null
        }
    }

    /**
     * Цвета своего фото: dominant, light, dark, accent (главный яркий цвет), under (тёмная четверть нижней половины
     * кадра — под текстом), underBright (её светлая часть), topBright (светлая часть верха кадра, где шапка).
     */
    fun photo(name: String): Int? {
        val ph = photo ?: return null
        val c = ph.dominant ?: return null
        return when (name) {
            "dominant" -> c
            "light" -> ColorMath.mix(c, Color.WHITE, 0.55f)
            "dark" -> ColorMath.mix(c, Color.BLACK, 0.55f)
            "accent" -> ph.accent ?: c
            "under" -> ph.under(aspect) ?: c
            "underBright" -> ph.underBright(aspect) ?: c
            "topBright" -> ph.topBright(aspect) ?: c
            else -> null
        }
    }
}

/** HSL, смешивание и контраст по WCAG. */
object ColorMath {
    fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) * (1 - k) + ((b ushr shift) and 0xFF) * k).roundToInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** [h 0..360, s 0..1, l 0..1]. */
    fun toHsl(c: Int): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 0f) {
            s = d / (1f - abs(2f * l - 1f))
            h = when (mx) {
                r -> ((g - b) / d).mod(6f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
        }
        return floatArrayOf(h, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float, alpha: Int = 255): Int {
        val hh = h.mod(360f)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((hh / 60f).mod(2f) - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Относительная яркость по WCAG 2. */
    fun luminance(c: Int): Float {
        fun lin(v: Int): Float {
            val s = v / 255f
            return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
        return 0.2126f * lin((c shr 16) and 0xFF) + 0.7152f * lin((c shr 8) and 0xFF) + 0.0722f * lin(c and 0xFF)
    }

    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** Двигает светлоту [c] от цвета [against], пока контраст не станет не меньше [ratio]. */
    fun ensureContrast(c: Int, against: Int, ratio: Float): Int {
        if (contrast(c, against) >= ratio) return c
        val hsl = toHsl(c)
        val down = luminance(against) > 0.18f
        var l = hsl[2]
        var out = c
        repeat(100) {
            l = (if (down) l - 0.01f else l + 0.01f).coerceIn(0f, 1f)
            out = fromHsl(hsl[0], hsl[1], l, c ushr 24)
            if (contrast(out, against) >= ratio || l <= 0f || l >= 1f) return out
        }
        return out
    }

    /** Подложка [tint] поверх [over] плотнее (до 0.9), пока [text] на ней не читается с контрастом [ratio]. */
    fun veil(tint: Int, over: Int, text: Int, ratio: Float): Int {
        val opaque = tint or 0xFF000000.toInt()
        var a = (tint ushr 24) / 255f
        while (a < 0.9f && contrast(text, mix(over or 0xFF000000.toInt(), opaque, a)) < ratio) a = (a + 0.02f).coerceAtMost(0.9f)
        return ((a * 255f).roundToInt() shl 24) or (tint and 0xFFFFFF)
    }

    /** Тон фона: light — светлый (нужен тёмный текст), vivid — яркий насыщенный, dark — тёмный. */
    fun tone(c: Int): String {
        val hsl = toHsl(c)
        return when {
            luminance(c) >= 0.4f -> "light"
            hsl[1] >= 0.5f && hsl[2] >= 0.3f -> "vivid"
            else -> "dark"
        }
    }

    fun average(b: Bitmap): Int? =
        runCatching { Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt() }.getOrNull()
}

/**
 * Цвет в файле стиля: "#RGB", "#RRGGBB", "#AARRGGBB", "transparent", ссылка на палитру "@text" (или "@palette.text"),
 * "@dynamic.accent1_600", "@wallpaper.primary", "@photo.dominant". Прозрачность — после косой черты: "@text/50%", "#FFFFFF/0.2".
 * Несколько вариантов через «|» — берётся первый доступный: "@wallpaper.primary | @dynamic.accent1_500 | #4B5BFF".
 * Функции:
 * - hsl(цвет, h: 30, dh: -4, s: 0.5, l: >0.6) — задать оттенок (h) или сдвинуть его (dh), насыщенность и светлоту:
 *   число — задать, ">x" — не меньше, "<x" — не больше, "*x" — умножить;
 * - mix(цвет, цвет, 0.3) — смешать (0 — первый, 1 — второй);
 * - contrast(цвет, against: цвет, min: 4.5) — осветлить или затемнить, пока контраст с against не станет не меньше min;
 * - veil(подложка, over: фон, text: цвет текста, min: 4.5) — сделать полупрозрачную подложку плотнее, пока текст
 *   на ней поверх фона не станет читаться с контрастом min (не плотнее 0.9). Если и так читается — подложка как есть.
 * Если цвет внутри функции недоступен, недоступна вся функция — берётся следующий вариант после «|».
 */
object ColorExpr {
    private val DYNAMIC = Regex("(accent[123]|neutral[12])_(0|10|50|100|200|300|400|500|600|700|800|900|1000)")
    private val WALLPAPER = setOf("primary", "secondary", "tertiary")
    private val PHOTO = setOf("dominant", "light", "dark", "accent", "under", "underBright", "topBright")
    private val FUNCTIONS = mapOf(
        "hsl" to setOf("h", "dh", "s", "l"),
        "mix" to emptySet(),
        "contrast" to setOf("against", "min"),
        "veil" to setOf("over", "text", "min"),
    )

    // ---------- Разбор ----------

    private class Alt(val options: List<Opt>)
    private class Opt(val atom: Atom, val alpha: Float?)
    private sealed interface Atom
    private class Hex(val color: Int) : Atom
    private class Ref(val name: String) : Atom
    private class Source(val kind: String, val name: String) : Atom
    private class Fn(val name: String, val args: List<Arg>) : Atom
    private class Arg(val key: String?, val color: Alt?, val op: Char?, val number: Float?)

    private class ParseError(message: String) : Exception(message)

    private val cache = ConcurrentHashMap<String, Result<Alt>>()

    private fun parsed(expr: String): Result<Alt> = cache.getOrPut(expr) { runCatching { Parser(expr).all() } }

    private class Parser(private val s: String) {
        private var i = 0

        fun all(): Alt {
            val a = alt()
            ws()
            if (i < s.length) throw ParseError("лишнее «${s.substring(i)}» в «$s»")
            return a
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char? {
            ws()
            return s.getOrNull(i)
        }

        private fun alt(): Alt {
            val opts = mutableListOf(opt())
            while (peek() == '|') {
                i++
                opts += opt()
            }
            return Alt(opts)
        }

        private fun opt(): Opt {
            val atom = atom()
            if (peek() != '/') return Opt(atom, null)
            i++
            ws()
            val start = i
            while (i < s.length && s[i] !in "|,)" && !s[i].isWhitespace()) i++
            val raw = s.substring(start, i)
            val k = if (raw.endsWith("%")) raw.dropLast(1).toFloatOrNull()?.div(100f) else raw.toFloatOrNull()
            if (k == null || k !in 0f..1f) throw ParseError("неверная прозрачность «/$raw» в «$s» (нужно, например, /50% или /0.5)")
            return Opt(atom, k)
        }

        private fun word(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
            return s.substring(start, i)
        }

        private fun atom(): Atom {
            when (peek()) {
                null, '|' -> throw ParseError("пустой вариант цвета в «$s»")
                '#' -> {
                    i++
                    val h = word()
                    return Hex(ColorExpr.parseHex("#$h") ?: throw ParseError("неверный цвет «#$h» (нужно #RRGGBB или #AARRGGBB)"))
                }
                '@' -> {
                    i++
                    val name = word()
                    return when {
                        name.startsWith("dynamic.") -> Source("dynamic", name.removePrefix("dynamic."))
                        name.startsWith("wallpaper.") -> Source("wallpaper", name.removePrefix("wallpaper."))
                        name.startsWith("photo.") -> Source("photo", name.removePrefix("photo."))
                        else -> Ref(name.removePrefix("palette."))
                    }
                }
            }
            val name = word()
            if (name == "transparent") return Hex(0)
            if (name.isEmpty() || peek() != '(') throw ParseError("непонятный цвет «$s»")
            if (name !in ColorExpr.FUNCTIONS) throw ParseError("нет функции «$name», есть: ${ColorExpr.FUNCTIONS.keys.joinToString()}")
            i++
            val args = mutableListOf<Arg>()
            while (true) {
                args += arg()
                when (peek()) {
                    ',' -> i++
                    ')' -> {
                        i++
                        return Fn(name, args)
                    }
                    else -> throw ParseError("в «$s» не закрыта скобка после $name(")
                }
            }
        }

        private fun arg(): Arg {
            ws()
            val save = i
            val key = word().takeIf { it.isNotEmpty() && peek() == ':' }
            if (key != null) i++ else i = save
            val c = peek()
            if (c != null && (c.isDigit() || c in ".-+<>*")) {
                val op = c.takeIf { it in "<>*" }?.also { i++ }
                ws()
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] in ".-+%")) i++
                val raw = s.substring(start, i)
                val v = if (raw.endsWith("%")) raw.dropLast(1).toFloatOrNull()?.div(100f) else raw.toFloatOrNull()
                return Arg(key, null, op, v ?: throw ParseError("неверное число «$raw» в «$s»"))
            }
            return Arg(key, alt(), null, null)
        }
    }

    // ---------- Проверка ----------

    /** Текст ошибки или null, если выражение правильное. [palette] — имена цветов палитры. */
    fun check(expr: String, palette: Set<String>): String? {
        val alt = parsed(expr).getOrElse { return it.message }
        checkAlt(alt, palette)?.let { return it }
        // Последний вариант должен быть всегда доступен, иначе на старом Android цвета не будет.
        if (!available(alt.options.last().atom)) return "у «$expr» нет запасного цвета: добавьте в конце, например, | #6750A4"
        return null
    }

    private fun checkAlt(alt: Alt, palette: Set<String>): String? {
        for (opt in alt.options) {
            val err = when (val a = opt.atom) {
                is Hex -> null
                is Ref -> if (a.name !in palette) "в palette нет цвета «${a.name}»" else null
                is Source -> when (a.kind) {
                    "dynamic" -> if (!DYNAMIC.matches(a.name)) "нет такого системного цвета «@dynamic.${a.name}» (пример: @dynamic.accent1_600)" else null
                    "wallpaper" -> if (a.name !in WALLPAPER) "у обоев есть только primary, secondary, tertiary: «@wallpaper.${a.name}»" else null
                    else -> if (a.name !in PHOTO) "у фото есть только ${PHOTO.joinToString()}: «@photo.${a.name}»" else null
                }
                is Fn -> checkFn(a, palette)
            }
            if (err != null) return err
        }
        return null
    }

    private fun checkFn(f: Fn, palette: Set<String>): String? {
        val keys = FUNCTIONS.getValue(f.name)
        for (a in f.args) a.color?.let { c -> checkAlt(c, palette)?.let { return it } }
        f.args.mapNotNull { it.key }.firstOrNull { it !in keys }?.let { return "${f.name}(): нет параметра «$it», есть: ${keys.joinToString()}" }
        val positional = f.args.filter { it.key == null }
        val ok = when (f.name) {
            "hsl" -> positional.size == 1 && positional[0].color != null && f.args.filter { it.key != null }.all { it.number != null }
            "mix" -> positional.size == 3 && positional[0].color != null && positional[1].color != null && positional[2].number != null
            "veil" -> positional.size == 1 && positional[0].color != null && f.args.any { it.key == "over" && it.color != null } &&
                f.args.any { it.key == "text" && it.color != null }
            else -> positional.size == 1 && positional[0].color != null &&
                f.args.any { it.key == "against" && it.color != null } && f.args.any { it.key == "min" && it.number != null }
        }
        return if (ok) null else when (f.name) {
            "hsl" -> "hsl(цвет, h: 30, dh: -4, s: 0.5, l: >0.6) — первым идёт цвет, дальше числа"
            "mix" -> "mix(цвет, цвет, доля)"
            "veil" -> "veil(подложка, over: фон, text: цвет текста, min: 4.5)"
            else -> "contrast(цвет, against: цвет, min: 4.5)"
        }
    }

    private fun available(a: Atom): Boolean = when (a) {
        is Hex, is Ref -> true
        is Source -> false
        is Fn -> a.args.all { arg -> arg.color?.let { c -> available(c.options.last().atom) } ?: true }
    }

    // ---------- Вычисление ----------

    /** Цвет или null, если ни один вариант недоступен. [palette] — уже готовые цвета палитры по имени. */
    fun resolve(expr: String, palette: (String) -> Int?, sources: ColorSources): Int? =
        parsed(expr).getOrNull()?.let { eval(it, palette, sources) }

    private fun eval(alt: Alt, palette: (String) -> Int?, sources: ColorSources): Int? {
        for (opt in alt.options) {
            val base = when (val a = opt.atom) {
                is Hex -> a.color
                is Ref -> palette(a.name)
                is Source -> when (a.kind) {
                    "dynamic" -> sources.dynamic(a.name)
                    "wallpaper" -> sources.wallpaper(a.name)
                    else -> sources.photo(a.name)
                }
                is Fn -> call(a, palette, sources)
            } ?: continue
            return opt.alpha?.let { withAlpha(base, it) } ?: base
        }
        return null
    }

    private fun call(f: Fn, palette: (String) -> Int?, sources: ColorSources): Int? {
        val positional = f.args.filter { it.key == null }
        fun named(k: String) = f.args.firstOrNull { it.key == k }
        val first = positional.firstOrNull()?.color?.let { eval(it, palette, sources) } ?: return null
        return when (f.name) {
            "hsl" -> {
                val hsl = ColorMath.toHsl(first)
                var h = named("h")?.number ?: hsl[0]
                named("dh")?.number?.let { h += it }
                fun apply(cur: Float, a: Arg?): Float {
                    val v = a?.number ?: return cur
                    return when (a.op) {
                        '>' -> max(cur, v)
                        '<' -> min(cur, v)
                        '*' -> cur * v
                        else -> v
                    }.coerceIn(0f, 1f)
                }
                ColorMath.fromHsl(h, apply(hsl[1], named("s")), apply(hsl[2], named("l")), first ushr 24)
            }
            "mix" -> {
                val second = positional.getOrNull(1)?.color?.let { eval(it, palette, sources) } ?: return null
                ColorMath.mix(first, second, (positional.getOrNull(2)?.number ?: 0.5f).coerceIn(0f, 1f))
            }
            "veil" -> {
                val over = named("over")?.color?.let { eval(it, palette, sources) } ?: return null
                val text = named("text")?.color?.let { eval(it, palette, sources) } ?: return null
                ColorMath.veil(first, over, text, named("min")?.number ?: 4.5f)
            }
            else -> {
                val against = named("against")?.color?.let { eval(it, palette, sources) } ?: return null
                ColorMath.ensureContrast(first, against, named("min")?.number ?: 4.5f)
            }
        }
    }

    /** Имена цветов палитры, на которые ссылается выражение (и внутри функций). */
    fun references(expr: String): List<String> {
        val out = mutableListOf<String>()
        fun walk(alt: Alt) {
            for (o in alt.options) {
                when (val a = o.atom) {
                    is Ref -> out += a.name
                    is Fn -> a.args.forEach { arg -> arg.color?.let(::walk) }
                    else -> Unit
                }
            }
        }
        parsed(expr).getOrNull()?.let(::walk)
        return out
    }

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

    private fun withAlpha(c: Int, k: Float): Int {
        val a = ((c ushr 24) * k).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (c and 0x00FFFFFF)
    }
}
