package io.github.hungrycube.lessonwidget.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.display.DisplayBuilder
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.core.time.ScheduleCalendar
import io.github.hungrycube.lessonwidget.core.time.Slot
import io.github.hungrycube.lessonwidget.core.time.formatMinute
import io.github.hungrycube.lessonwidget.data.ReleaseInfo
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.UpdateChecker
import io.github.hungrycube.lessonwidget.data.humanMessage
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

private val DAY_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

fun dateTitle(date: LocalDate) = "${DAY_NAMES[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS_GEN[date.monthValue - 1]}"

/** «обновлено 5 мин назад». */
fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - millis) / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 24 * 60 -> "${min / 60} ч назад"
        else -> "${min / (24 * 60)} дн назад"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(group: String, onSettings: () -> Unit, onConfigureWidget: (() -> Unit)?) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository(context) }
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var schedule by remember(group) { mutableStateOf(repo.load(group)) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(group) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var weekOffset by rememberSaveable(group) { mutableIntStateOf(0) }
    val now = rememberNow()
    val subgroup = settings.subgroup.takeIf { it > 0 }

    fun refresh(minInterval: Long) {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                schedule = repo.refresh(group, minInterval)
                error = null
                WidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                error = e.humanMessage()
            }
            refreshing = false
        }
    }

    LaunchedEffect(group) {
        val s = schedule
        if (s == null || System.currentTimeMillis() - s.fetchedAt > 12 * 3600_000L) refresh(60_000L)
    }

    // Раз в сутки — тихая проверка новой версии.
    val update by produceState<ReleaseInfo?>(null) {
        if (System.currentTimeMillis() - settings.lastUpdateCheck > 24 * 3600_000L) {
            val rel = runCatching { UpdateChecker.latest() }.getOrNull()
            if (rel != null) {
                settings.lastUpdateCheck = System.currentTimeMillis()
                if (UpdateChecker.isNewer(rel.version)) value = rel
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(schedule?.title ?: group, maxLines = 1)
                        val week = schedule?.let { ScheduleCalendar(it).weekOf(LocalDate.now(NOVOSIBIRSK)) }
                        if (week != null) {
                            Text(
                                "$week учебная неделя · ${if (week % 2 == 0) "чётная" else "нечётная"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    if (onConfigureWidget != null) {
                        IconButton(onClick = onConfigureWidget) { Icon(Icons.Default.Edit, contentDescription = "Настроить этот виджет") }
                    }
                    IconButton(onClick = { refresh(60_000L) }, enabled = !refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить расписание")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Настройки") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (refreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Column {
                    val s = schedule
                    val text = when {
                        s == null && refreshing -> "Загружаю расписание с сайта НГТУ…"
                        s == null -> "Расписания пока нет. Нажмите «обновить» вверху."
                        else -> "Обновлено ${ago(s.fetchedAt, now)}"
                    }
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let {
                        Text(
                            if (schedule != null) "$it. Показаны сохранённые данные." else it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            update?.let { rel ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Доступна версия ${rel.version}", fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apk?.downloadUrl ?: rel.pageUrl)))
                            }) { Text("Скачать обновление") }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Сегодня") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Неделя") })
                }
            }
            val s = schedule
            if (s != null) {
                val cal = ScheduleCalendar(s, subgroup)
                val today = Instant.ofEpochMilli(now).atZone(NOVOSIBIRSK).toLocalDate()
                if (mode == 0) {
                    val slots = cal.slotsOn(today)
                    item { DayHeader(DisplayBuilder.dayLabel(today, today) + " · " + dateTitle(today).lowercase()) }
                    if (slots.isEmpty()) {
                        item { Hint(if (cal.isTeachingWeek(today)) "Сегодня пар нет" else "Пар нет — каникулы или сессия") }
                    }
                    items(slots, key = { "t${it.startMinute}" }) { LessonCard(it, now) }
                    val upcoming = slots.none { it.end.toInstant().toEpochMilli() > now }
                    if (upcoming) {
                        val next = cal.nextStudyDay(today)
                        if (next != null) {
                            item { DayHeader(DisplayBuilder.dayLabel(next.first, today) + " · " + dateTitle(next.first).lowercase()) }
                            items(next.second, key = { "n${it.startMinute}" }) { LessonCard(it, now) }
                        }
                    }
                } else {
                    val monday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
                    item {
                        val week = cal.weekOf(monday)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { weekOffset-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
                            }
                            Text(
                                (week?.let { "Неделя $it · " } ?: "") +
                                    "${monday.dayOfMonth} ${MONTHS_GEN[monday.monthValue - 1]} – " +
                                    "${monday.plusDays(6).dayOfMonth} ${MONTHS_GEN[monday.plusDays(6).monthValue - 1]}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { weekOffset++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
                            }
                        }
                    }
                    for (i in 0 until 7) {
                        val date = monday.plusDays(i.toLong())
                        val slots = cal.slotsOn(date)
                        if (i == 6 && slots.isEmpty()) continue
                        item(key = "h$i") { DayHeader(dateTitle(date)) }
                        if (slots.isEmpty()) {
                            item(key = "e$i") { Hint("Пар нет") }
                        } else {
                            items(slots, key = { "d$i-${it.startMinute}" }) { LessonCard(it, now) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LessonCard(slot: Slot, now: Long) {
    val start = slot.start.toInstant().toEpochMilli()
    val end = slot.end.toInstant().toEpochMilli()
    val current = now in start until end
    val past = now >= end
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.width(52.dp)) {
                Text(formatMinute(slot.startMinute), fontWeight = FontWeight.Bold)
                Text(formatMinute(slot.endMinute), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(slot.number?.let { "$it пара" }, if (current) "идёт сейчас" else null, if (past) "прошла" else null)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (e in slot.entries) {
                    Column {
                        Text(e.title, style = MaterialTheme.typography.titleSmall)
                        val details = listOfNotNull(
                            e.type,
                            e.rooms.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "ауд. $it" },
                            e.subgroup?.let { "$it п.гр." },
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                        if (e.teachers.isNotEmpty()) {
                            Text(
                                e.teachers.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
