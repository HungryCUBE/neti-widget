package io.github.hungrycube.lessonwidget.ui.design

import android.graphics.Paint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Цвета текущей темы (светлая или тёмная, как в системе). */
val appColors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** Тень как у CSS box-shadow: смещение [dy] и размытие [blur] под скруглённым прямоугольником. */
fun Modifier.softShadow(color: Color, radius: Dp, fill: Color, blur: Dp = 3.dp, dy: Dp = 1.dp): Modifier =
    if (color.alpha == 0f) this else drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = fill.toArgb()
                setShadowLayer(blur.toPx(), 0f, dy.toPx(), color.toArgb())
            }
            val r = radius.toPx()
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }

/** Карточка: фон card, радиус 22, внутренняя обводка 1 dp. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 18.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
    radius: Dp = Dims.cardRadius,
    border: Color = appColors.cardBorder,
    borderWidth: Dp = 1.dp,
    spacing: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = appColors
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(borderWidth, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Круглая кнопка 44 dp с иконкой на фоне soft. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Dims.touch,
    iconSize: Dp = 19.dp,
    iconModifier: Modifier = Modifier,
) {
    val c = appColors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.soft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = iconModifier.size(iconSize))
    }
}

enum class PillKind { Primary, Soft, Outline }

/** Кнопка-пилюля высотой 44–46 dp. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.Soft,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = Dims.pill,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = appColors
    val shape = RoundedCornerShape(height / 2)
    val (bg, fg) = when (kind) {
        PillKind.Primary -> c.primary to c.onPrimary
        PillKind.Soft -> c.soft to c.text
        PillKind.Outline -> Color.Transparent to c.text
    }
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (kind == PillKind.Outline) Modifier.border(1.5.dp, c.cardBorderStrong, shape) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(text, style = Type.button, color = fg, maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
    }
}

/** Сегменты «Сегодня / Неделя», «Все / 1 / 2». */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    textSize: TextUnit = 14.sp,
) {
    val c = appColors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.segOuter))
            .background(c.soft)
            .padding(4.dp),
    ) {
        for (o in options) {
            val isSelected = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .then(if (isSelected) Modifier.softShadow(c.segShadow, Dims.segInner, c.seg) else Modifier)
                    .clip(RoundedCornerShape(Dims.segInner))
                    .background(if (isSelected) c.seg else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(o) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    style = (if (isSelected) Type.segmentSelected else Type.segment).copy(fontSize = textSize),
                    color = if (isSelected) c.text else c.sec,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Переключатель 52×32: трек primary, ручка 24 onPrimary. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val c = appColors
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) c.primary else c.clearBg)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.switchOff),
        )
    }
}

/** Отметка выбора: круг 24 primary с галочкой или пустое кольцо 22. */
@Composable
fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    val c = appColors
    Box(Modifier.size(24.dp).then(modifier), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(Modifier.size(22.dp).border(2.dp, c.ter, CircleShape))
        }
    }
}

/** Подпись секции «Группа», «Подгруппа»: 13/600 sec. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.label, color = appColors.sec, modifier = modifier)
}

/** Подсказка под настройкой: 12.5 ter. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = Type.hint, color = appColors.ter, modifier = modifier, textAlign = textAlign)
}

/** Секция настроек: подпись и содержимое с промежутком 8. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        content()
    }
}

/** Бейдж-пилюля: «через 8 ч 26 мин», «прошла». */
@Composable
fun Badge(text: String, primary: Boolean) {
    val c = appColors
    Text(
        text,
        style = Type.badge,
        color = if (primary) c.onPrimary else c.sec,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) c.primary else c.soft)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Шапка экрана: «Назад» и заголовок 20/700; при прокрутке под ней линия 1 dp. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    backIcon: ImageVector = LucideIcons.back,
    backDescription: String = "Назад",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = appColors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .drawBehind {
                if (divider) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(c.cardBorder, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleButton(backIcon, backDescription, onBack, iconSize = 20.dp)
        } else {
            Box(Modifier.width(6.dp))
        }
        Text(title, style = Type.screenTitle, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

/** Точка статуса 6 dp. */
@Composable
fun StatusDot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

/**
 * Текст, который сначала переносится на вторую строку, а если и так не влезает — уменьшается.
 * Для заголовка группы: длинные коды не обрезаются до нечитаемости.
 */
@Composable
fun AutoShrinkText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, minScale: Float = 0.7f, maxLines: Int = 2) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    Text(
        text,
        style = style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
        ),
        color = color,
        maxLines = maxLines,
        modifier = modifier,
        onTextLayout = { r ->
            if ((r.hasVisualOverflow || r.didOverflowWidth) && scale > minScale) scale = (scale - 0.06f).coerceAtLeast(minScale)
        },
    )
}

/** Пустое состояние: иконка в круге, заголовок, пояснение и, если нужно, кнопка. */
@Composable
fun EmptyState(
    icon: ImageVector?,
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    val c = appColors
    AppCard(modifier, padding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.soft), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(color = c.text, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).heightIn(min = 44.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                Text(title, style = Type.rowTitle, color = c.text)
                if (text != null) Text(text, style = Type.detail, color = c.sec)
            }
        }
        if (action != null && onAction != null) {
            PillButton(action, onAction, kind = PillKind.Primary, height = Dims.pillTall, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}
