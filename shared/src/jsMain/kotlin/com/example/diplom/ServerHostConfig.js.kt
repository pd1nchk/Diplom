package com.example.diplom

import kotlinx.browser.window

private fun queryParam(name: String): String? {
    val raw = window.location.search.removePrefix("?").trim()
    if (raw.isEmpty()) return null
    return raw.split("&").firstNotNullOfOrNull { part ->
        val idx = part.indexOf('=')
        if (idx <= 0) return@firstNotNullOfOrNull null
        val k = part.substring(0, idx)
        if (k != name) return@firstNotNullOfOrNull null
        part.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }
}

/**
 * В браузере по умолчанию берём хост страницы (тот же хост, порт API — [SERVER_PORT]).
 * Если фронт и API на разных хостах: `?apiHost=api.example.com` (без схемы и порта).
 */
actual fun configuredServerHost(): String {
    queryParam("apiHost")?.let { return it }
    val host = window.location.hostname.trim()
    if (host.isNotEmpty()) return host
    return DEFAULT_SERVER_HOST
}
