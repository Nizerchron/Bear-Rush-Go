package com.bearrushbuilder.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bearrushbuilder.app.MainActivity
import com.bearrushbuilder.app.R
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.tasks.Tasks

/**
 * Worker that checks for Google Play app updates in the background.
 * If an update is available, posts a notification to the system tray.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("UpdateCheckWorker", "Starting background update check...")
            val appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            
            // Wait for task result synchronously in the coroutine context
            val appUpdateInfo = Tasks.await(appUpdateInfoTask)
            
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                Log.d("UpdateCheckWorker", "New app update available! Posting notification...")
                showUpdateNotification()
            } else {
                Log.d("UpdateCheckWorker", "No update available or not allowed.")
            }
            
            Result.success()
        } catch (t: Throwable) {
            Log.e("UpdateCheckWorker", "Background update check failed: ${t.message}", t)
            Result.failure()
        }
    }

    private fun showUpdateNotification() {
        val channelId = "app_update_channel"
        val notificationId = 1003
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pembalasan Update",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi pembaruan aplikasi Bear Rush Mod"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            pendingFlags
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bear Rush Mod Update Baru!")
            .setContentText("Versi terbaru sudah rilis di Play Store. Ketuk untuk menginstal!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
