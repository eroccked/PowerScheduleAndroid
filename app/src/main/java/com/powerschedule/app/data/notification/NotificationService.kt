package com.powerschedule.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.powerschedule.app.MainActivity
import com.powerschedule.app.R
import com.powerschedule.app.data.models.Shutdown
import java.util.concurrent.TimeUnit

class NotificationService private constructor(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "power_schedule_channel"
        private const val WORK_TAG_SHUTDOWN = "shutdown_notification"

        @Volatile
        private var instance: NotificationService? = null

        fun getInstance(context: Context): NotificationService {
            return instance ?: synchronized(this) {
                instance ?: NotificationService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val workManager = WorkManager.getInstance(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    fun scheduleShutdownNotifications(
        shutdowns: List<Shutdown>,
        queueName: String,
        queueId: String,
        minutesBefore: Int
    ) {
        cancelNotifications(queueId)

        shutdowns.forEach { shutdown ->
            val notificationTime = shutdown.getNotificationTimeMillis(minutesBefore)
            if (notificationTime != null && notificationTime > System.currentTimeMillis()) {
                val delay = notificationTime - System.currentTimeMillis()

                val timeText = formatTimeText(minutesBefore)
                val data = workDataOf(
                    "queue_name" to queueName,
                    "shutdown_time" to shutdown.from,
                    "time_text" to timeText
                )

                val workRequest = OneTimeWorkRequestBuilder<ShutdownNotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag("$WORK_TAG_SHUTDOWN-$queueId")
                    .build()

                workManager.enqueue(workRequest)
            }
        }
    }

    fun showScheduleUpdateNotification(queueName: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.schedule_updated))
            .setContentText(context.getString(R.string.schedule_changed_for, queueName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9999, notification)
    }

    fun cancelNotifications(queueId: String) {
        workManager.cancelAllWorkByTag("$WORK_TAG_SHUTDOWN-$queueId")
    }

    fun cancelAllNotifications() {
        workManager.cancelAllWorkByTag(WORK_TAG_SHUTDOWN)
        notificationManager.cancelAll()
    }

    private fun formatTimeText(minutes: Int): String {
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins == 0) context.getString(R.string.in_x_hours, hours)
                else context.getString(R.string.in_x_hours_y_minutes, hours, mins)
            }
            minutes > 0 -> context.getString(R.string.in_x_minutes, minutes)
            else -> context.getString(R.string.now)
        }
    }
}

class ShutdownNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val queueName = inputData.getString("queue_name") ?: return Result.failure()
        val shutdownTime = inputData.getString("shutdown_time") ?: return Result.failure()
        val timeText = inputData.getString("time_text") ?: return Result.failure()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return Result.failure()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, "power_schedule_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚡ Скоро відключення!")
            .setContentText("$queueName: відключення о $shutdownTime ($timeText)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}