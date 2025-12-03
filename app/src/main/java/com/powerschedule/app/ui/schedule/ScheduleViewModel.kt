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

            val result = apiService.fetchSchedule(currentQueue.queueNumber)

            result.onSuccess { scheduleData ->
                _uiState.value = _uiState.value.copy(isLoading = false, scheduleData = scheduleData)

                if (_notificationsEnabled.value) {
                    scheduleNotifications(scheduleData)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        updateQueueSettings()

        if (enabled) {
            _uiState.value.scheduleData?.let { scheduleNotifications(it) }
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