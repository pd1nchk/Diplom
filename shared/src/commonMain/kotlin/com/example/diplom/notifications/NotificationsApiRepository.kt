package com.example.diplom.notifications

import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.api.NotificationDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class NotificationsApiRepository {
    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            connectTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            socketTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
        }
    }

    suspend fun listForUser(ticket: String): List<NotificationDto> = runCatching {
        val response = client.get("$SERVER_BASE_URL/notifications/$ticket")
        if (response.status != HttpStatusCode.OK) {
            return@runCatching emptyList()
        }
        response.body<List<NotificationDto>>()
    }.getOrElse { emptyList() }
}
