package io.github.hungrycube.lessonwidget.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.PhotoFrame
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.WidgetStyle
import io.github.hungrycube.lessonwidget.render.theme.OptionSpec
import io.github.hungrycube.lessonwidget.render.theme.ThemeLoader
import io.github.hungrycube.lessonwidget.ui.design.AppSwitch
import io.github.hungrycube.lessonwidget.ui.design.CircleButton
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.Hint
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.ScreenHeader
import io.github.hungrycube.lessonwidget.ui.design.SectionLabel
import io.github.hungrycube.lessonwidget.ui.design.Segmented
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Что выбрано на экране настройки стиля: значения настроек, кадр фото и, если выбрали новое фото, — его черновик. */
data class StyleDraft(val options: Map<String, String>, val frame: PhotoFrame, val newPhoto: File? = null)

/**
 * Настройка стиля для виджета (или для виджетов по умолчанию): своё фото с кадром и настройки стиля из его файла.
 * Превью рисует тот же код, что и виджет, под пропорции виджета [widthDp]×[heightDp].
 * [currentPhoto] — фото, которое уже есть; новое из галереи кладётся в [draftFile].
 * Кнопки «Готово» нет: каждое изменение сразу уходит в [onChange] (кадр — когда палец отпущен), «Назад» просто закрывает.
 */
@Composable
fun StyleSetupScreen(
    style: WidgetStyle,
    initial: StyleDraft,
    currentPhoto: File?,
    draftFile: File,
    widthDp: Float,
    heightDp: Float,
    display: (Long) -> WidgetDisplay,
    onChange: (StyleDraft) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val c = appColors
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    var options by remember { mutableStateOf(ThemeLoader.optionValues(style.options, initial.options)) }
    var frame by remember { mutableStateOf(initial.frame) }
    var newPhoto by remember { mutableStateOf(initial.newPhoto?.takeIf { it.isFile }) }
    var version by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val aspect = widthDp / heightDp

    val photoFile = newPhoto ?: currentPhoto
    val bitmap by produceState<Bitmap?>(null, photoFile, version) {
        value = withContext(Dispatchers.IO) { WidgetPhotos.load(photoFile) }
    }
    var dragging by remember { mutableStateOf(false) }
    fun renderWith(photo: PhotoInput?): ImageBitmap? = runCatching {
        WidgetRenderer(context).render(
            style, display(now), widthDp, heightDp, RenderMode.WIDGET,
            nowMillis = now, density = 2f, custom = StyleCustom(options, photo),
        ).bitmap.asImageBitmap()
    }.getOrNull()
    // Полное превью и кадр, для которого оно нарисовано. Пока палец двигает фото, не перерисовывается:
    // целиком виджет рисуется сотни миллисекунд, и фото шло бы за пальцем рывками.
    val preview by produceState<Pair<PhotoFrame, ImageBitmap?>?>(null, bitmap, options, frame, now, dragging) {
        if (dragging) return@produceState
        val f = frame
        value = f to withContext(Dispatchers.Default) { renderWith(bitmap?.let { PhotoInput(it, f.focusX, f.focusY, f.flip) }) }
    }
    // Текст и подложка без самого фото — поверх фото, которое во время перетаскивания рисуется прямо на экране.
    val overlay by produceState<ImageBitmap?>(null, bitmap, options, now) {
        val f = frame
        value = withContext(Dispatchers.Default) { bitmap?.let { renderWith(PhotoInput(it, f.focusX, f.focusY, f.flip, visible = false)) } }
    }
    val photoImage = remember(bitmap) { bitmap?.asImageBitmap() }
    val cardRadius = remember(style) { style.spec(emptyList()).background.radius }
    // Пока палец двигает фото или новое превью ещё рисуется — фото на экране и готовые текст с подложкой поверх.
    val live = style.usesPhoto && photoImage != null && overlay != null && (dragging || preview?.first != frame)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { WidgetPhotos.import(context, uri, draftFile) }
                    .onFailure { CrashLog.record(context, "Своё фото: сохранение", it) }
                    .getOrDefault(false)
            }
            importing = false
            if (ok) {
                newPhoto = draftFile
                version++
                frame = PhotoFrame(flip = frame.flip)
            } else {
                error = "Не получилось открыть это фото. Попробуйте другое."
            }
        }
    }
    // Изменения применяются сразу; пока палец двигает фото — не дёргаем виджет на каждый пиксель.
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(options, frame, newPhoto, version, dragging) {
        if (dragging) return@LaunchedEffect
        delay(250)
        change(StyleDraft(options, frame, newPhoto))
    }
    fun close() {
        onChange(StyleDraft(options, frame, newPhoto))
        onBack()
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    // Пока фото нет, превью — заглушка «Нажмите, чтобы выбрать фото»: нажатие в любом месте открывает галерею.
    val tapToPick = style.usesPhoto && bitmap == null && !importing

    BackHandler(onBack = ::close)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader(style.title, ::close, divider = scroll.value > 0)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(start = Dims.side, end = Dims.side, top = 6.dp, bottom = 28.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.previewBackdrop)
                    .then(if (tapToPick) Modifier.clickable(onClickLabel = "Выбрать фото", onClick = ::pickPhoto) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (style.usesPhoto) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
                    }
                }
                Box(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 22.dp)
                        .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                        .pointerInput(bitmap) {
                            val bmp = bitmap ?: return@pointerInput
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, amount ->
                                change.consume()
                                // Палец тянет фото: кадр сдвигается в обратную сторону, в пикселях фото.
                                val input = PhotoInput(bmp, frame.focusX, frame.focusY, frame.flip)
                                val crop = input.crop(aspect)
                                val kx = crop.width().toFloat() / size.width / bmp.width
                                val ky = crop.height().toFloat() / size.height / bmp.height
                                val (x, y) = input.clampFocus(aspect, frame.focusX - amount.x * kx, frame.focusY - amount.y * ky)
                                frame = frame.copy(focusX = x, focusY = y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val full = preview?.second
                    if (live) {
                        val img = photoImage!!
                        val bmp = bitmap!!
                        Canvas(Modifier.fillMaxSize()) {
                            // Тот же кадр, что посчитает виджет, со скруглением карточки.
                            val crop = PhotoInput(bmp, frame.focusX, frame.focusY, frame.flip).crop(aspect)
                            val r = cardRadius * size.width / widthDp
                            clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }) {
                                scale(if (frame.flip) -1f else 1f, 1f) {
                                    drawImage(
                                        img,
                                        srcOffset = IntOffset(crop.left, crop.top),
                                        srcSize = IntSize(crop.width(), crop.height()),
                                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    )
                                }
                            }
                        }
                        overlay?.let { Image(it, contentDescription = "Превью виджета", modifier = Modifier.fillMaxSize()) }
                    } else if (full != null) {
                        Image(full, contentDescription = "Превью виджета", modifier = Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                if (style.usesPhoto && bitmap != null) {
                    Text(
                        "Двигайте фото пальцем, чтобы выбрать кадр",
                        style = Type.badge.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    )
                }
            }
            if (style.usesPhoto) {
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        "Выбрать из галереи",
                        onClick = ::pickPhoto,
                        kind = PillKind.Primary,
                        icon = LucideIcons.image,
                        height = Dims.pillTall,
                        loading = importing,
                        modifier = Modifier.weight(1f),
                    )
                    CircleButton(
                        LucideIcons.flip,
                        "Отразить фото",
                        onClick = { frame = frame.copy(flip = !frame.flip, focusX = 1f - frame.focusX) },
                        enabled = bitmap != null,
                        size = Dims.pillTall,
                        iconSize = 18.dp,
                    )
                }
                error?.let { Text(it, style = Type.caption, color = c.warn, modifier = Modifier.padding(top = 8.dp)) }
                if (bitmap == null && !importing) {
                    Hint(
                        "Фото ещё не выбрано — нажмите на превью или «Выбрать из галереи». Фото хранится только на телефоне, в уменьшенной копии.",
                        Modifier.padding(top = 10.dp),
                    )
                }
            }
            if (style.options.isNotEmpty()) {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    for ((name, o) in style.options) {
                        OptionRow(o, options[name].orEmpty()) { v -> options = options + (name to v) }
                    }
                }
            }
            Hint("Изменения применяются сразу.", Modifier.padding(top = 20.dp))
        }
    }
}

/** Одна настройка стиля: выбор из нескольких, число ползунком или переключатель. */
@Composable
private fun OptionRow(o: OptionSpec, value: String, onChange: (String) -> Unit) {
    val c = appColors
    when (o.type) {
        "choice" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(o.label)
            Segmented(
                options = o.values,
                selected = value,
                label = { o.labels[it] ?: it },
                onSelect = onChange,
                height = 38.dp,
                textSize = 13.sp,
            )
        }
        "range" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val v = value.toFloatOrNull() ?: o.min
            Row {
                Text(o.label, style = Type.label, color = c.sec, modifier = Modifier.weight(1f))
                Text("${v.roundToInt()}${o.unit}", style = Type.label, color = c.text)
            }
            Slider(
                value = v,
                onValueChange = { onChange(it.roundToInt().toString()) },
                valueRange = o.min..o.max,
                colors = SliderDefaults.colors(
                    thumbColor = c.primary,
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.clearBg,
                ),
            )
        }
        else -> Row(Modifier.heightIn(min = Dims.touch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(o.label, style = Type.button, color = c.text)
                o.hint?.let { Text(it, style = Type.small, color = c.sec) }
            }
            AppSwitch(value == "on", onCheckedChange = { onChange(if (it) "on" else "off") }, contentDescription = o.label)
        }
    }
}
