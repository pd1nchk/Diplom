package com.example.diplom.auth

import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest

/**
 * Хеширование паролей: BCrypt (основной алгоритм) с поддержкой проверки
 * ранее сохранённых SHA-256 для плавной миграции демо-БД.
 */
object PasswordHasher {
    /** Сложность BCrypt (log rounds), аналог saltRounds = 10 в методичке. */
    private const val BCRYPT_ROUNDS = 10

    fun hash(raw: String): String = BCrypt.hashpw(raw, BCrypt.gensalt(BCRYPT_ROUNDS))

    fun verify(storedHash: String, rawPassword: String): Boolean {
        if (storedHash.isBlank()) return false
        return if (isBcryptHash(storedHash)) {
            BCrypt.checkpw(rawPassword, storedHash)
        } else {
            legacySha256Hex(rawPassword) == storedHash
        }
    }

    /** true, если в БД остался устаревший SHA-256 — после успешного входа можно обновить хеш. */
    fun needsUpgrade(storedHash: String): Boolean =
        storedHash.isNotBlank() && !isBcryptHash(storedHash)

    private fun isBcryptHash(stored: String): Boolean =
        stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")

    private fun legacySha256Hex(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
