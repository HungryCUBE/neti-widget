package io.github.hungrycube.lessonwidget.render.theme

import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Corners
import io.github.hungrycube.lessonwidget.render.Dim
import io.github.hungrycube.lessonwidget.render.FontCache
import io.github.hungrycube.lessonwidget.render.Gradient
import io.github.hungrycube.lessonwidget.render.Ink
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.Shadow
import io.github.hungrycube.lessonwidget.render.TextShadow
import io.github.hungrycube.lessonwidget.render.Typo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** Ошибка в файле стиля. Текст — для человека: файл, поле и что не так. */
class ThemeException(message: String) : Exception(message)

/** Ошибки (стиль не загрузится) и предупреждения (загрузится, но не совсем как задумано). */
class Problems {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
}

/**
 * Разбор, склейка и проверка файлов стилей. Порядок слоёв: общие умолчания (assets/engine/defaults.json),
 * стиль из extends, сам стиль; у каждого слоя после основы применяются его variants для текущей формы.
 */
object ThemeLoader {
    const val SCHEMA_VERSION = 1

    /** Формы в порядке применения вариантов. */
    val FORMS = listOf("short", "compact", "large")
    val SIZES = setOf("compact", "standard", "large")
    val BASE_ROLES = listOf("header", "title", "details", "next", "counter")
    val PROGRESS_TYPES = setOf("line", "thick", "ring", "dots", "blocks", "striped", "glossy", "text")
    val COUNTER_TYPES = setOf("text", "orb", "ring", "surface")
    val LAYER_TYPES = setOf("solid", "linear", "radial", "mesh", "wallpaperMesh", "image", "photo", "rules", "line", "band", "shape")
    val HIGHLIGHT_TYPES = setOf("innerStroke", "gloss")

    /** Варианты по тону фона (см. ThemeSpec.tone). */
    val TONES = listOf("tone:light", "tone:dark", "tone:vivid")
    val OPTION_TYPES = setOf("choice", "range", "switch")
    private val ALIGNS = setOf("start", "center", "end")
    private val WHEN = setOf("widget", "preview")
    private val FITS = setOf("cover", "contain", "stretch")
    private val RADIUS_OF = setOf("diagonal", "width", "height", "min", "farthest", "closest")
    private val COUNTER_UNITS = setOf("none", "inline", "below")
    private val PLACEMENTS = setOf("inline", "side", "aside", "header")

    /** Варианты, которые дают настройки стиля: "имя:значение" для choice и switch. */
    fun optionForms(options: Map<String, OptionSpec>): Set<String> = options.flatMap { (name, o) ->
        when (o.type) {
            "choice" -> o.values.map { "$name:$it" }
            "switch" -> listOf("$name:on", "$name:off")
            else -> emptyList()
        }
    }.toSet()

    /** Значения настроек: заданные пользователем, иначе по умолчанию. Неизвестные и неверные пропускаются. */
    fun optionValues(options: Map<String, OptionSpec>, chosen: Map<String, String>): Map<String, String> = options.mapValues { (name, o) ->
        val v = chosen[name]
        when (o.type) {
            "choice" -> v?.takeIf { it in o.values } ?: o.default ?: o.values.firstOrNull().orEmpty()
            "switch" -> v?.takeIf { it == "on" || it == "off" } ?: o.default ?: "off"
            else -> (v?.toFloatOrNull() ?: o.default?.toFloatOrNull() ?: o.min).coerceIn(o.min, o.max).toString()
        }
    }

    /** Имена, которые можно подставлять в strings. */
    val VARS = setOf(
        "group", "week", "status", "headline", "title", "dayLabel", "message",
        "num", "num2", "numLabel", "type", "typeShort", "room", "subgroup", "start", "end", "until",
        "next", "nextStart", "nextEnd", "nextRoom", "nextType", "nextTypeShort", "nextNum",
        "m", "minutes", "prefix", "duration", "percent", "cdUnit",
        "cmd", "h", "mm", "min", "n", "pairs", "phaseTitle",
    )

    private val json = Json { ignoreUnknownKeys = false }

    /** Текст файла → JSON-объект. */
    fun parse(text: String, file: String): JsonObject {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ThemeException("$file: ${syntaxMessage(e, text)}")
        }
        return element as? JsonObject ?: throw ThemeException("$file: файл стиля должен быть объектом { … }")
    }

    /** Глубокая склейка: объекты сливаются, остальное (строки, числа, списки) заменяется. null удаляет поле. */
    fun merge(base: JsonObject, over: JsonObject): JsonObject {
        val out = LinkedHashMap(base)
        for ((k, v) in over) {
            val old = out[k]
            when {
                v is JsonNull -> out.remove(k)
                v is JsonObject && old is JsonObject -> out[k] = merge(old, v)
                else -> out[k] = v
            }
        }
        return JsonObject(out)
    }

    /**
     * Склеивает слои [chain] с вариантами для [forms]. Порядок вариантов: сначала формы размера (short, compact, large),
     * потом остальные (тон, настройки) в том порядке, в каком они переданы.
     */
    fun compose(chain: List<JsonObject>, forms: Collection<String>): JsonObject {
        val ordered = FORMS.filter { it in forms } + forms.filter { it !in FORMS }
        var acc = JsonObject(emptyMap())
        for (layer in chain) {
            acc = merge(acc, JsonObject(layer - "variants"))
            val variants = layer["variants"] as? JsonObject ?: continue
            for (f in ordered) {
                (variants[f] as? JsonObject)?.let { acc = merge(acc, it) }
            }
        }
        return acc
    }

    /** Формы, для которых в слоях есть варианты: все их сочетания нужно проверить. */
    fun variantForms(chain: List<JsonObject>): Set<String> =
        chain.flatMap { (it["variants"] as? JsonObject)?.keys.orEmpty() }.toSet()

    fun decode(obj: JsonObject, file: String): ThemeSpec = try {
        json.decodeFromJsonElement(ThemeSpec.serializer(), obj)
    } catch (e: SerializationException) {
        throw ThemeException("$file: ${decodeMessage(e)}")
    } catch (e: IllegalArgumentException) {
        throw ThemeException("$file: ${decodeMessage(e)}")
    }

    /** Проверяет готовый (склеенный) стиль. [fontExists] — есть ли шрифт с таким именем. */
    fun check(spec: ThemeSpec, layouts: Set<String>, fontExists: (String) -> Boolean, rawVariants: Set<String> = emptySet()): Problems {
        val p = Problems()
        fun err(path: String, msg: String) {
            p.errors += "$path: $msg"
        }
        val names = spec.palette.keys
        fun color(path: String, expr: String?) {
            if (expr != null) ColorExpr.check(expr, names)?.let { err(path, it) }
        }
        fun whenMode(path: String, w: String?) {
            if (w != null && w !in WHEN) err("$path.when", "нужно widget или preview")
        }
        fun gradient(path: String, g: GradientSpec) {
            if (g.type != "linear" && g.type != "radial") err("$path.type", "нужно linear или radial")
            if (g.stops.size < 2) err("$path.stops", "нужно хотя бы два цвета")
            g.stops.forEachIndexed { i, s -> color("$path.stops[$i]", s.color) }
            if ((g.from == null) != (g.to == null)) err(path, "from и to задаются вместе")
            g.from?.let { if (it.size != 2) err("$path.from", "нужно [x, y]") }
            g.to?.let { if (it.size != 2) err("$path.to", "нужно [x, y]") }
            g.opacity?.let { if (it !in 0f..1f) err("$path.opacity", "от 0 до 1") }
            if (g.radiusOf !in RADIUS_OF) err("$path.radiusOf", "нужно ${RADIUS_OF.joinToString()}")
        }
        val ranges = spec.options.filterValues { it.type == "range" }.keys
        fun rangeRef(path: String, name: String?) {
            if (name != null && name !in ranges) err(path, "нет настройки-числа «$name» в options" + if (ranges.isEmpty()) "" else ", есть: ${ranges.joinToString()}")
        }
        fun dim(path: String, v: JsonPrimitive?) {
            if (v != null && Dim.parse(v.content) == null) err(path, "нужно число (dp) или доля «16%»")
        }
        fun ink(path: String, e: JsonElement?) {
            when (e) {
                null, JsonNull -> Unit
                is JsonPrimitive -> if (e.isString) color(path, e.content) else err(path, "нужен цвет-строка или градиент-объект")
                is JsonObject -> runCatching { json.decodeFromJsonElement(GradientSpec.serializer(), e) }
                    .onSuccess { gradient(path, it) }
                    .onFailure { err(path, decodeMessage(it)) }
                else -> err(path, "нужен цвет-строка или градиент-объект")
            }
        }
        fun shadow(path: String, s: ShadowSpec) {
            color("$path.color", s.color)
            whenMode(path, s.whenMode)
        }
        fun boxShadows(path: String, list: List<BoxShadowSpec>) = list.forEachIndexed { i, s ->
            color("$path[$i].color", s.color)
            whenMode("$path[$i]", s.whenMode)
            if (s.blur < 0f) err("$path[$i].blur", "не меньше нуля")
        }
        fun background(path: String, bg: BackgroundSpec) {
            bg.corners?.let { c -> Corners.error(c)?.let { err("$path.corners", it) } }
            boxShadows("$path.shadows", bg.shadows)
            if (bg.backdropBlur < 0f) err("$path.backdropBlur", "не меньше нуля")
            bg.layers.forEachIndexed { i, l ->
                val lp = "$path.layers[$i]"
                whenMode(lp, l.whenMode)
                rangeRef("$lp.opacityFrom", l.opacityFrom)
                when (l.type) {
                    "solid", "rules" -> if (l.color == null) err(lp, "нужен color") else color("$lp.color", l.color)
                    "line" -> {
                        if (l.color == null) err(lp, "нужен color") else color("$lp.color", l.color)
                        if (l.x == null && l.y == null) err(lp, "нужен x или y")
                    }
                    "band" -> {
                        if (l.color == null) err(lp, "нужен color") else color("$lp.color", l.color)
                        if (l.x == null) err(lp, "нужен x (доля ширины)")
                    }
                    "linear", "radial" -> gradient(lp, l.gradient())
                    "mesh" -> {
                        if (l.points.isEmpty()) err(lp, "нужны points")
                        l.points.forEachIndexed { j, pt -> color("$lp.points[$j].color", pt.color) }
                    }
                    "wallpaperMesh" -> l.points.forEachIndexed { j, pt -> color("$lp.points[$j].color", pt.color) }
                    "image" -> {
                        val src = l.src
                        if (src == null) err(lp, "нужен src") else if (".." in src || src.startsWith("/")) err("$lp.src", "только путь внутри папки стиля")
                        if (l.fit !in FITS) err("$lp.fit", "нужно cover, contain или stretch")
                    }
                    "photo" -> {
                        if (l.fit !in FITS) err("$lp.fit", "нужно cover, contain или stretch")
                        rangeRef("$lp.blurFrom", l.blurFrom)
                    }
                    "shape" -> {
                        l.box?.let { b ->
                            dim("$lp.box.left", b.left)
                            dim("$lp.box.top", b.top)
                            dim("$lp.box.right", b.right)
                            dim("$lp.box.bottom", b.bottom)
                            dim("$lp.box.width", b.width)
                            dim("$lp.box.height", b.height)
                        }
                        l.corners?.let { c -> Corners.error(c)?.let { err("$lp.corners", it) } }
                        ink("$lp.fill", l.fill)
                        l.surface?.let { background("$lp.surface", it) }
                        if (l.fill == null && l.surface == null) err(lp, "нужен fill или surface")
                    }
                    else -> err("$lp.type", "неизвестный слой «${l.type}», есть: ${LAYER_TYPES.joinToString()}")
                }
            }
            bg.border?.let {
                ink("$path.border.color", it.color)
                whenMode("$path.border", it.whenMode)
            }
            bg.highlights.forEachIndexed { i, h ->
                if (h.type !in HIGHLIGHT_TYPES) err("$path.highlights[$i].type", "нужно innerStroke или gloss")
                ink("$path.highlights[$i].color", h.color)
                whenMode("$path.highlights[$i]", h.whenMode)
            }
            bg.shadow?.let { shadow("$path.shadow", it) }
        }

        if (spec.schemaVersion != SCHEMA_VERSION) err("schemaVersion", "версия ${spec.schemaVersion} не поддерживается этой версией приложения")
        if (!spec.id.matches(Regex("[a-z0-9][a-z0-9_-]*"))) err("id", "нужны строчные латинские буквы, цифры, «-» и «_»")
        if (spec.name.isBlank()) err("name", "нет названия")
        if (spec.layout !in layouts) err("layout", "нет раскладки «${spec.layout}», есть: ${layouts.sorted().joinToString()}")
        spec.sizes.filter { it !in SIZES }.forEach { err("sizes", "неизвестный размер «$it», есть: compact, standard, large") }
        spec.palette.forEach { (k, v) -> color("palette.$k", v) }
        paletteCycle(spec.palette)?.let { err("palette", "цвета ссылаются друг на друга по кругу: $it") }
        background("background", spec.background)
        spec.backdrop?.let { background("backdrop", it) }
        spec.surfaces.forEach { (k, s) -> background("surfaces.$k", s) }
        color("tone", spec.tone)
        spec.options.forEach { (name, o) ->
            val path = "options.$name"
            if (!name.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) err(path, "имя латиницей, без пробелов")
            if (o.type !in OPTION_TYPES) err("$path.type", "нужно choice, range или switch")
            if (o.label.isBlank()) err("$path.label", "нет подписи")
            when (o.type) {
                "choice" -> {
                    if (o.values.size < 2) err("$path.values", "нужно хотя бы два значения")
                    if (o.default != null && o.default !in o.values) err("$path.default", "нет среди values")
                }
                "switch" -> if (o.default != null && o.default != "on" && o.default != "off") err("$path.default", "нужно on или off")
                "range" -> {
                    if (o.max <= o.min) err(path, "max должен быть больше min")
                    val d = o.default?.toFloatOrNull()
                    if (o.default != null && (d == null || d !in o.min..o.max)) err("$path.default", "число от ${o.min} до ${o.max}")
                }
            }
        }

        BASE_ROLES.filter { it !in spec.typography }.forEach { err("typography", "нет роли «$it»") }
        for ((role, t) in spec.typography) {
            val path = "typography.$role"
            t.base?.let { if (it !in spec.typography) err("$path.base", "нет роли «$it»") }
            t.size?.let { if (it <= 0f) err("$path.size", "размер должен быть больше нуля") }
            t.weight?.let { if (it !in 1..1000) err("$path.weight", "жирность от 1 до 1000") }
            t.lineHeight?.let { if (it < 0f) err("$path.lineHeight", "не меньше нуля (0 — как normal в CSS)") }
            t.align?.let { if (it !in ALIGNS) err("$path.align", "нужно start, center или end") }
            color("$path.color", t.color)
            t.fill?.let { gradient("$path.fill", it) }
            t.textShadow?.let { shadow("$path.textShadow", it) }
            t.shadows?.let { boxShadows("$path.shadows", it) }
            t.font?.let { if (!fontExists(it)) p.warnings += "$path.font: шрифта «$it» нет ни в res/font, ни в assets/fonts — будет системный" }
        }
        roleCycle(spec.typography)?.let { err("typography", "роли ссылаются друг на друга по кругу: $it") }

        val pr = spec.progress
        if (pr.type !in PROGRESS_TYPES) err("progress.type", "неизвестный тип «${pr.type}», есть: ${PROGRESS_TYPES.joinToString()}")
        ink("progress.track", pr.track)
        ink("progress.fill", pr.fill)
        color("progress.current", pr.current)
        pr.glow?.let { shadow("progress.glow", it) }
        if (pr.cap != "butt" && pr.cap != "round") err("progress.cap", "нужно butt или round")
        if (pr.step <= 0f || pr.block <= 0f) err("progress", "step и block должны быть больше нуля")
        fun surfaceRef(path: String, name: String?) {
            if (name != null && name !in spec.surfaces) err(path, "нет подложки «$name» в surfaces")
        }
        surfaceRef("progress.trackSurface", pr.trackSurface)
        surfaceRef("progress.fillSurface", pr.fillSurface)

        val cn = spec.counter
        if (cn.type !in COUNTER_TYPES) err("counter.type", "неизвестный тип «${cn.type}», есть: ${COUNTER_TYPES.joinToString()}")
        ink("counter.fill", cn.fill)
        color("counter.track", cn.track)
        surfaceRef("counter.surface", cn.surface)
        if (cn.type == "surface" && cn.surface == null) err("counter.surface", "для type surface нужна подложка из surfaces")
        if (cn.unit !in COUNTER_UNITS) err("counter.unit", "нужно ${COUNTER_UNITS.joinToString()}")
        if (cn.placement !in PLACEMENTS) err("counter.placement", "нужно ${PLACEMENTS.joinToString()}")
        if (cn.valign != "center" && cn.valign != "end") err("counter.valign", "нужно center или end")
        if (cn.insets.size > 4) err("counter.insets", "от одного до четырёх чисел, как padding в CSS")

        val tplErrors = mutableListOf<String>()
        val tpls = spec.strings.mapNotNull { (k, v) -> Templates.parse(v, "strings.$k", tplErrors)?.let { k to it } }.toMap()
        p.errors += tplErrors
        for ((k, t) in tpls) {
            for (ph in Templates.placeholders(t).toSet()) {
                val parts = ph.split('|').map { it.trim() }
                val name = parts[0]
                when {
                    name.startsWith("@") -> if (name.substring(1) !in tpls) err("strings.$k", "{$ph}: нет строки «${name.substring(1)}»")
                    name !in VARS -> err("strings.$k", "{$name}: нет такого значения. Есть: ${VARS.joinToString()}")
                }
                parts.drop(1).filter { it !in Templates.FILTERS }.forEach { err("strings.$k", "{$ph}: нет фильтра «$it», есть: ${Templates.FILTERS.joinToString()}") }
            }
        }

        val ic = spec.icons
        color("icons.color", ic.color)
        val iconNames = Icons.BUILT_IN.keys + ic.custom.keys
        (ic.slots.entries.map { "icons.slots.${it.key}" to it.value } + ic.byType.entries.map { "icons.byType.${it.key}" to it.value })
            .forEach { (path, name) -> if (name !in iconNames) err(path, "нет иконки «$name», есть: ${iconNames.sorted().joinToString()}") }
        ic.custom.forEach { (name, def) ->
            if (def.paths.isEmpty() && def.pixels.isEmpty()) err("icons.custom.$name", "нужны paths или pixels")
            def.pixels.forEachIndexed { i, px ->
                val ok = px.size == 5 && px.take(4).all { (it as? JsonPrimitive)?.content?.toFloatOrNull() != null } &&
                    (px[4] as? JsonPrimitive)?.isString == true
                if (!ok) err("icons.custom.$name.pixels[$i]", "нужно [x, y, w, h, \"#цвет\"]") else color("icons.custom.$name.pixels[$i]", (px[4] as JsonPrimitive).content)
            }
        }

        color("agenda.text", spec.agenda.text)
        color("agenda.muted", spec.agenda.muted)
        color("agenda.accent", spec.agenda.accent)

        val a = spec.adapt
        if (listOf(a.baseWidth, a.baseHeight, a.minWidth, a.compactWidth, a.compactHeight, a.mainHeight).any { it <= 0f }) {
            err("adapt", "размеры должны быть больше нуля")
        }
        if (a.maxScale < 1f) err("adapt.maxScale", "не меньше 1")
        if (a.minScale !in 0.1f..1f) err("adapt.minScale", "от 0.1 до 1")
        val known = FORMS + TONES + optionForms(spec.options)
        (spec.variants.keys + rawVariants).filter { it !in known }.toSet()
            .forEach { err("variants", "неизвестный вариант «$it», есть: ${known.joinToString()}") }
        if (spec.tone == null && (spec.variants.keys + rawVariants).any { it in TONES }) err("tone", "варианты tone:… есть, а цвета tone нет")
        return p
    }

    /** Тон фона стиля по цвету [ThemeSpec.tone]: "tone:light", "tone:dark", "tone:vivid" или null. */
    fun tone(spec: ThemeSpec, sources: ColorSources): String? {
        val expr = spec.tone ?: return null
        val c = PaletteResolver(spec, sources).color(expr) ?: return null
        return "tone:" + ColorMath.tone(c)
    }

    /** Цвета палитры по требованию, со ссылками друг на друга. */
    private class PaletteResolver(private val spec: ThemeSpec, private val sources: ColorSources) {
        val resolved = HashMap<String, Int>()
        private val visiting = HashSet<String>()

        fun pal(k: String): Int? {
            resolved[k]?.let { return it }
            val expr = spec.palette[k] ?: return null
            if (!visiting.add(k)) return null
            val c = ColorExpr.resolve(expr, ::pal, sources)
            visiting.remove(k)
            if (c != null) resolved[k] = c
            return c
        }

        fun color(expr: String): Int? = ColorExpr.resolve(expr, ::pal, sources)
    }

    private fun paletteCycle(palette: Map<String, String>): String? {
        val state = HashMap<String, Int>()
        fun visit(k: String, path: List<String>): String? {
            when (state[k]) {
                1 -> return (path + k).joinToString(" → ")
                2 -> return null
            }
            state[k] = 1
            for (ref in ColorExpr.references(palette[k] ?: "")) if (ref in palette) visit(ref, path + k)?.let { return it }
            state[k] = 2
            return null
        }
        return palette.keys.firstNotNullOfOrNull { visit(it, emptyList()) }
    }

    private fun roleCycle(typography: Map<String, TextSpec>): String? {
        for (start in typography.keys) {
            val seen = mutableListOf(start)
            var cur = typography[start]?.base
            while (cur != null) {
                if (cur in seen) return (seen + cur).joinToString(" → ")
                seen += cur
                cur = typography[cur]?.base
            }
        }
        return null
    }

    // ---------- Сборка готовой темы ----------

    /**
     * Готовая тема. [widget] — для рабочего стола (тени и слои с when). [options] — значения настроек стиля
     * (уже с умолчаниями, см. [optionValues]), [photo] — своё фото виджета для слоя photo.
     */
    fun resolve(
        spec: ThemeSpec,
        sources: ColorSources,
        assets: ThemeAssets,
        widget: Boolean,
        options: Map<String, String> = emptyMap(),
        photo: PhotoInput? = null,
    ): Theme {
        val pr0 = PaletteResolver(spec, sources)
        spec.palette.keys.forEach { pr0.pal(it) }
        val palette = Palette(pr0.resolved)
        val textColor = palette["text"]
        fun col(expr: String?, default: Int = textColor): Int = expr?.let { pr0.color(it) } ?: default
        fun active(w: String?) = w == null || (w == "widget") == widget
        fun shadow(s: ShadowSpec?): TextShadow? = s?.takeIf { active(it.whenMode) }?.let { TextShadow(col(it.color), it.blur, it.dx, it.dy) }
        fun boxShadows(list: List<BoxShadowSpec>?): List<Shadow> =
            list.orEmpty().filter { active(it.whenMode) }.map { Shadow(it.dx, it.dy, it.blur, it.spread, col(it.color), it.inset) }
        // Множитель от настройки-числа: значение / значение по умолчанию.
        fun factor(name: String?): Float {
            val o = name?.let { spec.options[it] } ?: return 1f
            val def = o.default?.toFloatOrNull() ?: o.max
            val v = options[name]?.toFloatOrNull() ?: def
            return if (def > 0f) v / def else v / o.max
        }
        fun scaled(c: Int, k: Float) = if (k == 1f) c else ((((c ushr 24) * k).roundToInt().coerceIn(0, 255)) shl 24) or (c and 0xFFFFFF)
        // k — множитель прозрачности каждого цвета (opacityFrom): так затемнение может быть и сильнее, чем в файле.
        fun gradient(g: GradientSpec, k: Float = 1f): Gradient {
            val positions = if (g.stops.isNotEmpty() && g.stops.all { it.at != null }) FloatArray(g.stops.size) { g.stops[it].at!! } else null
            return Gradient(
                radial = g.type == "radial",
                angle = g.angle,
                from = g.from?.toFloatArray(),
                to = g.to?.toFloatArray(),
                center = g.center?.toFloatArray(),
                radius = g.radius ?: if (g.radiusOf == "farthest" || g.radiusOf == "closest") 1f else 0.5f,
                radiusOf = g.radiusOf,
                colors = IntArray(g.stops.size) { scaled(col(g.stops[it].color), k) },
                positions = positions,
                opacity = g.opacity ?: 1f,
                ellipse = g.ellipse,
            )
        }
        fun ink(e: JsonElement?, default: String): Ink = when (e) {
            is JsonObject -> Ink(0, gradient(json.decodeFromJsonElement(GradientSpec.serializer(), e)))
            is JsonPrimitive -> Ink(col(e.content, col(default)))
            else -> Ink(col(default))
        }
        fun alpha(o: Float?) = ((o ?: 1f) * 255f).let { kotlin.math.round(it).toInt() }.coerceIn(0, 255)
        fun dim(v: JsonPrimitive?) = v?.let { Dim.parse(it.content) }
        fun background(bg: BackgroundSpec, extra: Layer? = null): BackgroundStyle = BackgroundStyle(
            radius = bg.radius,
            clip = bg.clip,
            layers = listOfNotNull(extra) + bg.layers.mapNotNull { l ->
                val k = factor(l.opacityFrom)
                when (l.type) {
                    "solid" -> Layer.Solid(scaled(col(l.color), k), l.whenMode)
                    "linear", "radial" -> Layer.Shade(gradient(l.gradient(), k), l.whenMode)
                    "mesh", "wallpaperMesh" -> {
                        val pts = l.points.ifEmpty { if (l.type == "wallpaperMesh") WALLPAPER_MESH else emptyList() }
                        Layer.Mesh(pts.map { MeshPoint(it.x, it.y, it.radius, col(it.color)) }, alpha((l.opacity ?: 1f) * k), l.whenMode)
                    }
                    "image" -> l.src?.let { src -> Layer.Image({ assets.image(src) }, l.fit, alpha((l.opacity ?: 1f) * k), l.whenMode) }
                    "photo" -> {
                        val blur = (l.blur ?: 0f) * if (l.blurFrom != null) {
                            val o = spec.options[l.blurFrom]
                            ((options[l.blurFrom]?.toFloatOrNull() ?: o?.default?.toFloatOrNull() ?: 0f) / (o?.max ?: 100f)).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        Layer.Photo(photo, alpha((l.opacity ?: 1f) * k), blur, l.whenMode)
                    }
                    "rules" -> Layer.Rules(l.offset, l.step, l.width, col(l.color), l.whenMode)
                    "line" -> Layer.Line(l.x, l.y, l.width, col(l.color), l.whenMode)
                    "band" -> Layer.Band(
                        l.x ?: 0f, l.width, l.angle ?: 0f, l.pivot?.getOrNull(0) ?: 0.5f, l.pivot?.getOrNull(1) ?: 0.5f,
                        col(l.color), l.whenMode,
                    )
                    "shape" -> {
                        val b = l.box ?: BoxSpec()
                        val fillLayer = l.fill?.let { f ->
                            val fi = ink(f, "transparent")
                            if (fi.gradient != null) {
                                Layer.Shade(if (f is JsonObject) gradient(json.decodeFromJsonElement(GradientSpec.serializer(), f), k) else fi.gradient, null)
                            } else {
                                Layer.Solid(scaled(fi.color, k), null)
                            }
                        }
                        val inner = l.surface ?: BackgroundSpec()
                        val style = background(inner.copy(corners = l.corners ?: inner.corners), fillLayer)
                        Layer.Shape(Box(dim(b.left), dim(b.top), dim(b.right), dim(b.bottom), dim(b.width), dim(b.height)), style, l.whenMode)
                    }
                    else -> null
                }
            },
            border = bg.border?.let { Border(it.width, ink(it.color, "@border"), it.whenMode) },
            highlights = bg.highlights.map { Highlight(it.type, it.width, it.inset, ink(it.color, "#59FFFFFF"), it.height, it.whenMode) },
            shadow = shadow(bg.shadow),
            corners = bg.corners?.let { Corners.parse(it) },
            shadows = boxShadows(bg.shadows),
            backdropBlur = bg.backdropBlur,
            saturate = bg.saturate,
        )
        val surfaces = spec.surfaces.mapValues { background(it.value) }

        // Роли текста: своя → base → defaults.
        val defaults = spec.typography["defaults"]
        fun textSpec(name: String, depth: Int = 0): TextSpec {
            val own = spec.typography[name] ?: TextSpec()
            val parent = own.base?.takeIf { depth < 8 }?.let { textSpec(it, depth + 1) } ?: defaults ?: TextSpec()
            return TextSpec(
                font = own.font ?: parent.font,
                size = own.size ?: parent.size,
                weight = own.weight ?: parent.weight,
                letterSpacing = own.letterSpacing ?: parent.letterSpacing,
                lineHeight = own.lineHeight ?: parent.lineHeight,
                color = own.color ?: parent.color,
                fill = own.fill ?: parent.fill,
                textShadow = own.textShadow ?: parent.textShadow,
                shadows = own.shadows ?: parent.shadows,
                uppercase = own.uppercase ?: parent.uppercase,
                tabular = own.tabular ?: parent.tabular,
                underline = own.underline ?: parent.underline,
                align = own.align ?: parent.align,
            )
        }
        val roles = spec.typography.keys.filter { it != "defaults" }.associateWith { name ->
            val t = textSpec(name)
            Role(
                typo = Typo(
                    font = t.font ?: FontCache.SYSTEM,
                    size = t.size ?: 12f,
                    weight = t.weight ?: 400,
                    tracking = t.letterSpacing ?: 0f,
                    lineHeight = t.lineHeight ?: 1.25f,
                    tabular = t.tabular ?: false,
                    caps = t.uppercase ?: false,
                    shadow = shadow(t.textShadow),
                    fill = t.fill?.let { gradient(it) },
                    underline = t.underline ?: false,
                    shadows = boxShadows(t.shadows),
                ),
                color = col(t.color),
                align = when (t.align) {
                    "center" -> Align.CENTER
                    "end" -> Align.END
                    else -> Align.START
                },
            )
        }

        val pr = spec.progress
        val height = pr.height ?: when (pr.type) {
            "thick", "striped" -> 8f
            "glossy" -> 10f
            "dots" -> 7f
            "blocks" -> 16f
            else -> 4f
        }
        val fill = ink(pr.fill, "@accent")
        val progress = ProgressStyle(
            type = pr.type,
            height = height,
            radius = pr.radius ?: if (pr.type == "blocks") 0f else height / 2f,
            width = pr.width ?: 5f,
            track = ink(pr.track, "@track"),
            fill = fill,
            current = pr.current?.let { col(it) } ?: fill.color,
            minFill = pr.minFill,
            glow = shadow(pr.glow),
            gloss = pr.gloss,
            round = pr.cap == "round",
            step = pr.step,
            max = pr.max,
            min = pr.min,
            block = pr.block,
            gap = pr.gap,
            inset = pr.inset,
            frame = pr.frame,
            glyph = pr.glyph,
            role = pr.role,
            trackSurface = pr.trackSurface?.let { surfaces[it] },
            fillSurface = pr.fillSurface?.let { surfaces[it] },
            fillInset = pr.fillInset,
            fillClip = pr.fillClip,
        )
        val cn = spec.counter
        val ins = cn.insets
        val counter = CounterStyle(
            type = cn.type,
            fill = ink(cn.fill, "@accent"),
            track = col(cn.track ?: "@track"),
            padding = cn.padding,
            width = cn.width,
            gloss = cn.gloss,
            surface = cn.surface?.let { surfaces[it] },
            size = cn.size,
            insets = when (ins.size) {
                0 -> floatArrayOf(0f, 0f, 0f, 0f)
                1 -> floatArrayOf(ins[0], ins[0], ins[0], ins[0])
                2 -> floatArrayOf(ins[0], ins[1], ins[0], ins[1])
                3 -> floatArrayOf(ins[0], ins[1], ins[2], ins[1])
                else -> floatArrayOf(ins[0], ins[1], ins[2], ins[3])
            },
            unit = cn.unit,
            unitGap = cn.unitGap,
            placement = cn.placement,
            valign = cn.valign,
        )

        val tpls = spec.strings.mapNotNull { (k, v) -> Templates.parse(v, k, mutableListOf())?.let { k to it } }.toMap()
        val icons = IconStyle(col(spec.icons.color), spec.icons.slots, spec.icons.byType, Icons.BUILT_IN + spec.icons.custom)
        val ag = spec.agenda
        val agenda = AgendaStyle(
            marker = ag.marker,
            text = col(ag.text),
            muted = col(ag.muted),
            accent = col(ag.accent),
            pastAlpha = ag.pastAlpha,
            rowHeight = ag.rowHeight,
            rowSize = ag.rowSize,
            headerSize = ag.headerSize,
            twoLine = ag.twoLine,
            maxGrow = ag.maxGrow,
            fontGrow = ag.fontGrow,
        )
        return Theme(
            id = spec.id,
            name = spec.name,
            layout = spec.layout,
            palette = palette,
            background = background(spec.background),
            backdrop = spec.backdrop?.let(::background),
            roles = roles,
            spacing = spec.spacing,
            metrics = spec.metrics,
            progress = progress,
            counter = counter,
            strings = tpls,
            flags = spec.flags,
            icons = icons,
            agenda = agenda,
            surfaces = surfaces,
        )
    }

    private val WALLPAPER_MESH = listOf(
        MeshPointSpec(0.15f, 0.15f, 0.75f, "@wallpaper.primary | #4B5BFF"),
        MeshPointSpec(0.85f, 0.3f, 0.65f, "@wallpaper.secondary | #FF4F9A"),
        MeshPointSpec(0.55f, 1f, 0.7f, "@wallpaper.tertiary | #20C4B5"),
    )

    // ---------- Понятные тексты ошибок ----------

    private fun syntaxMessage(e: Throwable, text: String): String {
        val msg = e.message.orEmpty().lineSequence().first()
        val offset = Regex("offset (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        val where = offset?.let { o ->
            val before = text.take(o.coerceAtMost(text.length))
            " (строка ${before.count { it == '\n' } + 1}, символ ${o - before.lastIndexOf('\n')})"
        }.orEmpty()
        return "ошибка в JSON$where: ${msg.substringAfter(": ", msg)}"
    }

    private fun decodeMessage(e: Throwable): String {
        val msg = e.message.orEmpty().lineSequence().first()
        val path = Regex("path: \\$\\.?(\\S*)").find(e.message.orEmpty())?.groupValues?.get(1)?.trimEnd('.')
        Regex("unknown key '([^']+)'").find(msg)?.let {
            return "неизвестное поле «${it.groupValues[1]}»" + (path?.takeIf { p -> p.isNotEmpty() }?.let { p -> " в $p" } ?: "") +
                " — опечатка или поле из другой версии схемы"
        }
        Regex("Field '([^']+)' is required").find(msg)?.let {
            return "не хватает поля «${it.groupValues[1]}»" + (path?.takeIf { p -> p.isNotEmpty() }?.let { p -> " в $p" } ?: "")
        }
        Regex("Fields? \\[([^\\]]+)\\] (?:is|are) required").find(msg)?.let {
            return "не хватает полей ${it.groupValues[1]}" + (path?.takeIf { p -> p.isNotEmpty() }?.let { p -> " в $p" } ?: "")
        }
        return msg + (path?.takeIf { it.isNotEmpty() && "path" !in msg }?.let { " ($it)" } ?: "")
    }
}
