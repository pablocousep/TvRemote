package com.reelog.tvremote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases every six hours, even when TvRemote is not open.
 * It never downloads or installs an APK automatically.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = TvStore(applicationContext)
        return Updater.check(applicationContext).fold(
            onSuccess = { info ->
                store.lastUpdateCheck = System.currentTimeMillis()
                if (info == null) {
                    store.pendingUpdateTag = null
                } else {
                    store.pendingUpdateTag = info.tag
                    if (store.notifiedUpdateTag != info.tag) {
                        notifyUpdate(applicationContext, info.tag)
                        store.notifiedUpdateTag = info.tag
                    }
                }
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }

    private fun notifyUpdate(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "tvremote_updates"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 530, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_w_power)
            .setContentTitle("Actualización disponible")
            .setContentText("TvRemote $tag ya está disponible")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(530, notification)
    }

    companion object {
        private const val WORK_NAME = "tvremote_background_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }
}
