package io.github.hungrycube.lessonwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(args: LaunchArgs) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var defaultGroup by remember { mutableStateOf(settings.defaultGroup) }
    var screen by rememberSaveable { mutableStateOf(if (defaultGroup == null) Screen.PICK else Screen.SCHEDULE) }
    var viewGroup by remember(args) { mutableStateOf(args.group ?: defaultGroup) }
    // Открыли с виджета — показываем его группу, а карандаш настраивает именно этот виджет.
    val widgetId = args.appWidgetId?.takeIf { args.group != null }

    when (screen) {
        Screen.PICK -> {
            val canGoBack = defaultGroup != null
            if (canGoBack) BackHandler { screen = Screen.SETTINGS }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (canGoBack) "Другая группа" else "Выберите свою группу") },
                        navigationIcon = {
                            if (canGoBack) {
                                IconButton(onClick = { screen = Screen.SETTINGS }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                GroupPicker(
                    modifier = Modifier.padding(padding),
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
            }
        }
        Screen.SCHEDULE -> {
            val group = viewGroup ?: defaultGroup
            if (group == null) {
                LaunchedEffect(Unit) { screen = Screen.PICK }
            } else {
                ScheduleScreen(
                    group = group,
                    onSettings = { screen = Screen.SETTINGS },
                    onConfigureWidget = widgetId?.let { id ->
                        {
                            context.startActivity(
                                Intent(context, ConfigureActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                            )
                        }
                    },
                )
            }
        }
        Screen.SETTINGS -> SettingsScreen(
            onBack = {
                viewGroup = defaultGroup
                screen = Screen.SCHEDULE
            },
            onChangeGroup = { screen = Screen.PICK },
        )
    }
}
