package com.example.diplom.auth

import com.example.diplom.api.AuthUserDto
import com.example.diplom.domain.UserRole

data class StoredUser(
    val id: Long,
    val studentTicket: String,
    val fullName: String,
    val passwordHash: String,
    val role: UserRole,
    val groupId: Long?,
)

class InMemoryAuthRepository {
    private val usersByTicket = linkedMapOf<String, StoredUser>()

    fun register(
        studentTicket: String,
        rawPassword: String,
    ): Result<AuthUserDto> {
        synchronized(this) {
            val existing = usersByTicket[studentTicket]
                ?: return Result.failure(IllegalStateException("Ticket is not pre-registered"))
            if (existing.passwordHash.isNotBlank()) {
                return Result.failure(IllegalStateException("Password already created for this ticket"))
            }
            val stored = existing.copy(
                passwordHash = PasswordHasher.hash(rawPassword),
            )
            usersByTicket[studentTicket] = stored
            return Result.success(
                AuthUserDto(
                    id = stored.id,
                    studentTicket = stored.studentTicket,
                    fullName = stored.fullName,
                    role = stored.role,
                )
            )
        }
    }

    fun login(studentTicket: String, rawPassword: String): AuthUserDto? {
        val stored = synchronized(this) { usersByTicket[studentTicket] } ?: return null
        return if (PasswordHasher.verify(stored.passwordHash, rawPassword)) {
            AuthUserDto(
                id = stored.id,
                studentTicket = stored.studentTicket,
                fullName = stored.fullName,
                role = stored.role,
            )
        } else {
            null
        }
    }

    init {
        // Предзаполненные учетные записи (имитация списка билетов у администратора).
        usersByTicket["student"] = StoredUser(1L, "student", "Иван Иванов", PasswordHasher.hash("password"), UserRole.STUDENT, 1L)
        usersByTicket["teacher"] = StoredUser(2L, "teacher", "Жданов Игорь Николаевич", PasswordHasher.hash("password"), UserRole.TEACHER, null)
        usersByTicket["admin"] = StoredUser(3L, "admin", "Анна Администратор", PasswordHasher.hash("password"), UserRole.ADMIN, null)
    }
}
