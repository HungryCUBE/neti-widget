package io.github.hungrycube.lessonwidget.render.theme

import android.graphics.Bitmap
import io.github.hungrycube.lessonwidget.render.Align
import io.github.hungrycube.lessonwidget.render.Corners
import io.github.hungrycube.lessonwidget.render.Dim
import io.github.hungrycube.lessonwidget.render.Gradient
import io.github.hungrycube.lessonwidget.render.Ink
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.Shadow
import io.github.hungrycube.lessonwidget.render.TextShadow
import io.github.hungrycube.lessonwidget.render.Typo

/*
 * Готовый к отрисовке стиль: все ссылки на цвета разрешены, варианты под размер, тон и настройки применены.
 * Собирается из ThemeSpec в ThemeLoader.resolve на каждую отрисовку (это дёшево).
 */

/** Роль текста: шрифт, цвет и выравнивание. */
class Role(val typo: Typo, val color: Int, val align: Align)

class Palette(private val colors: Map<String, Int>) {
    /** Цвет по имени; если его нет — цвет текста. */
    operator fun get(key: String): Int = colors[key] ?: colors["text"] ?: 0xFF000000.toInt()
    fun getOrNull(key: String): Int? = colors[key]
}

/** Картинки стиля: из assets/themes или из папки импортированных стилей. */
fun interface ThemeAssets {
    fun image(src: String): Bitmap?
}

sealed interface Layer {
    val whenMode: String?

    class Solid(val color: Int, override val whenMode: String?) : Layer
    class Shade(val gradient: Gradient, override val whenMode: String?) : Layer
    class Mesh(val points: List<MeshPoint>, val alpha: Int, override val whenMode: String?) : Layer
    class Image(val bitmap: () -> Bitmap?, val fit: String, val alpha: Int, override val whenMode: String?) : Layer

    /** Своё фото виджета: кадр по точке фокуса, отражение и размытие [blur] (как в CSS). Без фото слой пропускается. */
    class Photo(val photo: PhotoInput?, val alpha: Int, val blur: Float, override val whenMode: String?) : Layer
    class Rules(val offset: Float, val step: Float, val width: Float, val color: Int, override val whenMode: String?) : Layer
    class Line(val x: Float?, val y: Float?, val width: Float, val color: Int, override val whenMode: String?) : Layer
    class Band(val x: Float, val width: Float, val angle: Float, val pivotX: Float, val pivotY: Float, val color: Int, override val whenMode: String?) : Layer

    /** Фигура внутри карточки на месте [box], со своей подложкой [style] (заливка, тени, размытие, рамка). */
    class Shape(val box: Box, val style: BackgroundStyle, override val whenMode: String?) : Layer
}

/** Место фигуры: каждое поле — dp или доля стороны родителя; null — не задано. */
class Box(
    val left: Dim?,
    val top: Dim?,
    val right: Dim?,
    val bottom: Dim?,
    val width: Dim?,
    val height: Dim?,
) {
    /** Прямоугольник внутри родителя [l]..[b], как position: absolute. */
    fun place(l: Float, t: Float, r: Float, b: Float): FloatArray {
        val w = r - l
        val h = b - t
        fun axis(start: Dim?, end: Dim?, size: Dim?, from: Float, len: Float): Pair<Float, Float> {
            val s = start?.of(len)
            val e = end?.of(len)
            val z = size?.of(len)
            return when {
                s != null && e != null -> from + s to from + len - e
                s != null && z != null -> from + s to from + s + z
                e != null && z != null -> from + len - e - z to from + len - e
                z != null -> from to from + z
                s != null -> from + s to from + len
                e != null -> from to from + len - e
                else -> from to from + len
            }
        }
        val (x0, x1) = axis(left, right, width, l, w)
        val (y0, y1) = axis(top, bottom, height, t, h)
        return floatArrayOf(x0, y0, x1, y1)
    }
}

class MeshPoint(val x: Float, val y: Float, val radius: Float, val color: Int)

class Border(val width: Float, val ink: Ink, val whenMode: String?)

class Highlight(val type: String, val width: Float, val inset: Float, val ink: Ink, val height: Float, val whenMode: String?)

class BackgroundStyle(
    val radius: Float,
    val clip: Boolean,
    val layers: List<Layer>,
    val border: Border?,
    val highlights: List<Highlight>,
    val shadow: TextShadow?,
    /** Скругления как border-radius в CSS (вместо [radius]). */
    val corners: Corners? = null,
    /** Тени box-shadow, уже отобранные для режима (виджет/превью). */
    val shadows: List<Shadow> = emptyList(),
    val backdropBlur: Float = 0f,
    val saturate: Float = 1f,
) {
    /** Есть ли что-то кроме прежних возможностей: тогда фон рисуется по фигуре с CSS-скруглениями. */
    val extended: Boolean
        get() = corners != null || shadows.isNotEmpty() || backdropBlur > 0f || saturate != 1f ||
            layers.any { it is Layer.Shape || it is Layer.Photo }
}

class ProgressStyle(
    val type: String,
    val height: Float,
    val radius: Float,
    val width: Float,
    val track: Ink,
    val fill: Ink,
    val current: Int,
    val minFill: Float,
    val glow: TextShadow?,
    val gloss: Float,
    val round: Boolean,
    val step: Float,
    val max: Int,
    val min: Int,
    val block: Float,
    val gap: Float,
    val inset: Float,
    val frame: String?,
    val glyph: String,
    val role: String,
    val trackSurface: BackgroundStyle? = null,
    val fillSurface: BackgroundStyle? = null,
    val fillInset: Float = 0f,
    val fillClip: Boolean = false,
)

class CounterStyle(
    val type: String,
    val fill: Ink,
    val track: Int,
    val padding: Float,
    val width: Float,
    val gloss: Boolean,
    val surface: BackgroundStyle? = null,
    val size: Float? = null,
    /** Поля вокруг текста: верх, право, низ, лево. */
    val insets: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    val unit: String = "none",
    val unitGap: Float = 3f,
    val placement: String = "inline",
    val valign: String = "center",
)

class IconStyle(val color: Int, val slots: Map<String, String>, val byType: Map<String, String>, val defs: Map<String, IconSpec>)

class AgendaStyle(
    val marker: String?,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val pastAlpha: Float,
    val rowHeight: Float,
    val rowSize: Float,
    val headerSize: Float,
    val twoLine: Boolean,
    val maxGrow: Float,
    val fontGrow: Float,
)

class Theme internal constructor(
    val id: String,
    val name: String,
    val layout: String,
    val palette: Palette,
    val background: BackgroundStyle,
    val backdrop: BackgroundStyle?,
    private val roles: Map<String, Role>,
    val spacing: SpacingSpec,
    private val metrics: Map<String, Float>,
    val progress: ProgressStyle,
    val counter: CounterStyle,
    val strings: Map<String, Tpl>,
    private val flags: Map<String, Boolean>,
    val icons: IconStyle,
    val agenda: AgendaStyle,
    private val surfaces: Map<String, BackgroundStyle> = emptyMap(),
) {
    /** Роль текста; если в стиле её нет — роль [fallback]. */
    fun role(name: String, fallback: String = "next"): Role =
        roles[name] ?: roles[fallback] ?: roles.getValue("details")

    fun hasRole(name: String) = name in roles

    fun metric(name: String, default: Float): Float = metrics[name] ?: default

    fun flag(name: String): Boolean = flags[name] == true

    fun list(key: String): List<String> = Templates.list(strings[key])

    /** Подложка из surfaces или null. */
    fun surface(name: String): BackgroundStyle? = surfaces[name]
}
