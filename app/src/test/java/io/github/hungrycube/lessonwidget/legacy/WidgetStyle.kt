package io.github.hungrycube.lessonwidget.legacy

import android.content.Context
import android.os.Build

/** Отступы карточки в dp: сверху, по бокам, снизу. */
data class Insets(val top: Float, val side: Float, val bottom: Float)

/**
 * Цвета стиля (ARGB). Общие роли:
 * [text] — основной текст, [secondary] — детали, [muted] — подписи, [accent] — прогресс и акценты,
 * [track] — подложка прогресса. [container], [onContainer], [tag1]–[tag3] — дополнительные роли,
 * каждая разметка использует их по-своему (см. комментарии в [Styles]).
 */
data class Palette(
    val surface: Int,
    val border: Int? = null,
    val text: Int,
    val secondary: Int,
    val muted: Int,
    val accent: Int,
    val track: Int,
    val container: Int = surface,
    val onContainer: Int = text,
    val tag1: Int = accent,
    val tag2: Int = accent,
    val tag3: Int = accent,
)

/** Фон под карточкой в превью (на рабочем столе вместо него — обои). */
data class Backdrop(val base: Int, val blobs: List<Blob> = emptyList(), val stripes: Int? = null)

/** Цветное пятно фона: центр и радиус в долях размера превью. */
data class Blob(val x: Float, val y: Float, val radius: Float, val color: Int)

/** Расположение элементов карточки. Новый стиль может переиспользовать любую разметку со своими цветами и шрифтами. */
enum class CardLayout {
    /** Плашка с номером пары, крупное название, светящаяся полоса. */
    GLASS,
    /** Время пары и номер, крупное название, тонкая линия прогресса. */
    CLEAN,
    /** Кольцо обратного отсчёта слева, название справа. */
    RING,
    /** Строки консоли с текстовым прогресс-баром. */
    TERMINAL,
    /** Тональный контейнер Material 3 с иконкой и пилюлей следующей пары. */
    TONAL,
    /** Лист в линейку с полем, без прогресса. */
    PAPER,
    /** Крупные точечные цифры и ряд из 24 точек. */
    DOTS,
    /** Окно Windows 95. */
    RETRO,
    /** Цветные теги, жёлтая плашка недели. */
    TAGS,
}

/**
 * Тема виджета. Чтобы добавить стиль, достаточно описать ещё один объект в [Styles]
 * (с одной из существующих разметок [CardLayout]).
 */
data class WidgetStyle(
    val id: String,
    val title: String,
    val layout: CardLayout,
    val radius: Float,
    val padding: Insets = Insets(16f, 18f, 14f),
    val palette: Palette,
    /** Крупное название пары. */
    val heading: Typo,
    /** Шапка: плашки, статус, время. */
    val label: Typo,
    /** Строка деталей: аудитория, тип. */
    val body: Typo,
    /** Нижняя строка со следующей парой и список пар дня. */
    val meta: Typo,
    /** Шрифт цифр обратного отсчёта или null, если стиль его не показывает. */
    val countdown: CountdownLayout?,
    /** Маркер текущей пары в списке дня (большой виджет). */
    val listMarker: String? = null,
    val backdrop: Backdrop,
    /** Брать цвета из обоев (Material You, Android 12+). */
    val dynamicColor: Boolean = false,
) {
    /** Стиль с учётом динамических цветов телефона. */
    fun resolved(context: Context): WidgetStyle {
        if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
        fun c(id: Int) = context.getColor(id)
        return copy(
            palette = palette.copy(
                surface = c(android.R.color.system_neutral1_10),
                container = c(android.R.color.system_accent1_100),
                onContainer = c(android.R.color.system_accent1_900),
                accent = c(android.R.color.system_accent1_600),
                secondary = c(android.R.color.system_accent1_700),
                track = c(android.R.color.system_accent1_200),
                tag1 = c(android.R.color.system_neutral2_50),
                text = c(android.R.color.system_neutral1_900),
                muted = c(android.R.color.system_neutral2_700),
            ),
        )
    }
}

private fun argb(hex: Long): Int = hex.toInt()

/** Все 9 стилей. Цвета, размеры и шрифты — из design/widget-styles-reference.html. */
object Styles {

    val GLASS = WidgetStyle(
        id = "glass",
        title = "Стекло",
        layout = CardLayout.GLASS,
        radius = 30f,
        palette = Palette(
            surface = argb(0x21FFFFFF),
            border = argb(0x52FFFFFF),
            text = argb(0xFFFFFFFF),
            secondary = argb(0xCCFFFFFF),
            muted = argb(0xE0FFFFFF),
            accent = argb(0xFFFFFFFF),
            track = argb(0x33FFFFFF),
        ),
        heading = Typo(AppFont.MANROPE, 26f, 800, tracking = -0.02f, lineHeight = 1.15f),
        label = Typo(AppFont.MANROPE, 11f, 700),
        body = Typo(AppFont.MANROPE, 13f, 500),
        meta = Typo(AppFont.MANROPE, 12f, 600),
        countdown = CountdownLayout.MANROPE_END,
        backdrop = Backdrop(
            base = argb(0xFF1B1450),
            blobs = listOf(
                Blob(0.18f, 0.22f, 0.42f, argb(0xFFFF8A5C)),
                Blob(0.82f, 0.78f, 0.50f, argb(0xFF4B5BFF)),
                Blob(0.74f, 0.12f, 0.38f, argb(0xFFFF4F9A)),
                Blob(0.30f, 0.90f, 0.40f, argb(0xFF20C4B5)),
            ),
        ),
    )

    /** container — цвет номера пары, onContainer — название следующей пары. */
    val MINIMAL = WidgetStyle(
        id = "minimal",
        title = "Минимализм",
        layout = CardLayout.CLEAN,
        radius = 26f,
        palette = Palette(
            surface = argb(0xFFFBFAF7),
            text = argb(0xFF141414),
            secondary = argb(0xFF6D6962),
            muted = argb(0xFF8C887F),
            accent = argb(0xFF141414),
            track = argb(0xFFE9E5DC),
            container = argb(0xFFB4AFA5),
            onContainer = argb(0xFF3D3B37),
        ),
        heading = Typo(AppFont.INTER_TIGHT, 30f, 600, tracking = -0.035f, lineHeight = 1.15f, tabular = true),
        label = Typo(AppFont.INTER_TIGHT, 12f, 500, tabular = true),
        body = Typo(AppFont.INTER_TIGHT, 13f, 400, tabular = true),
        meta = Typo(AppFont.INTER_TIGHT, 12f, 400, tabular = true),
        countdown = CountdownLayout.INTER_TIGHT_END,
        backdrop = Backdrop(argb(0xFFE4E0D7)),
    )

    /** container — разделитель над следующей парой. */
    val OLED = WidgetStyle(
        id = "oled",
        title = "Тёмный OLED",
        layout = CardLayout.RING,
        radius = 30f,
        palette = Palette(
            surface = argb(0xFF000000),
            border = argb(0xFF1F1F22),
            text = argb(0xFFF2F2F2),
            secondary = argb(0xFFA1A1A6),
            muted = argb(0xFF8E8E93),
            accent = argb(0xFF8FB0FF),
            track = argb(0xFF1E1E22),
            container = argb(0xFF1A1A1D),
        ),
        heading = Typo(AppFont.INTER_TIGHT, 20f, 600, tracking = -0.02f, lineHeight = 1.15f, tabular = true),
        label = Typo(AppFont.INTER_TIGHT, 11f, 500, tabular = true),
        body = Typo(AppFont.INTER_TIGHT, 12f, 400, tabular = true),
        meta = Typo(AppFont.INTER_TIGHT, 12f, 400, tabular = true),
        countdown = CountdownLayout.INTER_TIGHT_CENTER,
        backdrop = Backdrop(argb(0xFF060607), listOf(Blob(0.7f, 0.2f, 0.55f, argb(0xFF1D2236)))),
    )

    /** text — яркий зелёный, secondary — обычный, muted — тусклый, track — пустые блоки. */
    val TERMINAL = WidgetStyle(
        id = "terminal",
        title = "Терминал",
        layout = CardLayout.TERMINAL,
        radius = 16f,
        padding = Insets(16f, 18f, 16f),
        palette = Palette(
            surface = argb(0xFF0A0D0B),
            border = argb(0xFF1F2B23),
            text = argb(0xFFCFF8DA),
            secondary = argb(0xFF8FD9A4),
            muted = argb(0xFF4E6B57),
            accent = argb(0xFFCFF8DA),
            track = argb(0xFF2B3D31),
        ),
        heading = Typo(AppFont.JETBRAINS_MONO, 15f, 700, lineHeight = 1.6f),
        label = Typo(AppFont.JETBRAINS_MONO, 12f, 400, lineHeight = 1.6f),
        body = Typo(AppFont.JETBRAINS_MONO, 12f, 400, lineHeight = 1.6f),
        meta = Typo(AppFont.JETBRAINS_MONO, 12f, 400, lineHeight = 1.6f),
        countdown = null,
        listMarker = "▸",
        backdrop = Backdrop(argb(0xFF121714)),
    )

    /**
     * container / onContainer — тональный контейнер, accent — иконка и прогресс,
     * secondary — подписи в контейнере, tag1 — пилюля следующей пары, text / muted — текст в пилюле.
     */
    val MATERIAL = WidgetStyle(
        id = "material",
        title = "Material You",
        layout = CardLayout.TONAL,
        radius = 32f,
        padding = Insets(9f, 9f, 9f),
        palette = Palette(
            surface = argb(0xFFFDF8FF),
            text = argb(0xFF1D1B20),
            secondary = argb(0xFF4F378B),
            muted = argb(0xFF49454F),
            accent = argb(0xFF6750A4),
            track = argb(0xFFCFBCFF),
            container = argb(0xFFE9DDFF),
            onContainer = argb(0xFF21005D),
            tag1 = argb(0xFFF1EBF7),
        ),
        heading = Typo(AppFont.ROBOTO_FLEX, 20f, 700, tracking = -0.01f, lineHeight = 1.15f),
        label = Typo(AppFont.ROBOTO_FLEX, 11f, 600),
        body = Typo(AppFont.ROBOTO_FLEX, 12f, 500),
        meta = Typo(AppFont.ROBOTO_FLEX, 13f, 600),
        countdown = CountdownLayout.ROBOTO_FLEX_END,
        backdrop = Backdrop(
            base = argb(0xFFE7E0F2),
            blobs = listOf(
                Blob(0.15f, 0.85f, 0.45f, argb(0xFFC9B8F0)),
                Blob(0.90f, 0.10f, 0.40f, argb(0xFFF3C9DD)),
            ),
        ),
        dynamicColor = true,
    )

    /** accent — красное поле, track — линейки, container — текст нижней строки. */
    val NOTEBOOK = WidgetStyle(
        id = "notebook",
        title = "Тетрадь",
        layout = CardLayout.PAPER,
        radius = 14f,
        padding = Insets(16f, 18f, 14f),
        palette = Palette(
            surface = argb(0xFFF8F2E6),
            border = argb(0xFFE3D5BB),
            text = argb(0xFF2F261C),
            secondary = argb(0xFF6D5A43),
            muted = argb(0xFF8B6E48),
            accent = argb(0xFFE2A196),
            track = argb(0xFFE8DCC6),
            container = argb(0xFF5B4A36),
        ),
        heading = Typo(AppFont.LORA, 30f, 500, tracking = -0.01f, lineHeight = 1.1f),
        label = Typo(AppFont.INTER_TIGHT, 11f, 600, tracking = 0.08f, caps = true),
        body = Typo(AppFont.LORA_ITALIC, 14f, 400),
        meta = Typo(AppFont.INTER_TIGHT, 13f, 400),
        countdown = null,
        backdrop = Backdrop(argb(0xFFD7CAB2)),
    )

    /** accent — красный, track — непройденные точки. */
    val NOTHING = WidgetStyle(
        id = "nothing",
        title = "Nothing",
        layout = CardLayout.DOTS,
        radius = 28f,
        palette = Palette(
            surface = argb(0xFFF6F6F4),
            text = argb(0xFF111111),
            secondary = argb(0xFF111111),
            muted = argb(0xFF7A7A76),
            accent = argb(0xFFD71921),
            track = argb(0xFFD6D6D2),
        ),
        heading = Typo(AppFont.JETBRAINS_MONO, 15f, 700, tracking = 0.03f, lineHeight = 1.05f, caps = true),
        label = Typo(AppFont.JETBRAINS_MONO, 10f, 700, tracking = 0.12f, caps = true),
        body = Typo(AppFont.JETBRAINS_MONO, 10f, 400, tracking = 0.1f, caps = true),
        meta = Typo(AppFont.JETBRAINS_MONO, 10f, 400, tracking = 0.1f, caps = true),
        countdown = CountdownLayout.DOTO_START,
        backdrop = Backdrop(argb(0xFFD4D4D1)),
    )

    /** accent — синий заголовок и прогресс, container — правый край градиента заголовка. */
    val WIN95 = WidgetStyle(
        id = "win95",
        title = "Win95",
        layout = CardLayout.RETRO,
        radius = 0f,
        padding = Insets(3f, 3f, 3f),
        palette = Palette(
            surface = argb(0xFFC0C0C0),
            text = argb(0xFF000000),
            secondary = argb(0xFF000000),
            muted = argb(0xFF808080),
            accent = argb(0xFF000080),
            track = argb(0xFFFFFFFF),
            container = argb(0xFF1084D0),
        ),
        heading = Typo(AppFont.SYSTEM, 13f, 700),
        label = Typo(AppFont.SYSTEM, 11f, 700),
        body = Typo(AppFont.SYSTEM, 11f, 400),
        meta = Typo(AppFont.SYSTEM, 11f, 400),
        countdown = null,
        backdrop = Backdrop(argb(0xFF008080)),
    )

    /** container — жёлтая плашка недели, tag1–tag3 — теги типа, аудитории и времени, accent — прогресс. */
    val NETI = WidgetStyle(
        id = "neti",
        title = "НЭТИ",
        layout = CardLayout.TAGS,
        radius = 18f,
        palette = Palette(
            surface = argb(0xFFFFFFFF),
            text = argb(0xFF111111),
            secondary = argb(0xFF111111),
            muted = argb(0xFF6B6B6B),
            accent = argb(0xFFD0124F),
            track = argb(0xFFECECEC),
            container = argb(0xFFF5A800),
            tag1 = argb(0xFF00A861),
            tag2 = argb(0xFF0C3B33),
            tag3 = argb(0xFF1E88E5),
        ),
        heading = Typo(AppFont.MONTSERRAT, 26f, 900, tracking = -0.02f, lineHeight = 1.05f),
        label = Typo(AppFont.MONTSERRAT, 10f, 800),
        body = Typo(AppFont.MONTSERRAT, 9f, 800, caps = true),
        meta = Typo(AppFont.MONTSERRAT, 10f, 800, tracking = 0.04f, caps = true),
        countdown = CountdownLayout.MONTSERRAT_END,
        backdrop = Backdrop(argb(0xFFEBE7E2), stripes = argb(0xFFD0124F)),
    )

    val all: List<WidgetStyle> = listOf(GLASS, MINIMAL, OLED, TERMINAL, MATERIAL, NOTEBOOK, NOTHING, WIN95, NETI)

    const val DEFAULT_ID = "minimal"

    fun byId(id: String?): WidgetStyle = all.firstOrNull { it.id == id } ?: MINIMAL
}
