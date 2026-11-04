package io.github.hungrycube.lessonwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.render.theme.WallpaperPalette
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.ui.design.appColors
import io.github.hungrycube.lessonwidget.widget.ConfigureActivity
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import io.github.hungrycube.lessonwidget.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** С чем открыли приложение: тап по виджету передаёт его группу и номер. */
data class LaunchArgs(val group: String?, val appWidgetId: Int?, val stamp: Long = System.nanoTime())

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GROUP = "io.github.hungrycube.lessonwidget.GROUP"
    }

    private var launchArgs by mutableStateOf(LaunchArgs(null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchArgs = parse(intent)
        setContent { AppTheme { AppRoot(launchArgs) } }
    }

    override fun onStart() {
        super.onStart()
        // Обои могли смениться: стили с цветами из обоев пересчитываются при каждом открытии приложения.
        val app = applicationContext
        WallpaperPalette.invalidate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { WidgetUpdater.updateAll(app) }.onFailure { CrashLog.record(app, "Обновление виджетов при открытии", it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchArgs = parse(intent)
    }

    private fun parse(intent: Intent?): LaunchArgs {
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        return LaunchArgs(intent?.getStringExtra(EXTRA_GROUP), id)
    }
}

private enum class Screen { PICK, SCHEDULE, SETTINGS }

@Composable
private fun AppRoot(args: LaunchArgs) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var defaultGroup by remember { mutableStateOf(settings.defaultGroup) }
    var screen by rememberSaveable { mutableStateOf(if (defaultGroup == null) Screen.PICK else Screen.SCHEDULE) }
    // Куда вернуться из выбора группы по «Назад».
    var pickReturn by rememberSaveable { mutableStateOf(Screen.SCHEDULE) }
    var viewGroup by remember(args) { mutableStateOf(args.group ?: defaultGroup) }
    // Открыли с виджета — показываем его группу, а карандаш настраивает именно этот виджет.
    val widgetId = args.appWidgetId?.takeIf { args.group != null }

    Box(Modifier.fillMaxSize().background(appColors.bg)) {
        when (screen) {
            Screen.PICK -> GroupPickerScreen(
                title = "Выбор группы",
                current = defaultGroup,
                onBack = { screen = pickReturn },
                onPicked = { g ->
                    settings.defaultGroup = g
                    defaultGroup = g
                    viewGroup = g
                    screen = Screen.SCHEDULE
                    val app = context.applicationContext
                    scope.launch {
                        ScheduleRepository(app).deleteUnused(Widgets.groupsInUse(app))
                        WidgetUpdater.updateAll(app)
                    }
                },
            )
            Screen.SCHEDULE -> ScheduleScreen(
                group = viewGroup ?: defaultGroup,
                onSettings = { screen = Screen.SETTINGS },
                onPickGroup = {
                    pickReturn = Screen.SCHEDULE
                    screen = Screen.PICK
                },
                onConfigureWidget = widgetId?.let { id ->
                    {
                        context.startActivity(
                            Intent(context, ConfigureActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        )
                    }
                },
            )
            Screen.SETTINGS -> SettingsScreen(
                onBack = {
                    viewGroup = defaultGroup
                    screen = Screen.SCHEDULE
                },
                onChangeGroup = {
                    pickReturn = Screen.SETTINGS
                    screen = Screen.PICK
                },
            )
        }
    }
}
