package com.simphotos.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.simphotos.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/** Checks GitHub Releases for a newer SimPhotos APK, downloads it and hands it to the system installer. */
class UpdateManager(private val context: Context) {

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val apkFile: File get() = File(context.cacheDir, "updates/SimPhotos.apk")

    /** Returns the latest release if it is newer than the installed version, null otherwise. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(LATEST_RELEASE_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val json = try {
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val version = json.optString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return@withContext null

        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        UpdateInfo(
            version = version,
            notes = json.optString("body").cleanNotes(),
            downloadUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /** Downloads the APK, reporting progress in 0..1. Returns the downloaded file. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = apkFile
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "SimPhotos.apk.part")
        val conn = open(info.downloadUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Impossibile salvare l'aggiornamento")
        target
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows SimPhotos to install apps. */
    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Removes a previously downloaded APK (e.g. once the update has been installed). */
    fun cleanUp() {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "SimPhotos/$currentVersion")
    }

    private fun String.cleanNotes(): String = lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("📲") }
        .joinToString("\n") { it.replace(Regex("^[-*] "), "• ") }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/carellice/SimPhotos/releases/latest"

        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
