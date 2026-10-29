package com.example.diplom

/** Тот же origin, что и страница (:8082); webpack проксирует API на Ktor :8080. */
actual fun apiBaseUrlOverride(): String? = ""
