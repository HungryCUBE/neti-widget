package io.github.hungrycube.lessonwidget.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Часы для живого превью: обновляются раз в 30 секунд. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Выбор стиля с живым превью — картинки рисует тот же код, что и виджет.
 * [customFor] — настройки и фото виджета для превью стилей, где они есть; [refresh] — перерисовать превью, когда они изменились.
 */
@Composable
fun StylePicker(
    selected: String,
    display: (Long) -> WidgetDisplay,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    customFor: (WidgetStyle) -> StyleCustom? = { null },
    refresh: Any? = null,
) {
    val context = LocalContext.current
    val now = rememberNow()
    val previews by produceState<Map<String, ImageBitmap>>(emptyMap(), now, refresh) {
        value = withContext(Dispatchers.Default) {
            val d = display(now)
            val renderer = WidgetRenderer(context)
            Styles.all(context).associate { st ->
                st.id to renderer.render(st, d, 316f, 168f, RenderMode.PREVIEW, nowMillis = now, density = 2f, custom = customFor(st)).bitmap.asImageBitmap()
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (st in Styles.all(context)) {
            val isSelected = st.id == selected
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .clickable { onSelect(st.id) }
                    .padding(8.dp),
            ) {
                val bmp = previews[st.id]
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Стиль ${st.title}",
                        modifier = Modifier.fillMaxWidth().aspectRatio(356f / 208f).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(356f / 208f))
                }
                Text(
                    (if (isSelected) "✓ " else "") + st.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }
    }
}
