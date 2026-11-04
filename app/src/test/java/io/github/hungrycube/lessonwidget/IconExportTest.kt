package io.github.hungrycube.lessonwidget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Растровые копии векторной иконки (app/build/screenshots/icon): legacy-mipmap для лаунчеров без adaptive icon,
 * 512×512 для README и проверочная картинка с масками разных лаунчеров и тематической иконкой.
 * PNG из ci-logs кладутся в res/mipmap-*dpi и docs/icon.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots", "icon").apply { mkdirs() }

    private fun drawable(id: Int): Drawable = ContextCompat.getDrawable(context, id)!!.mutate()

    /** Слои adaptive icon целиком (108 dp) в квадрат [full] px. */
    private fun layers(full: Int, monochrome: Int? = null): Bitmap {
        val bmp = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (monochrome != null) {
            canvas.drawColor(0xFFD3EBDD.toInt())
            drawable(R.drawable.ic_launcher_monochrome).apply {
                colorFilter = PorterDuffColorFilter(monochrome, PorterDuff.Mode.SRC_IN)
                setBounds(0, 0, full, full)
                draw(canvas)
            }
        } else {
            for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
                drawable(id).apply {
                    setBounds(0, 0, full, full)
                    draw(canvas)
                }
            }
        }
        return bmp
    }

    /**
     * Иконка [size] px: видимая область 72 dp занимает квадрат со стороной size − 2·[inset],
     * форма [shape] вырезается со сглаживанием.
     */
    private fun icon(size: Int, inset: Float, shape: (RectF) -> Path, monochrome: Int? = null): Bitmap {
        val content = size - 2 * inset
        val full = Math.round(content * 108f / 72f)
        val src = layers(full, monochrome)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val off = inset - (full - content) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setTranslate(off, off) })
            }
        }
        canvas.drawPath(shape(RectF(inset, inset, inset + content, inset + content)), paint)
        return bmp
    }

    private fun circle(r: RectF) = Path().apply { addOval(r, Path.Direction.CW) }
    private fun rounded(frac: Float) = { r: RectF -> Path().apply { addRoundRect(r, r.width() * frac, r.width() * frac, Path.Direction.CW) } }
    private fun teardrop(r: RectF) = Path().apply {
        val radii = r.width() / 2
        addRoundRect(r, floatArrayOf(radii, radii, radii, radii, r.width() * 0.12f, r.width() * 0.12f, radii, radii), Path.Direction.CW)
    }

    private fun save(name: String, bmp: Bitmap) {
        val f = File(out, "$name.png")
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun export() {
        // Legacy: 48 dp с полями 2 dp, как делает Android Studio.
        val densities = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((d, size) in densities) {
            save("mipmap-$d/ic_launcher", icon(size, size / 24f, rounded(0.2f)))
            save("mipmap-$d/ic_launcher_round", icon(size, size / 24f, ::circle))
        }
        save("icon_512", icon(512, 0f, rounded(0.225f)))

        // Проверка: маски Pixel (круг), Samsung/HyperOS (сквиркл), ColorOS (скруглённый квадрат), капля, тематическая.
        val tile = 216
        val masks: List<Pair<(RectF) -> Path, Int?>> = listOf(
            ::circle to null,
            rounded(0.32f) to null,
            rounded(0.18f) to null,
            ::teardrop to null,
            ::circle to 0xFF1E4D37.toInt(),
        )
        val sheet = Bitmap.createBitmap(tile * (masks.size + 1), tile, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF8A8A8A.toInt())
        for ((i, m) in masks.withIndex()) {
            canvas.drawBitmap(icon(tile, 24f, m.first, m.second), (i * tile).toFloat(), 0f, null)
        }
        // Последняя клетка: слой 108 dp целиком и безопасный круг 66 dp.
        val full = layers(tile)
        canvas.drawBitmap(full, (masks.size * tile).toFloat(), 0f, null)
        val k = tile / 108f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.YELLOW
        }
        canvas.drawCircle(masks.size * tile + 54 * k, 54 * k, 33 * k, ring)
        ring.color = Color.WHITE
        canvas.drawRect(masks.size * tile + 18 * k, 18 * k, masks.size * tile + 90 * k, 90 * k, ring)
        save("icon_masks", sheet)
    }
}
