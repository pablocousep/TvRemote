package com.reelog.tvremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    private const val PERIODIC_WORK = "tvremote_periodic_update_check"
    private const val STARTUP_WORK = "tvremote_startup_update_check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val startupCheck = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            STARTUP_WORK,
            ExistingWorkPolicy.KEEP,
            startupCheck
        )

        val periodicCheck = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicCheck
        )
    }
}

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val connection = (URL("https://api.github.com/repos/pablocousep/TvRemote/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TvRemote-Android")
            }

            val release = try {
                if (connection.responseCode !in 200..299) return Result.retry()
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }

            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                return Result.success()
            }

            val latestTag = release.optString("tag_name")
            val releaseUrl = release.optString("html_url")
            val currentVersion = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0).versionName.orEmpty()

            if (latestTag.isBlank() || releaseUrl.isBlank() ||
                compareVersions(latestTag, currentVersion) <= 0) {
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences("tvremote_updates", Context.MODE_PRIVATE)
            if (prefs.getString("last_notified_tag", null) != latestTag) {
                showUpdateNotification(latestTag, releaseUrl)
                prefs.edit().putString("last_notified_tag", latestTag).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        val a = Regex("\\d+").findAll(first).map { it.value.toLongOrNull() ?: 0L }.toList()
        val b = Regex("\\d+").findAll(second).map { it.value.toLongOrNull() ?: 0L }.toList()
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0L }
            val right = b.getOrElse(index) { 0L }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun showUpdateNotification(tag: String, releaseUrl: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tvremote_app_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Actualizaciones de TvRemote", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            4102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Nueva versión de TvRemote")
            .setContentText("Ya está disponible $tag. Pulsa para descargarla.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(4102, notification)
        }
    }
}
