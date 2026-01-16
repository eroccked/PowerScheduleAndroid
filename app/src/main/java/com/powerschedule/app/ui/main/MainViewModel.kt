package com.powerschedule.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.QueueCardState
import com.powerschedule.app.data.models.Shutdown
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
        if (!isValidQueueFormat(queueNumber)) {
            showErrorAlert("❌ Невірний формат черги!")
            return
        }

        val finalName = if (name.isBlank()) queueNumber else name

        val newQueue = PowerQueue(name = finalName, queueNumber = queueNumber)
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
                val isTomorrow = isDateTomorrow(scheduleData.eventDate)

                // Перевіряємо чи зараз відключення (з урахуванням переходу через північ)
                val isPowerOn = if (isToday) {
                    !scheduleData.shutdowns.any { shutdown ->
                        isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                    }
                } else {
                    true
                }

                // Майбутні відключення сьогодні (які ще не почались)
                val futureShutdownsToday = if (isToday) {
                    scheduleData.shutdowns.filter { shutdown ->
                        isFutureShutdown(shutdown, currentTotalMinutes)
                    }
                } else {
                    emptyList()
                }

                val preview = when {
                    // Графік на завтра
                    isTomorrow -> {
                        if (scheduleData.shutdowns.isNotEmpty()) {
                            val firstShutdown = scheduleData.shutdowns.first()
                            val fromParts = firstShutdown.from.split(":").mapNotNull { it.toIntOrNull() }

                            // Якщо відключення о 00:00-02:59 і зараз вечір (після 20:00) - це "сьогодні вночі"
                            if (fromParts.size == 2 && fromParts[0] < 3 && currentHour >= 20) {
                                "Сьогодні вночі о ${firstShutdown.from}"
                            } else {
                                "Завтра відключення о ${firstShutdown.from}"
                            }
                        } else {
                            "Завтра відключень немає"
                        }
                    }
                    // Зараз відключення
                    !isPowerOn -> {
                        val currentShutdown = scheduleData.shutdowns.firstOrNull { shutdown ->
                            isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                        }

                        if (currentShutdown != null) {
                            "Увімкнуть о ${currentShutdown.to}"
                        } else {
                            "Поточний стан невідомий"
                        }
                    }
                    // Є майбутні відключення сьогодні
                    futureShutdownsToday.isNotEmpty() -> {
                        "Відключення о ${futureShutdownsToday.first().from}"
                    }
                    // Всі відключення сьогодні минули
                    scheduleData.shutdowns.isNotEmpty() -> {
                        "Сьогодні відключень більше немає"
                    }
                    // Відключень немає
                    else -> {
                        "Відключень немає"
                    }
                }

                updateQueueCardState(queue.id) {
                    it.copy(
                        isPowerOn = isPowerOn,
                        isDataAvailable = true,
                        schedulePreview = preview,
                        lastUpdated = timeFormatter.format(Date()),
                        isLoading = false,
                        scheduleData = scheduleData,
                        isFromCache = false
                    )
                }

                if (queue.isNotificationsEnabled) {
                    val minutesBefore = storageService.loadNotificationMinutes()
                    notificationService.scheduleShutdownNotifications(
                        shutdowns = scheduleData.shutdowns,
                        queueName = queue.name,
                        queueId = queue.id,
                        minutesBefore = minutesBefore,
                        eventDate = scheduleData.eventDate
                    )
                }

                // Зберігаємо в кеш
                val jsonString = json.encodeToString(scheduleData.shutdowns)
                storageService.saveScheduleJSON(jsonString, queue.id)
                storageService.saveScheduleCache(queue.id, scheduleData.eventDate, jsonString)

            }.onFailure {
                // Спробуємо завантажити з кешу
                val cache = storageService.loadScheduleCache(queue.id)

                if (cache != null && isDateToday(cache.eventDate)) {
                    // Є кеш на сьогодні - показуємо його
                    try {
                        val shutdowns: List<com.powerschedule.app.data.models.Shutdown> =
                            json.decodeFromString(cache.shutdownsJson)

                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
                        val currentTotalMinutes = currentHour * 60 + currentMinute

                        val isPowerOn = !shutdowns.any { shutdown ->
                            isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                        }

                        val futureShutdowns = shutdowns.filter { shutdown ->
                            isFutureShutdown(shutdown, currentTotalMinutes)
                        }

                        val preview = when {
                            !isPowerOn -> {
                                val currentShutdown = shutdowns.firstOrNull { shutdown ->
                                    isCurrentlyInShutdown(shutdown, currentTotalMinutes)
                                }
                                if (currentShutdown != null) "Увімкнуть о ${currentShutdown.to}"
                                else "Немає з'єднання"
                            }
                            futureShutdowns.isNotEmpty() -> {
                                "Відключення о ${futureShutdowns.first().from}"
                            }
                            shutdowns.isNotEmpty() -> {
                                "Сьогодні відключень більше немає"
                            }
                            else -> {
                                "Відключень немає"
                            }
                        }

                        val scheduleData = com.powerschedule.app.data.models.ScheduleData(
                            eventDate = cache.eventDate,
                            createdAt = "",
                            scheduleApprovedSince = "",
                            shutdowns = shutdowns
                        )

                        updateQueueCardState(queue.id) {
                            it.copy(
                                isPowerOn = isPowerOn,
                                isDataAvailable = true,
                                schedulePreview = preview,
                                lastUpdated = "⚠️ Офлайн",
                                isLoading = false,
                                scheduleData = scheduleData,
                                isFromCache = true
                            )
                        }
                    } catch (e: Exception) {
                        // Не вдалося розпарсити кеш
                        showNoConnectionState(queue.id)
                    }
                } else {
                    // Кешу немає або він застарілий
                    showNoConnectionState(queue.id)
                }
            }
        }
    }

    private fun showNoConnectionState(queueId: String) {
        updateQueueCardState(queueId) {
            it.copy(
                isPowerOn = true,
                isDataAvailable = false,
                schedulePreview = "Немає з'єднання",
                lastUpdated = timeFormatter.format(Date()),
                isLoading = false,
                isFromCache = false
            )
        }
    }
    /**
     * Перевіряє чи відключення ще не почалось (в майбутньому)
     */
    private fun isFutureShutdown(shutdown: Shutdown, currentTotalMinutes: Int): Boolean {
        val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
        if (fromParts.size != 2) return false

        val fromMinutes = fromParts[0] * 60 + fromParts[1]

        // Відключення в майбутньому, якщо:
        // 1. Час початку більший за поточний
        // 2. І ми зараз НЕ в цьому відключенні
        return fromMinutes > currentTotalMinutes && !isCurrentlyInShutdown(shutdown, currentTotalMinutes)
    }

    /**
     * Перевіряє чи поточний час потрапляє в період відключення
     * Враховує перехід через північ (наприклад 23:00 - 01:00 або 20:30 - 00:00)
     */
    private fun isCurrentlyInShutdown(shutdown: Shutdown, currentTotalMinutes: Int): Boolean {
        val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
        val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }

        if (fromParts.size != 2 || toParts.size != 2) return false

        val fromMinutes = fromParts[0] * 60 + fromParts[1]
        val toMinutes = toParts[0] * 60 + toParts[1]

        return if (toMinutes > fromMinutes) {
            // Звичайний випадок: 08:00 - 12:00
            currentTotalMinutes >= fromMinutes && currentTotalMinutes < toMinutes
        } else {
            // Перехід через північ: 20:30 - 00:00 або 23:00 - 01:00
            currentTotalMinutes >= fromMinutes || currentTotalMinutes < toMinutes
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

    private fun isDateTomorrow(dateString: String): Boolean {
        return try {
            val eventDate = dateFormatter.parse(dateString) ?: return false
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val eventCal = Calendar.getInstance().apply { time = eventDate }
            tomorrow.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                    tomorrow.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) { false }
    }

    private fun showErrorAlert(message: String) {
        _errorMessage.value = message
        _showError.value = true
    }

    fun dismissError() {
        _showError.value = false
    }
}