package io.github.hungrycube.lessonwidget.data

import io.github.hungrycube.lessonwidget.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** HTTP без лишних библиотек. Представляется сайту честным User-Agent с адресом проекта. */
object Net {
    val userAgent =
        "RaspisanieNETI-Widget/${BuildConfig.VERSION_NAME} (unofficial; +https://github.com/${BuildConfig.GITHUB_REPO})"

    private const val MAX_BYTES = 4 * 1024 * 1024

    @Throws(IOException::class)
    fun get(url: String, accept: String = "text/html"): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "ru")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code)
            val charset = conn.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("Слишком большой ответ")
                }
                out.toByteArray()
            }
            return String(bytes, charset)
        } finally {
            conn.disconnect()
        }
    }
}

class HttpException(val code: Int) : IOException("HTTP $code")

/** Понятный текст ошибки для человека. */
fun Throwable.humanMessage(): String = when (this) {
    is GroupNotFoundException -> "На сайте нет расписания группы $group"
    is HttpException -> if (code >= 500) "Сайт НГТУ сейчас не отвечает (ошибка $code)" else "Сайт вернул ошибку $code"
    is java.net.UnknownHostException -> "Нет подключения к интернету"
    is java.net.SocketTimeoutException -> "Сайт НГТУ долго не отвечает"
    is IOException -> "Ошибка сети: ${message ?: javaClass.simpleName}"
    else -> "Не удалось разобрать страницу расписания"
}
