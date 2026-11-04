package io.github.hungrycube.lessonwidget.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hungrycube.lessonwidget.R

/**
 * Токены оформления приложения (макеты design/mockups/app-*.html, 1 css px = 1 dp).
 * Все цвета, радиусы и шрифты экранов берутся отсюда.
 */
@Immutable
data class AppColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    /** Внутренняя обводка карточек 1 dp и разделители строк. */
    val cardBorder: Color,
    /** Обводка кнопок-контуров. */
    val cardBorderStrong: Color,
    /** Фон круглых кнопок, полей и подложка сегментов. */
    val soft: Color,
    /** Выбранный сегмент. */
    val seg: Color,
    /** Тень под выбранным сегментом (в тёмной теме её нет). */
    val segShadow: Color,
    val text: Color,
    val sec: Color,
    val ter: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Круглая кнопка очистки в поле поиска, выключенный переключатель. */
    val clearBg: Color,
    /** Ручка выключенного переключателя. */
    val switchOff: Color,
    /** Расписание обновлено. */
    val ok: Color,
    /** Обновить не удалось, показано сохранённое. */
    val warn: Color,
    /** Фон под превью виджета на экране настройки стиля. */
    val previewBackdrop: Color,
)

val LightColors = AppColors(
    dark = false,
    bg = Color(0xFFFBFAF8),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFECE9E3),
    cardBorderStrong = Color(0xFFD6D3CC),
    soft = Color(0xFFEFEDE8),
    seg = Color(0xFFFFFFFF),
    segShadow = Color(0x1A000000),
    text = Color(0xFF17171A),
    sec = Color(0xFF6B6964),
    ter = Color(0xFF77746E),
    primary = Color(0xFF17171A),
    onPrimary = Color(0xFFFFFFFF),
    clearBg = Color(0xFFDEDBD4),
    switchOff = Color(0xFFFFFFFF),
    ok = Color(0xFF2FA35A),
    warn = Color(0xFFD9822B),
    previewBackdrop = Color(0xFF2B2E36),
)

val DarkColors = AppColors(
    dark = true,
    bg = Color(0xFF0F0F11),
    card = Color(0xFF18181B),
    cardBorder = Color(0xFF232327),
    cardBorderStrong = Color(0xFF3A3A40),
    soft = Color(0xFF1F1F23),
    seg = Color(0xFF34343A),
    segShadow = Color.Transparent,
    text = Color(0xFFF3F2EE),
    sec = Color(0xFFA3A19C),
    ter = Color(0xFF8A8883),
    primary = Color(0xFFF3F2EE),
    onPrimary = Color(0xFF111113),
    clearBg = Color(0xFF34343A),
    switchOff = Color(0xFFA3A19C),
    ok = Color(0xFF2FA35A),
    warn = Color(0xFFE8963F),
    previewBackdrop = Color(0xFF2B2E36),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Размеры из макетов. */
object Dims {
    /** Боковые отступы экранов. */
    val side: Dp = 22.dp
    val cardRadius: Dp = 22.dp
    val segOuter: Dp = 16.dp
    val segInner: Dp = 12.dp
    /** Круглые кнопки и минимальный размер касаемого элемента. */
    val touch: Dp = 44.dp
    val pill: Dp = 44.dp
    val pillTall: Dp = 46.dp
    val styleCardRadius: Dp = 26.dp
    val previewRadius: Dp = 19.dp
    val previewHeight: Dp = 184.dp
}

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_tight,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter Tight (OFL) — вариативный, жирность задаётся осью wght. */
val InterTight = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Float, weight: Int, spacing: Float = 0f, lineHeight: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = InterTight,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = if (spacing == 0f) TextUnit.Unspecified else spacing.em,
    lineHeight = lineHeight,
    // Цифры одной ширины: время и номера не прыгают.
    fontFeatureSettings = "tnum",
)

/** Типографика: размер/жирность как в макетах. */
object Type {
    /** Заголовок главной «ФМА · ЭМ-63». */
    val display = style(26f, 700, -0.02f, 30.sp)
    /** Заголовок экрана «Настройки». */
    val screenTitle = style(20f, 700, -0.01f)
    /** Заголовок дня «Сегодня». */
    val dayTitle = style(18f, 700, -0.01f)
    /** Начало пары. */
    val time = style(17f, 700, -0.01f)
    /** Код группы в карточке настроек. */
    val groupCode = style(17f, 600)
    /** Аватар-буква. */
    val avatar = style(17f, 700)
    /** Название пары. */
    val lessonTitle = style(16f, 600, -0.01f, 21.sp)
    /** Код группы в списке, заголовок пустого состояния. */
    val rowTitle = style(16f, 600)
    /** Текст в поле поиска. */
    val input = style(16f, 500)
    /** Названия пунктов настроек, стилей. */
    val itemTitle = style(15f, 600)
    val body = style(14f, 400)
    val button = style(14f, 600)
    val segment = style(14f, 500)
    val segmentSelected = style(14f, 600)
    /** «Лекция · ауд. 1-402», длинные пояснения в карточках. */
    val detail = style(13.5f, 400, lineHeight = 19.5.sp)
    /** Подписи под заголовками, преподаватель, конец пары. */
    val caption = style(13f, 400)
    /** Подписи секций «Группа», «Подгруппа». */
    val label = style(13f, 600)
    /** «1 пара», строка статуса. */
    val meta = style(12.5f, 500)
    /** Подсказки под настройками. */
    val hint = style(12.5f, 400, lineHeight = 18.sp)
    val small = style(12f, 400, lineHeight = 17.5.sp)
    /** Бейдж «через 8 ч 26 мин». */
    val badge = style(11.5f, 600)
}
