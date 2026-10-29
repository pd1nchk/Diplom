package com.example.diplom.auth

import java.time.Instant

object PasswordResetCodeRegistry {
    private const val TtlMillis = 10 * 60 * 1000L
    const val DemoCode = "4321"
    private val codes = mutableMapOf<String, ResetCode>()

    fun issue(studentTicket: String): ResetCode = synchronized(this) {
        val entry = ResetCode(
            code = DemoCode,
            expiresAtEpochMillis = Instant.now().toEpochMilli() + TtlMillis,
        )
        codes[studentTicket] = entry
        entry
    }

    fun consume(studentTicket: String, code: String): Boolean = synchronized(this) {
        val entry = codes[studentTicket] ?: return false
        if (Instant.now().toEpochMilli() > entry.expiresAtEpochMillis) {
            codes.remove(studentTicket)
            return false
        }
        if (entry.code != code.trim()) return false
        codes.remove(studentTicket)
        true
    }
}

data class ResetCode(
    val code: String,
    val expiresAtEpochMillis: Long,
)
