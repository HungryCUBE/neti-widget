package io.github.hungrycube.lessonwidget.data

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import io.github.hungrycube.lessonwidget.BuildConfig
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Журнал сбоев: последние 5 ошибок с версией приложения, моделью телефона и стектрейсом.
 * Лежит только на телефоне, в сеть сам не уходит — отправить его можно вручную из настроек.
 */
object CrashLog {
    private const val FILE = "crash_log.txt"
    private const val MAX_ENTRIES = 5
    private const val MAX_TRACE_CHARS = 6_000
    private const val SEPARATOR = "\n\n=====\n\n"
    private val lock = Any()

    /** Ловит любое необработанное исключение, записывает его и отдаёт системе как обычно. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, "Сбой приложения (поток ${thread.name})", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, where: String, error: Throwable) {
        Log.w("LessonWidget", where, error)
        val trace = error.stackTraceToString().let {
            if (it.length > MAX_TRACE_CHARS) it.take(MAX_TRACE_CHARS) + "\n…" else it
        }
        val entry = buildString {
            appendLine("Время: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")))
            appendLine("Где: $where")
            appendLine("Приложение: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            append(trace.trimEnd())
        }
        synchronized(lock) {
            val file = file(context)
            val entries = (listOf(entry) + read(file)).take(MAX_ENTRIES)
            val out = file.startWrite()
            try {
                out.write(entries.joinToString(SEPARATOR).toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    /** Записи от новых к старым. */
    fun entries(context: Context): List<String> = synchronized(lock) { read(file(context)) }

    fun clear(context: Context) = synchronized(lock) { file(context).delete() }

    /** Текст для отправки: все записи журнала. */
    fun report(context: Context): String =
        "Отчёт об ошибке — Расписание НЭТИ\n\n" + entries(context).joinToString(SEPARATOR)

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE))

    private fun read(file: AtomicFile): List<String> =
        if (!file.baseFile.exists()) emptyList()
        else runCatching { String(file.readFully(), Charsets.UTF_8).split(SEPARATOR).filter { it.isNotBlank() } }
            .getOrDefault(emptyList())
}
