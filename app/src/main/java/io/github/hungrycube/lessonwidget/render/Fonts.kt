package io.github.hungrycube.lessonwidget.render

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import io.github.hungrycube.lessonwidget.R

/** Шрифты из Google Fonts (лицензия OFL), лежат в res/font. Все — вариативные, жирность задаётся осью wght. */
enum class AppFont(@FontRes val res: Int) {
    MANROPE(R.font.manrope),
    INTER_TIGHT(R.font.inter_tight),
    JETBRAINS_MONO(R.font.jetbrains_mono),
    ROBOTO_FLEX(R.font.roboto_flex),
    LORA(R.font.lora),
    LORA_ITALIC(R.font.lora_italic),
    DOTO(R.font.doto),
    MONTSERRAT(R.font.montserrat),

    /** Системный шрифт — для Win95 (Tahoma в Android нет). */
    SYSTEM(0),
}

object FontCache {
    private val cache = HashMap<AppFont, Typeface>()

    fun get(context: Context, font: AppFont): Typeface = synchronized(cache) {
        cache.getOrPut(font) {
            if (font.res == 0) {
                Typeface.SANS_SERIF
            } else {
                runCatching { ResourcesCompat.getFont(context, font.res) }.getOrNull() ?: Typeface.DEFAULT
            }
        }
    }
}

/** Шрифт, жирность и выравнивание цифр обратного отсчёта в стиле. */
enum class CountdownLayout(val font: AppFont, val weight: Int, val align: Align) {
    MANROPE_END(AppFont.MANROPE, 600, Align.END),
    INTER_TIGHT_END(AppFont.INTER_TIGHT, 600, Align.END),
    INTER_TIGHT_CENTER(AppFont.INTER_TIGHT, 600, Align.CENTER),
    ROBOTO_FLEX_END(AppFont.ROBOTO_FLEX, 600, Align.END),
    DOTO_START(AppFont.DOTO, 900, Align.START),
    MONTSERRAT_END(AppFont.MONTSERRAT, 900, Align.END);

    fun typo(size: Float) = Typo(font, size, weight, lineHeight = 1f, tabular = true)
}
