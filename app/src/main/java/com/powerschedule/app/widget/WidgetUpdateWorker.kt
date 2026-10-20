package com.powerschedule.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// Оновлення віджетів у WorkManager замість корутини в BroadcastReceiver:
// процес живе, поки doWork() не завершиться, тож updateAppWidget гарантовано виконається
class WidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "widget_update_work"
        const val BOUNDARY_WORK_NAME = "widget_boundary_work"

        fun enqueue(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()

            // REPLACE - щоб worker завжди читав актуальний список черг
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        // Оновлення на найближчій межі відключення. REPLACE - завжди лишається тільки найсвіжіший розклад
        fun scheduleBoundary(context: Context, delayMs: Long) {
            val workRequest = OneTimeWorkRequestBuilder<WidgetBoundaryWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                BOUNDARY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelAll(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(BOUNDARY_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (PowerScheduleWidget.getWidgetIds(applicationContext).isEmpty()) {
            return Result.success()
        }
        PowerScheduleWidget.refreshAllWidgets(applicationContext)
        return Result.success()
    }
}

// Окремий worker для межі: лише запускає звичайне оновлення.
// Так WidgetUpdateWorker, плануючи наступну межу через REPLACE, не скасовує сам себе
class WidgetBoundaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        WidgetUpdateWorker.enqueue(applicationContext)
        return Result.success()
    }
}
