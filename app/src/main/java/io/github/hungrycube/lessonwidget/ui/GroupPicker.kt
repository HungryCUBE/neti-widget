package io.github.hungrycube.lessonwidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.hungrycube.lessonwidget.core.groups.GroupSearch
import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import io.github.hungrycube.lessonwidget.data.GroupsRepository
import io.github.hungrycube.lessonwidget.data.humanMessage
import kotlinx.coroutines.launch

/**
 * Поиск группы с автодополнением по списку с сайта.
 * Если списка нет (нет сети) или группы в нём нет — ручной ввод с проверкой на сайте.
 */
@Composable
fun GroupPicker(onPicked: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { GroupsRepository(context) }
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(repo.cached()) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (groups.isEmpty() || repo.isStale()) {
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

    val results: List<GroupInfo> = remember(query, groups) {
        if (query.isBlank()) emptyList() else GroupSearch.search(query, groups)
    }
    val exact = results.any { GroupSearch.key(it.name) == GroupSearch.key(query) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                checkError = null
            },
            label = { Text("Учебная группа, например ЭМ-63") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        listError?.let {
            Text(
                "Список групп не загрузился: $it. Можно ввести группу вручную.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (query.isBlank()) {
            Text(
                "Начните вводить название группы — можно без дефиса и в любом регистре: «эм63».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(results, key = { it.name }) { g ->
                ListItem(
                    headlineContent = { Text(if (g.label != g.name) "${g.name} (${g.label})" else g.name) },
                    supportingContent = {
                        val course = if (g.course > 0) "${g.course} курс" else ""
                        Text(listOf(g.faculty, g.level, course).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    modifier = Modifier.clickable { onPicked(g.name) },
                )
                HorizontalDivider()
            }
            if (query.isNotBlank() && !exact) {
                item {
                    val candidate = GroupSearch.normalizeInput(query)
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (results.isEmpty()) "В списке такой группы нет." else "Нет нужной группы в списке?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            enabled = !checking && candidate.isNotBlank(),
                            onClick = {
                                checking = true
                                checkError = null
                                scope.launch {
                                    try {
                                        val title = repo.verify(candidate)
                                        if (title != null) onPicked(candidate) else checkError = "На сайте нет расписания группы «$candidate»"
                                    } catch (e: Exception) {
                                        checkError = e.humanMessage()
                                    }
                                    checking = false
                                }
                            },
                        ) { Text(if (checking) "Проверяю…" else "Проверить «$candidate» на сайте") }
                        checkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
