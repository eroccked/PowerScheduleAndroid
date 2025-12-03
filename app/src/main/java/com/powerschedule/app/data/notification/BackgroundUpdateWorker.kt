package com.powerschedule.app.data.notification

import android.content.Context
import androidx.work.*
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.storage.StorageService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class BackgroundUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "schedule_update_work"

        fun schedule(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BackgroundUpdateWorker>(
                intervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    private val storageService = StorageService.getInstance(applicationContext)
    private val apiService = APIService.getInstance()
    private val notificationService = NotificationService.getInstance(applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        return try {
            val queues = storageService.loadQueues()

            queues.filter { it.isAutoUpdateEnabled }.forEach { queue ->
                val result = apiService.fetchSchedule(queue.queueNumber)

                result.onSuccess { scheduleData ->
                    val jsonString = json.encodeToString(scheduleData.shutdowns)
                    val savedJSON = storageService.loadScheduleJSON(queue.id)

                    if (savedJSON != null && savedJSON != jsonString) {
                        notificationService.showScheduleUpdateNotification(queue.name)
                    }

                    storageService.saveScheduleJSON(jsonString, queue.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}