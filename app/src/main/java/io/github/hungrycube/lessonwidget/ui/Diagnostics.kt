package io.github.hungrycube.lessonwidget.ui

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Подсказка, если система не даёт виджету обновляться в фоне. */
@Composable
fun WidgetLagSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pkg = context.packageName
    // Пересчитать состояние, когда пользователь вернётся из системных настроек.
    var checks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checks++
        scope.launch { WidgetUpdater.updateAll(context) }
    }
    fun open(intent: Intent) {
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
    }
    val unrestricted = remember(checks) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(pkg) == true
    }
    val exactAllowed = remember(checks) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    Text(
        "Если виджет показывает старое время, скорее всего, система ограничивает приложение в фоне.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!exactAllowed) {
        Button(onClick = {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
        }) { Text("Разрешить точное время") }
    }
    if (unrestricted) {
        Text("Ограничений батареи нет.", style = MaterialTheme.typography.bodyMedium)
    } else {
        Button(onClick = {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        }) { Text("Снять ограничения батареи") }
    }
    Text(
        "Xiaomi, Redmi, POCO (HyperOS, MIUI): в настройках приложения включите «Автозапуск», " +
            "а в «Контроль активности» выберите «Нет ограничений».\n" +
            "OPPO, realme, OnePlus (ColorOS): в настройках приложения откройте «Использование батареи» " +
            "и разрешите работу в фоне и автозапуск.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }) { Text("Открыть настройки приложения") }
}

/** Последняя ошибка из журнала: показать, скопировать, отправить. */
@Composable
fun CrashReportSection() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(CrashLog.entries(context)) }
    if (entries.isEmpty()) {
        Text("Ошибок не было.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(
        if (entries.size == 1) "Последняя ошибка:" else "Последняя ошибка (всего в журнале: ${entries.size}):",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        entries.first().lines().take(20).joinToString("\n"),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
    Button(onClick = { copyReport(context) }) { Text("Скопировать") }
    Button(onClick = { shareReport(context) }) { Text("Отправить в Telegram") }
    TextButton(onClick = {
        CrashLog.clear(context)
        entries = emptyList()
    }) { Text("Очистить журнал") }
    Text(
        "Отчёт уходит, только если вы сами его отправите. В нём версия приложения, модель телефона и техническое описание ошибки.",
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun copyReport(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Отчёт об ошибке", CrashLog.report(context)))
    // С Android 13 система сама показывает, что текст скопирован.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }
}

private fun shareReport(context: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, CrashLog.report(context))
    try {
        context.startActivity(Intent(send).setPackage("org.telegram.messenger"))
    } catch (e: ActivityNotFoundException) {
        // Telegram не установлен или другой клиент — обычное меню «Поделиться».
        context.startActivity(Intent.createChooser(send, "Отправить отчёт @jackkidney"))
    }
}
