package io.github.hungrycube.lessonwidget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.hungrycube.lessonwidget.ui.design.DarkColors
import io.github.hungrycube.lessonwidget.ui.design.LightColors
import io.github.hungrycube.lessonwidget.ui.design.LocalAppColors
import io.github.hungrycube.lessonwidget.ui.design.Type

/** Тема приложения: светлая или тёмная, как в системе. Цвета — токены из ui/design/Tokens.kt. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    // Material-компоненты (ползунок, индикатор загрузки) берут цвета из тех же токенов.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        background = c.bg,
        onBackground = c.text,
        surface = c.bg,
        onSurface = c.text,
        surfaceVariant = c.soft,
        onSurfaceVariant = c.sec,
        outline = c.cardBorderStrong,
        outlineVariant = c.cardBorder,
        secondaryContainer = c.soft,
        onSecondaryContainer = c.text,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalAppColors provides c,
            LocalContentColor provides c.text,
            LocalTextStyle provides Type.body,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = c.primary, backgroundColor = c.primary.copy(alpha = 0.25f)),
            content = content,
        )
    }
}
