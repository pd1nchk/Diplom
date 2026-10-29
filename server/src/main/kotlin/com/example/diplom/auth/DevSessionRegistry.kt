package com.example.diplom.auth

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Учёт выданных при логине/регистрации токенов (демо-слой без JWT).
 * Позволяет на [POST /auth/logout] инвалидировать текущую сессию на сервере.
 */
object DevSessionRegistry {
    private val activeTokenByUserId = ConcurrentHashMap<Long, String>()
    private val revokedTokens = ConcurrentHashMap.newKeySet<String>()

    fun issueToken(userId: Long): String {
        val previous = activeTokenByUserId.remove(userId)
        if (previous != null) {
            revokedTokens.add(previous)
        }
        val token = "dev-$userId-${UUID.randomUUID()}"
        activeTokenByUserId[userId] = token
        revokedTokens.remove(token)
        return token
    }

    /** Текущий активный токен пользователя (для проверок). */
    fun activeTokenForUser(userId: Long): String? = activeTokenByUserId[userId]

    fun isTokenRevoked(token: String): Boolean = token in revokedTokens

    /**
     * Снимает сессию, если [token] совпадает с выданным для [userId].
     * @return true при успешной инвалидации; false — нет активной сессии или токен не совпал.
     */
    fun revokeSession(userId: Long, token: String): Boolean {
        val current = activeTokenByUserId[userId] ?: return false
        if (current != token) return false
        if (!activeTokenByUserId.remove(userId, current)) return false
        revokedTokens.add(current)
        return true
    }
}
