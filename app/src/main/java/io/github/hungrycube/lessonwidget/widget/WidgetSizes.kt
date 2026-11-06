package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import kotlin.math.max

/**
 * Под какие размеры рисовать виджет: один для портрета, другой для ландшафта.
 *
 * Лаунчер сообщает список размеров, но настоящую область виджета приложению не показывает. Если отдать
 * системе картинку на каждый размер, она сама ищет ту, что «влезает» в область, а если не влезает ни одна
 * (лаунчер округлил размеры или вычел свои поля, так бывает на OPPO), берёт самую маленькую по площади.
 * Обычно это широкая и низкая картинка для ландшафта — на экране получался сплющенный текст.
 * Поэтому картинок всегда две, и выбирает их система только по ориентации экрана.
 */
data class WidgetSizes(val portrait: DpSize, val landscape: DpSize) {
    companion object {
        /** Ниже этого раскладки стилей не рассчитаны: рисунок делается с запасом и уменьшается целиком. */
        private const val MIN_RENDER_DP = 110f

        /** Null, если лаунчер размер ещё не сообщил (виджет только что добавили). */
        fun from(options: Bundle): WidgetSizes? {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            } else {
                null
            }
            return pick(
                list.orEmpty(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            )
        }

        /** Из списка: самый вытянутый вверх — портрет, самый вытянутый вширь — ландшафт. Без списка: min/max, как в документации Android. */
        fun pick(list: List<SizeF>, minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int): WidgetSizes? {
            if (list.isNotEmpty() && list.all { it.width > 0f && it.height > 0f }) {
                val tall = list.minBy { it.width / it.height }
                val wide = list.maxBy { it.width / it.height }
                return WidgetSizes(DpSize(tall.width.dp, tall.height.dp), DpSize(wide.width.dp, wide.height.dp))
            }
            if (minWidth <= 0 || minHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) return null
            return WidgetSizes(DpSize(minWidth.dp, maxHeight.dp), DpSize(maxWidth.dp, minHeight.dp))
        }

        /**
         * Размер рисунка: те же пропорции, но не меньше [MIN_RENDER_DP] по каждой стороне.
         * На экране такая картинка уменьшается целиком — текст мельче, зато ничего не наезжает друг на друга.
         */
        fun forRender(size: DpSize): DpSize {
            val k = max(1f, max(MIN_RENDER_DP / size.width.value, MIN_RENDER_DP / size.height.value))
            return DpSize((size.width.value * k).dp, (size.height.value * k).dp)
        }
    }
}
