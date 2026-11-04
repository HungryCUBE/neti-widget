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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.ui.design.AppCard
import io.github.hungrycube.lessonwidget.ui.design.Hint
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.StatusDot
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Подсказка, если система не даёт виджету обновляться в фоне. */
@Composable
fun WidgetLagCard() {
    val context = LocalContext.current
    val c = appColors
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

    AppCard(spacing = 10.dp) {
        Text(
            "Если виджет показывает старое время, скорее всего, система ограничивает приложение в фоне.",
            style = Type.detail,
            color = c.text,
        )
        if (!exactAllowed) {
            PillButton("Разрешить точное время", onClick = {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
            }, kind = PillKind.Primary, modifier = Modifier.fillMaxWidth())
        }
        if (unrestricted) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.ok)
                Text("Ограничений батареи нет", style = Type.meta, color = c.sec)
            }
        } else {
            PillButton("Снять ограничения батареи", onClick = {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
            }, kind = PillKind.Primary, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "Xiaomi, Redmi, POCO (HyperOS, MIUI): в настройках приложения включите «Автозапуск», " +
                "а в «Контроль активности» выберите «Нет ограничений».\n" +
                "OPPO, realme, OnePlus (ColorOS): в настройках приложения откройте «Использование батареи» " +
                "и разрешите работу в фоне и автозапуск.",
            style = Type.small,
            color = c.ter,
        )
        PillButton("Открыть настройки приложения", onClick = {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }, kind = PillKind.Outline, modifier = Modifier.fillMaxWidth())
    }
}

/** Последняя ошибка из журнала: показать, скопировать, отправить. */
@Composable
fun CrashReportCard() {
    val context = LocalContext.current
    val c = appColors
    var entries by remember { mutableStateOf(CrashLog.entries(context)) }
    AppCard(spacing = 10.dp) {
        if (entries.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.ok)
                Text("Ошибок не было", style = Type.body, color = c.text)
            }
        } else {
            Text(
                if (entries.size == 1) "Последняя ошибка:" else "Последняя ошибка (всего в журнале: ${entries.size}):",
                style = Type.detail,
                color = c.text,
            )
            Text(
                entries.first().lines().take(20).joinToString("\n"),
                style = Type.small.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                color = c.sec,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.soft)
                    .padding(10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Скопировать", onClick = { copyReport(context) }, modifier = Modifier.weight(1f))
                PillButton("Отправить", onClick = { shareReport(context) }, kind = PillKind.Primary, icon = LucideIcons.send, modifier = Modifier.weight(1f))
            }
            PillButton("Очистить журнал", onClick = {
                CrashLog.clear(context)
                entries = emptyList()
            }, kind = PillKind.Outline, modifier = Modifier.fillMaxWidth())
            Hint(
                "Отчёт уходит, только если вы сами его отправите. В нём версия приложения, модель телефона и техническое описание ошибки.",
                Modifier.padding(end = 6.dp),
            )
        }
    }
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
