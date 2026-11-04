package io.github.hungrycube.lessonwidget.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.BuildConfig
import io.github.hungrycube.lessonwidget.core.display.Phase
import io.github.hungrycube.lessonwidget.core.display.WidgetDisplay
import io.github.hungrycube.lessonwidget.core.groups.Faculties
import io.github.hungrycube.lessonwidget.core.groups.describe
import io.github.hungrycube.lessonwidget.core.groups.subgroupCount
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.ReleaseInfo
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.UpdateChecker
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.data.humanMessage
import io.github.hungrycube.lessonwidget.render.Demo
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.ui.design.AppCard
import io.github.hungrycube.lessonwidget.ui.design.AppSwitch
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.Hint
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.ScreenHeader
import io.github.hungrycube.lessonwidget.ui.design.Segmented
import io.github.hungrycube.lessonwidget.ui.design.SettingsSection
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import io.github.hungrycube.lessonwidget.widget.Widgets
import kotlinx.coroutines.launch

/** Данные для превью стилей: реальное расписание группы, а если его нет — момент из макета. */
fun previewDisplay(context: Context, group: String?, subgroup: Int?, now: Long): WidgetDisplay {
    val d = Widgets.displayFor(context, group, subgroup, now, Settings(context).shortTitles)
    return if (group == null || d.phase == Phase.NO_DATA) Demo.display(now) else d
}

/** Подпись выбранной группы: «ФМА · бакалавриат, специалитет» из списка групп или факультет из заголовка расписания. */
fun groupSubtitle(context: Context, group: String?): String? {
    if (group == null) return null
    GroupsRepository(context).find(group)?.describe()?.takeIf { it.isNotEmpty() }?.let { return it }
    return Faculties.fromTitle(ScheduleRepository(context).load(group)?.title, group)
}

/** Шрифты из лицензий в assets — то, что реально лежит в приложении. */
private fun usedFonts(context: Context): String {
    val pretty = mapOf("inter_tight" to "Inter Tight", "jetbrains_mono" to "JetBrains Mono", "roboto_flex" to "Roboto Flex", "open_sans" to "Open Sans")
    return runCatching { context.assets.list("licenses")?.toList() }.getOrNull().orEmpty()
        .filter { it.startsWith("OFL-") }
        .map { it.removePrefix("OFL-").substringBefore('.') }
        .sorted()
        .joinToString(", ") { pretty[it] ?: it.replaceFirstChar { ch -> ch.uppercaseChar() } }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeGroup: () -> Unit) {
    val context = LocalContext.current
    val c = appColors
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val group = settings.defaultGroup
    val schedule = remember(group) { group?.let { ScheduleRepository(context).load(it) } }
    val subtitle = remember(group) { groupSubtitle(context, group) }
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
            // Всё применяется сразу: стиль становится стилем по умолчанию, фото и настройки сохраняются.
            onChange = { d ->
                if (d.newPhoto?.isFile == true) WidgetPhotos.commitDraft(context, WidgetPhotos.DEFAULT, WidgetPhotos.DEFAULT)
                settings.defaultOptions = d.options
                settings.defaultPhoto = d.frame
                defaults = d.copy(newPhoto = null)
                style = id
                settings.defaultStyle = id
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
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader("Настройки", onBack, divider = scroll.value > 0)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(start = Dims.side, end = Dims.side, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection("Группа") {
                AppCard(padding = PaddingValues(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(group ?: "Не выбрана", style = Type.groupCode, color = c.text)
                            Text(subtitle ?: if (group == null) "Выберите свою группу" else "НГТУ НЭТИ", style = Type.caption, color = c.sec)
                        }
                        PillButton(
                            if (group == null) "Выбрать" else "Изменить",
                            onClick = onChangeGroup,
                            kind = if (group == null) PillKind.Primary else PillKind.Soft,
                        )
                    }
                }
            }
            // Подгруппы — по данным выбранной группы; если пары не делятся, секции нет.
            val count = maxOf(schedule?.subgroupCount() ?: 0, subgroup)
            if (count > 0) {
                SettingsSection("Подгруппа") {
                    Segmented(
                        options = listOf(0) + (1..count).toList(),
                        selected = subgroup,
                        label = { if (it == 0) "Все" else "$it" },
                        onSelect = { n ->
                            subgroup = n
                            settings.subgroup = n
                            widgetsChanged()
                        },
                    )
                    Hint("Пары другой подгруппы не показываются. Отдельному виджету можно выбрать свою.")
                }
            }
            SettingsSection("Тема приложения") {
                Segmented(
                    options = listOf("auto", "light", "dark"),
                    selected = ThemeChoice.mode,
                    label = {
                        when (it) {
                            "light" -> "Светлая"
                            "dark" -> "Тёмная"
                            else -> "Авто"
                        }
                    },
                    onSelect = { ThemeChoice.set(context, it) },
                )
                Hint("«Авто» — как в системе. На виджет не влияет, у него свой стиль.")
            }
            SettingsSection("Виджет") {
                AppCard(padding = PaddingValues(start = 18.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Короткие названия", style = Type.itemTitle, color = c.text)
                            Text("На виджете «Мат. анализ» вместо «Математический анализ», в приложении — полные", style = Type.caption, color = c.sec)
                        }
                        AppSwitch(short, onCheckedChange = {
                            short = it
                            settings.shortTitles = it
                            widgetsChanged()
                        }, contentDescription = "Короткие названия предметов")
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Стиль по умолчанию", style = Type.itemTitle, color = c.text)
                    Hint("Для виджетов без своего стиля. Чтобы сменить стиль одного виджета, нажмите на него, затем на карандаш.")
                }
                StylePicker(
                    selected = style,
                    display = { now -> previewDisplay(context, group, subgroup.takeIf { it > 0 }, now) },
                    onSelect = {
                        val st = Styles.byId(context, it)
                        if (st.usesPhoto || st.options.isNotEmpty()) {
                            // Стиль со своими настройками или фото — сразу на экран настройки, там он и применяется.
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
                    PillButton(
                        if (selected.usesPhoto) "Фото и настройки стиля" else "Настройки стиля",
                        onClick = { setupFor = style },
                        kind = PillKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("Новый виджет сразу получит это фото и настройки, их можно поменять у каждого виджета отдельно.")
                }
            }
            SettingsSection("Обновления") { UpdateCard() }
            SettingsSection("Автор и отзывы") { AuthorCard() }
            SettingsSection("Виджет отстаёт?") { WidgetLagCard() }
            SettingsSection("Отчёт об ошибке") { CrashReportCard() }
            SettingsSection("О приложении") { AboutCard() }
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
private fun UpdateCard() {
    val context = LocalContext.current
    val c = appColors
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    fun check() {
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
    }
    AppCard(padding = PaddingValues(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Версия ${BuildConfig.VERSION_NAME}", style = Type.itemTitle, color = c.text)
                val (text, color) = when (val s = state) {
                    UpdateState.UpToDate -> "Установлена · это последняя версия" to c.sec
                    is UpdateState.Available -> "Доступна ${s.release.version}" to c.text
                    is UpdateState.Failed -> "Не удалось проверить: ${s.message}" to c.warn
                    else -> "Установлена" to c.sec
                }
                Text(text, style = Type.caption, color = color)
            }
            val s = state
            if (s is UpdateState.Available) {
                PillButton("Скачать", onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.release.apk?.downloadUrl ?: s.release.pageUrl)))
                }, kind = PillKind.Primary)
            } else {
                PillButton("Проверить", onClick = ::check, loading = s == UpdateState.Checking)
            }
        }
        val s = state
        if (s is UpdateState.Available) {
            s.release.body?.takeIf { it.isNotBlank() }?.let {
                Text(it.trim(), style = Type.small, color = c.sec, maxLines = 12, modifier = Modifier.padding(top = 10.dp, end = 6.dp))
            }
            Hint("Откройте скачанный файл и нажмите «Обновить» — настройки и виджеты сохранятся.", Modifier.padding(top = 8.dp, end = 6.dp))
        }
    }
}

@Composable
private fun AuthorCard() {
    val context = LocalContext.current
    val c = appColors
    AppCard(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.soft), contentAlignment = Alignment.Center) {
                Text("Т", style = Type.avatar, color = c.text)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Сделано Тёмой", style = Type.itemTitle, color = c.text)
                Text("@jackkidney", style = Type.caption, color = c.sec)
            }
        }
        Text(
            "Если виджет где-то ошибается, чего-то не хватает или есть идея для нового стиля — напишите в Telegram. " +
                "Буду рад любому отзыву, даже короткому.",
            style = Type.detail,
            color = c.sec,
        )
        PillButton(
            "Написать в Telegram",
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/jackkidney"))) },
            kind = PillKind.Primary,
            icon = LucideIcons.send,
            height = Dims.pillTall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val c = appColors
    val fonts = remember { usedFonts(context) }
    AppCard(spacing = 8.dp) {
        Text(
            "Неофициальное приложение. Не связано с НГТУ НЭТИ и не одобрено университетом. " +
                "Расписание берётся с публичной страницы nstu.ru раз в сутки. Если что-то расходится — верно расписание на сайте.",
            style = Type.detail,
            color = c.text,
        )
        Text(
            "Приложение ничего не собирает и не отправляет, кроме запросов к сайту НГТУ и проверки версий на GitHub.",
            style = Type.small,
            color = c.ter,
        )
        Text("Шрифты: $fonts — SIL Open Font License. Иконки — Lucide (ISC).", style = Type.small, color = c.ter)
        PillButton(
            "Проект на GitHub",
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))) },
            kind = PillKind.Outline,
            trailingIcon = LucideIcons.external,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
