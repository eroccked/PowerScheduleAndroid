package com.powerschedule.app.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.ScheduleData
import com.powerschedule.app.data.models.ScheduleUiState
import com.powerschedule.app.data.notification.NotificationService
import com.powerschedule.app.data.storage.StorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScheduleViewModel(
    application: Application,
    private val queueId: String
) : AndroidViewModel(application) {

    private val storageService = StorageService.getInstance(application)
    private val apiService = APIService.getInstance()
    private val notificationService = NotificationService.getInstance(application)

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _queue = MutableStateFlow<PowerQueue?>(null)
    val queue: StateFlow<PowerQueue?> = _queue.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(true)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _todaySchedule = MutableStateFlow<ScheduleData?>(null)
    val todaySchedule: StateFlow<ScheduleData?> = _todaySchedule.asStateFlow()

    private val _tomorrowSchedule = MutableStateFlow<ScheduleData?>(null)
    val tomorrowSchedule: StateFlow<ScheduleData?> = _tomorrowSchedule.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _dayLabels = MutableStateFlow(Pair("Сьогодні", "Завтра"))
    val dayLabels: StateFlow<Pair<String, String>> = _dayLabels.asStateFlow()

    private val _hasTwoDays = MutableStateFlow(false)
    val hasTwoDays: StateFlow<Boolean> = _hasTwoDays.asStateFlow()

    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    init {
        loadQueue()
        fetchSchedule()
    }

    private fun loadQueue() {
        val queues = storageService.loadQueues()
        val foundQueue = queues.find { it.id == queueId }
        _queue.value = foundQueue
        _notificationsEnabled.value = foundQueue?.isNotificationsEnabled ?: false
        _autoUpdateEnabled.value = foundQueue?.isAutoUpdateEnabled ?: true
    }

    fun fetchSchedule() {
        val currentQueue = _queue.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = apiService.fetchAllSchedules(currentQueue.queueNumber)

            result.onSuccess { schedules ->
                processSchedules(schedules)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scheduleData = _todaySchedule.value ?: schedules.firstOrNull()
                )

                // Сповіщення для сьогоднішнього графіка
                if (_notificationsEnabled.value) {
                    _todaySchedule.value?.let { scheduleNotifications(it) }
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    private fun processSchedules(schedules: List<ScheduleData>) {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        var todayScheduleData: ScheduleData? = null
        var tomorrowScheduleData: ScheduleData? = null
        var yesterdayScheduleData: ScheduleData? = null

        // Спочатку розсортуємо по датах
        for (schedule in schedules) {
            val eventDate = try {
                dateFormatter.parse(schedule.eventDate)
            } catch (e: Exception) {
                continue
            } ?: continue

            val eventCal = Calendar.getInstance().apply { time = eventDate }

            val isToday = today.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)

            val isYesterday = yesterday.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)

            val isTomorrow = tomorrow.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
                    tomorrow.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)

            when {
                isToday -> todayScheduleData = schedule
                isTomorrow -> tomorrowScheduleData = schedule
                isYesterday -> yesterdayScheduleData = schedule
            }
        }

        // Визначаємо що показувати
        val firstDaySchedule: ScheduleData?
        val secondDaySchedule: ScheduleData?
        val labels: Pair<String, String>

        when {
            // Є сьогодні і завтра
            todayScheduleData != null && tomorrowScheduleData != null -> {
                firstDaySchedule = todayScheduleData
                secondDaySchedule = tomorrowScheduleData
                labels = Pair("Сьогодні", "Завтра")
            }
            // Є тільки сьогодні
            todayScheduleData != null -> {
                firstDaySchedule = todayScheduleData
                secondDaySchedule = null
                labels = Pair("Сьогодні", "")
            }
            // Є тільки завтра
            tomorrowScheduleData != null -> {
                firstDaySchedule = tomorrowScheduleData
                secondDaySchedule = null
                labels = Pair("Завтра", "")
            }
            // Є вчора і сьогодні
            yesterdayScheduleData != null && todayScheduleData != null -> {
                firstDaySchedule = yesterdayScheduleData
                secondDaySchedule = todayScheduleData
                labels = Pair("Вчора", "Сьогодні")
            }
            // Є тільки вчора
            yesterdayScheduleData != null -> {
                firstDaySchedule = yesterdayScheduleData
                secondDaySchedule = null
                labels = Pair("Вчора", "")
            }
            // Нічого не знайшли - беремо перший з списку
            schedules.isNotEmpty() -> {
                firstDaySchedule = schedules.first()
                secondDaySchedule = null
                labels = Pair("Графік", "")
            }
            else -> {
                firstDaySchedule = null
                secondDaySchedule = null
                labels = Pair("", "")
            }
        }

        _todaySchedule.value = firstDaySchedule
        _tomorrowSchedule.value = secondDaySchedule
        _dayLabels.value = labels
        _hasTwoDays.value = firstDaySchedule != null && secondDaySchedule != null
        _selectedDayIndex.value = 0
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index
    }

    fun getCurrentScheduleData(): ScheduleData? {
        return if (_selectedDayIndex.value == 0) {
            _todaySchedule.value
        } else {
            _tomorrowSchedule.value
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        updateQueueSettings()

        if (enabled) {
            _todaySchedule.value?.let { scheduleNotifications(it) }
        } else {
            _queue.value?.let { notificationService.cancelNotifications(it.id) }
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        updateQueueSettings()
    }

    private fun updateQueueSettings() {
        val currentQueue = _queue.value ?: return
        val updatedQueue = currentQueue.copy(
            isNotificationsEnabled = _notificationsEnabled.value,
            isAutoUpdateEnabled = _autoUpdateEnabled.value
        )
        storageService.updateQueue(updatedQueue)
        _queue.value = updatedQueue
    }

    private fun scheduleNotifications(scheduleData: ScheduleData) {
        val currentQueue = _queue.value ?: return
        val minutesBefore = storageService.loadNotificationMinutes()

        notificationService.scheduleShutdownNotifications(
            shutdowns = scheduleData.shutdowns,
            queueName = currentQueue.name,
            queueId = currentQueue.id,
            minutesBefore = minutesBefore
        )
    }
}

class ScheduleViewModelFactory(
    private val application: Application,
    private val queueId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleViewModel(application, queueId) as T
    }
}