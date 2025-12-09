package com.powerschedule.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.powerschedule.app.data.models.PowerQueue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StorageService private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "power_schedule_prefs"
        private const val QUEUES_KEY = "saved_queues"
        private const val UPDATE_INTERVAL_KEY = "update_interval"
        private const val NOTIFICATION_MINUTES_KEY = "notification_minutes_before"
        private const val SCHEDULE_PREFIX = "schedule_"

        @Volatile
        private var instance: StorageService? = null

        fun getInstance(context: Context): StorageService {
            return instance ?: synchronized(this) {
                instance ?: StorageService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveQueues(queues: List<PowerQueue>) {
        val encoded = json.encodeToString(queues)
        prefs.edit().putString(QUEUES_KEY, encoded).apply()
    }

    fun loadQueues(): List<PowerQueue> {
        val data = prefs.getString(QUEUES_KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQueue(queue: PowerQueue) {
        val queues = loadQueues().toMutableList()
        queues.add(queue)
        saveQueues(queues)
    }

    fun deleteQueue(queue: PowerQueue) {
        val queues = loadQueues().toMutableList()
        queues.removeAll { it.id == queue.id }
        saveQueues(queues)
    }

    fun updateQueue(queue: PowerQueue) {
        val queues = loadQueues().toMutableList()
        val index = queues.indexOfFirst { it.id == queue.id }
        if (index != -1) {
            queues[index] = queue
            saveQueues(queues)
        }
    }

    fun saveUpdateInterval(minutes: Int) {
        prefs.edit().putInt(UPDATE_INTERVAL_KEY, minutes).apply()
    }

    fun loadUpdateInterval(): Int {
        val interval = prefs.getInt(UPDATE_INTERVAL_KEY, 0)
        return if (interval > 0) interval else 15
    }

    fun saveNotificationMinutes(minutes: Int) {
        prefs.edit().putInt(NOTIFICATION_MINUTES_KEY, minutes).apply()
    }

    fun loadNotificationMinutes(): Int {
        val minutes = prefs.getInt(NOTIFICATION_MINUTES_KEY, 0)
        return if (minutes > 0) minutes else 30
    }

    fun saveScheduleJSON(jsonStr: String, queueId: String) {
        prefs.edit().putString("$SCHEDULE_PREFIX$queueId", jsonStr).apply()
    }

    fun loadScheduleJSON(queueId: String): String? {
        return prefs.getString("$SCHEDULE_PREFIX$queueId", null)
    }

    fun saveWidgetQueueId(widgetId: Int, queueId: String) {
        prefs.edit().putString("widget_queue_$widgetId", queueId).apply()
    }

    fun loadWidgetQueueId(widgetId: Int): String? {
        return prefs.getString("widget_queue_$widgetId", null)
    }

    fun removeWidgetQueueId(widgetId: Int) {
        prefs.edit().remove("widget_queue_$widgetId").apply()
    }
    fun saveWidgetCache(widgetId: Int, name: String, queueNumber: String, status: String, preview: String, updated: String) {
        prefs.edit()
            .putString("widget_cache_${widgetId}_name", name)
            .putString("widget_cache_${widgetId}_queue", queueNumber)
            .putString("widget_cache_${widgetId}_status", status)
            .putString("widget_cache_${widgetId}_preview", preview)
            .putString("widget_cache_${widgetId}_updated", updated)
            .apply()
    }

    fun loadWidgetCache(widgetId: Int): WidgetCache? {
        val name = prefs.getString("widget_cache_${widgetId}_name", null) ?: return null
        return WidgetCache(
            name = name,
            queueNumber = prefs.getString("widget_cache_${widgetId}_queue", "") ?: "",
            status = prefs.getString("widget_cache_${widgetId}_status", "") ?: "",
            preview = prefs.getString("widget_cache_${widgetId}_preview", "") ?: "",
            updated = prefs.getString("widget_cache_${widgetId}_updated", "") ?: ""
        )
    }

    data class WidgetCache(
        val name: String,
        val queueNumber: String,
        val status: String,
        val preview: String,
        val updated: String
    )
}