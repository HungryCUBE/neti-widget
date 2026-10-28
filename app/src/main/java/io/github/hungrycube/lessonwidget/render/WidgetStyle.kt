package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.render.layout.Layouts
import io.github.hungrycube.lessonwidget.render.theme.AdaptSpec
import io.github.hungrycube.lessonwidget.render.theme.ColorSources
import io.github.hungrycube.lessonwidget.render.theme.Problems
import io.github.hungrycube.lessonwidget.render.theme.Theme
import io.github.hungrycube.lessonwidget.render.theme.ThemeAssets
import io.github.hungrycube.lessonwidget.render.theme.ThemeException
import io.github.hungrycube.lessonwidget.render.theme.ThemeLoader
import io.github.hungrycube.lessonwidget.render.theme.ThemeSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Стиль виджета, загруженный из JSON. Хранит склеенные слои файла (умолчания → extends → сам файл),
 * а готовую [Theme] под конкретный размер собирает в [theme].
 */
class WidgetStyle internal constructor(
    val id: String,
    val title: String,
    val order: Int,
    val layout: String,
    val sizes: Set<String>,
    val adapt: AdaptSpec,
    private val chain: List<JsonObject>,
    private val assets: ThemeAssets,
    /** Предупреждения загрузчика (например, нет шрифта — будет системный). */
    val warnings: List<String>,
    /** Откуда стиль: файл в assets или импортированный. */
    val source: String,
) {
    private val specs = ConcurrentHashMap<List<String>, ThemeSpec>()

    fun spec(forms: List<String>): ThemeSpec = specs.getOrPut(forms) { ThemeLoader.decode(ThemeLoader.compose(chain, forms), source) }

    /** Тема для формы [forms] (см. [SizeClass.forms]). [widget] — для рабочего стола. */
    fun theme(forms: List<String>, sources: ColorSources, widget: Boolean): Theme =
        ThemeLoader.resolve(spec(forms), sources, assets, widget)

    override fun toString() = "WidgetStyle($id)"
}

/** Результат импорта своего стиля. */
class ImportResult(val style: WidgetStyle?, val errors: List<String>, val warnings: List<String>)

/**
 * Все стили: JSON-файлы из assets/themes и импортированные из filesDir/themes.
 * Новый стиль — новый файл, галерея и выбор строятся по ним сами.
 */
object Styles {
    const val DEFAULT_ID = "minimal"

    private const val ASSET_DIR = "themes"
    private const val DEFAULTS_ASSET = "engine/defaults.json"
    private const val USER_DIR = "themes"

    /** Стиль на крайний случай, если файлы стилей не читаются. */
    private const val FALLBACK_JSON = """
        {
          "schemaVersion": 1, "id": "fallback", "name": "Запасной", "layout": "clean",
          "palette": {"surface": "#FFFFFF", "text": "#141414", "secondary": "#5F5F5F", "muted": "#8A8A8A",
                      "accent": "#141414", "track": "#E6E6E6", "number": "#A0A0A0", "nextTitle": "#3D3D3D"},
          "background": {"radius": 24, "layers": [{"type": "solid", "color": "@surface"}]},
          "typography": {
            "defaults": {"font": "system"},
            "header": {"size": 12, "color": "@muted"},
            "title": {"size": 26, "weight": 700, "lineHeight": 1.15, "color": "@text"},
            "details": {"size": 13, "color": "@secondary"},
            "next": {"size": 12, "color": "@muted"},
            "counter": {"size": 12, "weight": 700, "lineHeight": 1, "align": "end", "color": "@text"}
          },
          "progress": {"type": "line", "height": 2, "radius": 0}
        }
    """

    class Catalog(val styles: List<WidgetStyle>, val problems: List<String>)

    private val lock = Any()

    @Volatile
    private var catalog: Catalog? = null

    @Volatile
    private var fallbackStyle: WidgetStyle? = null

    fun all(context: Context): List<WidgetStyle> = catalog(context).styles

    /** Стиль по id; если его нет или файл сломан — стиль по умолчанию. */
    fun byId(context: Context, id: String?): WidgetStyle {
        val all = all(context)
        return all.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == DEFAULT_ID } ?: all.firstOrNull() ?: fallback(context)
    }

    /** Ошибки в файлах стилей с последней загрузки. */
    fun problems(context: Context): List<String> = catalog(context).problems

    fun fallback(context: Context): WidgetStyle = fallbackStyle ?: synchronized(lock) {
        fallbackStyle ?: run {
            val raw = ThemeLoader.parse(FALLBACK_JSON, "fallback")
            val chain = listOf(defaults(context), raw)
            val problems = Problems()
            val style = build(context, "fallback", chain, assetsFor(context, null), problems)
                ?: throw ThemeException(problems.errors.joinToString("\n"))
            style.also { fallbackStyle = it }
        }
    }

    /** Перечитать файлы (после импорта). */
    fun reload() {
        catalog = null
        FontCache.clear()
    }

    fun userDir(context: Context): File = File(context.applicationContext.filesDir, USER_DIR)

    /**
     * Импорт своего стиля: тот же загрузчик, что и для встроенных, только файл из другого места.
     * [fonts] — файлы шрифтов «имя.ttf» → содержимое. Стиль с ошибками не сохраняется.
     */
    fun import(context: Context, json: String, fonts: Map<String, ByteArray> = emptyMap()): ImportResult {
        val app = context.applicationContext
        val raw = try {
            ThemeLoader.parse(json, "импорт")
        } catch (e: ThemeException) {
            return ImportResult(null, listOf(e.message.orEmpty()), emptyList())
        }
        val id = (raw["id"] as? JsonPrimitive)?.content.orEmpty()
        if (all(app).any { it.id == id && it.source.startsWith("assets/") }) {
            return ImportResult(null, listOf("id: «$id» занят встроенным стилем, выберите другой"), emptyList())
        }
        val badFont = fonts.keys.firstOrNull { !it.matches(Regex("[a-z0-9_]+\\.(ttf|otf)")) }
        if (badFont != null) return ImportResult(null, listOf("шрифт «$badFont»: имя из строчных латинских букв, цифр и _, .ttf или .otf"), emptyList())
        val dir = userDir(app)
        val fontDir = File(dir, "fonts")
        fontDir.mkdirs()
        for ((name, bytes) in fonts) writeAtomically(File(fontDir, name), bytes)
        FontCache.extraDirs = listOf(fontDir)
        FontCache.clear()
        val problems = Problems()
        val style = load(app, "$id.json (импорт)", raw, rawById(app), assetsFor(app, dir), problems)
        if (style == null) return ImportResult(null, problems.errors, problems.warnings)
        writeAtomically(File(dir, "$id.json"), json.toByteArray(Charsets.UTF_8))
        reload()
        return ImportResult(byId(app, id).takeIf { it.id == id }, emptyList(), problems.warnings)
    }

    // ---------- Загрузка ----------

    private fun catalog(context: Context): Catalog = catalog ?: synchronized(lock) {
        catalog ?: loadCatalog(context.applicationContext).also { catalog = it }
    }

    private class RawFile(val obj: JsonObject, val source: String, val userDir: File?)

    @Volatile
    private var defaultsCache: JsonObject? = null

    private fun defaults(context: Context): JsonObject = defaultsCache ?: run {
        val text = context.assets.open(DEFAULTS_ASSET).bufferedReader().use { it.readText() }
        ThemeLoader.parse(text, DEFAULTS_ASSET).also { defaultsCache = it }
    }

    private fun rawFiles(context: Context, problems: Problems): List<RawFile> {
        val out = ArrayList<RawFile>()
        val names = runCatching { context.assets.list(ASSET_DIR)?.toList() }.getOrNull().orEmpty().filter { it.endsWith(".json") }.sorted()
        for (name in names) {
            try {
                val text = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                out += RawFile(ThemeLoader.parse(text, name), "assets/$ASSET_DIR/$name", null)
            } catch (e: ThemeException) {
                problems.errors += e.message.orEmpty()
            }
        }
        val dir = userDir(context)
        FontCache.extraDirs = listOf(File(dir, "fonts"))
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name }?.forEach { file ->
            try {
                out += RawFile(ThemeLoader.parse(file.readText(), file.name), "files/${file.name}", dir)
            } catch (e: Exception) {
                problems.errors += e.message ?: file.name
            }
        }
        return out
    }

    private fun rawById(context: Context): Map<String, JsonObject> =
        rawFiles(context, Problems()).associate { (it.obj["id"] as? JsonPrimitive)?.content.orEmpty() to it.obj }

    private fun loadCatalog(context: Context): Catalog {
        val problems = Problems()
        val files = rawFiles(context, problems)
        val byId = LinkedHashMap<String, JsonObject>()
        for (f in files) {
            val id = (f.obj["id"] as? JsonPrimitive)?.content.orEmpty()
            if (id in byId) problems.errors += "${f.source}: id «$id» уже есть у другого стиля" else byId[id] = f.obj
        }
        val styles = files.mapNotNull { f ->
            val id = (f.obj["id"] as? JsonPrimitive)?.content.orEmpty()
            if (byId[id] !== f.obj) return@mapNotNull null
            load(context, f.source, f.obj, byId, assetsFor(context, f.userDir), problems)
        }.sortedWith(compareBy<WidgetStyle> { it.order }.thenBy { it.title })
        if (problems.errors.isNotEmpty()) {
            CrashLog.record(context, "Файлы стилей", ThemeException(problems.errors.joinToString("\n")))
        }
        return Catalog(styles, problems.errors + problems.warnings)
    }

    /** Загружает один стиль: склеивает с умолчаниями и extends, проверяет все сочетания вариантов. */
    private fun load(
        context: Context,
        source: String,
        raw: JsonObject,
        byId: Map<String, JsonObject>,
        assets: ThemeAssets,
        problems: Problems,
    ): WidgetStyle? {
        val chain = ArrayList<JsonObject>()
        var cur: JsonObject? = raw
        val seen = HashSet<String>()
        while (cur != null) {
            chain.add(0, cur)
            val parent = (cur["extends"] as? JsonPrimitive)?.content ?: break
            if (!seen.add(parent) || seen.size > 8) {
                problems.errors += "$source: extends — стили наследуют друг друга по кругу"
                return null
            }
            cur = byId[parent] ?: run {
                problems.errors += "$source: extends — нет стиля «$parent»"
                return null
            }
        }
        chain.add(0, defaults(context))
        return build(context, source, chain, assets, problems)
    }

    private fun build(context: Context, source: String, chain: List<JsonObject>, assets: ThemeAssets, problems: Problems): WidgetStyle? {
        val local = Problems()
        val fontExists = { name: String -> FontCache.exists(context, name) }
        val variantForms = ThemeLoader.variantForms(chain)
        val combos = subsets(variantForms.filter { it in ThemeLoader.FORMS })
        var base: ThemeSpec? = null
        for (forms in combos) {
            try {
                val spec = ThemeLoader.decode(ThemeLoader.compose(chain, forms), source)
                if (forms.isEmpty()) base = spec
                val p = ThemeLoader.check(spec, Layouts.ids, fontExists, variantForms)
                val label = if (forms.isEmpty()) "" else " (вариант ${forms.joinToString("+")})"
                p.errors.forEach { local.errors += "$source$label: $it" }
                p.warnings.forEach { local.warnings += "$source$label: $it" }
            } catch (e: ThemeException) {
                local.errors += e.message.orEmpty()
            }
        }
        problems.errors += local.errors.distinct()
        problems.warnings += local.warnings.distinct()
        val spec = base ?: return null
        if (local.errors.isNotEmpty()) return null
        return WidgetStyle(
            id = spec.id,
            title = spec.name,
            order = spec.order,
            layout = spec.layout,
            sizes = spec.sizes.toSet(),
            adapt = spec.adapt,
            chain = chain,
            assets = assets,
            warnings = local.warnings.distinct(),
            source = source,
        )
    }

    private fun subsets(items: List<String>): List<List<String>> =
        items.fold(listOf(emptyList<String>())) { acc, x -> acc + acc.map { it + x } }
            .map { set -> ThemeLoader.FORMS.filter { it in set } }

    private val images = LruCache<String, Bitmap>(8)

    /** Картинки стиля: из assets/themes или из папки импортированных; «@photo» — своё фото. */
    private fun assetsFor(context: Context, userDir: File?): ThemeAssets = ThemeAssets { src ->
        val key = (userDir?.path ?: "assets") + "/" + src
        images.get(key) ?: runCatching {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            when {
                src == ThemeLoader.PHOTO_SRC -> PhotoStore.file(context).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path, opts) }
                userDir != null -> BitmapFactory.decodeFile(File(userDir, src).path, opts)
                else -> context.assets.open("$ASSET_DIR/$src").use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()?.also { images.put(key, it) }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val af = AtomicFile(file)
        val out = af.startWrite()
        try {
            out.write(bytes)
            af.finishWrite(out)
        } catch (e: Exception) {
            af.failWrite(out)
            throw e
        }
    }
}

/** Своё фото для слоя photo и цветов @photo. Выбора в интерфейсе пока нет — файл кладётся сюда. */
object PhotoStore {
    fun file(context: Context): File = File(context.applicationContext.filesDir, "theme_photo.jpg")

    fun bitmap(context: Context): Bitmap? = file(context).takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
}
