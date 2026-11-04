package io.github.hungrycube.lessonwidget.ui

import android.app.Activity
import android.content.Context
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.ui.design.DarkColors
import io.github.hungrycube.lessonwidget.ui.design.LightColors
import io.github.hungrycube.lessonwidget.ui.design.LocalAppColors
import io.github.hungrycube.lessonwidget.ui.design.Type

/** Выбранная тема («auto», «light», «dark»): общая для всех экранов, смена сразу перекрашивает приложение. */
object ThemeChoice {
    private var loaded = false
    var mode by mutableStateOf("auto")

    fun load(context: Context) {
        if (!loaded) {
            mode = Settings(context).theme
            loaded = true
        }
    }

    fun set(context: Context, value: String) {
        Settings(context).theme = value
        mode = value
        loaded = true
    }
}

/**
 * Тема приложения: светлая, тёмная или как в системе — по выбору в настройках. Цвета — токены из ui/design/Tokens.kt.
 * [dark] задают тесты, чтобы снять обе темы.
 */
@Composable
fun AppTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    ThemeChoice.load(context)
    val system = isSystemInDarkTheme()
    val isDark = dark ?: when (ThemeChoice.mode) {
        "light" -> false
        "dark" -> true
        else -> system
    }
    // Значки строки состояния и навигации — под фон экрана, а не под тему системы.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    val c = if (isDark) DarkColors else LightColors
    // Material-компоненты (ползунок, индикатор загрузки) берут цвета из тех же токенов.
    val base = if (isDark) darkColorScheme() else lightColorScheme()
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
