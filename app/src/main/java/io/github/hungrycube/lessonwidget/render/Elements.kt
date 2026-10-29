package io.github.hungrycube.lessonwidget.render

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import androidx.core.graphics.withSave
import io.github.hungrycube.lessonwidget.core.display.DayLine
import io.github.hungrycube.lessonwidget.core.display.LineStatus
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.render.theme.BackgroundStyle
import io.github.hungrycube.lessonwidget.render.theme.ColorExpr
import io.github.hungrycube.lessonwidget.render.theme.Layer
import io.github.hungrycube.lessonwidget.render.theme.ProgressStyle
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Элементы, общие для всех раскладок: фон карточки, полоса прогресса, отсчёт, список пар, иконки.
 * Как они выглядят, задаёт файл стиля (background, progress, counter, agenda, icons), раскладка решает только где.
 */

// ---------- Фон ----------

/** Фон карточки по background стиля: слои, блики, рамка, тень. */
fun Frame.card() = Backgrounds.draw(p, th.background, 0f, 0f, W, H, mode)

internal object Backgrounds {

    fun draw(p: Painter, bg: BackgroundStyle, l: Float, t: Float, r: Float, b: Float, mode: RenderMode) {
        if (bg.extended) return drawShape(p, bg, l, t, r, b, mode)
        fun active(w: String?) = w == null || (w == "widget") == (mode == RenderMode.WIDGET)
        val radius = bg.radius
        bg.shadow?.let { s ->
            p.canvas.withSave {
                clipOutPath(roundPath(l, t, r, b, radius))
                p.fill.shader = null
                p.fill.color = s.color or 0xFF000000.toInt()
                p.fill.setShadowLayer(s.blur, s.dx, s.dy, s.color)
                shape(p, l, t, r, b, radius)
                p.fill.clearShadowLayer()
            }
        }
        if (bg.clip) {
            p.canvas.save()
            p.clipRound(l, t, r, b, radius)
        }
        val layerRadius = if (bg.clip) 0f else radius
        for (layer in bg.layers) if (active(layer.whenMode)) layer(p, layer, l, t, r, b, layerRadius)
        if (bg.clip) p.canvas.restore()
        for (hl in bg.highlights) {
            if (!active(hl.whenMode)) continue
            when (hl.type) {
                "innerStroke" -> p.roundRectStroke(
                    l + hl.inset, t + hl.inset, r - hl.inset, b - hl.inset, max(0f, radius - hl.inset),
                    hl.ink.paintColor, hl.width, hl.ink.gradient?.shader(l, t, r, b),
                )
                "gloss" -> p.canvas.withSave {
                    p.clipRound(l, t, r, b, radius)
                    val gb = t + (b - t) * hl.height
                    p.fill.shader = hl.ink.gradient?.shader(l, t, r, gb)
                        ?: LinearGradient(0f, t, 0f, gb, hl.ink.color, hl.ink.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    p.fill.color = if (hl.ink.gradient != null) hl.ink.paintColor else 0xFF000000.toInt()
                    drawRect(l + hl.inset, t + hl.inset, r - hl.inset, gb, p.fill)
                    p.fill.shader = null
                }
            }
        }
        bg.border?.takeIf { active(it.whenMode) }?.let { border ->
            p.roundRectStroke(l, t, r, b, radius, border.ink.paintColor, border.width, border.ink.gradient?.shader(l, t, r, b))
        }
    }

    private fun roundPath(l: Float, t: Float, r: Float, b: Float, radius: Float) =
        Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) }

    /** Фигура карточки текущей кистью p.fill. */
    private fun shape(p: Painter, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        if (radius > 0f) p.canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, p.fill) else p.canvas.drawRect(l, t, r, b, p.fill)
    }

    private fun layer(p: Painter, layer: Layer, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val w = r - l
        val h = b - t
        when (layer) {
            is Layer.Solid -> if (radius > 0f) p.roundRect(l, t, r, b, radius, layer.color) else p.rect(l, t, r, b, layer.color)
            is Layer.Shade -> {
                p.fill.shader = layer.gradient.shader(l, t, r, b)
                p.fill.color = layer.gradient.paintAlpha shl 24
                shape(p, l, t, r, b, radius)
                p.fill.shader = null
            }
            is Layer.Mesh -> {
                val diag = sqrt(w * w + h * h)
                for (pt in layer.points) {
                    val cx = l + pt.x * w
                    val cy = t + pt.y * h
                    val rr = max(0.01f, pt.radius * diag)
                    p.fill.shader = RadialGradient(cx, cy, rr, pt.color, pt.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    p.fill.color = layer.alpha shl 24
                    shape(p, l, t, r, b, radius)
                    p.fill.shader = null
                }
            }
            is Layer.Image -> {
                val bmp = layer.bitmap() ?: return
                p.canvas.withSave {
                    if (radius > 0f) p.clipRound(l, t, r, b, radius)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = layer.alpha }
                    drawBitmap(bmp, crop(bmp, w, h, layer.fit), fitRect(bmp, l, t, r, b, layer.fit), paint)
                }
            }
            is Layer.Rules -> {
                var ly = t + layer.offset
                while (ly < b) {
                    p.rect(l, ly, r, ly + layer.width, layer.color)
                    ly += layer.step
                }
            }
            is Layer.Line -> {
                if (layer.x != null) p.rect(l + layer.x, t, l + layer.x + layer.width, b, layer.color)
                if (layer.y != null) p.rect(l, t + layer.y, r, t + layer.y + layer.width, layer.color)
            }
            is Layer.Band -> p.canvas.withSave {
                rotate(layer.angle, l + w * layer.pivotX, t + h * layer.pivotY)
                p.rect(l + w * layer.x, t - h, l + w * layer.x + layer.width, t + 2 * h, layer.color)
            }
            is Layer.Photo -> photo(p, layer, l, t, r, b)
            // Фигуры рисует drawShape: им нужна маска родителя и режим.
            is Layer.Shape -> Unit
        }
    }

    /**
     * Фон с новыми возможностями: фигура с CSS-скруглениями, тени box-shadow, размытие под подложкой, фигуры внутри.
     * Порядок как в CSS: наружные тени, размытие под фигурой, заливки, inset-тени, вложенные фигуры, блики, рамка.
     * Всё внутри обрезается по фигуре (overflow: hidden).
     */
    private fun drawShape(p: Painter, bg: BackgroundStyle, l: Float, t: Float, r: Float, b: Float, mode: RenderMode) {
        fun active(w: String?) = w == null || (w == "widget") == (mode == RenderMode.WIDGET)
        val shape = Shape.of(l, t, r, b, bg.radius, bg.corners)
        val path = shape.path()
        bg.shadow?.let { s ->
            p.canvas.withSave {
                clipOutPath(path)
                p.fill.shader = null
                p.fill.color = s.color or 0xFF000000.toInt()
                p.fill.setShadowLayer(s.blur, s.dx, s.dy, s.color)
                drawPath(path, p.fill)
                p.fill.clearShadowLayer()
            }
        }
        Shadows.outer(p, shape, bg.shadows)
        if (bg.backdropBlur > 0f || bg.saturate != 1f) p.blurBehind(path, l, t, r, b, bg.backdropBlur, bg.saturate)
        p.canvas.withSave {
            clipPath(path)
            for (layer in bg.layers) if (layer !is Layer.Shape && active(layer.whenMode)) layer(p, layer, l, t, r, b, 0f)
            Shadows.inner(p, shape, bg.shadows)
            for (layer in bg.layers) {
                if (layer !is Layer.Shape || !active(layer.whenMode)) continue
                val box = layer.box.place(l, t, r, b)
                draw(p, layer.style, box[0], box[1], box[2], box[3], mode)
            }
        }
        for (hl in bg.highlights) {
            if (!active(hl.whenMode)) continue
            when (hl.type) {
                "innerStroke" -> {
                    val s = shape.inset(hl.inset + hl.width / 2f)
                    p.stroke.shader = hl.ink.gradient?.shader(l, t, r, b)
                    p.stroke.color = hl.ink.paintColor
                    p.stroke.strokeWidth = hl.width
                    p.canvas.drawPath(s.path(), p.stroke)
                    p.stroke.shader = null
                }
                "gloss" -> p.canvas.withSave {
                    clipPath(path)
                    val gb = t + (b - t) * hl.height
                    p.fill.shader = hl.ink.gradient?.shader(l, t, r, gb)
                        ?: LinearGradient(0f, t, 0f, gb, hl.ink.color, hl.ink.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    p.fill.color = if (hl.ink.gradient != null) hl.ink.paintColor else 0xFF000000.toInt()
                    drawRect(l + hl.inset, t + hl.inset, r - hl.inset, gb, p.fill)
                    p.fill.shader = null
                }
            }
        }
        bg.border?.takeIf { active(it.whenMode) }?.let { border ->
            p.stroke.shader = border.ink.gradient?.shader(l, t, r, b)
            p.stroke.color = border.ink.paintColor
            p.stroke.strokeWidth = border.width
            p.canvas.drawPath(shape.inset(border.width / 2f).path(), p.stroke)
            p.stroke.shader = null
        }
    }

    /** Своё фото: кадр под пропорции по точке фокуса, отражение, размытие самого фото (без ореола по краям). */
    private fun photo(p: Painter, layer: Layer.Photo, l: Float, t: Float, r: Float, b: Float) {
        val ph = layer.photo ?: return
        val w = r - l
        val h = b - t
        if (w <= 0f || h <= 0f) return
        val crop = ph.crop(w / h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = layer.alpha }
        var src: Bitmap = ph.bitmap
        var srcRect: Rect? = crop
        var temp: Bitmap? = null
        if (layer.blur > 0f) {
            val k = p.deviceScale()
            val sigma = layer.blur / 2f * k
            val down = max(1, (sigma / 3f).toInt())
            val tw = max(2, (w * k / down).roundToInt())
            val th = max(2, (h * k / down).roundToInt())
            temp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also { bmp ->
                android.graphics.Canvas(bmp).drawBitmap(ph.bitmap, crop, Rect(0, 0, tw, th), Paint(Paint.FILTER_BITMAP_FLAG))
                Blur.gaussian(bmp, sigma / down)
            }
            src = temp
            srcRect = null
        }
        p.canvas.withSave {
            if (ph.flip) scale(-1f, 1f, (l + r) / 2f, (t + b) / 2f)
            drawBitmap(src, srcRect, RectF(l, t, r, b), paint)
        }
        temp?.recycle()
    }

    /** Часть картинки: для cover — середина нужной пропорции, иначе вся. */
    private fun crop(bmp: Bitmap, w: Float, h: Float, fit: String): Rect {
        if (fit != "cover" || w <= 0f || h <= 0f) return Rect(0, 0, bmp.width, bmp.height)
        val k = max(w / bmp.width, h / bmp.height)
        val cw = (w / k).roundToInt().coerceIn(1, bmp.width)
        val ch = (h / k).roundToInt().coerceIn(1, bmp.height)
        val x = (bmp.width - cw) / 2
        val y = (bmp.height - ch) / 2
        return Rect(x, y, x + cw, y + ch)
    }

    private fun fitRect(bmp: Bitmap, l: Float, t: Float, r: Float, b: Float, fit: String): RectF {
        if (fit != "contain") return RectF(l, t, r, b)
        val k = min((r - l) / bmp.width, (b - t) / bmp.height)
        val w = bmp.width * k
        val h = bmp.height * k
        val x = l + (r - l - w) / 2f
        val y = t + (b - t - h) / 2f
        return RectF(x, y, x + w, y + h)
    }
}

// ---------- Прогресс ----------

/**
 * Прогресс по progress стиля в прямоугольнике от [l] до [r], верх — [t], высота — [ProgressStyle.height].
 * [value] null — пустая полоса (для точек).
 */
fun Frame.progress(l: Float, t: Float, r: Float, value: Float?) {
    val ps = th.progress
    when (ps.type) {
        "dots" -> dotsProgress(ps, l, t, r, value)
        "blocks" -> blocksProgress(ps, l, t, r, value ?: 0f)
        "text" -> if (value != null) textProgress(ps, l, t, r, value)
        else -> barProgress(ps, l, t, r, value ?: 0f)
    }
}

/** Высота, которую раскладка оставляет под прогресс. */
val Frame.progressHeight: Float
    get() = if (th.progress.type == "text") p.lineHeight(role(th.progress.role, "details").typo) else th.progress.height

private fun Frame.inkRect(l: Float, t: Float, r: Float, b: Float, radius: Float, ink: Ink, shaderL: Float, shaderR: Float) {
    val g = ink.gradient
    if (g == null) {
        if (radius > 0f) p.roundRect(l, t, r, b, radius, ink.color) else p.rect(l, t, r, b, ink.color)
        return
    }
    p.fill.shader = g.shader(shaderL, t, shaderR, b)
    p.fill.color = ink.paintColor
    if (radius > 0f) p.canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, p.fill) else p.canvas.drawRect(l, t, r, b, p.fill)
    p.fill.shader = null
}

/**
 * Полоса из подложек (progress.trackSurface / fillSurface): градиенты, тени, блики как у фона.
 * Заполнение отступает от краёв на fillInset и при fillClip обрезается по полосе (прямой правый край).
 */
private fun Frame.surfaceProgress(ps: ProgressStyle, l: Float, t: Float, r: Float, value: Float) {
    val h = ps.height
    val track = ps.trackSurface
    if (track != null) Backgrounds.draw(p, track, l, t, r, t + h, mode) else inkRect(l, t, r, t + h, ps.radius, ps.track, l, r)
    val i = ps.fillInset
    val il = l + i
    val ir = r - i
    val fw = (ir - il) * value
    if (fw <= 0.5f && (ps.minFill > 0f || fw <= 0f)) return
    val fr = il + if (ps.minFill > 0f) max(fw, ps.minFill) else fw
    p.canvas.withSave {
        if (ps.fillClip) clipPath(Shape.of(l, t, r, t + h, ps.radius, track?.corners).path())
        val fill = ps.fillSurface
        if (fill != null) {
            Backgrounds.draw(p, fill, il, t + i, fr, t + h - i, mode)
        } else {
            inkRect(il, t + i, fr, t + h - i, if (ps.fillClip) 0f else ps.radius, ps.fill, il, ir)
        }
    }
}

private fun Frame.barProgress(ps: ProgressStyle, l: Float, t: Float, r: Float, value: Float) {
    if (ps.trackSurface != null || ps.fillSurface != null) return surfaceProgress(ps, l, t, r, value)
    val h = ps.height
    inkRect(l, t, r, t + h, ps.radius, ps.track, l, r)
    val fw = (r - l) * value
    // С минимальной длиной заполнение появляется, только когда его уже видно.
    if (ps.minFill > 0f && fw <= 0.5f) return
    val fr = if (ps.minFill > 0f) l + max(fw, ps.minFill) else l + fw
    val glow = ps.glow
    if (glow != null) p.fill.setShadowLayer(glow.blur, glow.dx, glow.dy, glow.color)
    inkRect(l, t, fr, t + h, ps.radius, ps.fill, l, r)
    if (glow != null) p.fill.clearShadowLayer()
    when (ps.type) {
        "glossy" -> p.canvas.withSave {
            p.clipRound(l, t, fr, t + h, ps.radius)
            val a = (ps.gloss * 255).roundToInt().coerceIn(0, 255)
            p.fill.shader = LinearGradient(0f, t, 0f, t + h * 0.55f, (a shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            p.fill.color = 0xFF000000.toInt()
            drawRect(l, t, fr, t + h * 0.55f, p.fill)
            p.fill.shader = null
        }
        "striped" -> p.canvas.withSave {
            p.clipRound(l, t, fr, t + h, ps.radius)
            val stripe = ps.block
            var x = l - h
            while (x < fr) {
                val path = Path().apply {
                    moveTo(x, t + h)
                    lineTo(x + stripe / 2f, t + h)
                    lineTo(x + stripe / 2f + h, t)
                    lineTo(x + h, t)
                    close()
                }
                p.fill.shader = null
                p.fill.color = 0x38FFFFFF
                drawPath(path, p.fill)
                x += stripe
            }
        }
    }
}

private fun Frame.dotsProgress(ps: ProgressStyle, l: Float, t: Float, r: Float, value: Float?) {
    val dot = ps.height
    val count = min(ps.max, floor((r - l - dot) / ps.step).toInt() + 1)
    if (count < 2) return
    val step = (r - l - dot) / (count - 1)
    val current = value?.let { floor(it * count).toInt().coerceIn(0, count - 1) } ?: -1
    for (i in 0 until count) {
        val c = when {
            value == null -> ps.track.color
            i < current -> ps.fill.color
            i == current -> ps.current
            else -> ps.track.color
        }
        p.circle(l + dot / 2f + i * step, t + dot / 2f, dot / 2f, c)
    }
}

private fun Frame.blocksProgress(ps: ProgressStyle, l: Float, t: Float, r: Float, value: Float) {
    val h = ps.height
    p.rect(l, t, r, t + h, ps.track.color)
    val frame = if (ps.frame == "bevel") 2f else 0f
    if (ps.frame == "bevel") bevel(l, t, r, t + h, raised = false)
    val il = l + frame
    val ir = r - frame
    val fillR = il + (ir - il) * value
    var sx = il + 1f
    while (sx < fillR - 0.5f) {
        p.rect(sx, t + ps.inset, min(sx + ps.block, fillR), t + (h - ps.inset), ps.fill.color)
        sx += ps.block + ps.gap
    }
}

/** Прогресс символами, как в консоли: «████░░░ 47%». */
private fun Frame.textProgress(ps: ProgressStyle, l: Float, t: Float, r: Float, value: Float) {
    val mono = role(ps.role, "details").typo
    val pct = sv("progressPercent", "percent" to "${(value * 100).roundToInt()}")
    val cw = p.width(ps.glyph, mono)
    val n = min(ps.max, floor((r - l - p.width(sv("progressPercent", "percent" to "100"), mono)) / cw).toInt()).coerceAtLeast(ps.min)
    val filled = (value * n).roundToInt().coerceIn(0, n)
    var bx = l
    bx += p.text(ps.glyph.repeat(filled), bx, t, mono, ps.fill.color)
    bx += p.text(ps.glyph.repeat(n - filled), bx, t, mono, ps.track.color)
    p.text(pct, bx, t, mono, ps.current)
}

/** Кольцо прогресса с центром ([cx], [cy]) и радиусом по середине линии [rr]. Толщина — progress.width. */
fun Frame.ringProgress(cx: Float, cy: Float, rr: Float, value: Float?) {
    val ps = th.progress
    val box = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
    p.stroke.shader = null
    p.stroke.strokeCap = if (ps.round) Paint.Cap.ROUND else Paint.Cap.BUTT
    p.stroke.strokeWidth = ps.width
    p.stroke.color = ps.track.color
    p.canvas.drawCircle(cx, cy, rr, p.stroke)
    value?.let { prog ->
        p.stroke.color = ps.fill.paintColor
        p.stroke.shader = ps.fill.gradient?.shader(box.left, box.top, box.right, box.bottom)
        p.canvas.drawArc(box, -90f, 360f * prog, false, p.stroke)
        p.stroke.shader = null
    }
    p.stroke.strokeCap = Paint.Cap.BUTT
}

/** Рамка в стиле Windows 95. Цвета — bevelLight, bevelShadow, bevelOuterLight, bevelOuterDark из палитры. */
fun Frame.bevel(l: Float, t: Float, r: Float, b: Float, raised: Boolean, thick: Boolean = true) {
    val hi = c("bevelLight")
    val sh = c("bevelShadow")
    val outerHi = c("bevelOuterLight")
    val outerSh = c("bevelOuterDark")
    val light = if (raised) hi else sh
    val dark = if (raised) sh else hi
    val outerLight = if (raised) outerHi else outerSh
    val outerDark = if (raised) outerSh else outerHi
    if (thick) {
        p.rect(l, t, r, t + 2f, light)
        p.rect(l, t, l + 2f, b, light)
        p.rect(l, b - 2f, r, b, dark)
        p.rect(r - 2f, t, r, b, dark)
        p.rect(l, t, r, t + 1f, outerLight)
        p.rect(l, t, l + 1f, b, outerLight)
        p.rect(l, b - 1f, r, b, outerDark)
        p.rect(r - 1f, t, r, b, outerDark)
    } else {
        p.rect(l, t, r, t + 1f, light)
        p.rect(l, t, l + 1f, b, light)
        p.rect(l, b - 1f, r, b, dark)
        p.rect(r - 1f, t, r, b, dark)
    }
}

// ---------- Отсчёт ----------

/**
 * Обратный отсчёт в минутах — рисуется в картинке шрифтом стиля (роль counter;
 * системный Chronometer лаунчеры показывают только системным шрифтом). Выравнивание — counter.align.
 * У counter.type orb и ring под цифрами рисуется капля или кольцо.
 */
fun Frame.countdown(x: Float, y: Float, w: Float, h: Float, text: String, size: Float, color: Int? = null) {
    if (d.countdownTo == null) return
    if (th.counter.type == "surface") {
        // Блок с подложкой рисуется своим размером, по выравниванию роли counter в отведённом месте.
        val box = counterBox() ?: return
        val bx = when (role("counter").align) {
            Align.START -> x
            Align.END -> x + w - box.w
            Align.CENTER -> x + (w - box.w) / 2f
        }
        box.draw(bx, y + (h - box.h) / 2f)
        return
    }
    val role = role("counter")
    val t = role.typo.sized(size)
    val ty = y + (h - p.lineHeight(t)) / 2f
    val ax = when (role.align) {
        Align.START -> x
        Align.END -> x + w
        Align.CENTER -> x + w / 2f
    }
    if (th.counter.type != "text") counterBadge(text, t, ax, ty, role.align)
    p.text(text, ax, ty, t, color ?: role.color, role.align)
}

private fun Frame.counterBadge(text: String, t: Typo, ax: Float, ty: Float, align: Align) {
    val cs = th.counter
    val tw = p.width(text, t)
    val lh = p.lineHeight(t)
    val left = when (align) {
        Align.START -> ax
        Align.END -> ax - tw
        Align.CENTER -> ax - tw / 2f
    }
    val cx = left + tw / 2f
    val cy = ty + lh / 2f
    val dia = max(tw, lh) + cs.padding * 2f
    val hw = max(dia, tw + cs.padding * 2f) / 2f
    val hh = dia / 2f
    if (cs.type == "orb") {
        val box = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        p.fill.shader = cs.fill.gradient?.shader(box.left, box.top, box.right, box.bottom)
            ?: RadialGradient(cx - hw * 0.3f, cy - hh * 0.4f, hw * 1.6f, lighten(cs.fill.color), cs.fill.color, Shader.TileMode.CLAMP)
        p.fill.color = if (cs.fill.gradient != null) cs.fill.paintColor else 0xFF000000.toInt()
        p.canvas.drawRoundRect(box, hh, hh, p.fill)
        p.fill.shader = null
        if (cs.gloss) {
            val g = RectF(box.left + hw * 0.25f, box.top + hh * 0.08f, box.right - hw * 0.25f, cy)
            p.fill.shader = LinearGradient(0f, g.top, 0f, g.bottom, 0xB3FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            p.fill.color = 0xFF000000.toInt()
            p.canvas.drawOval(g, p.fill)
            p.fill.shader = null
        }
        p.roundRectStroke(box.left, box.top, box.right, box.bottom, hh, cs.track, 1f)
    } else {
        val rr = hh - cs.width / 2f
        p.stroke.shader = null
        p.stroke.strokeWidth = cs.width
        p.stroke.color = cs.track
        p.canvas.drawCircle(cx, cy, rr, p.stroke)
        d.progress?.let { prog ->
            p.stroke.strokeCap = Paint.Cap.ROUND
            p.stroke.color = cs.fill.color
            p.canvas.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90f, 360f * prog, false, p.stroke)
            p.stroke.strokeCap = Paint.Cap.BUTT
        }
    }
}

private fun lighten(c: Int): Int {
    fun ch(shift: Int) = (((c shr shift) and 0xFF) + (255 - ((c shr shift) and 0xFF)) * 0.45f).roundToInt()
    return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Отсчёт как готовый блок: размер, базовая линия числа от верха блока и отрисовка в точке (левый верхний угол). */
class CounterBox(val w: Float, val h: Float, val baseline: Float, private val drawAt: (Float, Float) -> Unit) {
    fun draw(x: Float, y: Float) = drawAt(x, y)
}

/**
 * Отсчёт блоком: число (роль counter) и подпись «мин» (роль unit) справа по базовой линии или под числом
 * (counter.unit), при counter.type surface — на подложке: кругом размера counter.size или по тексту с полями insets.
 * null — отсчёта сейчас нет (до события больше 90 минут).
 */
fun Frame.counterBox(): CounterBox? {
    if (d.countdownTo == null) return null
    val cs = th.counter
    val num = role("counter")
    val unit = role("unit", "counter")
    val unitText = if (cs.unit == "none") null else s("unitLabel")
    val numText = if (unitText == null) counterText() else "${d.minutesLeft ?: 0}"
    val nt = num.typo
    val ut = unit.typo
    val nw = p.width(numText, nt)
    val nh = p.lineHeight(nt)
    val nb = p.baseline(0f, nt)
    val uw = unitText?.let { p.width(it, ut) } ?: 0f
    val uh = unitText?.let { p.lineHeight(ut) } ?: 0f
    val ub = unitText?.let { p.baseline(0f, ut) } ?: 0f
    // Содержимое: ширина, высота и где верх числа и подписи.
    val cw: Float
    val ch: Float
    val numTop: Float
    val unitTop: Float
    if (unitText != null && cs.unit == "inline") {
        val above = max(nb, ub)
        cw = nw + cs.unitGap + uw
        ch = above + max(nh - nb, uh - ub)
        numTop = above - nb
        unitTop = above - ub
    } else if (unitText != null) {
        cw = max(nw, uw)
        ch = nh + cs.unitGap + uh
        numTop = 0f
        unitTop = nh + cs.unitGap
    } else {
        cw = nw
        ch = nh
        numTop = 0f
        unitTop = 0f
    }
    val surface = if (cs.type == "surface") cs.surface else null
    val (padT, padR, padB, padL) = cs.insets.toList()
    val bw: Float
    val bh: Float
    val ox: Float
    val oy: Float
    val size = cs.size
    when {
        surface != null && size != null -> {
            bw = size
            bh = size
            ox = (size - cw) / 2f
            oy = (size - ch) / 2f
        }
        surface != null -> {
            bw = cw + padL + padR
            bh = ch + padT + padB
            ox = padL
            oy = padT
        }
        else -> {
            bw = cw
            bh = ch
            ox = 0f
            oy = 0f
        }
    }
    return CounterBox(bw, bh, oy + numTop + nb) { x, y ->
        if (surface != null) Backgrounds.draw(p, surface, x, y, x + bw, y + bh, mode)
        val cx = x + ox
        val cy = y + oy
        if (unitText != null && cs.unit == "inline") {
            p.text(numText, cx, cy + numTop, nt, num.color)
            p.text(unitText, cx + nw + cs.unitGap, cy + unitTop, ut, unit.color)
        } else {
            // Под числом: выравнивание столбика — по роли counter.
            fun at(width: Float) = when (num.align) {
                Align.START -> cx
                Align.END -> cx + cw - width
                Align.CENTER -> cx + (cw - width) / 2f
            }
            p.text(numText, at(nw), cy + numTop, nt, num.color)
            if (unitText != null) p.text(unitText, at(uw), cy + unitTop, ut, unit.color)
        }
    }
}

// ---------- Иконки ----------

/** Иконка пары по icons стиля: перемена, другой день, тип пары. */
fun Frame.lessonIcon(): String {
    val icons = th.icons
    when {
        d.phase == Phase.BREAK || d.phase == Phase.WINDOW -> return icons.slots["break"] ?: "coffee"
        nextDay || f == null -> return icons.slots["day"] ?: "calendar"
    }
    val type = f?.typeShort
    icons.byType.entries.firstOrNull { type?.startsWith(it.key) == true }?.let { return it.value }
    return icons.slots["lesson"] ?: "book"
}

/** Имя иконки слота стиля (window, document…) или null. */
fun Frame.iconFor(slot: String): String? = th.icons.slots[slot]

/** Рисует иконку [name] в квадрате [size] с левым верхним углом ([x], [y]). Векторные — цветом [color]. */
fun Frame.icon(name: String, x: Float, y: Float, size: Float, color: Int = th.icons.color) {
    val def = th.icons.defs[name] ?: return
    if (def.pixels.isNotEmpty()) {
        val k = size / def.grid
        for (px in def.pixels) {
            val n = px.take(4).map { (it as JsonPrimitive).content.toFloat().toInt() }
            val c = ColorExpr.parseHex((px[4] as JsonPrimitive).content) ?: color
            p.rect(x + n[0] * k, y + n[1] * k, x + (n[0] + n[2]) * k, y + (n[1] + n[3]) * k, c)
        }
        return
    }
    if (def.fill) {
        p.canvas.withSave {
            translate(x, y)
            scale(size / def.viewBox, size / def.viewBox)
            p.fill.shader = null
            p.fill.color = color
            for (data in def.paths) drawPath(PathParser.createPathFromPathData(data), p.fill)
        }
        return
    }
    p.canvas.withSave {
        translate(x, y)
        scale(size / def.viewBox, size / def.viewBox)
        p.stroke.shader = null
        p.stroke.color = color
        p.stroke.strokeWidth = def.stroke
        p.stroke.strokeCap = Paint.Cap.ROUND
        p.stroke.strokeJoin = Paint.Join.ROUND
        for (data in def.paths) drawPath(PathParser.createPathFromPathData(data), p.stroke)
        p.stroke.strokeCap = Paint.Cap.BUTT
        p.stroke.strokeJoin = Paint.Join.MITER
    }
}

// ---------- Список пар дня (большой виджет) ----------

/**
 * Список пар дня под карточкой. Подстраивается под место: если хватает — строки в две строчки
 * (время окончания, тип, аудитория), затем растягивается до [AgendaStyle.maxGrow] — строки выше,
 * шрифт крупнее. Если места мало — сначала пропадают прошедшие пары, потом последние («ещё N»).
 */
fun Frame.agenda(top: Float, bottom: Float, left: Float = l, right: Float = r) {
    val lines = d.day
    val avail = bottom - top
    if (lines.isEmpty() || avail < 24f) return
    val a = th.agenda
    val baseHead = if (th.hasRole("agendaHeader")) role("agendaHeader").typo
    else role("header").typo.let { it.copy(size = min(it.size, a.headerSize), lineHeight = 1.25f) }
    val baseRow = if (th.hasRole("agendaRow")) role("agendaRow").typo
    else role("next").typo.let { it.copy(size = min(it.size, a.rowSize), lineHeight = 1.25f) }
    val baseSub = baseRow.copy(size = baseRow.size * 0.9f)

    val headH0 = p.lineHeight(baseHead) + 6f
    val single0 = max(a.rowHeight, p.lineHeight(baseRow) + 6f)
    val double0 = p.lineHeight(baseRow) + p.lineHeight(baseSub) + 10f
    val n = lines.size
    val twoLine = a.twoLine && headH0 + n * double0 <= avail
    val row0 = if (twoLine) double0 else single0

    var shown: List<DayLine> = lines
    var hidden = 0
    val fit = floor((avail - headH0) / row0).toInt()
    if (fit <= 0) return
    if (n > fit) {
        val firstLive = lines.indexOfFirst { it.status != LineStatus.PAST }.let { if (it < 0) n else it }
        shown = lines.drop(min(firstLive, n - fit))
        if (shown.size > fit) {
            hidden = shown.size - (fit - 1)
            shown = shown.take(fit - 1)
        }
    }
    val rows = shown.size + if (hidden > 0) 1 else 0
    val grow = (avail / (headH0 + rows * row0)).coerceIn(1f, max(1f, a.maxGrow))
    val k = 1f + (grow - 1f) * a.fontGrow
    val head = baseHead.sized(baseHead.size * k)
    val rowT = baseRow.sized(baseRow.size * k)
    val subT = baseSub.sized(baseSub.size * k)
    val headH = headH0 * grow
    val rowH = row0 * grow
    val pastAlpha = (a.pastAlpha * 255f).roundToInt().coerceIn(0, 255) shl 24

    var y = top
    val header = sv("agendaHeader", "pairs" to s("pairs", "n" to "$n"))
    p.text(header, left, y + (headH - 6f * grow - p.lineHeight(head)).coerceAtLeast(0f), head, a.muted, maxWidth = right - left)
    y += headH
    val timeW = p.width("00:00", rowT) + 10f * k
    val markerW = 12f * k
    val textL = left + markerW + timeW
    for (line in shown) {
        val past = line.status == LineStatus.PAST
        val text = if (past) (a.text and 0x00FFFFFF) or pastAlpha else a.text
        val muted = if (past) (a.muted and 0x00FFFFFF) or pastAlpha else a.muted
        val info = line.info
        val vars = arrayOf(
            "title" to info.title, "type" to info.type, "typeShort" to info.typeShort, "room" to info.room,
            "subgroup" to info.subgroupNote, "start" to fmt(info.startMinute), "end" to fmt(info.endMinute),
            "num" to info.number?.toString(),
        )
        val lh = p.lineHeight(rowT)
        val ty = if (twoLine) y + (rowH - lh - p.lineHeight(subT)) / 2f else y + (rowH - lh) / 2f
        if (line.status == LineStatus.NOW) {
            val marker = a.marker
            if (marker != null) p.text(marker, left, ty, rowT, a.accent) else p.circle(left + 3f * k, ty + lh / 2f, 3f * k, a.accent)
        }
        p.text(fmt(info.startMinute), left + markerW, ty, rowT, muted)
        if (twoLine) {
            p.text(sv("agendaTitle", *vars), textL, ty, rowT, text, maxWidth = right - textL)
            val sy = ty + lh
            p.text(fmt(info.endMinute), left + markerW, sy, subT, muted)
            p.text(sv("agendaSub", *vars), textL, sy, subT, muted, maxWidth = right - textL)
        } else {
            // На узком виджете аудитория уступает место названию.
            val room = info.room?.takeIf { right - textL - min(p.width(it, rowT) + 8f, (right - left) / 3f) >= (right - left) * 0.45f }
            val roomW = room?.let { p.width(it, rowT) + 8f } ?: 0f
            room?.let { p.text(it, right, ty, rowT, muted, Align.END, maxWidth = (right - left) / 3f) }
            p.text(sv("agendaRow", *vars), textL, ty, rowT, text, maxWidth = right - textL - min(roomW, (right - left) / 3f))
        }
        y += rowH
    }
    if (hidden > 0) {
        p.text(sv("agendaMore", "n" to "$hidden"), textL, y + (rowH - p.lineHeight(rowT)) / 2f, rowT, a.muted, maxWidth = right - textL)
    }
}
