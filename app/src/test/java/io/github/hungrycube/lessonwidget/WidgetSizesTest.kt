package io.github.hungrycube.lessonwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.widget.ScheduleWidgetReceiver
import io.github.hungrycube.lessonwidget.widget.WidgetSizes
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Сбой с OPPO Reno 5 Lite (Android 13): виджет сплющен, потому что система показывала широкую и низкую
 * картинку для ландшафта в обычной области виджета. Размеры в тесте — как мог сообщить такой лаунчер.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetSizesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(context)
    private val portrait = SizeF(340f, 170f)
    private val landscape = SizeF(380f, 80f)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        CrashLog.clear(context)
    }

    @Test
    fun picksSizeByShape() {
        val s = WidgetSizes.pick(listOf(landscape, portrait), 0, 0, 0, 0)!!
        assertEquals(DpSize(340.dp, 170.dp), s.portrait)
        assertEquals(DpSize(380.dp, 80.dp), s.landscape)
        val one = WidgetSizes.pick(listOf(portrait), 0, 0, 0, 0)!!
        assertEquals(one.portrait, one.landscape)
    }

    @Test
    fun fallsBackToMinMax() {
        val s = WidgetSizes.pick(emptyList(), 300, 90, 380, 200)!!
        assertEquals(DpSize(300.dp, 200.dp), s.portrait)
        assertEquals(DpSize(380.dp, 90.dp), s.landscape)
        assertNull(WidgetSizes.pick(emptyList(), 0, 0, 0, 0))
        // Битый список — как будто его нет.
        assertNotNull(WidgetSizes.pick(listOf(SizeF(0f, 0f)), 300, 90, 380, 200))
    }

    @Test
    fun lowSizeIsRenderedWithSameProportions() {
        val r = WidgetSizes.forRender(DpSize(380.dp, 80.dp))
        assertEquals(110f, r.height.value, 0.01f)
        assertEquals(380f / 80f, r.width.value / r.height.value, 0.01f)
        // Обычные размеры не меняются — стили остаются пиксель в пиксель.
        assertEquals(DpSize(316.dp, 168.dp), WidgetSizes.forRender(DpSize(316.dp, 168.dp)))
    }

    /**
     * Причина сбоя, на настоящем коде Android: область чуть меньше обоих сообщённых размеров,
     * ни один не «влезает» — и из нескольких картинок система берёт самую маленькую по площади.
     */
    @Test
    fun androidPicksLandscapeFromSizedViewsWhenNothingFits() {
        val port = RemoteViews(context.packageName, R.layout.widget_preview)
        val land = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        val sized = RemoteViews(mapOf(portrait to port, landscape to land))
        val method = RemoteViews::class.java.getDeclaredMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
        method.isAccessible = true
        val chosen = method.invoke(sized, context, SizeF(334f, 165f)) as RemoteViews
        assertEquals(android.R.layout.simple_list_item_1, chosen.layoutId)
    }

    private fun View.images(): List<ImageView> = when (this) {
        is ImageView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).images() }
        else -> emptyList()
    }

    private fun bind(id: Int) {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ScheduleWidgetReceiver::class.java)
            minWidth = 250
            minHeight = 110
        }
        shadowOf(manager).addBoundWidget(id, info)
    }

    /** Что покажет система при текущей ориентации экрана. */
    private fun shownImage(id: Int): ImageView = runBlocking {
        bind(id)
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
        val sizes = WidgetSizes.pick(listOf(landscape, portrait), 0, 0, 0, 0)!!
        val views = WidgetUpdater.composeViews(context, glanceId, sizes)
        views.apply(context, FrameLayout(context)).images().single()
    }

    private fun assertShown(image: ImageView, expectedAspect: Float) {
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
        val bmp = (image.drawable as BitmapDrawable).bitmap
        assertEquals(expectedAspect, bmp.width.toFloat() / bmp.height, 0.02f)
    }

    @Test
    fun portraitScreenShowsPortraitPicture() {
        assertShown(shownImage(21), 340f / 170f)
        assertTrue(CrashLog.entries(context).isEmpty())
    }

    @Test
    @Config(qualifiers = "land")
    fun landscapeScreenShowsLandscapePicture() {
        assertShown(shownImage(22), 380f / 80f)
        assertTrue(CrashLog.entries(context).isEmpty())
    }

    /**
     * Все стили: как было на таком телефоне и как стало. Вторая колонка — лаунчер сообщил размер почти верно,
     * третья — худший случай, сообщённые пропорции сильно отличаются от настоящих.
     */
    @Test
    fun beforeAfterSheet() {
        val renderer = WidgetRenderer(context)
        val now = System.currentTimeMillis()
        val d = Demo.display(now)
        val photoFile = File(System.getProperty("samples.dir") ?: "../samples", "photos/night_city.jpg")
        val photo = PhotoInput(BitmapFactory.decodeFile(photoFile.path))
        val area = RectF(0f, 0f, 668f, 330f)
        val styles = Styles.all(context)
        val rowH = area.height().toInt() + 44
        val sheet = Bitmap.createBitmap(3 * (area.width().toInt() + 24) + 24, styles.size * rowH + 24, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.rgb(242, 241, 237))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun picture(id: String, w: Float, h: Float): Bitmap {
            val st = styles.first { it.id == id }
            val size = WidgetSizes.forRender(DpSize(w.dp, h.dp))
            val custom = if (st.usesPhoto) StyleCustom(emptyMap(), photo) else null
            return renderer.render(st, d, size.width.value, size.height.value, RenderMode.WIDGET, nowMillis = now, density = 2f, custom = custom).bitmap
        }
        for ((row, st) in styles.withIndex()) {
            val y = 24f + row * rowH
            for (col in 0..2) {
                val x = 24f + col * (area.width() + 24)
                val label = listOf("было", "стало: размер почти верный", "стало: размер сильно неверный")[col]
                c.drawText("${st.title}: $label", x, y + 22, text)
                val box = RectF(x, y + 32, x + area.width(), y + 32 + area.height())
                paint.color = Color.rgb(40, 44, 54)
                c.drawRect(box, paint)
                paint.color = Color.BLACK
                // Как было: широкая низкая картинка (ещё без запаса по высоте) растянута на всю область.
                val bmp = when (col) {
                    0 -> renderer.render(st, d, 380f, 80f, RenderMode.WIDGET, nowMillis = now, density = 2f, custom = if (st.usesPhoto) StyleCustom(emptyMap(), photo) else null).bitmap
                    1 -> picture(st.id, 340f, 170f)
                    else -> picture(st.id, 300f, 200f)
                }
                val dst = if (col == 0) {
                    box
                } else {
                    val k = minOf(box.width() / bmp.width, box.height() / bmp.height)
                    val w = bmp.width * k
                    val h = bmp.height * k
                    RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
                }
                c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dst, paint)
            }
        }
        val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(out, "widget_sizes_before_after.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
