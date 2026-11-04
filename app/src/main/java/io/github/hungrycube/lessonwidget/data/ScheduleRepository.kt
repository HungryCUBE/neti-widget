package io.github.hungrycube.lessonwidget.data

import android.content.Context
import android.util.AtomicFile
import io.github.hungrycube.lessonwidget.core.groups.GroupSearch
import io.github.hungrycube.lessonwidget.core.model.GroupInfo
import io.github.hungrycube.lessonwidget.core.model.GroupSchedule
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import io.github.hungrycube.lessonwidget.core.parse.ScheduleAssembler
import io.github.hungrycube.lessonwidget.core.time.NOVOSIBIRSK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

class GroupNotFoundException(val group: String) : Exception("Нет расписания группы $group")

private const val BASE = "https://www.nstu.ru/studies/schedule/schedule_classes"

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/** Записывает файл целиком или не трогает его вовсе. */
private fun AtomicFile.writeAtomically(bytes: ByteArray) {
    val out = startWrite()
    try {
        out.write(bytes)
        finishWrite(out)
    } catch (e: Exception) {
        failWrite(out)
        throw e
    }
}

/**
 * Кэш расписаний: по файлу на группу. Новое расписание сначала скачивается и разбирается,
 * и только при успехе заменяет старое — при ошибке остаются прежние данные.
 */
class ScheduleRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "schedules")

    fun load(group: String): GroupSchedule? {
        val file = fileFor(group)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(GroupSchedule.serializer(), String(AtomicFile(file).readFully(), Charsets.UTF_8))
        }.getOrNull()
    }

    internal fun save(schedule: GroupSchedule) {
        dir.mkdirs()
        AtomicFile(fileFor(schedule.group))
            .writeAtomically(json.encodeToString(GroupSchedule.serializer(), schedule).toByteArray(Charsets.UTF_8))
    }

    /**
     * Скачивает расписание группы: страницу «весь семестр» и страницу текущей недели (для номера недели).
     * Не чаще раза в [minIntervalMs], чтобы не нагружать сайт повторными нажатиями.
     */
    suspend fun refresh(group: String, minIntervalMs: Long = 60_000L): GroupSchedule = withContext(Dispatchers.IO) {
        val old = load(group)
        if (old != null && System.currentTimeMillis() - old.fetchedAt < minIntervalMs) return@withContext old
        val encoded = GroupSearch.urlEncode(group)
        val semester = NstuParser.parseSchedulePage(Net.get("$BASE/schedule?group=$encoded&print=true"))
        if (!semester.hasTable) throw GroupNotFoundException(group)
        val week = NstuParser.parseSchedulePage(Net.get("$BASE/schedule?group=$encoded"))
        val schedule = ScheduleAssembler.assemble(
            group = group,
            semester = semester,
            week = week,
            fetchedAt = System.currentTimeMillis(),
            today = LocalDate.now(NOVOSIBIRSK),
            previous = old,
        )
        save(schedule)
        schedule
    }

    /** Удаляет кэш групп, которые больше нигде не используются. */
    fun deleteUnused(used: Set<String>) {
        val keep = used.map { fileFor(it).name }.toSet()
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    private fun fileFor(group: String): File {
        val key = group.trim().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        return File(dir, "$key.json")
    }
}

/** Список всех групп всех факультетов с сайта. Обновляется вместе с расписанием, не чаще раза в сутки. */
class GroupsRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "groups.json")

    fun cached(): List<GroupInfo> =
        if (!file.exists()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(GroupInfo.serializer()), String(AtomicFile(file).readFully(), Charsets.UTF_8))
        }.getOrDefault(emptyList())

    fun cachedAt(): Long = if (file.exists()) file.lastModified() else 0L

    fun isStale(): Boolean = System.currentTimeMillis() - cachedAt() > 20L * 3600 * 1000

    /** Группа из сохранённого списка по имени. */
    fun find(name: String?): GroupInfo? = name?.let { n -> cached().firstOrNull { it.name == n } }

    suspend fun refresh(): List<GroupInfo> = withContext(Dispatchers.IO) {
        val groups = NstuParser.parseGroups(Net.get(BASE))
        if (groups.isEmpty()) throw IllegalStateException("Список групп пуст")
        store(groups)
        groups
    }

    /** Заменяет сохранённый список целиком. */
    internal fun store(groups: List<GroupInfo>) {
        file.parentFile?.mkdirs()
        AtomicFile(file).writeAtomically(json.encodeToString(ListSerializer(GroupInfo.serializer()), groups).toByteArray(Charsets.UTF_8))
    }

    /** Проверка введённой вручную группы: есть ли у неё расписание на сайте. Возвращает заголовок страницы. */
    suspend fun verify(group: String): String? = withContext(Dispatchers.IO) {
        val page = NstuParser.parseSchedulePage(Net.get("$BASE/schedule?group=${GroupSearch.urlEncode(group)}&print=true"))
        if (page.hasTable) page.title ?: group else null
    }
}
