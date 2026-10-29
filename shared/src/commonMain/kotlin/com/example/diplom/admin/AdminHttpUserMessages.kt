package com.example.diplom.admin

import com.example.diplom.SERVER_BASE_URL
import io.ktor.http.HttpStatusCode

/** Короткие сообщения для UI админки без «сырого» HTTP и стектрейсов. */
fun adminHttpUserMessage(status: HttpStatusCode, rawBody: String): String {
    val code = status.value
    val body = rawBody.trim().replace("\n", " ").take(160)
    return when (status) {
        HttpStatusCode.Forbidden -> "Сервер не отдал данные (403). Проверьте подключение и что на сервере установлена актуальная версия API."
        HttpStatusCode.Unauthorized -> "Сессия недействительна. Выйдите и войдите снова."
        HttpStatusCode.NotFound -> "Сервис не найден (404). Проверьте, что сервер обновлён и адрес API совпадает с приложением: $SERVER_BASE_URL"
        HttpStatusCode.BadRequest -> if (body.isNotEmpty() && body.length < 180) body else "Запрос отклонён сервером (400)."
        HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout ->
            "Сервер временно недоступен. Попробуйте позже."
        else -> when (code) {
            in 500..599 -> "Ошибка на сервере ($code). Попробуйте позже."
            else -> if (body.isNotEmpty() && body.length < 180 && !body.startsWith("{")) body else "Не удалось выполнить запрос (код $code)."
        }
    }
}

/** Сообщение для UI при сетевом сбое Ktor (таймаут, DNS, TLS и т.д.). */
fun adminFetchFailureMessage(method: String, relativePath: String, cause: Throwable?): String {
    val kind = cause?.let { "${it::class.simpleName}: ${it.message}" } ?: "unknown"
    return "$method $relativePath — $kind · API: $SERVER_BASE_URL"
}
