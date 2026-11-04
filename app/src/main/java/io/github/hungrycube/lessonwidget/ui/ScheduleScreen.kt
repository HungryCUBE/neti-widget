package io.github.hungrycube.lessonwidget.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.groups.Faculties
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.model.Lesson
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import io.github.hungrycube.lessonwidget.core.time.ScheduleCalendar
import io.github.hungrycube.lessonwidget.core.time.Slot
import io.github.hungrycube.lessonwidget.core.time.formatMinute
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.ReleaseInfo
import io.github.hungrycube.lessonwidget.data.ScheduleRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.UpdateChecker
import io.github.hungrycube.lessonwidget.data.humanMessage
import io.github.hungrycube.lessonwidget.ui.design.AppCard
import io.github.hungrycube.lessonwidget.ui.design.AutoShrinkText
import io.github.hungrycube.lessonwidget.ui.design.Badge
import io.github.hungrycube.lessonwidget.ui.design.CircleButton
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.EmptyState
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.Segmented
import io.github.hungrycube.lessonwidget.ui.design.StatusDot
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import io.github.hungrycube.lessonwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

private val DAY_NAMES = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
private val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

/** «28 октября». */
fun dayMonth(date: LocalDate) = "${date.dayOfMonth} ${MONTHS_GEN[date.monthValue - 1]}"

/** «среда, 28 октября». */
fun dateTitle(date: LocalDate) = "${DAY_NAMES[date.dayOfWeek.value - 1]}, ${dayMonth(date)}"

private fun weekdayName(date: LocalDate) = DAY_NAMES[date.dayOfWeek.value - 1].replaceFirstChar { it.uppercaseChar() }

/** «5 мин назад». */
fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - millis) / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 24 * 60 -> "${min / 60} ч назад"
        else -> "${min / (24 * 60)} дн назад"
    }
}

/** «34 мин», «8 ч 26 мин», «2 дн». */
fun span(minutes: Long): String {
    val m = minutes.coerceAtLeast(1)
    return when {
        m < 60 -> "$m мин"
        m < 24 * 60 -> if (m % 60 == 0L) "${m / 60} ч" else "${m / 60} ч ${m % 60} мин"
        else -> "${m / (24 * 60)} дн"
    }
}

/** «N учебная неделя · нечётная» или null, если сайт не сообщил номер недели. */
fun weekLine(schedule: GroupSchedule?, date: LocalDate): String? {
    val s = schedule ?: return null
    val week = ScheduleCalendar(s).weekOf(date) ?: return null
    val total = s.semesterWeeks
    if (week < 1 || (total != null && week > total)) return "Неучебная неделя"
    return "$week учебная неделя · ${if (week % 2 == 0) "чётная" else "нечётная"}"
}

/** Заголовок группы «ФМА · ЭМ-63»: с сайта, а пока расписания нет — из списка групп. */
fun groupTitle(schedule: GroupSchedule?, group: String, faculty: String?): String =
    schedule?.title?.takeIf { it.isNotBlank() }
        ?: faculty?.let { Faculties.short(it) }?.takeIf { it.isNotEmpty() }?.let { "$it · $group" }
        ?: group

/** Отметка у пары: текст и выделена ли она (primary) или приглушена (soft). */
private data class SlotBadge(val text: String, val primary: Boolean)

@Composable
fun ScheduleScreen(
    group: String?,
    onSettings: () -> Unit,
    onPickGroup: () -> Unit,
    onConfigureWidget: (() -> Unit)?,
    initialMode: Int = 0,
) {
    val context = LocalContext.current
    val c = appColors
    val repo = remember { ScheduleRepository(context) }
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var schedule by remember(group) { mutableStateOf(group?.let(repo::load)) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(group) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    var weekOffset by rememberSaveable(group) { mutableIntStateOf(0) }
    val now = rememberNow()
    val subgroup = settings.subgroup.takeIf { it > 0 }
    val info = remember(group) { GroupsRepository(context).find(group) }

    fun refresh() {
        val g = group ?: return
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                schedule = repo.refresh(g, 60_000L)
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
        if (group != null && (s == null || AppClock.now() - s.fetchedAt > 12 * 3600_000L)) refresh()
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

    val today = Instant.ofEpochMilli(now).atZone(NOVOSIBIRSK).toLocalDate()
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(
            start = Dims.side,
            end = Dims.side,
            top = bars.calculateTopPadding() + 18.dp,
            bottom = bars.calculateBottomPadding() + 28.dp,
        ),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val title = if (group == null) "Расписание НЭТИ" else groupTitle(schedule, group, info?.faculty)
                    AutoShrinkText(title, Type.display, c.text)
                    weekLine(schedule, today)?.let { Text(it, style = Type.body, color = c.sec) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onConfigureWidget?.let { CircleButton(LucideIcons.pencil, "Настроить этот виджет", it) }
                    if (group != null) RefreshButton(refreshing, onClick = ::refresh)
                    CircleButton(LucideIcons.settings, "Настройки", onSettings)
                }
            }
        }
        if (group != null) {
            item(key = "status") { StatusLine(schedule, refreshing, error, now) }
        }
        update?.let { rel -> item(key = "update") { UpdateCard(rel, Modifier.padding(top = 14.dp)) } }
        if (group == null) {
            item(key = "nogroup") {
                EmptyState(
                    LucideIcons.users,
                    "Группа не выбрана",
                    "Выберите свою группу — расписание загрузится с сайта НГТУ.",
                    modifier = Modifier.padding(top = 24.dp),
                    action = "Выбрать группу",
                    onAction = onPickGroup,
                )
            }
            return@LazyColumn
        }
        item(key = "tabs") {
            Segmented(
                options = listOf(0, 1),
                selected = mode,
                label = { if (it == 0) "Сегодня" else "Неделя" },
                onSelect = { mode = it },
                modifier = Modifier.padding(top = 18.dp),
            )
        }
        val s = schedule
        if (s == null) {
            item(key = "nodata") {
                if (refreshing) {
                    EmptyState(null, "Загружаю расписание", "Берём его с сайта НГТУ, это несколько секунд.", Modifier.padding(top = 24.dp), loading = true)
                } else {
                    EmptyState(
                        LucideIcons.wifiOff,
                        "Расписания пока нет",
                        error ?: "Нажмите «Обновить», когда появится интернет.",
                        Modifier.padding(top = 24.dp),
                        action = "Обновить",
                        onAction = ::refresh,
                    )
                }
            }
            return@LazyColumn
        }
        val cal = ScheduleCalendar(s, subgroup)
        if (mode == 0) {
            todayItems(cal, today, now)
        } else {
            weekItems(cal, today, now, weekOffset, onWeek = { weekOffset = it })
        }
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    // Пока идёт обновление, стрелка крутится; анимация живёт только в это время.
    val spin = if (refreshing) {
        val t = rememberInfiniteTransition(label = "refresh")
        val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
        Modifier.graphicsLayer { rotationZ = angle }
    } else {
        Modifier
    }
    CircleButton(LucideIcons.refresh, "Обновить расписание", onClick, iconModifier = spin)
}

@Composable
private fun StatusLine(schedule: GroupSchedule?, refreshing: Boolean, error: String?, now: Long) {
    val c = appColors
    val (color, text) = when {
        refreshing && schedule == null -> c.ter to "Загружаю расписание…"
        refreshing -> c.ter to "Обновляю…"
        error != null && schedule != null -> c.warn to "Не удалось обновить · показано сохранённое"
        error != null -> c.warn to error
        schedule != null -> c.ok to "Обновлено ${ago(schedule.fetchedAt, now)}"
        else -> c.ter to "Расписание ещё не загружено"
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        Text(text, style = Type.meta, color = c.ter)
    }
}

@Composable
private fun UpdateCard(release: ReleaseInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = appColors
    AppCard(modifier, padding = PaddingValues(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Доступна версия ${release.version}", style = Type.itemTitle, color = c.text)
                Text("Настройки и виджеты сохранятся", style = Type.caption, color = c.sec)
            }
            PillButton("Скачать", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apk?.downloadUrl ?: release.pageUrl)))
            }, kind = PillKind.Primary)
        }
    }
}

/** Пары, которые сейчас идут, прошли и ближайшая будущая — для бейджей. */
private fun badgeFor(slot: Slot, now: Long, nextStart: Long?): SlotBadge? {
    val start = slot.start.toInstant().toEpochMilli()
    val end = slot.end.toInstant().toEpochMilli()
    return when {
        now >= end -> SlotBadge("прошла", primary = false)
        now >= start -> SlotBadge("идёт · ещё ${span((end - now + 59_999) / 60_000)}", primary = true)
        start == nextStart -> SlotBadge("через ${span((start - now) / 60_000)}", primary = true)
        else -> null
    }
}

/** Начало ближайшей пары, если сейчас ни одна не идёт. */
private fun nextStartOf(slots: List<Slot>, now: Long): Long? {
    val times = slots.map { it.start.toInstant().toEpochMilli() to it.end.toInstant().toEpochMilli() }
    if (times.any { now >= it.first && now < it.second }) return null
    return times.map { it.first }.filter { it > now }.minOrNull()
}

private fun LazyListScope.todayItems(cal: ScheduleCalendar, today: LocalDate, now: Long) {
    val slots = cal.slotsOn(today)
    val next = if (slots.none { it.end.toInstant().toEpochMilli() > now }) cal.nextStudyDay(today) else null
    val nextStart = nextStartOf(slots + next?.second.orEmpty(), now)

    item(key = "h-today") { DayHeader("Сегодня", dateTitle(today), top = 24.dp) }
    if (slots.isEmpty()) {
        item(key = "e-today") {
            val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
            when {
                !cal.isTeachingWeek(today) -> EmptyState(LucideIcons.sun, "Каникулы", "Учебных пар на этой неделе нет.", Modifier.padding(bottom = 10.dp))
                weekend || next == null -> EmptyState(LucideIcons.coffee, "Выходной", "Сегодня пар нет — можно отдохнуть.", Modifier.padding(bottom = 10.dp))
                else -> EmptyState(LucideIcons.coffee, "Сегодня пар нет", "Можно отдохнуть или заняться своими делами.", Modifier.padding(bottom = 10.dp))
            }
        }
    }
    lessonItems("t", slots, now, nextStart)
    if (next != null) {
        val (date, nextSlots) = next
        item(key = "h-next") {
            if (date == today.plusDays(1)) DayHeader("Завтра", dateTitle(date)) else DayHeader(weekdayName(date), dayMonth(date))
        }
        lessonItems("n", nextSlots, now, nextStart)
    }
}

private fun LazyListScope.weekItems(cal: ScheduleCalendar, today: LocalDate, now: Long, weekOffset: Int, onWeek: (Int) -> Unit) {
    val monday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    item(key = "week-nav") {
        val c = appColors
        val week = cal.weekOf(monday)
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleButton(LucideIcons.chevronLeft, "Предыдущая неделя", { onWeek(weekOffset - 1) })
            Column(
                Modifier
                    .weight(1f)
                    .then(if (weekOffset != 0) Modifier.clickable(onClickLabel = "К текущей неделе") { onWeek(0) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val title = when {
                    week == null -> "Неделя"
                    !cal.isTeachingWeek(monday) -> "Неучебная неделя"
                    else -> "$week неделя · ${if (week % 2 == 0) "чётная" else "нечётная"}"
                }
                Text(title, style = Type.itemTitle, color = c.text, textAlign = TextAlign.Center)
                Text("${dayMonth(monday)} – ${dayMonth(monday.plusDays(6))}", style = Type.caption, color = c.sec, textAlign = TextAlign.Center)
            }
            CircleButton(LucideIcons.chevronRight, "Следующая неделя", { onWeek(weekOffset + 1) })
        }
    }
    if (!cal.isTeachingWeek(monday)) {
        item(key = "week-off") {
            EmptyState(LucideIcons.sun, "Пар нет", "Каникулы или сессия — на этой неделе учебных пар нет.", Modifier.padding(top = 24.dp))
        }
        return
    }
    val days = (0 until 7).map { monday.plusDays(it.toLong()) }.map { it to cal.slotsOn(it) }
    val nextStart = nextStartOf(days.flatMap { it.second }, now)
    var first = true
    for ((date, slots) in days) {
        // Пустые выходные не показываем.
        if (slots.isEmpty() && date.dayOfWeek.value >= 6) continue
        val top = if (first) 24.dp else 14.dp
        first = false
        item(key = "wh-$date") { DayHeader(weekdayName(date), dayMonth(date), isToday = date == today, top = top) }
        if (slots.isEmpty()) {
            item(key = "we-$date") {
                Text("Пар нет", style = Type.body, color = appColors.ter, modifier = Modifier.padding(bottom = 10.dp))
            }
        } else {
            lessonItems("w$date", slots, now, nextStart)
        }
    }
}

private fun LazyListScope.lessonItems(prefix: String, slots: List<Slot>, now: Long, nextStart: Long?) {
    for (slot in slots) {
        val past = now >= slot.end.toInstant().toEpochMilli()
        val badge = badgeFor(slot, now, nextStart)
        // Подгруппы в одном слоте — отдельные карточки.
        for ((i, lesson) in slot.entries.withIndex()) {
            item(key = "$prefix-${slot.startMinute}-$i") {
                LessonCard(slot, lesson, badge, past, Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(title: String, date: String?, isToday: Boolean = false, top: Dp = 14.dp) {
    val c = appColors
    Row(
        Modifier.fillMaxWidth().padding(top = top, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.dayTitle, color = c.text, modifier = Modifier.alignByBaseline())
        if (date != null) Text(date, style = Type.body, color = c.sec, modifier = Modifier.alignByBaseline())
        if (isToday) Badge("сегодня", primary = true)
    }
}

@Composable
private fun LessonCard(slot: Slot, lesson: Lesson, badge: SlotBadge?, past: Boolean, modifier: Modifier = Modifier) {
    val c = appColors
    AppCard(
        modifier.alpha(if (past) 0.6f else 1f),
        padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(50.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatMinute(slot.startMinute), style = Type.time, color = c.text, maxLines = 1)
                Text(formatMinute(slot.endMinute), style = Type.caption, color = c.sec, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val label = listOfNotNull(slot.number?.let { "$it пара" }, lesson.subgroup?.let { "$it подгруппа" }).joinToString(" · ")
                if (label.isNotEmpty() || badge != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (label.isNotEmpty()) Text(label, style = Type.meta, color = c.sec, maxLines = 1)
                        badge?.let { Badge(it.text, it.primary) }
                    }
                }
                Text(lesson.title, style = Type.lessonTitle, color = c.text)
                val details = listOfNotNull(
                    lesson.type?.replaceFirstChar { it.uppercaseChar() },
                    lesson.rooms.takeIf { it.isNotEmpty() }?.let { "ауд. " + it.joinToString(", ") },
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = Type.detail, color = c.sec)
                if (lesson.teachers.isNotEmpty()) Text(lesson.teachers.joinToString(", "), style = Type.caption, color = c.ter)
            }
        }
    }
}
