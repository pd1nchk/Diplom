package com.example.diplom

import io.ktor.client.plugins.HttpRequestTimeoutException

fun networkFailureUserMessage(throwable: Throwable, serverBaseUrl: String): String {
    if (throwable is HttpRequestTimeoutException) {
        return "Сервер не отвечает ($serverBaseUrl). На ПК в каталоге проекта выполните: gradlew :server:run " +
            "(по умолчанию используется встроенная БД H2). " +
            "Порт 8080 должен быть свободен; при необходимости разрешите его в брандмауэре Windows."
    }
    val text = buildString {
        append(throwable.message.orEmpty())
        var c: Throwable? = throwable.cause
        while (c != null) {
            append(' ')
            append(c.message.orEmpty())
            c = c.cause
        }
    }
    val lower = text.lowercase()
    return when {
        "connection refused" in lower ->
            "Сервер не запущен или порт занят ($serverBaseUrl). Запустите gradlew :server:run и проверьте, что порт 8080 слушает Ktor."
        "failed to connect" in lower ->
            "Нет соединения с сервером ($serverBaseUrl). Проверьте: " +
                "1) на ПК выполнен gradlew :server:run; " +
                "2) на реальном телефоне в корне проекта в local.properties задайте diplom.server.host=<IPv4 ПК в Wi‑Fi> " +
                "(ipconfig → IPv4 адаптера Wi‑Fi), на эмуляторе хост подставляется автоматически; " +
                "3) телефон в той же сети, что и ПК; " +
                "4) в брандмауэре Windows разрешён входящий TCP 8080."
        "timed out" in lower || "timeout" in lower ->
            "Таймаут ($serverBaseUrl). Проверьте, что сервер запущен (gradlew :server:run), и брандмауэр не блокирует порт 8080."
        text.isNotBlank() -> text
        else -> "Нет соединения с сервером ($serverBaseUrl)."
    }
}
