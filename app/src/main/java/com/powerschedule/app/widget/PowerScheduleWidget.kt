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

    override fun onEnabled(context: Context) {}

    override fun onDisabled(context: Context) {}

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_power_schedule)

            // Click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Load data
            val storageService = StorageService.getInstance(context)
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

            // Отримуємо збережену чергу для цього віджета
            val savedQueueId = storageService.loadWidgetQueueId(appWidgetId)
            val queue = if (savedQueueId != null) {
                queues.find { it.id == savedQueueId } ?: queues.first()
            } else {
                queues.first()
            }

            views.setTextViewText(R.id.widget_name, queue.name)
            views.setTextViewText(R.id.widget_queue_number, queue.queueNumber)

            // Fetch schedule
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

                    views.setTextViewText(R.id.widget_status, if (isPowerOn) "Світло є" else "Відключення")
                    views.setImageViewResource(
                        R.id.widget_status_icon,
                        if (isPowerOn) R.drawable.ic_status_on else R.drawable.ic_status_off
                    )
                    views.setTextViewText(R.id.widget_preview, preview)
                    views.setTextViewText(R.id.widget_updated, "Оновлено о ${timeFormatter.format(Date())}")

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }.onFailure {
                    views.setTextViewText(R.id.widget_status, "Помилка")
                    views.setTextViewText(R.id.widget_preview, "Оновіть віджет")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}