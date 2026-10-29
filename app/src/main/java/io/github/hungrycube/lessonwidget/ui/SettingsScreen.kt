package io.github.hungrycube.lessonwidget.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.BuildConfig
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.data.ReleaseInfo
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.UpdateChecker
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.data.humanMessage
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import io.github.hungrycube.lessonwidget.widget.Widgets
import kotlinx.coroutines.launch

/** Данные для превью стилей: реальное расписание группы, а если его нет — момент из макета. */
fun previewDisplay(context: android.content.Context, group: String?, subgroup: Int?, now: Long): WidgetDisplay {
    val d = Widgets.displayFor(context, group, subgroup, now, Settings(context).shortTitles)
    return if (group == null || d.phase == Phase.NO_DATA) Demo.display(now) else d
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun SubgroupChips(selected: Int?, onSelect: (Int?) -> Unit, withInherit: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = (if (withInherit) listOf<Int?>(null) else emptyList()) + listOf(0, 1, 2, 3)
        for (n in options) {
            FilterChip(
                selected = selected == n,
                onClick = { onSelect(n) },
                label = { Text(when (n) { null -> "Как в приложении"; 0 -> "Все"; else -> "$n" }) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeGroup: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val group = settings.defaultGroup
    var subgroup by remember { mutableIntStateOf(settings.subgroup) }
    var style by remember { mutableStateOf(settings.defaultStyle) }
    var short by remember { mutableStateOf(settings.shortTitles) }
    // Стиль со своими настройками или фото, который настраивают как стиль по умолчанию.
    var setupFor by remember { mutableStateOf<String?>(null) }
    var defaults by remember { mutableStateOf(StyleDraft(settings.defaultOptions, settings.defaultPhoto)) }

    fun widgetsChanged() {
        scope.launch { WidgetUpdater.updateAll(context) }
    }

    setupFor?.let { id ->
        val draftFile = remember { WidgetPhotos.draft(context, WidgetPhotos.DEFAULT) }
        StyleSetupScreen(
            style = Styles.byId(context, id),
            initial = defaults,
            currentPhoto = WidgetPhotos.file(context, WidgetPhotos.DEFAULT).takeIf { it.isFile },
            draftFile = draftFile,
            widthDp = 316f,
            heightDp = 168f,
            display = { now -> previewDisplay(context, group, subgroup.takeIf { it > 0 }, now) },
            onDone = { d ->
                if (d.newPhoto?.isFile == true) WidgetPhotos.commitDraft(context, WidgetPhotos.DEFAULT, WidgetPhotos.DEFAULT)
                settings.defaultOptions = d.options
                settings.defaultPhoto = d.frame
                defaults = d.copy(newPhoto = null)
                style = id
                settings.defaultStyle = id
                setupFor = null
                widgetsChanged()
            },
            onBack = {
                draftFile.delete()
                setupFor = null
            },
        )
        return
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Группа") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group ?: "не выбрана", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onChangeGroup) { Text("Изменить") }
                }
            }
            Section("Подгруппа") {
                Text(
                    "Пары другой подгруппы не показываются. Отдельному виджету можно выбрать свою.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SubgroupChips(subgroup, onSelect = { n ->
                    subgroup = n ?: 0
                    settings.subgroup = subgroup
                    widgetsChanged()
                })
            }
            Section("Виджет") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Короткие названия предметов")
                        Text("«Матанализ» вместо «Математический анализ»", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = short, onCheckedChange = {
                        short = it
                        settings.shortTitles = it
                        widgetsChanged()
                    })
                }
                Text(
                    "Стиль по умолчанию — для виджетов, где стиль не выбран отдельно. " +
                        "Чтобы изменить один виджет, нажмите на него и затем на карандаш вверху.",
                    style = MaterialTheme.typography.bodySmall,
                )
                StylePicker(
                    selected = style,
                    display = { now -> previewDisplay(context, group, subgroup.takeIf { it > 0 }, now) },
                    onSelect = {
                        val st = Styles.byId(context, it)
                        if (st.usesPhoto || st.options.isNotEmpty()) {
                            // Стиль станет стилем по умолчанию после «Готово» на экране настройки.
                            setupFor = it
                        } else {
                            style = it
                            settings.defaultStyle = it
                            widgetsChanged()
                        }
                    },
                    customFor = { st ->
                        if (!st.usesPhoto && st.options.isEmpty()) {
                            null
                        } else {
                            val photo = if (st.usesPhoto) WidgetPhotos.input(WidgetPhotos.file(context, WidgetPhotos.DEFAULT), defaults.frame) else null
                            StyleCustom(defaults.options, photo)
                        }
                    },
                    refresh = defaults,
                )
                val selected = Styles.byId(context, style)
                if (selected.usesPhoto || selected.options.isNotEmpty()) {
                    OutlinedButton(onClick = { setupFor = style }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected.usesPhoto) "Фото и настройки стиля" else "Настройки стиля")
                    }
                    Text(
                        "Новый виджет сразу получит это фото и настройки, их можно поменять у каждого виджета отдельно.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Section("Виджет отстаёт?") { WidgetLagSection() }
            Section("Обновления") { UpdateSection() }
            Section("Автор и отзывы") {
                Text("Сделано Тёмой — @jackkidney", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Если виджет где-то ошибается, чего-то не хватает или есть идея для нового стиля — " +
                        "напишите в Telegram. Буду рад любому отзыву, даже короткому.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/jackkidney")))
                }) { Text("Написать в Telegram") }
            }
            Section("О приложении") {
                Text(
                    "Неофициальное приложение. Не связано с НГТУ НЭТИ и не одобрено университетом. " +
                        "Расписание берётся с публичной страницы nstu.ru раз в сутки. Если что-то расходится — верно расписание на сайте.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Приложение ничего не собирает и не отправляет, кроме запросов к сайту НГТУ и проверки версий на GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Шрифты: Manrope, Inter Tight, JetBrains Mono, Roboto Flex, Lora, Doto, Montserrat, Open Sans, Unbounded, " +
                        "IBM Plex Mono — SIL Open Font License. Иконки — Lucide (ISC).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")))
                }) { Text("Проект на GitHub") }
            }
            Section("Отчёт об ошибке") { CrashReportSection() }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    Text("Установлена версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
    Button(
        enabled = state != UpdateState.Checking,
        onClick = {
            state = UpdateState.Checking
            scope.launch {
                state = try {
                    val rel = UpdateChecker.latest()
                    Settings(context).lastUpdateCheck = System.currentTimeMillis()
                    if (UpdateChecker.isNewer(rel.version)) UpdateState.Available(rel) else UpdateState.UpToDate
                } catch (e: Exception) {
                    UpdateState.Failed(e.humanMessage())
                }
            }
        },
    ) { Text(if (state == UpdateState.Checking) "Проверяю…" else "Проверить обновления") }
    when (val s = state) {
        UpdateState.UpToDate -> Text("У вас последняя версия.")
        is UpdateState.Failed -> Text("Не удалось проверить: ${s.message}", color = MaterialTheme.colorScheme.error)
        is UpdateState.Available -> {
            Text("Доступна версия ${s.release.version}", fontWeight = FontWeight.Bold)
            s.release.body?.takeIf { it.isNotBlank() }?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val url = s.release.apk?.downloadUrl ?: s.release.pageUrl
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text("Скачать обновление") }
            Text(
                "Откройте скачанный файл и нажмите «Обновить» — настройки и виджеты сохранятся.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> Unit
    }
}
