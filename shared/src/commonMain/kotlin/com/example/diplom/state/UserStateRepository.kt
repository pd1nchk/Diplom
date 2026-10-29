package com.example.diplom.state

import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.api.UserStateDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.put
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class UserStateRepository {
    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            connectTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            socketTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
        }
    }

    suspend fun load(studentTicket: String): UserStateDto? {
        val response = client.get("$SERVER_BASE_URL/state/$studentTicket")
        if (response.status != HttpStatusCode.OK) {
            return null
        }
        return runCatching { response.body<UserStateDto>() }.getOrNull()
    }

    /** @return true если сервер подтвердил сохранение */
    suspend fun save(studentTicket: String, state: UserStateDto): Boolean {
        val response = client.put("$SERVER_BASE_URL/state/$studentTicket") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(state)
        }
        return response.status == HttpStatusCode.OK
    }
}
