package com.example.diplom.auth

import com.example.diplom.api.AuthResponse
import com.example.diplom.api.ChangePasswordRequest
import com.example.diplom.api.ForgotPasswordRequest
import com.example.diplom.api.LoginRequest
import com.example.diplom.api.LogoutRequest
import com.example.diplom.api.PasswordResetPhoneConfirmRequest
import com.example.diplom.api.PasswordResetPhoneStartRequest
import com.example.diplom.api.PasswordResetPhoneStartResponse
import com.example.diplom.api.RegisterRequest
import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.SERVER_PORT
import com.example.diplom.networkFailureUserMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AuthRepository {
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

    suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/register") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun submitForgotPassword(request: ForgotPasswordRequest): Result<Unit> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/forgot-password") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun startPhonePasswordReset(request: PasswordResetPhoneStartRequest): Result<PasswordResetPhoneStartResponse> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/password-reset/phone/start") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<PasswordResetPhoneStartResponse>())
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun confirmPhonePasswordReset(request: PasswordResetPhoneConfirmRequest): Result<Unit> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/password-reset/phone/confirm") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/login") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/password") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }

    suspend fun logout(request: LogoutRequest): Result<Unit> {
        return try {
            val response = client.post("$SERVER_BASE_URL/auth/logout") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(response.userFacingErrorText()))
            }
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(networkFailureUserMessage(e, SERVER_BASE_URL)))
        }
    }
}

private suspend fun HttpResponse.userFacingErrorText(): String {
    val raw = runCatching { bodyAsText() }.getOrNull()?.trim().orEmpty()
    val body = sanitizeHtmlOrDevServerBody(raw)
    return when (status) {
        HttpStatusCode.Unauthorized -> when {
            body.isBlank() -> "Неверный билет или пароль"
            body.equals("Invalid credentials", ignoreCase = true) -> "Неверный билет или пароль"
            else -> body
        }
        HttpStatusCode.Conflict -> body.ifBlank { "Регистрация отклонена" }
        HttpStatusCode.BadRequest -> body.ifBlank { "Неверный запрос" }
        HttpStatusCode.NotFound -> body.ifBlank { "Не найдено" }
        HttpStatusCode.ServiceUnavailable -> body.ifBlank { "Сервис временно недоступен" }
        else ->
            if (status.value in 500..599) {
                body.ifBlank { "Ошибка сервера (${status.value})" }
            } else {
                body.ifBlank { "Ошибка (${status.value})" }
            }
    }
}

/** Ответ не JSON (часто HTML от webpack: «Cannot POST /…») — не показываем разметку пользователю. */
private fun sanitizeHtmlOrDevServerBody(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    val lower = t.lowercase()
    if (t.startsWith("<!DOCTYPE", ignoreCase = true) ||
        t.startsWith("<html", ignoreCase = true) ||
        lower.contains("<body") ||
        lower.contains("cannot post") ||
        lower.contains("cannot get") ||
        lower.contains("trying to proxy")
    ) {
        return "Не удалось связаться с API ($SERVER_BASE_URL). Запустите Ktor на порту $SERVER_PORT. Веб-клиент откройте с другого порта (Gradle, обычно :8081), запросы к API пойдут на :$SERVER_PORT (CORS уже настроен)."
    }
    return t
}
