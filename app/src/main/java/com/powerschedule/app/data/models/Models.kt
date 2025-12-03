package com.powerschedule.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID


@Serializable
data class PowerQueue(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val queueNumber: String,
    val isNotificationsEnabled: Boolean = false,
    val isAutoUpdateEnabled: Boolean = true
)


@Serializable
data class ScheduleResponse(
    val eventDate: String,
    val createdAt: String,
    val scheduleApprovedSince: String,
    val queues: Map<String, List<Shutdown>>
)


@Serializable
data class Shutdown(
    val from: String,
    val to: String,
    @SerialName("shutdownHours")
    val shutdownHours: String
) {
    val durationMinutes: Int
        get() {
            val fromParts = from.split(":").mapNotNull { it.toIntOrNull() }
            val toParts = to.split(":").mapNotNull { it.toIntOrNull() }

            if (fromParts.size != 2 || toParts.size != 2) return 0

            val fromMinutes = fromParts[0] * 60 + fromParts[1]
            val toMinutes = toParts[0] * 60 + toParts[1]

            return toMinutes - fromMinutes
        }

    fun getNotificationTimeMillis(minutesBefore: Int): Long? {
        val parts = from.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return null

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, parts[0])
        calendar.set(java.util.Calendar.MINUTE, parts[1])
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.add(java.util.Calendar.MINUTE, -minutesBefore)

        return calendar.timeInMillis
    }
}


data class ScheduleData(
    val eventDate: String,
    val createdAt: String,
    val scheduleApprovedSince: String,
    val shutdowns: List<Shutdown>
) {
    val totalMinutesWithoutPower: Int
        get() = shutdowns.sumOf { it.durationMinutes }

    val totalHours: Int
        get() = totalMinutesWithoutPower / 60

    val remainingMinutes: Int
        get() = totalMinutesWithoutPower % 60

    val hourlyTimeline: List<Boolean>
        get() {
            val timeline = MutableList(24) { true }

            for (shutdown in shutdowns) {
                val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
                val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }

                if (fromParts.size != 2 || toParts.size != 2) continue

                val fromHour = fromParts[0]
                val toHour = toParts[0]

                for (hour in fromHour until minOf(toHour, 24)) {
                    timeline[hour] = false
                }
            }

            return timeline
        }
}


data class QueueCardState(
    val isPowerOn: Boolean = true,
    val schedulePreview: String = "Завантаження...",
    val lastUpdated: String = "",
    val isLoading: Boolean = false,
    val scheduleData: ScheduleData? = null
)


data class ScheduleUiState(
    val isLoading: Boolean = false,
    val scheduleData: ScheduleData? = null,
    val errorMessage: String? = null
)


data class SettingsState(
    val updateInterval: Int = 15,
    val notificationMinutes: Int = 30,
    val notificationsEnabled: Boolean = false,
    val totalQueues: Int = 0,
    val activeQueues: Int = 0
)