package io.github.hungrycube.lessonwidget.render.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject

/*
 * Схема файла стиля (assets/themes/<id>.json), schemaVersion 1. Полное описание — docs/STYLES.md.
 * Размеры — в dp для стандартного виджета 4×2 (316×168). Как они меняются на других размерах,
 * решает движок (см. Adaptive), стиль об этом не думает.
 * Цвет — строка: "#RRGGBB", "#AARRGGBB", ссылка "@имя" на palette, "@dynamic.accent1_600",
 * "@wallpaper.primary", "@photo.dominant", прозрачность "@text/50%"; варианты через "|" — берётся первый доступный.
 */

@Serializable
data class ThemeSpec(
    val schemaVersion: Int = 1,
    val id: String = "",
    val name: String = "",
    /** id стиля, от которого берутся все значения, не заданные здесь. */
    val extends: String? = null,
    /** Порядок в галерее. */
    val order: Int = 100,
    /** id раскладки (см. Layouts). */
    val layout: String = "",
    /** Поддерживаемые формы: compact (2×2), standard (4×2), large (4×4, со списком пар). */
    val sizes: List<String> = listOf("compact", "standard", "large"),
    val palette: Map<String, String> = emptyMap(),
    val background: BackgroundSpec = BackgroundSpec(),
    /** Фон под виджетом в превью и на скриншотах (на рабочем столе вместо него обои). */
    val backdrop: BackgroundSpec? = null,
    /** Роли текста: header, title, details, next, counter и дополнительные роли раскладки. "defaults" — общее для всех. */
    val typography: Map<String, TextSpec> = emptyMap(),
    val spacing: SpacingSpec = SpacingSpec(),
    /** Числа раскладки (размер кольца, иконки и т. п.), у каждой раскладки свои, см. docs/STYLES.md. */
    val metrics: Map<String, Float> = emptyMap(),
    val progress: ProgressSpec = ProgressSpec(),
    val counter: CounterSpec = CounterSpec(),
    /** Шаблоны текстов: строка "{num} пара", список частей ["{room}", "{type}"] через « · » и т. д. */
    val strings: Map<String, JsonElement> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
    val icons: IconsSpec = IconsSpec(),
    /** Список пар дня на большом виджете. */
    val agenda: AgendaSpec = AgendaSpec(),
    /** Поведение на разных размерах. */
    val adapt: AdaptSpec = AdaptSpec(),
    /**
     * Переопределения: формы "short", "compact", "large", тон "tone:light" / "tone:dark" / "tone:vivid"
     * и значения настроек "имя:значение" (см. options). Внутри — любые поля этого же файла.
     */
    val variants: Map<String, JsonObject> = emptyMap(),
    /** Именованные подложки для элементов раскладки (chip, panel, counter, полосы прогресса…), устроены как background. */
    val surfaces: Map<String, BackgroundSpec> = emptyMap(),
    /**
     * Цвет, по яркости которого выбирается вариант tone:light (светлый фон), tone:dark или tone:vivid (яркий),
     * например "@wallpaper.primary | #F4E6D4".
     */
    val tone: String? = null,
    /** Настройки стиля для пользователя. Экран настройки виджета строится по ним сам. */
    val options: Map<String, OptionSpec> = emptyMap(),
)

/** Настройка стиля. choice и switch выбирают варианты "имя:значение", range — число для opacityFrom и blurFrom. */
@Serializable
data class OptionSpec(
    /** choice — одно из values, range — число от min до max, switch — on или off. */
    val type: String = "choice",
    val label: String = "",
    val hint: String? = null,
    val values: List<String> = emptyList(),
    /** Подписи значений choice. */
    val labels: Map<String, String> = emptyMap(),
    /** choice — значение, range — число, switch — on или off. */
    val default: String? = null,
    val min: Float = 0f,
    val max: Float = 100f,
    /** range: что дописать к числу ("%"). */
    val unit: String = "",
)

/** Тень как box-shadow / text-shadow в CSS: blur — как в CSS, spread — расширение, inset — внутри фигуры. */
@Serializable
data class BoxShadowSpec(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val blur: Float = 0f,
    val spread: Float = 0f,
    val color: String = "#40000000",
    val inset: Boolean = false,
    @SerialName("when") val whenMode: String? = null,
)

/** Место фигуры внутри родителя, как position: absolute в CSS. Число — dp, строка "16%" — доля стороны родителя. */
@Serializable
data class BoxSpec(
    val left: JsonPrimitive? = null,
    val top: JsonPrimitive? = null,
    val right: JsonPrimitive? = null,
    val bottom: JsonPrimitive? = null,
    val width: JsonPrimitive? = null,
    val height: JsonPrimitive? = null,
)

@Serializable
data class SpacingSpec(
    val top: Float = 16f,
    val side: Float = 18f,
    val bottom: Float = 14f,
)

@Serializable
data class TextSpec(
    /** Роль, от которой берутся незаданные поля (кроме defaults). */
    val base: String? = null,
    val font: String? = null,
    val size: Float? = null,
    val weight: Int? = null,
    /** В em, как letter-spacing в CSS. */
    val letterSpacing: Float? = null,
    /** Множитель размера шрифта. 0 — как line-height: normal в CSS (по метрикам шрифта). */
    val lineHeight: Float? = null,
    val color: String? = null,
    /** Градиент вместо цвета (хромовый текст и т. п.). */
    val fill: GradientSpec? = null,
    val textShadow: ShadowSpec? = null,
    /** Тени как text-shadow в CSS (blur — как в CSS), первая — сверху. */
    val shadows: List<BoxShadowSpec>? = null,
    val uppercase: Boolean? = null,
    /** Цифры одинаковой ширины. */
    val tabular: Boolean? = null,
    val underline: Boolean? = null,
    /** start | center | end — для ролей, которые раскладка выравнивает сама (counter). */
    val align: String? = null,
)

@Serializable
data class ShadowSpec(
    val color: String = "#40000000",
    val blur: Float = 2f,
    val dx: Float = 0f,
    val dy: Float = 1f,
    /** widget | preview — только на рабочем столе или только в превью. */
    @SerialName("when") val whenMode: String? = null,
)

@Serializable
data class StopSpec(val color: String, val at: Float? = null)

/** Стопы градиента: [{"color": "#fff", "at": 0}, …] или коротко ["#fff", "#000"]. */
object StopListSerializer : JsonTransformingSerializer<List<StopSpec>>(ListSerializer(StopSpec.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) {
            JsonArray(element.map { if (it is JsonPrimitive) buildJsonObject { put("color", it) } else it })
        } else {
            element
        }
}

@Serializable
data class GradientSpec(
    /** linear | radial */
    val type: String = "linear",
    /** Угол как в CSS linear-gradient: 0 — снизу вверх, 90 — слева направо, 180 — сверху вниз. */
    val angle: Float? = null,
    /** Или точные концы в долях прямоугольника: [x, y]. */
    val from: List<Float>? = null,
    val to: List<Float>? = null,
    /** Для radial: центр в долях и радиус в долях от radiusOf. */
    val center: List<Float>? = null,
    val radius: Float? = null,
    /** diagonal | width | height | min | farthest (до дальнего угла — как в CSS по умолчанию) | closest (до ближней стороны) */
    val radiusOf: String = "diagonal",
    /** radial: эллипс по пропорциям прямоугольника, как radial-gradient(ellipse …) в CSS. */
    val ellipse: Boolean = false,
    @Serializable(with = StopListSerializer::class) val stops: List<StopSpec> = emptyList(),
    /** Общая прозрачность 0..1. */
    val opacity: Float? = null,
)

@Serializable
data class MeshPointSpec(val x: Float, val y: Float, val radius: Float, val color: String)

/**
 * Слой фона. type: solid, linear, radial, mesh, wallpaperMesh, image, photo, rules, line, band, shape.
 * Поля зависят от типа, лишние не мешают.
 */
@Serializable
data class LayerSpec(
    val type: String,
    val color: String? = null,
    val angle: Float? = null,
    val from: List<Float>? = null,
    val to: List<Float>? = null,
    val center: List<Float>? = null,
    val radius: Float? = null,
    val radiusOf: String = "diagonal",
    val ellipse: Boolean = false,
    @Serializable(with = StopListSerializer::class) val stops: List<StopSpec> = emptyList(),
    val opacity: Float? = null,
    /** mesh, wallpaperMesh: цветные пятна. */
    val points: List<MeshPointSpec> = emptyList(),
    /** image: путь к картинке относительно папки стиля. */
    val src: String? = null,
    /** image, photo: cover | contain | stretch */
    val fit: String = "cover",
    /** rules: первая линия, шаг, толщина. */
    val offset: Float = 0f,
    val step: Float = 24f,
    val width: Float = 1f,
    /** line, band: положение. line — в dp, band — в долях ширины. */
    val x: Float? = null,
    val y: Float? = null,
    /** band: точка поворота в долях. */
    val pivot: List<Float>? = null,
    /** shape: фигура внутри карточки (обрезается по ней), как вложенный блок с position: absolute. */
    val box: BoxSpec? = null,
    /** shape: скругления как border-radius в CSS: "24", "50%", "24px 24px 50% 50% / 24px 24px 16px 16px". */
    val corners: String? = null,
    /** shape: заливка — цвет-строка или градиент-объект. */
    val fill: JsonElement? = null,
    /** shape: свои слои, тени, рамка и размытие — как у background (поверх fill). */
    val surface: BackgroundSpec? = null,
    /** photo: размытие как blur() в CSS; с blurFrom — при значении настройки 100. */
    val blur: Float? = null,
    val blurFrom: String? = null,
    /** Прозрачность умножается на значение настройки (range), делённое на её значение по умолчанию. */
    val opacityFrom: String? = null,
    @SerialName("when") val whenMode: String? = null,
) {
    fun gradient() = GradientSpec(type, angle, from, to, center, radius, radiusOf, ellipse, stops, opacity)
}

@Serializable
data class BorderSpec(
    val width: Float = 1f,
    /** Цвет-строка или градиент-объект. */
    val color: JsonElement? = null,
    @SerialName("when") val whenMode: String? = null,
)

/** Внутренние блики: innerStroke — обводка внутри края, gloss — глянцевая полоса сверху. */
@Serializable
data class HighlightSpec(
    val type: String,
    val width: Float = 1f,
    val inset: Float = 1f,
    /** Цвет-строка или градиент-объект. */
    val color: JsonElement? = null,
    /** gloss: доля высоты. */
    val height: Float = 0.5f,
    @SerialName("when") val whenMode: String? = null,
)

@Serializable
data class BackgroundSpec(
    val radius: Float = 0f,
    /** Рисовать слои прямоугольниками внутри скруглённой маски (нужно для узоров вроде линеек). */
    val clip: Boolean = false,
    val layers: List<LayerSpec> = emptyList(),
    val border: BorderSpec? = null,
    val highlights: List<HighlightSpec> = emptyList(),
    /** Тень карточки. На рабочем столе ей нужен adapt.widgetInset. */
    val shadow: ShadowSpec? = null,
    /** Скругления как border-radius в CSS, вместо radius: "50%", "24px 24px 50% 50% / 24px 24px 16px 16px". */
    val corners: String? = null,
    /** Тени как box-shadow в CSS: снаружи и inset. Первая в списке — сверху. Наружным на рабочем столе нужен adapt.widgetInset. */
    val shadows: List<BoxShadowSpec> = emptyList(),
    /**
     * Размыть то, что уже нарисовано под подложкой, как backdrop-filter: blur() saturate() в CSS.
     * Обоев виджет не видит — размывается только нарисованное им самим (своё фото, подложка превью).
     */
    val backdropBlur: Float = 0f,
    val saturate: Float = 1f,
)

@Serializable
data class ProgressSpec(
    /** line | thick | ring | dots | blocks | striped | glossy | text */
    val type: String = "line",
    /** Высота полосы (line, thick, striped, glossy, blocks) или диаметр точки (dots). */
    val height: Float? = null,
    val radius: Float? = null,
    /** Толщина кольца (ring). */
    val width: Float? = null,
    /** Цвет-строка или градиент-объект. */
    val track: JsonElement? = null,
    val fill: JsonElement? = null,
    /** dots: текущая точка; text: процент. */
    val current: String? = null,
    /** Минимальная видимая длина заполнения. 0 — без минимума. */
    val minFill: Float = 0f,
    /** Свечение заполнения. */
    val glow: ShadowSpec? = null,
    /** Сила глянца 0..1 (glossy). */
    val gloss: Float = 0.6f,
    /** Полоса и заполнение — подложки из surfaces (градиенты, тени, блики), вместо track и fill. */
    val trackSurface: String? = null,
    val fillSurface: String? = null,
    /** Отступ заполнения от краёв полосы (рамка внутри полосы). */
    val fillInset: Float = 0f,
    /** Заполнение обрезается по полосе (прямой правый край), как overflow: hidden в CSS. */
    val fillClip: Boolean = false,
    /** butt | round */
    val cap: String = "butt",
    /** dots: шаг и наибольшее число точек; text: наибольшее число символов. */
    val step: Float = 9f,
    val max: Int = 24,
    val min: Int = 4,
    /** blocks: ширина блока, зазор, отступ внутри рамки; striped: ширина полосы. */
    val block: Float = 8f,
    val gap: Float = 2f,
    val inset: Float = 3f,
    /** blocks: рамка "bevel" (вдавленная, как в Win95) или нет. */
    val frame: String? = null,
    /** text: символ блока и роль текста. */
    val glyph: String = "█",
    val role: String = "details",
)

@Serializable
data class CounterSpec(
    /** text | orb | ring | surface. Шрифт и цвет цифр — typography.counter. */
    val type: String = "text",
    /** surface: подложка из surfaces. */
    val surface: String? = null,
    /** surface: размер круга (капли); без него подложка по тексту с полями insets. */
    val size: Float? = null,
    /** surface: поля как padding в CSS — [все], [верх и низ, бока], [верх, бока, низ] или [верх, право, низ, лево]. */
    val insets: List<Float> = emptyList(),
    /** Подпись «мин» (роль unit): none — она внутри counterFormat, inline — справа по базовой линии, below — под числом. */
    val unit: String = "none",
    val unitGap: Float = 3f,
    /**
     * Где отсчёт в раскладке hero: inline — справа от названия по базовой линии, side — справа от названия и деталей,
     * aside — справа от всего блока вместе с шапкой, header — в шапке справа.
     */
    val placement: String = "inline",
    /** side и aside: center или end (по низу). */
    val valign: String = "center",
    /** orb: заливка капли (цвет или градиент), ring: цвет дуги. */
    val fill: JsonElement? = null,
    /** ring: подложка; orb: ободок. */
    val track: String? = null,
    /** Поля вокруг текста. */
    val padding: Float = 6f,
    /** ring: толщина. */
    val width: Float = 3f,
    /** orb: глянцевый блик сверху. */
    val gloss: Boolean = true,
)

@Serializable
data class IconSpec(
    /** Векторные контуры (SVG path) в сетке viewBox×viewBox, рисуются обводкой. */
    val paths: List<String> = emptyList(),
    val viewBox: Float = 24f,
    val stroke: Float = 2f,
    /** Контуры заливаются, а не обводятся. */
    val fill: Boolean = false,
    /** Пиксельная иконка: [x, y, w, h, "#цвет"] в сетке grid×grid. */
    val pixels: List<JsonArray> = emptyList(),
    val grid: Float = 16f,
)

@Serializable
data class IconsSpec(
    val color: String = "#FFFFFF",
    /** Слот → имя иконки: lesson, break, day, window, document… */
    val slots: Map<String, String> = emptyMap(),
    /** Начало сокращённого типа пары → имя иконки ("лаб" → "flask"). */
    val byType: Map<String, String> = emptyMap(),
    /** Свои иконки стиля. */
    val custom: Map<String, IconSpec> = emptyMap(),
)

@Serializable
data class AgendaSpec(
    /** Маркер текущей пары; null — точка. */
    val marker: String? = null,
    val text: String = "@text",
    val muted: String = "@muted",
    val accent: String = "@accent",
    /** Прозрачность прошедших пар. */
    val pastAlpha: Float = 0.45f,
    val rowHeight: Float = 22f,
    /** Наибольшие размеры шрифта строки и заголовка списка до растягивания. */
    val rowSize: Float = 12f,
    val headerSize: Float = 11f,
    /** Разрешить строки в две строчки (время окончания, тип, аудитория), если хватает места. */
    val twoLine: Boolean = true,
    /** Насколько можно растянуть строки, чтобы список занял всё место. */
    val maxGrow: Float = 1.5f,
    /** Какая доля растяжения уходит в размер шрифта. */
    val fontGrow: Float = 0.25f,
)

@Serializable
data class AdaptSpec(
    /** Стандартный размер, под который заданы все размеры стиля. */
    val baseWidth: Float = 316f,
    val baseHeight: Float = 168f,
    /** Самая узкая виртуальная ширина при увеличении: чем меньше, тем крупнее текст на высоких виджетах. */
    val minWidth: Float = 300f,
    /** Уже — компактный вид (2×2), его размер и как сильно можно ужать. */
    val compactBelow: Float = 230f,
    val compactWidth: Float = 150f,
    val compactHeight: Float = 150f,
    /** Ниже — форма short (детали убираются). */
    val shortBelow: Float = 140f,
    /**
     * Насколько карточка может быть выше baseHeight, пока под ней нет списка пар (4×3). Если выше —
     * рисунок увеличивается сильнее, сужаясь по ширине до tallMinWidth, чтобы в середине не было пустоты.
     */
    val stretch: Float = 1.2f,
    val tallMinWidth: Float = 250f,
    /** Выше — форма large: карточка высотой mainHeight и список пар под ней. */
    val largeFrom: Float = 250f,
    val mainHeight: Float = 168f,
    val maxScale: Float = 1.8f,
    val minScale: Float = 0.85f,
    /** Прозрачный отступ от краёв на рабочем столе (Android 12+ скругляет углы виджета). */
    val widgetInset: Float = 0f,
)
