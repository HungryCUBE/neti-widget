package io.github.hungrycube.lessonwidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import io.github.hungrycube.lessonwidget.render.PhotoInput
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.max

/** Кадр своего фото: точка фокуса (центр видимой части в долях фото, как его видно) и отражение. */
@Serializable
data class PhotoFrame(val focusX: Float = 0.5f, val focusY: Float = 0.5f, val flip: Boolean = false)

/**
 * Свои фото виджетов: filesDir/widget_photos/<appWidgetId>.jpg и default.jpg — фото по умолчанию из настроек
 * (его получает новый виджет). В файле — копия из галереи, уменьшенная до [MAX_SIDE] по длинной стороне.
 * Фото удаляется вместе с виджетом и при смене фото или стиля; черновики выбора — при выходе без сохранения.
 */
object WidgetPhotos {
    const val DEFAULT = "default"

    /** Больше не нужно: виджет — картинка не больше полутора экранов, а для кадра и стекла хватает с запасом. */
    private const val MAX_SIDE = 1600

    private const val DRAFT = ".draft"

    fun dir(context: Context): File = File(context.applicationContext.filesDir, "widget_photos")

    fun file(context: Context, key: String): File = File(dir(context), "$key.jpg")

    /** Черновик: выбранное, но ещё не сохранённое фото. */
    fun draft(context: Context, key: String): File = File(dir(context), "$key$DRAFT.jpg")

    /** Фото виджета: своё, а если его нет — фото по умолчанию из настроек. */
    fun fileFor(context: Context, appWidgetId: Int?): File? =
        appWidgetId?.let { file(context, "$it") }?.takeIf { it.isFile } ?: file(context, DEFAULT).takeIf { it.isFile }

    // Последние картинки: перерисовка раз в минуту не должна каждый раз распаковывать JPEG.
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun load(file: File?): Bitmap? {
        if (file == null || !file.isFile) return null
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    /** Своё фото для отрисовки или null, если фото нет. */
    fun input(file: File?, frame: PhotoFrame?): PhotoInput? {
        val bmp = load(file) ?: return null
        val f = frame ?: PhotoFrame()
        return PhotoInput(bmp, f.focusX, f.focusY, f.flip)
    }

    /**
     * Копирует фото из галереи в [dest]: уменьшает до [MAX_SIDE], поворачивает по EXIF, сохраняет JPEG.
     * Возвращает false, если файл не читается как картинка.
     */
    fun import(context: Context, uri: Uri, dest: File): Boolean {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val k = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        val m = Matrix()
        if (k < 1f) m.postScale(k, k)
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
        }
        if (!m.isIdentity) {
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        dest.parentFile?.mkdirs()
        val af = AtomicFile(dest)
        val stream = af.startWrite()
        try {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            af.finishWrite(stream)
        } catch (e: Exception) {
            af.failWrite(stream)
            throw e
        } finally {
            bmp.recycle()
        }
        return true
    }

    /** Копия фото (например, фото по умолчанию для нового виджета). */
    fun copy(from: File, to: File) {
        to.parentFile?.mkdirs()
        val af = AtomicFile(to)
        val stream = af.startWrite()
        try {
            from.inputStream().use { it.copyTo(stream) }
            af.finishWrite(stream)
        } catch (e: Exception) {
            af.failWrite(stream)
            throw e
        }
    }

    /** Черновик становится фото [key]; старое фото заменяется. */
    fun commitDraft(context: Context, draftKey: String, key: String) {
        val d = draft(context, draftKey)
        if (!d.isFile) return
        val dest = file(context, key)
        if (!d.renameTo(dest)) {
            copy(d, dest)
            d.delete()
        }
    }

    fun delete(context: Context, key: String) {
        file(context, key).delete()
        draft(context, key).delete()
    }

    /** Удаляет фото виджетов, которых больше нет, и все черновики. Фото по умолчанию остаётся. */
    fun cleanup(context: Context, widgetIds: Set<Int>) {
        dir(context).listFiles()?.forEach { f ->
            val name = f.name.removeSuffix(".jpg")
            val keep = name == DEFAULT || name.toIntOrNull()?.let { it in widgetIds } == true
            if (!keep) f.delete()
        }
    }
}
