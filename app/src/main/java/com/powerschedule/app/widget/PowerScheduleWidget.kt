package com.powerschedule.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.powerschedule.app.MainActivity
import com.powerschedule.app.R
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.models.ScheduleData
import com.powerschedule.app.data.models.Shutdown
import com.powerschedule.app.data.storage.StorageService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.SimpleDateFormat
import java.util.*

class PowerScheduleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.powerschedule.app.REFRESH_WIDGET"

        // Запас після межі, щоб оновлення гарантовано потрапило вже в нову хвилину
        private const val BOUNDARY_BUFFER_MS = 5_000L

        // Година, з якої змінюється текст для завтрашнього графіка ("Сьогодні о" / "Вночі о")
        private const val EVENING_HOUR = 20

        fun getWidgetIds(context: Context): IntArray {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, PowerScheduleWidget::class.java)
            return appWidgetManager.getAppWidgetIds(widgetComponent)
        }

        private fun createViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_power_schedule)

            // Click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            return views
        }

        // Миттєво показує кеш (без мережі). Свіжі дані підтягує WidgetUpdateWorker
        fun showCached(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = createViews(context, appWidgetId)
            val storageService = StorageService.getInstance(context)

            if (storageService.loadQueues().isEmpty()) {
                applyNoQueues(views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val cache = storageService.loadWidgetCache(appWidgetId)
            if (cache != null) {
                applyDataToViews(
                    views,
                    cache.name,
                    cache.queueNumber,
                    cache.status,
                    cache.preview,
                    cache.updated,
                    cache.status == "Світло є"
                )
            } else {
                views.setTextViewText(R.id.widget_name, "Завантаження...")
                views.setTextViewText(R.id.widget_queue_number, "")
                views.setTextViewText(R.id.widget_status, "")
                views.setTextViewText(R.id.widget_preview, "Зачекайте")
                views.setTextViewText(R.id.widget_updated, "")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Оновлює всі віджети і планує наступне оновлення на найближчу межу відключення
        suspend fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val nextBoundaryDelays = getWidgetIds(context).toList().mapNotNull { widgetId ->
                refreshWidget(context, appWidgetManager, widgetId)
            }

            nextBoundaryDelays.minOrNull()?.let { delayMs ->
                WidgetUpdateWorker.scheduleBoundary(context, delayMs)
            }
        }

        // Завантажує свіжі дані і оновлює віджет. Повертається тільки після updateAppWidget.
        // Результат - через скільки мс наступна межа (початок/кінець відключення), або null
        suspend fun refreshWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Long? {
            val views = createViews(context, appWidgetId)
            val storageService = StorageService.getInstance(context)

            val queues = storageService.loadQueues()
            if (queues.isEmpty()) {
                applyNoQueues(views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return null
            }

            val savedQueueId = storageService.loadWidgetQueueId(appWidgetId)
            val queue = if (savedQueueId != null) {
                queues.find { it.id == savedQueueId } ?: queues.first()
            } else {
                queues.first()
            }

            val result = APIService.getInstance().fetchSchedule(queue.queueNumber)

            // fetchSchedule ловить усі винятки, включно зі скасуванням worker-а.
            // Якщо нас скасували (REPLACE) - не малюємо "помилку", новий worker оновить сам
            currentCoroutineContext().ensureActive()

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val freshSchedule = result.getOrNull()
            if (freshSchedule != null) {
                storageService.saveWidgetSchedule(queue.id, freshSchedule)
            }

            // Без мережі перераховуємо статус за останнім збереженим графіком
            val schedule = freshSchedule ?: storageService.loadWidgetSchedule(queue.id)
            if (schedule == null) {
                views.setTextViewText(R.id.widget_name, queue.name)
                views.setTextViewText(R.id.widget_queue_number, queue.queueNumber)
                views.setTextViewText(R.id.widget_status, "Невідомо")
                views.setImageViewResource(R.id.widget_status_icon, R.drawable.ic_status_unknown)
                views.setTextViewText(R.id.widget_preview, "Немає з'єднання")
                views.setTextViewText(R.id.widget_updated, timeFormatter.format(Date()))
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return null
            }

            val now = Calendar.getInstance()
            val (isPowerOn, preview) = computeState(schedule, now)
            val status = if (isPowerOn) "Світло є" else "Відключення"
            val updated = if (freshSchedule != null) {
                "Оновлено о ${timeFormatter.format(now.time)}"
            } else {
                "⚠️ ${timeFormatter.format(now.time)}"
            }

            storageService.saveWidgetCache(
                appWidgetId,
                queue.name,
                queue.queueNumber,
                status,
                preview,
                updated
            )

            applyDataToViews(
                views,
                queue.name,
                queue.queueNumber,
                status,
                preview,
                updated,
                isPowerOn
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)

            return millisUntilNextBoundary(schedule.shutdowns, now)
        }

        // Повертає (isPowerOn, preview)
        private fun computeState(scheduleData: ScheduleData, now: Calendar): Pair<Boolean, String> {
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentTotalMinutes = currentHour * 60 + currentMinute
            val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

            val isToday = try {
                val eventDate = dateFormatter.parse(scheduleData.eventDate)
                val eventCal = Calendar.getInstance().apply { time = eventDate!! }
                now.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
            } catch (e: Exception) {
                true
            }

            val isTomorrow = try {
                val eventDate = dateFormatter.parse(scheduleData.eventDate)
                val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                val eventCal = Calendar.getInstance().apply { time = eventDate!! }
                tomorrow.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                        tomorrow.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
            } catch (e: Exception) {
                false
            }

            val isPowerOn: Boolean
            val preview: String

            if (isToday) {
                isPowerOn = !scheduleData.shutdowns.any { shutdown ->
                    isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                }

                val futureShutdownsToday = scheduleData.shutdowns.filter { shutdown ->
                    isFutureShutdown(shutdown, currentTotalMinutes)
                }

                preview = when {
                    !isPowerOn -> {
                        val currentShutdown = scheduleData.shutdowns.firstOrNull { shutdown ->
                            isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                        }
                        if (currentShutdown != null) "Увімкнуть о ${currentShutdown.to}"
                        else "Відключення"
                    }
                    futureShutdownsToday.isNotEmpty() -> {
                        "Відключення о ${futureShutdownsToday.first().from}"
                    }
                    scheduleData.shutdowns.isNotEmpty() -> "Сьогодні більше немає"
                    else -> "Відключень немає"
                }
            } else if (isTomorrow) {
                // Перевіряємо чи ми в відключенні з завтрашнього графіка
                val currentlyInTomorrowShutdown = scheduleData.shutdowns.any { shutdown ->
                    isCurrentlyInTomorrowShutdown(shutdown, currentTotalMinutes)
                }

                isPowerOn = !currentlyInTomorrowShutdown

                preview = if (currentlyInTomorrowShutdown) {
                    val currentShutdown = scheduleData.shutdowns.firstOrNull { shutdown ->
                        isCurrentlyInTomorrowShutdown(shutdown, currentTotalMinutes)
                    }
                    if (currentShutdown != null) "Увімкнуть о ${currentShutdown.to}"
                    else "Відключення"
                } else if (scheduleData.shutdowns.isNotEmpty()) {
                    val firstShutdown = scheduleData.shutdowns.first()
                    val fromParts = firstShutdown.from.split(":").mapNotNull { it.toIntOrNull() }

                    if (fromParts.size == 2) {
                        val shutdownHour = fromParts[0]
                        when {
                            shutdownHour < 3 && currentHour >= EVENING_HOUR -> "Вночі о ${firstShutdown.from}"
                            shutdownHour >= EVENING_HOUR -> "Сьогодні о ${firstShutdown.from}"
                            else -> "Завтра о ${firstShutdown.from}"
                        }
                    } else {
                        "Завтра о ${firstShutdown.from}"
                    }
                } else {
                    "Завтра відключень немає"
                }
            } else {
                isPowerOn = true
                preview = "Графік недоступний"
            }

            return isPowerOn to preview
        }

        // Мс до найближчої хвилини, коли стан віджета може змінитись:
        // початок/кінець кожного відключення, північ (сьогодні/завтра) та 20:00 (текст завтрашнього графіка)
        private fun millisUntilNextBoundary(shutdowns: List<Shutdown>, now: Calendar): Long {
            val currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val boundaries = shutdowns
                .flatMap { listOf(it.from, it.to) }
                .mapNotNull { time ->
                    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
                    if (parts.size == 2) parts[0] * 60 + parts[1] else null
                } + listOf(0, EVENING_HOUR * 60)

            val minutesAhead = boundaries
                .map { ((it - currentTotalMinutes) % 1440 + 1440) % 1440 }
                .filter { it > 0 }
                .minOrNull() ?: 1440

            val millisIntoMinute = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
            return minutesAhead * 60_000L - millisIntoMinute + BOUNDARY_BUFFER_MS
        }

        private fun applyNoQueues(views: RemoteViews) {
            views.setTextViewText(R.id.widget_name, "Немає черг")
            views.setTextViewText(R.id.widget_queue_number, "")
            views.setTextViewText(R.id.widget_status, "Додайте чергу")
            views.setTextViewText(R.id.widget_preview, "в додатку")
            views.setTextViewText(R.id.widget_updated, "")
        }

        private fun isFutureShutdown(shutdown: Shutdown, currentTotalMinutes: Int): Boolean {
            val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
            if (fromParts.size != 2) return false

            val fromMinutes = fromParts[0] * 60 + fromParts[1]

            return fromMinutes > currentTotalMinutes && !isCurrentlyInShutdown(shutdown, currentTotalMinutes)
        }

        private fun isCurrentlyInShutdown(shutdown: Shutdown, currentTotalMinutes: Int): Boolean {
            val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
            val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }

            if (fromParts.size != 2 || toParts.size != 2) return false

            val fromMinutes = fromParts[0] * 60 + fromParts[1]
            val toMinutes = toParts[0] * 60 + toParts[1]

            return if (toMinutes > fromMinutes) {
                currentTotalMinutes >= fromMinutes && currentTotalMinutes < toMinutes
            } else {
                currentTotalMinutes >= fromMinutes || currentTotalMinutes < toMinutes
            }
        }

        private fun isCurrentlyInTomorrowShutdown(shutdown: Shutdown, currentTotalMinutes: Int): Boolean {
            val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
            val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }

            if (fromParts.size != 2 || toParts.size != 2) return false

            val fromMinutes = fromParts[0] * 60 + fromParts[1]
            val toMinutes = toParts[0] * 60 + toParts[1]

            // Якщо відключення переходить через північ і починається ввечері
            if (fromMinutes > toMinutes && fromMinutes >= EVENING_HOUR * 60) {
                // Перевіряємо чи зараз ми у вечірній частині
                if (currentTotalMinutes >= fromMinutes) {
                    return true
                }
            }

            return false
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

            val iconRes = when (status) {
                "Невідомо" -> R.drawable.ic_status_unknown
                "Світло є" -> R.drawable.ic_status_on
                else -> R.drawable.ic_status_off
            }
            views.setImageViewResource(R.id.widget_status_icon, iconRes)

            views.setTextViewText(R.id.widget_preview, preview)
            views.setTextViewText(R.id.widget_updated, updated)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            showCached(context, appWidgetManager, appWidgetId)
        }
        WidgetUpdateWorker.enqueue(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                onUpdate(context, appWidgetManager, getWidgetIds(context))
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Останній віджет видалено - більше не плануємо оновлення
        WidgetUpdateWorker.cancelAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val storageService = StorageService.getInstance(context)
        for (appWidgetId in appWidgetIds) {
            storageService.removeWidgetQueueId(appWidgetId)
            storageService.removeWidgetCache(appWidgetId)
        }
    }
}
