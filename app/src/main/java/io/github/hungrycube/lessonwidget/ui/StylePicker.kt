package io.github.hungrycube.lessonwidget.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.WidgetStyle
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.SelectMark
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Текущее время для экранов. В тестах подменяется, чтобы картинки не зависели от дня запуска. */
object AppClock {
    @Volatile
    var now: () -> Long = System::currentTimeMillis
}

/** Часы для живого превью и отсчётов: обновляются раз в 30 секунд. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(AppClock.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = AppClock.now()
        }
    }
    return now
}

/**
 * Карточки стилей с живым превью — картинки рисует тот же код, что и виджет, на фоне-примере стиля.
 * Список строится из каталога стилей, новые появляются сами.
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
    val c = appColors
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (st in Styles.all(context)) {
            val isSelected = st.id == selected
            val shape = RoundedCornerShape(Dims.styleCardRadius)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.card)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.primary else c.cardBorder, shape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Выбрать стиль ${st.title}") { onSelect(st.id) }
                    .padding(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dims.previewHeight)
                        .clip(RoundedCornerShape(Dims.previewRadius))
                        .background(c.soft),
                ) {
                    previews[st.id]?.let {
                        Image(it, contentDescription = "Стиль ${st.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(st.title, style = Type.itemTitle, color = c.text, modifier = Modifier.weight(1f))
                    SelectMark(isSelected)
                }
            }
        }
    }
}
