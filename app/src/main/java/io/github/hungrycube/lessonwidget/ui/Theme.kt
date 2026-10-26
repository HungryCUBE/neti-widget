package io.github.hungrycube.lessonwidget.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF0C3B33),
    onPrimary = Color.White,
    secondary = Color(0xFF00A861),
    tertiary = Color(0xFFF5A800),
    background = Color(0xFFF7F5F2),
    surface = Color(0xFFF7F5F2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3B8),
    secondary = Color(0xFF5BD69A),
    tertiary = Color(0xFFF5C04D),
    background = Color(0xFF141311),
    surface = Color(0xFF141311),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
