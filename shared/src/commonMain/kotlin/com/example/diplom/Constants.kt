package com.example.diplom

const val SERVER_PORT = 8080

const val HTTP_CLIENT_TIMEOUT_MS = 15_000L

/** Запасной хост, если таргет не подставляет свой (см. [configuredServerHost]). */
const val DEFAULT_SERVER_HOST = "192.168.100.10"

/**
 * Базовый URL API.
 * - `""` — относительные пути (`/auth/login`), webpack dev-server проксирует на Ktor :8080.
 * - `null` — `http://`[configuredServerHost]`:`[SERVER_PORT] (Android, iOS, desktop).
 * - иначе — явный URL.
 */
expect fun apiBaseUrlOverride(): String?

val SERVER_BASE_URL: String
    get() {
        when (val override = apiBaseUrlOverride()) {
            null -> Unit
            else -> {
                val trimmed = override.trim().trimEnd('/')
                if (trimmed.isEmpty()) return ""
                return trimmed
            }
        }
        return "http://${configuredServerHost()}:$SERVER_PORT"
    }