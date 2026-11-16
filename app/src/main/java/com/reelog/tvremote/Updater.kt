package com.reelog.tvremote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

/**
 * Self-updater backed by this project's GitHub releases. Release tags are `v0.3.<n>` where
 * `<n>` is also the APK's versionCode, so a newer release is simply a bigger `<n>`.
 */
object Updater {
    private const val REPO = "pablocousep/TvRemote"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Returns the latest release if it is newer than the installed app, else null. */
    suspend fun check(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TvRemote-Android")
            }
            val body = try {
                if (c.responseCode == 404) return@runCatching null // no releases yet
                if (c.responseCode !in 200..299) error("GitHub respondió ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val code = Regex("""(\d+)$""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: error("Versión del release no reconocida: $tag")
            val assets = json.optJSONArray("assets")
            val apks = (0 until (assets?.length() ?: 0)).mapNotNull { assets!!.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            // Prefer the signed release APK over the debug one when both are attached.
            val apk = apks.firstOrNull { !it.optString("name").contains("debug", true) } ?: apks.firstOrNull()
                ?: error("El release $tag no tiene APK")
            val info = UpdateInfo(
                versionCode = code,
                tag = tag,
                notes = json.optString("body").trim(),
                apkUrl = apk.optString("browser_download_url"),
                sizeBytes = apk.optLong("size")
            )
            if (code > currentVersionCode(context)) info else null
        }
    }

    /** Downloads the APK into the app cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, info: UpdateInfo, progress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "TvRemote-${info.tag}.apk")
                var url = URL(info.apkUrl)
                var c: HttpURLConnection
                // GitHub redirects asset downloads to its storage host; follow manually to be safe.
                var hops = 0
                while (true) {
                    c = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 10000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", "TvRemote-Android")
                        setRequestProperty("Accept", "application/octet-stream")
                    }
                    if (c.responseCode in 300..399 && hops++ < 5) {
                        url = URL(url, c.getHeaderField("Location"))
                        c.disconnect()
                        continue
                    }
                    break
                }
                try {
                    if (c.responseCode !in 200..299) error("Descarga fallida (${c.responseCode})")
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    c.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) progress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                file
            }
        }

    /** True when Android lets this app open the installer. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the user allows this app to install updates. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the downloaded APK to Android's package installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
