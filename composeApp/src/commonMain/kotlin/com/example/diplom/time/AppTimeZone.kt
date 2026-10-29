package com.example.diplom.time

import kotlinx.datetime.TimeZone

/**
 * Часовой пояс приложения (Беларусь, UTC+3).
 * На Wasm/JS IANA `Europe/Minsk` недоступен в kotlinx-datetime — fallback на фиксированный offset.
 */
val AppTimeZone: TimeZone = runCatching { TimeZone.of("Europe/Minsk") }
    .getOrElse { TimeZone.of("+03:00") }
