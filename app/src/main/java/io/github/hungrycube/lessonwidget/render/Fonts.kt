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

/**
 * Разметки системного обратного отсчёта (res/layout/countdown_*.xml).
 * Шрифт, жирность и выравнивание зашиты в разметку — у RemoteViews их не поменять из кода.
 */
enum class CountdownLayout(val layoutRes: Int, val font: AppFont, val weight: Int, val align: Align) {
    MANROPE_END(R.layout.countdown_manrope_end, AppFont.MANROPE, 600, Align.END),
    INTER_TIGHT_END(R.layout.countdown_inter_tight_end, AppFont.INTER_TIGHT, 600, Align.END),
    INTER_TIGHT_CENTER(R.layout.countdown_inter_tight_center, AppFont.INTER_TIGHT, 600, Align.CENTER),
    ROBOTO_FLEX_END(R.layout.countdown_roboto_flex_end, AppFont.ROBOTO_FLEX, 600, Align.END),
    DOTO_START(R.layout.countdown_doto_start, AppFont.DOTO, 900, Align.START),
    MONTSERRAT_END(R.layout.countdown_montserrat_end, AppFont.MONTSERRAT, 900, Align.END);

    fun typo(size: Float) = Typo(font, size, weight, lineHeight = 1f, tabular = true)
}
