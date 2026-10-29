package com.example.diplom

/**
 * В Wasm по умолчанию — [DEFAULT_SERVER_HOST].
 * Локально с ПК: откройте `http://localhost:8082?apiHost=192.168.100.2` (или ваш IP из `ipconfig`).
 * Для авто-хоста страницы удобнее: `.\gradlew.bat :composeApp:jsBrowserDevelopmentRun`
 */
actual fun configuredServerHost(): String = DEFAULT_SERVER_HOST
