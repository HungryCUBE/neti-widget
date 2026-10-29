package io.github.hungrycube.lessonwidget.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.data.PhotoFrame
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.PhotoInput
import io.github.hungrycube.lessonwidget.render.RenderMode
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.WidgetRenderer
import io.github.hungrycube.lessonwidget.render.WidgetStyle
import io.github.hungrycube.lessonwidget.render.theme.OptionSpec
import io.github.hungrycube.lessonwidget.render.theme.ThemeLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Что выбрано на экране настройки стиля: значения настроек, кадр фото и, если выбрали новое фото, — его черновик. */
data class StyleDraft(val options: Map<String, String>, val frame: PhotoFrame, val newPhoto: File? = null)

/**
 * Настройка стиля для виджета (или для виджетов по умолчанию): своё фото с кадром и настройки стиля из его файла.
 * Превью рисует тот же код, что и виджет, под пропорции виджета [widthDp]×[heightDp].
 * [currentPhoto] — фото, которое уже есть; новое из галереи кладётся в [draftFile] и сохраняется только по «Готово».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSetupScreen(
    style: WidgetStyle,
    initial: StyleDraft,
    currentPhoto: File?,
    draftFile: File,
    widthDp: Float,
    heightDp: Float,
    display: (Long) -> WidgetDisplay,
    onDone: (StyleDraft) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
    val preview by produceState<ImageBitmap?>(null, bitmap, options, frame, now) {
        value = withContext(Dispatchers.Default) {
            val photo = bitmap?.let { PhotoInput(it, frame.focusX, frame.focusY, frame.flip) }
            runCatching {
                WidgetRenderer(context).render(
                    style, display(now), widthDp, heightDp, RenderMode.WIDGET,
                    nowMillis = now, density = 2f, custom = StyleCustom(options, photo),
                ).bitmap.asImageBitmap()
            }.getOrNull()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { WidgetPhotos.import(context, uri, draftFile) }.getOrDefault(false) }
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
    fun done() = onDone(StyleDraft(options, frame, newPhoto))

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(style.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = { TextButton(onClick = ::done) { Text("Готово") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF2B2E36)),
                contentAlignment = Alignment.Center,
            ) {
                if (style.usesPhoto) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
                    }
                }
                val img = preview
                if (img != null) {
                    Image(
                        img,
                        contentDescription = "Превью виджета",
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 22.dp)
                            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                            .pointerInput(bitmap) {
                                val bmp = bitmap ?: return@pointerInput
                                detectDragGestures { change, amount ->
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
                    )
                } else {
                    CircularProgressIndicator()
                }
                if (style.usesPhoto && bitmap != null) {
                    Text(
                        "Двигайте фото пальцем, чтобы выбрать кадр",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    )
                }
            }
            if (style.usesPhoto) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !importing,
                        modifier = Modifier.weight(1f).height(46.dp),
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) {
                        if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Выбрать из галереи")
                    }
                    FilledTonalIconButton(
                        enabled = bitmap != null,
                        modifier = Modifier.size(46.dp).semantics { contentDescription = "Отразить фото" },
                        onClick = { frame = frame.copy(flip = !frame.flip, focusX = 1f - frame.focusX) },
                    ) { Text("⇋", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (bitmap == null && !importing) {
                    Text(
                        "Фото ещё не выбрано — пока виджет показывает пример. Фото хранится только на телефоне, в уменьшенной копии.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            for ((name, o) in style.options) {
                OptionRow(o, options[name].orEmpty()) { v -> options = options + (name to v) }
            }
            Button(onClick = ::done, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Одна настройка стиля: выбор из нескольких, число ползунком или переключатель. */
@Composable
private fun OptionRow(o: OptionSpec, value: String, onChange: (String) -> Unit) {
    val label = MaterialTheme.typography.labelLarge
    when (o.type) {
        "choice" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(o.label, style = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
            ) {
                for (v in o.values) {
                    val selected = v == value
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onChange(v) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            o.labels[v] ?: v,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        "range" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val v = value.toFloatOrNull() ?: o.min
            Row {
                Text(o.label, style = label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${v.roundToInt()}${o.unit}", style = label)
            }
            Slider(value = v, onValueChange = { onChange(it.roundToInt().toString()) }, valueRange = o.min..o.max)
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                o.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = value == "on", onCheckedChange = { onChange(if (it) "on" else "off") })
        }
    }
}
