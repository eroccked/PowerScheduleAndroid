package com.powerschedule.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.powerschedule.app.data.notification.BackgroundUpdateWorker
import com.powerschedule.app.data.storage.StorageService

class PowerScheduleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channel
        createNotificationChannel()

        // Schedule background updates
        val updateInterval = StorageService.getInstance(this).loadUpdateInterval()
        BackgroundUpdateWorker.schedule(this, updateInterval)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "power_schedule_channel"
    }
}
