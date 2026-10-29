package io.github.hungrycube.lessonwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.WidgetConfig
import io.github.hungrycube.lessonwidget.data.WidgetConfigs
import io.github.hungrycube.lessonwidget.data.WidgetPhotos
import io.github.hungrycube.lessonwidget.render.StyleCustom
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.ui.AppTheme
import io.github.hungrycube.lessonwidget.ui.GroupPicker
import io.github.hungrycube.lessonwidget.ui.Section
import io.github.hungrycube.lessonwidget.ui.StyleDraft
import io.github.hungrycube.lessonwidget.ui.StylePicker
import io.github.hungrycube.lessonwidget.ui.StyleSetupScreen
import io.github.hungrycube.lessonwidget.ui.SubgroupChips
import io.github.hungrycube.lessonwidget.ui.previewDisplay
import io.github.hungrycube.lessonwidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Настройка виджета: при добавлении на рабочий стол и по карандашу в приложении. */
class ConfigureActivity : ComponentActivity() {

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
        setContent { AppTheme { ConfigureScreen(appWidgetId, onDone = ::save, onCancel = ::cancel) } }
    }

    private fun cancel() {
        // Выбранное, но не сохранённое фото не копится.
        WidgetPhotos.draft(applicationContext, "$appWidgetId").delete()
        finish()
    }

    private fun save(config: WidgetConfig, draft: StyleDraft) {
        val app = applicationContext
        val settings = Settings(app)
        val key = "$appWidgetId"
        val style = Styles.byId(app, config.style ?: settings.defaultStyle)
        try {
            if (style.usesPhoto) {
                val own = WidgetPhotos.file(app, key)
                val default = WidgetPhotos.file(app, WidgetPhotos.DEFAULT)
                when {
                    draft.newPhoto?.isFile == true -> WidgetPhotos.commitDraft(app, key, key)
                    // Новый виджет со «Своим фото» по умолчанию получает копию этого фото сразу.
                    !own.isFile && default.isFile -> WidgetPhotos.copy(default, own)
                }
            } else {
                WidgetPhotos.delete(app, key)
            }
        } catch (e: Exception) {
            CrashLog.record(app, "Своё фото виджета $appWidgetId", e)
        }
        WidgetPhotos.draft(app, key).delete()
        WidgetConfigs(app).set(
            appWidgetId,
            config.copy(
                options = draft.options.takeIf { style.options.isNotEmpty() },
                photo = draft.frame.takeIf { style.usesPhoto },
            ),
        )
        if (settings.defaultGroup == null && config.group != null) settings.defaultGroup = config.group
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val id = appWidgetId
        val group = config.group ?: settings.defaultGroup
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = ScheduleRepository(app)
                WidgetUpdater.update(app, id)
                if (group != null && repo.load(group) == null) {
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
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(appWidgetId: Int, onDone: (WidgetConfig, StyleDraft) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
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
    var setup by remember { mutableStateOf(false) }
    val draftFile = remember { WidgetPhotos.draft(context, "$appWidgetId") }
    val currentPhoto = WidgetPhotos.fileFor(context, appWidgetId)
    val widgetSize = remember { widgetSizeDp(context, appWidgetId) }

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
            onDone = {
                draft = it
                setup = false
            },
            onBack = { setup = false },
        )
        return
    }

    if (picking) {
        val canBack = appGroup != null || ownGroup != null
        BackHandler { if (canBack) picking = false else onCancel() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Группа для виджета") },
                    navigationIcon = {
                        IconButton(onClick = { if (canBack) picking = false else onCancel() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            GroupPicker(
                modifier = Modifier.padding(padding),
                onPicked = {
                    ownGroup = it
                    useAppGroup = false
                    picking = false
                },
            )
        }
        return
    }

    val group = if (useAppGroup) appGroup else ownGroup
    val effectiveSubgroup = (subgroup ?: settings.subgroup).takeIf { it > 0 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Виджет расписания") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Отмена") }
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
                if (appGroup != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = useAppGroup, onClick = { useAppGroup = true })
                        Text("Как в приложении — $appGroup")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !useAppGroup,
                        onClick = { if (ownGroup == null) picking = true else useAppGroup = false },
                    )
                    Text(ownGroup?.let { "Своя — $it" } ?: "Другая группа", modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text("Выбрать") }
                }
            }
            Section("Подгруппа") {
                SubgroupChips(subgroup, onSelect = { subgroup = it }, withInherit = true)
            }
            Section("Стиль") {
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
                    OutlinedButton(onClick = { setup = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected.usesPhoto) "Фото и настройки стиля" else "Настройки стиля")
                    }
                }
            }
            Button(
                enabled = group != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onDone(WidgetConfig(group = if (useAppGroup) null else ownGroup, style = style, subgroup = subgroup), draft) },
            ) { Text("Готово") }
            Text(
                "Тап по виджету открывает расписание на сегодня и неделю.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Размер виджета в dp, как его видит лаунчер в портрете; если ещё неизвестен — стандартный 4×2. */
private fun widgetSizeDp(context: android.content.Context, appWidgetId: Int): Pair<Float, Float> {
    val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
    return if (w > 0 && h > 0) w.toFloat() to h.toFloat() else 316f to 168f
}
