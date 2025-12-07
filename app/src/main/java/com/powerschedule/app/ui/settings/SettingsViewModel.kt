package com.powerschedule.app.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerschedule.app.data.api.APIService
import com.powerschedule.app.data.models.SettingsState
import com.powerschedule.app.data.notification.BackgroundUpdateWorker
import com.powerschedule.app.data.notification.NotificationService
import com.powerschedule.app.data.storage.StorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val storageService = StorageService.getInstance(application)
    private val apiService = APIService.getInstance()
    private val notificationService = NotificationService.getInstance(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _showDeleteAllDialog = MutableStateFlow(false)
    val showDeleteAllDialog: StateFlow<Boolean> = _showDeleteAllDialog.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init { loadData() }

    fun loadData() {
        val queues = storageService.loadQueues()
        _state.value = SettingsState(
            updateInterval = storageService.loadUpdateInterval(),
            notificationMinutes = storageService.loadNotificationMinutes(),
            notificationsEnabled = notificationService.areNotificationsEnabled(),
            totalQueues = queues.size,
            activeQueues = queues.count { it.isAutoUpdateEnabled }
        )
    }

    fun setUpdateInterval(minutes: Int) {
        storageService.saveUpdateInterval(minutes)
        _state.value = _state.value.copy(updateInterval = minutes)
        BackgroundUpdateWorker.schedule(getApplication(), minutes)
    }
    
    suspend fun setNotificationMinutes(minutes: Int) {
        storageService.saveNotificationMinutes(minutes)
        _state.value = _state.value.copy(notificationMinutes = minutes)
    }

    fun checkForUpdatesNow() {
        viewModelScope.launch {
            storageService.loadQueues().filter { it.isAutoUpdateEnabled }.forEach { queue ->
                apiService.fetchSchedule(queue.queueNumber).onSuccess { scheduleData ->
                    val jsonString = json.encodeToString(scheduleData.shutdowns)
                    val savedJSON = storageService.loadScheduleJSON(queue.id)
                    if (savedJSON != null && savedJSON != jsonString) {
                        notificationService.showScheduleUpdateNotification(queue.name)
                    }
                    storageService.saveScheduleJSON(jsonString, queue.id)
                }
            }
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, getApplication<Application>().packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(intent)
    }

    fun showDeleteAllConfirmation() { _showDeleteAllDialog.value = true }
    fun dismissDeleteAllDialog() { _showDeleteAllDialog.value = false }

    fun deleteAllQueues() {
        storageService.saveQueues(emptyList())
        notificationService.cancelAllNotifications()
        loadData()
        _showDeleteAllDialog.value = false
    }

    fun getNotificationTimeText(): String {
        val minutes = _state.value.notificationMinutes
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            minutes < 60 -> "$minutes хв"
            mins == 0 -> "$hours год"
            else -> "$hours год $mins хв"
        }
    }
}