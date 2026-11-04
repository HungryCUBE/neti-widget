package io.github.hungrycube.lessonwidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.groups.GroupSearch
import io.github.hungrycube.lessonwidget.core.groups.describe
import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.Settings
import io.github.hungrycube.lessonwidget.data.humanMessage
import io.github.hungrycube.lessonwidget.ui.design.Dims
import io.github.hungrycube.lessonwidget.ui.design.EmptyState
import io.github.hungrycube.lessonwidget.ui.design.Hint
import io.github.hungrycube.lessonwidget.ui.design.LucideIcons
import io.github.hungrycube.lessonwidget.ui.design.PillButton
import io.github.hungrycube.lessonwidget.ui.design.PillKind
import io.github.hungrycube.lessonwidget.ui.design.ScreenHeader
import io.github.hungrycube.lessonwidget.ui.design.SectionLabel
import io.github.hungrycube.lessonwidget.ui.design.StatusDot
import io.github.hungrycube.lessonwidget.ui.design.Type
import io.github.hungrycube.lessonwidget.ui.design.appColors
import kotlinx.coroutines.launch

/** Результаты поиска одного факультета, внутри — по курсам. */
private data class FacultyResults(val faculty: String, val courses: List<Pair<Int, List<GroupInfo>>>)

/** Группирует найденное по факультетам (сначала тот, где совпадение лучше) и курсам. */
private fun groupResults(results: List<GroupInfo>): List<FacultyResults> =
    results.groupBy { it.faculty }.map { (faculty, list) ->
        FacultyResults(faculty, list.groupBy { it.course }.toList().sortedBy { (course, _) -> if (course > 0) course else 99 })
    }

/**
 * Выбор любой группы любого факультета: при первом запуске, из настроек и для отдельного виджета.
 * Поиск по всему списку групп nstu.ru: по любой части кода, без учёта регистра, дефисов, пробелов и раскладки.
 */
@Composable
fun GroupPickerScreen(
    title: String,
    current: String?,
    onBack: (() -> Unit)?,
    onPicked: (String) -> Unit,
    initialQuery: String = "",
) {
    val context = LocalContext.current
    val c = appColors
    val repo = remember { GroupsRepository(context) }
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var groups by remember { mutableStateOf(repo.cached()) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        if (attempt > 0 || groups.isEmpty() || repo.isStale()) {
            loading = true
            try {
                groups = repo.refresh()
                listError = null
            } catch (e: Exception) {
                listError = e.humanMessage()
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) {
        // Клавиатура сразу: человек пришёл сюда, чтобы набрать номер группы.
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    onBack?.let { BackHandler(onBack = it) }

    fun pick(name: String) {
        settings.addRecent(name)
        keyboard?.hide()
        onPicked(name)
    }

    val results = remember(query, groups) { if (query.isBlank()) emptyList() else GroupSearch.search(query, groups, limit = 400) }
    val sections = remember(results) { groupResults(results) }
    val recent = remember(groups) {
        settings.recentGroups.map { name -> groups.firstOrNull { it.name == name } ?: GroupInfo(name, "", "", "", 0) }
    }
    val candidate = GroupSearch.normalizeInput(query)
    val exact = results.any { GroupSearch.key(it.name) == GroupSearch.key(query) }

    fun verify() {
        if (candidate.isBlank() || checking) return
        checking = true
        checkError = null
        scope.launch {
            try {
                if (repo.verify(candidate) != null) pick(candidate) else checkError = "На сайте нет расписания группы «$candidate»"
            } catch (e: Exception) {
                checkError = e.humanMessage()
            }
            checking = false
        }
    }

    val list = rememberLazyListState()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        ScreenHeader(title, onBack, divider = list.canScrollBackward)
        Column(Modifier.padding(start = Dims.side, end = Dims.side, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Учебная группа")
            SearchField(
                value = query,
                onValueChange = {
                    query = it
                    checkError = null
                },
                focus = focus,
                onSearch = { keyboard?.hide() },
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(start = Dims.side, end = Dims.side, bottom = nav.calculateBottomPadding() + 28.dp),
        ) {
            // Список с сайта не обновился, но есть сохранённый — работаем с ним.
            if (listError != null && groups.isNotEmpty()) {
                item(key = "offline") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(c.warn)
                        Text("Нет сети — показан сохранённый список", style = Type.meta, color = c.ter, modifier = Modifier.weight(1f))
                        PillButton("Повторить", onClick = { attempt++ }, loading = loading)
                    }
                }
            }
            if (groups.isEmpty()) {
                item(key = "nolist") {
                    if (loading || listError == null) {
                        Row(
                            Modifier.padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = c.text, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text("Загружаю список групп…", style = Type.caption, color = c.sec)
                        }
                    } else {
                        EmptyState(
                            LucideIcons.wifiOff,
                            "Список групп не загрузился",
                            "$listError. Можно ввести номер группы и проверить его на сайте.",
                            Modifier.padding(top = 20.dp),
                            action = "Повторить",
                            onAction = { attempt++ },
                        )
                    }
                }
            }
            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column {
                            SectionLabel("Недавние", Modifier.padding(top = 20.dp, bottom = 8.dp))
                            GroupList(recent, current, onClick = ::pick)
                        }
                    }
                }
                item(key = "hint") {
                    Hint("Начните вводить номер группы — например, «эм63» или просто «63».", Modifier.padding(top = 14.dp))
                }
            } else {
                if (results.isEmpty() && groups.isNotEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            LucideIcons.search,
                            "Ничего не найдено",
                            "Проверьте номер группы. Если её нет в списке, можно проверить на сайте.",
                            Modifier.padding(top = 20.dp),
                        )
                    }
                }
                for (s in sections) {
                    item(key = "f-${s.faculty}") {
                        SectionLabel(s.faculty.ifBlank { "Другие группы" }, Modifier.padding(top = 20.dp))
                    }
                    for ((course, list) in s.courses) {
                        item(key = "c-${s.faculty}-$course") {
                            Column {
                                Text(
                                    if (course > 0) "$course курс" else "Курс не указан",
                                    style = Type.meta,
                                    color = c.ter,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                                )
                                GroupList(list, current, onClick = ::pick)
                            }
                        }
                    }
                }
                // Группы нет в списке (новая или список не загрузился) — проверить на сайте напрямую.
                if (!exact && candidate.isNotBlank()) {
                    item(key = "verify") {
                        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (results.isNotEmpty()) Hint("Нет нужной группы в списке?")
                            PillButton(
                                "Проверить «$candidate» на сайте",
                                onClick = ::verify,
                                kind = if (results.isEmpty()) PillKind.Primary else PillKind.Outline,
                                loading = checking,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            checkError?.let { Text(it, style = Type.caption, color = c.warn) }
                        }
                    }
                }
            }
        }
    }
}

/** Поле поиска 52 dp: лупа, текст, круглая кнопка очистки; в фокусе — обводка 2 dp primary. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit) {
    val c = appColors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = Type.input.copy(color = c.text),
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(52.dp)
                    .clip(shape)
                    .background(c.soft)
                    .then(if (focused) Modifier.border(2.dp, c.primary, shape) else Modifier)
                    .padding(start = 14.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LucideIcons.search, contentDescription = null, tint = c.sec, modifier = Modifier.size(19.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("например, ЭМ-63", style = Type.input, color = c.ter, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.clearBg)
                            .clickable(role = Role.Button) { onValueChange("") }
                            .semantics { contentDescription = "Очистить" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LucideIcons.close, contentDescription = null, tint = c.text, modifier = Modifier.size(14.dp))
                    }
                }
            }
        },
    )
}

/** Карточка-список групп: строки от 62 dp, разделители 1 dp, у выбранной — галочка. */
@Composable
private fun GroupList(groups: List<GroupInfo>, current: String?, onClick: (String) -> Unit) {
    val c = appColors
    val shape = RoundedCornerShape(Dims.cardRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.cardBorder, shape),
    ) {
        for ((i, g) in groups.withIndex()) {
            val selected = g.name == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .drawBehind {
                        if (i > 0) drawLine(c.cardBorder, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
                    }
                    .clickable(onClickLabel = "Выбрать группу ${g.name}") { onClick(g.name) }
                    .padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(g.name, style = Type.rowTitle, color = c.text)
                    val about = listOfNotNull(g.label.takeIf { it != g.name }?.let { "на сайте «$it»" }, g.describe().takeIf { it.isNotEmpty() })
                        .joinToString(" · ")
                    if (about.isNotEmpty()) Text(about, style = Type.caption, color = c.sec)
                }
                Icon(
                    if (selected) LucideIcons.check else LucideIcons.chevronRight,
                    contentDescription = if (selected) "Выбрана" else null,
                    tint = if (selected) c.primary else c.ter,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
