package com.powerschedule.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.QueueCardState
import com.powerschedule.app.data.notification.BackgroundUpdateWorker
import com.powerschedule.app.data.notification.NotificationService
import com.powerschedule.app.data.storage.StorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Job

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storageService = StorageService.getInstance(application)
    private val apiService = APIService.getInstance()
    private val notificationService = NotificationService.getInstance(application)

    private val _queues = MutableStateFlow<List<PowerQueue>>(emptyList())
    val queues: StateFlow<List<PowerQueue>> = _queues.asStateFlow()

    private val _queueCardStates = MutableStateFlow<Map<String, QueueCardState>>(emptyMap())
    val queueCardStates: StateFlow<Map<String, QueueCardState>> = _queueCardStates.asStateFlow()

    private val _showError = MutableStateFlow(false)
    val showError: StateFlow<Boolean> = _showError.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var refreshJob: Job? = null

    init {
        loadQueues()
        startBackgroundUpdates()
    }

    fun loadQueues() {
        _queues.value = storageService.loadQueues()
        refreshAllQueues()
    }

    fun refreshAllQueues() {
        refreshJob?.cancel()

        refreshJob = viewModelScope.launch {
            _queues.value.forEach { loadQueuePreview(it) }
        }
    }

    fun addQueue(name: String, queueNumber: String) {
        if (name.isBlank()) {
            showErrorAlert("❌ Введіть назву!")
            return
        }

        if (!isValidQueueFormat(queueNumber)) {
            showErrorAlert("❌ Невірний формат черги!")
            return
        }

        val newQueue = PowerQueue(name = name, queueNumber = queueNumber)
        storageService.addQueue(newQueue)
        loadQueues()
    }

    fun deleteQueue(queue: PowerQueue) {
        notificationService.cancelNotifications(queue.id)
        storageService.deleteQueue(queue)
        loadQueues()
    }

    fun toggleNotifications(queue: PowerQueue) {
        val updatedQueue = queue.copy(isNotificationsEnabled = !queue.isNotificationsEnabled)
        storageService.updateQueue(updatedQueue)
        if (!updatedQueue.isNotificationsEnabled) {
            notificationService.cancelNotifications(queue.id)
        }
        loadQueues()
    }

    fun loadQueuePreview(queue: PowerQueue) {
        viewModelScope.launch {
            updateQueueCardState(queue.id) { it.copy(isLoading = true) }

            val result = apiService.fetchSchedule(queue.queueNumber)

            result.onSuccess { scheduleData ->
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
                val currentTotalMinutes = currentHour * 60 + currentMinute
                val isToday = isDateToday(scheduleData.eventDate)

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
                } else {
                    true
                }

                val futureShutdownsToday = if (isToday) {
                    scheduleData.shutdowns.filter { shutdown ->
                        val parts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
                        if (parts.size == 2) {
                            val shutdownMinutes = parts[0] * 60 + parts[1]
                            shutdownMinutes > currentTotalMinutes
                        } else false
                    }
                } else {
                    emptyList()
                }

                val preview = when {
                    !isToday -> {
                        if (scheduleData.shutdowns.isNotEmpty()) {
                            "Завтра відключення о ${scheduleData.shutdowns.first().from}"
                        } else {
                            "Завтра відключень немає"
                        }
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

                        if (currentShutdown != null) {
                            "Увімкнуть о ${currentShutdown.to}"
                        } else {
                            "Поточний стан невідомий"
                        }
                    }
                    futureShutdownsToday.isNotEmpty() -> {
                        "Відключення о ${futureShutdownsToday.first().from}"
                    }
                    scheduleData.shutdowns.isNotEmpty() -> {
                        "Сьогодні відключень більше немає"
                    }
                    else -> {
                        "Відключень немає"
                    }
                }

                updateQueueCardState(queue.id) {
                    it.copy(
                        isPowerOn = isPowerOn,
                        schedulePreview = preview,
                        lastUpdated = timeFormatter.format(Date()),
                        isLoading = false,
                        scheduleData = scheduleData
                    )
                }

                if (queue.isNotificationsEnabled) {
                    val minutesBefore = storageService.loadNotificationMinutes()
                    notificationService.scheduleShutdownNotifications(
                        shutdowns = scheduleData.shutdowns,
                        queueName = queue.name,
                        queueId = queue.id,
                        minutesBefore = minutesBefore
                    )
                }

                val jsonString = json.encodeToString(scheduleData.shutdowns)
                storageService.saveScheduleJSON(jsonString, queue.id)

            }.onFailure {
                updateQueueCardState(queue.id) {
                    it.copy(
                        isPowerOn = false,
                        schedulePreview = "Помилка завантаження",
                        lastUpdated = timeFormatter.format(Date()),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun startBackgroundUpdates() {
        val interval = storageService.loadUpdateInterval()
        BackgroundUpdateWorker.schedule(getApplication(), interval)
    }

    private fun updateQueueCardState(queueId: String, update: (QueueCardState) -> QueueCardState) {
        val currentStates = _queueCardStates.value.toMutableMap()
        val currentState = currentStates[queueId] ?: QueueCardState()
        currentStates[queueId] = update(currentState)
        _queueCardStates.value = currentStates
    }

    private fun isValidQueueFormat(queue: String): Boolean {
        return "^\\d+\\.\\d+$".toRegex().matches(queue)
    }

    private fun isDateToday(dateString: String): Boolean {
        return try {
            val eventDate = dateFormatter.parse(dateString) ?: return true
            val today = Calendar.getInstance()
            val eventCal = Calendar.getInstance().apply { time = eventDate }
            today.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) { true }
    }

    private fun showErrorAlert(message: String) {
        _errorMessage.value = message
        _showError.value = true
    }

    fun dismissError() {
        _showError.value = false
    }
}