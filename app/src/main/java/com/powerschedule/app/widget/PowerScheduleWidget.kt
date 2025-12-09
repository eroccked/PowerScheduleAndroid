package com.powerschedule.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.powerschedule.app.MainActivity
import com.powerschedule.app.R
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.storage.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PowerScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val storageService = StorageService.getInstance(context)
        for (appWidgetId in appWidgetIds) {
            storageService.removeWidgetQueueId(appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_power_schedule)
            val storageService = StorageService.getInstance(context)

            // Click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // 1. Спочатку показуємо кеш (якщо є)
            val cache = storageService.loadWidgetCache(appWidgetId)
            if (cache != null) {
                applyDataToViews(views, cache.name, cache.queueNumber, cache.status, cache.preview, cache.updated, cache.status == "Світло є")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                // Показуємо "Завантаження..." замість "Оновіть віджет"
                views.setTextViewText(R.id.widget_name, "Завантаження...")
                views.setTextViewText(R.id.widget_queue_number, "")
                views.setTextViewText(R.id.widget_status, "")
                views.setTextViewText(R.id.widget_preview, "Зачекайте")
                views.setTextViewText(R.id.widget_updated, "")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // 2. Завантажуємо свіжі дані
            val queues = storageService.loadQueues()
            if (queues.isEmpty()) {
                views.setTextViewText(R.id.widget_name, "Немає черг")
                views.setTextViewText(R.id.widget_queue_number, "")
                views.setTextViewText(R.id.widget_status, "Додайте чергу")
                views.setTextViewText(R.id.widget_preview, "в додатку")
                views.setTextViewText(R.id.widget_updated, "")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val savedQueueId = storageService.loadWidgetQueueId(appWidgetId)
            val queue = if (savedQueueId != null) {
                queues.find { it.id == savedQueueId } ?: queues.first()
            } else {
                queues.first()
            }

            // Fetch schedule в фоні
            CoroutineScope(Dispatchers.IO).launch {
                val apiService = APIService.getInstance()
                val result = apiService.fetchSchedule(queue.queueNumber)

                result.onSuccess { scheduleData ->
                    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
                    val currentTotalMinutes = currentHour * 60 + currentMinute
                    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

                    val isToday = try {
                        val eventDate = dateFormatter.parse(scheduleData.eventDate)
                        val today = Calendar.getInstance()
                        val eventCal = Calendar.getInstance().apply { time = eventDate!! }
                        today.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                                today.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
                    } catch (e: Exception) { true }

                    val isPowerOn = if (isToday) {
                        !scheduleData.shutdowns.any { shutdown ->
                            val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
                            val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }
                            if (fromParts.size == 2 && toParts.size == 2) {
                                val fromMinutes = fromParts[0] * 60 + fromParts[1]
                                val toMinutes = toParts[0] * 60 + toParts[1]
                                currentTotalMinutes >= fromMinutes && currentTotalMinutes < toMinutes
                            } else false
                        }
                    } else true

                    val futureShutdownsToday = if (isToday) {
                        scheduleData.shutdowns.filter { shutdown ->
                            val parts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
                            if (parts.size == 2) {
                                val shutdownMinutes = parts[0] * 60 + parts[1]
                                shutdownMinutes > currentTotalMinutes
                            } else false
                        }
                    } else emptyList()

                    val preview = when {
                        !isToday -> {
                            if (scheduleData.shutdowns.isNotEmpty()) {
                                "Завтра о ${scheduleData.shutdowns.first().from}"
                            } else "Завтра відключень немає"
                        }
                        !isPowerOn -> {
                            val currentShutdown = scheduleData.shutdowns.firstOrNull { shutdown ->
                                val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
                                val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }
                                if (fromParts.size == 2 && toParts.size == 2) {
                                    val fromMinutes = fromParts[0] * 60 + fromParts[1]
                                    val toMinutes = toParts[0] * 60 + toParts[1]
                                    currentTotalMinutes >= fromMinutes && currentTotalMinutes < toMinutes
                                } else false
                            }
                            if (currentShutdown != null) "Увімкнуть о ${currentShutdown.to}"
                            else "Поточний стан"
                        }
                        futureShutdownsToday.isNotEmpty() -> {
                            "Відключення о ${futureShutdownsToday.first().from}"
                        }
                        scheduleData.shutdowns.isNotEmpty() -> "Сьогодні більше немає"
                        else -> "Відключень немає"
                    }

                    val status = if (isPowerOn) "Світло є" else "Відключення"
                    val updated = "Оновлено о ${timeFormatter.format(Date())}"

                    // Зберігаємо в кеш
                    storageService.saveWidgetCache(
                        appWidgetId,
                        queue.name,
                        queue.queueNumber,
                        status,
                        preview,
                        updated
                    )

                    // Оновлюємо віджет
                    applyDataToViews(views, queue.name, queue.queueNumber, status, preview, updated, isPowerOn)
                    appWidgetManager.updateAppWidget(appWidgetId, views)

                }.onFailure {
                    // При помилці залишаємо кеш, не показуємо "Оновіть віджет"
                    if (cache == null) {
                        views.setTextViewText(R.id.widget_name, queue.name)
                        views.setTextViewText(R.id.widget_queue_number, queue.queueNumber)
                        views.setTextViewText(R.id.widget_status, "Помилка мережі")
                        views.setTextViewText(R.id.widget_preview, "Натисніть для оновлення")
                        views.setTextViewText(R.id.widget_updated, "")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }

        private fun applyDataToViews(
            views: RemoteViews,
            name: String,
            queueNumber: String,
            status: String,
            preview: String,
            updated: String,
            isPowerOn: Boolean
        ) {
            views.setTextViewText(R.id.widget_name, name)
            views.setTextViewText(R.id.widget_queue_number, queueNumber)
            views.setTextViewText(R.id.widget_status, status)
            views.setImageViewResource(
                R.id.widget_status_icon,
                if (isPowerOn) R.drawable.ic_status_on else R.drawable.ic_status_off
            )
            views.setTextViewText(R.id.widget_preview, preview)
            views.setTextViewText(R.id.widget_updated, updated)
        }
    }
}