package io.github.hungrycube.lessonwidget.data

import io.github.hungrycube.lessonwidget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

@Serializable
data class ReleaseInfo(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val pageUrl: String,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    val version: String get() = tag.removePrefix("v")
    val apk: ReleaseAsset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
}

/** Проверка новой версии через GitHub Releases. Ничего не устанавливает — только даёт ссылку. */
object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val text = Net.get(
            "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest",
            accept = "application/vnd.github+json",
        )
        json.decodeFromString(ReleaseInfo.serializer(), text)
    }

    /** «1.2.0» новее «1.1.9»? Суффиксы вроде «-dev» игнорируются. */
    fun isNewer(candidate: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
