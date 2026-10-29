package com.example.diplom

/**
 * Хост без `http://` и без порта.
 * На Android берётся из `BuildConfig` (см. `local.properties` → `diplom.server.host`).
 * На остальных таргетах — [DEFAULT_SERVER_HOST].
 */
expect fun configuredServerHost(): String
