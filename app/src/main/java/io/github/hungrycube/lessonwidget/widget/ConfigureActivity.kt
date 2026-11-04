package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.groups.subgroupCount
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetConfig
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.ui.AppTheme
import io.github.hungrycube.lessonwidget.ui.GroupPickerScreen
import io.github.hungrycube.lessonwidget.ui.StyleDraft
import io.github.hungrycube.lessonwidget.ui.StylePicker
import io.github.hungrycube.lessonwidget.ui.StyleSetupScreen
import io.github.hungrycube.lessonwidget.ui.design.AppCard
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.Hint
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.ScreenHeader
import io.github.hungrycube.lessonwidget.ui.design.SectionLabel
import io.github.hungrycube.lessonwidget.ui.design.Segmented
import io.github.hungrycube.lessonwidget.ui.design.SelectMark
import io.github.hungrycube.lessonwidget.ui.design.SettingsSection
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import io.github.hungrycube.lessonwidget.ui.groupSubtitle
import io.github.hungrycube.lessonwidget.ui.previewDisplay
import io.github.hungrycube.lessonwidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Настройка виджета: при добавлении на рабочий стол и по карандашу в приложении. */
class ConfigureActivity : ComponentActivity() {

    companion object {
        /** Открыть сразу экран фото и настроек стиля (тап по виджету без фото). */
        const val EXTRA_STYLE_SETUP = "io.github.hungrycube.lessonwidget.STYLE_SETUP"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val setupOnly = intent?.getBooleanExtra(EXTRA_STYLE_SETUP, false) == true
        setContent { AppTheme { ConfigureScreen(appWidgetId, setupOnly, onApply = ::apply, onClose = ::close) } }
    }

    /** Хоть раз сохранили — виджет остаётся на рабочем столе. */
    private var applied = false

    /** Группа, расписание которой уже проверяли после выбора. */
    private var checkedGroup: String? = null

    private fun close() {
        // Выбранное, но не сохранённое фото не копится.
        if (!applied) WidgetPhotos.draft(applicationContext, "$appWidgetId").delete()
        finish()
    }

    /** Сохраняет настройки виджета и сразу перерисовывает его: кнопки «Готово» нет. */
    private fun apply(config: WidgetConfig, draft: StyleDraft) {
        val app = applicationContext
        val settings = Settings(app)
        val key = "$appWidgetId"
        val style = Styles.byId(app, config.style ?: settings.defaultStyle)
        try {
            // Своё фото остаётся, даже если выбрали другой стиль: можно вернуться к нему без повторного выбора.
            if (style.usesPhoto) {
                val own = WidgetPhotos.file(app, key)
                val default = WidgetPhotos.file(app, WidgetPhotos.DEFAULT)
                when {
                    draft.newPhoto?.isFile == true -> WidgetPhotos.commitDraft(app, key, key)
                    // Новый виджет со «Своим фото» по умолчанию получает копию этого фото сразу.
                    !own.isFile && default.isFile -> WidgetPhotos.copy(default, own)
                }
            }
        } catch (e: Exception) {
            CrashLog.record(app, "Своё фото виджета $appWidgetId", e)
        }
        WidgetConfigs(app).set(
            appWidgetId,
            config.copy(
                options = draft.options.takeIf { style.options.isNotEmpty() },
                photo = draft.frame.takeIf { style.usesPhoto },
            ),
        )
        if (settings.defaultGroup == null && config.group != null) settings.defaultGroup = config.group
        applied = true
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val id = appWidgetId
        val group = config.group ?: settings.defaultGroup
        val download = group != null && group != checkedGroup
        if (download) checkedGroup = group
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(app, id)
                if (!download || group == null) return@launch
                val repo = ScheduleRepository(app)
                if (repo.load(group) == null) {
                    try {
                        repo.refresh(group)
                    } catch (e: Exception) {
                        RefreshScheduler.refreshNow(app)
                    }
                }
                repo.deleteUnused(Widgets.groupsInUse(app))
                WidgetUpdater.update(app, id)
            } catch (e: Exception) {
                // Без этого ошибка в фоне уронила бы всё приложение.
                CrashLog.record(app, "Настройка виджета $id", e)
            }
        }
    }
}

@Composable
private fun ConfigureScreen(appWidgetId: Int, setupOnly: Boolean, onApply: (WidgetConfig, StyleDraft) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = appColors
    val settings = remember { Settings(context) }
    val existing = remember { WidgetConfigs(context).get(appWidgetId) }
    val appGroup = settings.defaultGroup
    var useAppGroup by remember { mutableStateOf(existing?.group == null && appGroup != null) }
    var ownGroup by remember { mutableStateOf(existing?.group) }
    var picking by remember { mutableStateOf(appGroup == null && existing?.group == null) }
    var style by remember { mutableStateOf(existing?.style ?: settings.defaultStyle) }
    var subgroup by remember { mutableStateOf(existing?.subgroup) }
    // Настройки стиля и кадр фото: свои у виджета, а у нового — как по умолчанию в приложении.
    var draft by remember {
        mutableStateOf(StyleDraft(existing?.options ?: settings.defaultOptions, existing?.photo ?: settings.defaultPhoto))
    }
    // С виджета без фото — сразу экран фото; «Назад» там закрывает настройку.
    var setup by remember { mutableStateOf(setupOnly) }
    val draftFile = remember { WidgetPhotos.draft(context, "$appWidgetId") }
    val currentPhoto = WidgetPhotos.fileFor(context, appWidgetId)
    val widgetSize = remember { widgetSizeDp(context, appWidgetId) }

    // Всё применяется сразу: группа, подгруппа и стиль — как только выбраны.
    fun config() = WidgetConfig(group = if (useAppGroup) null else ownGroup, style = style, subgroup = subgroup)
    val hasGroup = (if (useAppGroup) appGroup else ownGroup) != null
    LaunchedEffect(useAppGroup, ownGroup, style, subgroup) {
        if (hasGroup) onApply(config(), draft)
    }

    if (setup) {
        val st = Styles.byId(context, style)
        val g = if (useAppGroup) appGroup else ownGroup
        StyleSetupScreen(
            style = st,
            initial = draft,
            currentPhoto = currentPhoto,
            draftFile = draftFile,
            widthDp = widgetSize.first,
            heightDp = widgetSize.second,
            display = { now -> previewDisplay(context, g, (subgroup ?: settings.subgroup).takeIf { it > 0 }, now) },
            onChange = {
                draft = it
                if (hasGroup) onApply(config(), it)
            },
            onBack = { if (setupOnly) onClose() else setup = false },
        )
        return
    }

    if (picking) {
        val canBack = appGroup != null || ownGroup != null
        GroupPickerScreen(
            title = "Группа для виджета",
            current = if (useAppGroup) appGroup else ownGroup,
            onBack = { if (canBack) picking = false else onClose() },
            onPicked = {
                ownGroup = it
                useAppGroup = false
                picking = false
            },
        )
        return
    }

    val group = if (useAppGroup) appGroup else ownGroup
    val effectiveSubgroup = (subgroup ?: settings.subgroup).takeIf { it > 0 }
    val subgroups = remember(group) { group?.let { ScheduleRepository(context).load(it) }?.subgroupCount() ?: 0 }
    val scroll = rememberScrollState()
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader(
            "Виджет расписания",
            onBack = onClose,
            divider = scroll.value > 0,
            backIcon = LucideIcons.close,
            backDescription = "Закрыть",
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(start = Dims.side, end = Dims.side, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection("Группа") {
                AppCard(padding = PaddingValues(0.dp)) {
                    if (appGroup != null) {
                        ChoiceRow(
                            title = "Как в приложении",
                            subtitle = listOfNotNull(appGroup, groupSubtitle(context, appGroup)).joinToString(" · "),
                            selected = useAppGroup,
                            divider = false,
                            onClick = { useAppGroup = true },
                        )
                    }
                    ChoiceRow(
                        title = ownGroup ?: "Своя группа",
                        subtitle = if (ownGroup != null) groupSubtitle(context, ownGroup) ?: "Своя группа" else "Любая группа любого факультета",
                        selected = !useAppGroup,
                        divider = appGroup != null,
                        onClick = { if (ownGroup == null) picking = true else useAppGroup = false },
                    ) {
                        PillButton(if (ownGroup == null) "Выбрать" else "Изменить", onClick = { picking = true })
                    }
                }
            }
            val maxSub = maxOf(subgroups, subgroup ?: 0)
            if (maxSub > 0) {
                SettingsSection("Подгруппа") {
                    Segmented(
                        options = listOf<Int?>(null, 0) + (1..maxSub).toList(),
                        selected = subgroup,
                        label = {
                            when (it) {
                                null -> "Как в прил."
                                0 -> "Все"
                                else -> "$it"
                            }
                        },
                        onSelect = { subgroup = it },
                    )
                    Hint("«Как в прил.» — подгруппа из настроек приложения.")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Стиль")
                val photoFile = draft.newPhoto?.takeIf { it.isFile } ?: currentPhoto
                StylePicker(
                    selected = style,
                    display = { now -> previewDisplay(context, group, effectiveSubgroup, now) },
                    onSelect = {
                        style = it
                        // У стиля есть свои настройки или фото — сразу на экран настройки.
                        val st = Styles.byId(context, it)
                        if (st.usesPhoto || st.options.isNotEmpty()) setup = true
                    },
                    customFor = { st ->
                        if (!st.usesPhoto && st.options.isEmpty()) {
                            null
                        } else {
                            StyleCustom(draft.options, if (st.usesPhoto) WidgetPhotos.input(photoFile, draft.frame) else null)
                        }
                    },
                    refresh = draft,
                )
                val selected = Styles.byId(context, style)
                if (selected.usesPhoto || selected.options.isNotEmpty()) {
                    PillButton(
                        if (selected.usesPhoto) "Фото и настройки стиля" else "Настройки стиля",
                        onClick = { setup = true },
                        kind = PillKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Hint("Изменения применяются сразу. Тап по виджету открывает расписание на сегодня и неделю.")
        }
    }
}

/** Строка выбора в карточке: отметка, заголовок с подписью и, если нужно, кнопка справа. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val c = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .drawBehind {
                if (divider) drawLine(c.cardBorder, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.rowTitle, color = c.text)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = Type.caption, color = c.sec)
        }
        trailing()
    }
}

/** Размер виджета в dp, как его видит лаунчер в портрете; если ещё неизвестен — стандартный 4×2. */
private fun widgetSizeDp(context: android.content.Context, appWidgetId: Int): Pair<Float, Float> {
    val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
    return if (w > 0 && h > 0) w.toFloat() to h.toFloat() else 316f to 168f
}
