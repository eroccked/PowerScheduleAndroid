package com.powerschedule.app.data.api

import com.powerschedule.app.data.models.ScheduleData
import com.powerschedule.app.data.models.ScheduleResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

sealed class APIError : Exception() {
    object InvalidURL : APIError() {
        private fun readResolve(): Any = InvalidURL
        override val message: String = "❌ Невірне посилання"
    }
    object ServerError : APIError() {
        private fun readResolve(): Any = ServerError
        override val message: String = "❌ Помилка сервера"
    }
    object NoData : APIError() {
        private fun readResolve(): Any = NoData
        override val message: String = "❌ Немає даних"
    }
}

class APIService {

    companion object {
        private const val BASE_URL = "https://be-svitlo.oe.if.ua"

        @Volatile
        private var instance: APIService? = null

        fun getInstance(): APIService {
            return instance ?: synchronized(this) {
                instance ?: APIService().also { instance = it }
            }
        }
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    suspend fun fetchSchedule(queueNumber: String): Result<ScheduleData> {
        return try {
            val url = "$BASE_URL/schedule-by-queue?queue=$queueNumber"

            val response: List<ScheduleResponse> = client.get(url).body()

            val scheduleResponse = response.firstOrNull()
                ?: return Result.failure(APIError.NoData)

            val shutdowns = scheduleResponse.queues[queueNumber]
                ?: return Result.failure(APIError.NoData)

            val scheduleData = ScheduleData(
                eventDate = scheduleResponse.eventDate,
                createdAt = scheduleResponse.createdAt,
                scheduleApprovedSince = scheduleResponse.scheduleApprovedSince,
                shutdowns = shutdowns
            )

            Result.success(scheduleData)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(APIError.ServerError)
        }
    }
}