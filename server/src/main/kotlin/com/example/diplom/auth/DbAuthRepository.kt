package com.example.diplom.auth

import com.example.diplom.api.AuthUserDto
import com.example.diplom.db.UsersTable
import com.example.diplom.domain.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class DbAuthRepository {
    fun register(
        studentTicket: String,
        rawPassword: String,
    ): Result<AuthUserDto> = transaction {
        val existing = UsersTable
            .selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
        if (rawPassword.length < 4) {
            return@transaction Result.failure(
                IllegalStateException("Пароль должен быть не короче 4 символов"),
            )
        }
        val newHash = PasswordHasher.hash(rawPassword)

        if (existing == null) {
            if (!studentTicket.matches(Regex("\\d{7}"))) {
                return@transaction Result.failure(
                    IllegalStateException("Номер студенческого билета — ровно 7 цифр"),
                )
            }
            val insertedId = UsersTable.insert {
                it[UsersTable.studentTicket] = studentTicket
                it[passwordHash] = newHash
                it[fullName] = "Студент $studentTicket"
                it[role] = UserRole.STUDENT.name
                it[isActive] = true
            } get UsersTable.id

            val created = UsersTable
                .selectAll()
                .where { UsersTable.id eq insertedId }
                .single()
            return@transaction Result.success(created.toAuthUserDto())
        }

        val existingHash = existing[UsersTable.passwordHash]
        if (existingHash.isNotBlank()) {
            return@transaction Result.failure(
                IllegalStateException("Для этого билета пароль уже создан — войдите или смените пароль в настройках"),
            )
        }

        UsersTable.update({ UsersTable.id eq existing[UsersTable.id] }) {
            it[passwordHash] = newHash
        }

        val saved = UsersTable
            .selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .single()

        Result.success(saved.toAuthUserDto())
    }

    fun login(studentTicket: String, rawPassword: String): AuthUserDto? = transaction {
        val row = UsersTable
            .selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
            ?: return@transaction null
        val stored = row[UsersTable.passwordHash]
        if (!PasswordHasher.verify(stored, rawPassword)) return@transaction null
        if (PasswordHasher.needsUpgrade(stored)) {
            UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) {
                it[passwordHash] = PasswordHasher.hash(rawPassword)
            }
        }
        row.toAuthUserDto()
    }

    fun changePassword(studentTicket: String, oldPassword: String, newPassword: String): Boolean = transaction {
        if (newPassword.length < 4) return@transaction false
        val row = UsersTable.selectAll().where { UsersTable.studentTicket eq studentTicket }.singleOrNull()
            ?: return@transaction false
        if (!PasswordHasher.verify(row[UsersTable.passwordHash], oldPassword)) return@transaction false
        UsersTable.update({ UsersTable.studentTicket eq studentTicket }) {
            it[UsersTable.passwordHash] = PasswordHasher.hash(newPassword)
        }
        true
    }

    fun resetPassword(studentTicket: String, newPassword: String): Boolean = transaction {
        if (newPassword.length < 4) return@transaction false
        val updated = UsersTable.update({ UsersTable.studentTicket eq studentTicket }) {
            it[UsersTable.passwordHash] = PasswordHasher.hash(newPassword)
        }
        updated > 0
    }

    fun phoneForTicket(studentTicket: String): String? = transaction {
        UsersTable
            .selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
            ?.get(UsersTable.phone)
            ?.takeIf { it.isNotBlank() }
    }

    fun seedDefaultsIfEmpty() = transaction {
        upsertSeedUser(
            studentTicket = "teacher",
            fullName = "Жданов Игорь Николаевич",
            role = UserRole.TEACHER,
            password = "1234",
            phone = "+375296392619",
        )
        upsertSeedUser("admin", "Анна Администратор", UserRole.ADMIN, "1234")
    }

    private fun upsertSeedUser(
        studentTicket: String,
        fullName: String,
        role: UserRole,
        password: String,
        phone: String? = null,
    ) {
        val existing = UsersTable.selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
        if (existing != null) {
            val currentPhone = existing[UsersTable.phone]
            UsersTable.update({ UsersTable.studentTicket eq studentTicket }) {
                it[UsersTable.passwordHash] = PasswordHasher.hash(password)
                it[UsersTable.fullName] = fullName
                it[UsersTable.role] = role.name
                it[isActive] = true
                if (phone != null && (currentPhone.isNullOrBlank() || currentPhone == "+375 (29) 200-00-01")) {
                    it[UsersTable.phone] = phone
                }
            }
        } else {
            UsersTable.insert {
                it[UsersTable.studentTicket] = studentTicket
                it[passwordHash] = PasswordHasher.hash(password)
                it[UsersTable.fullName] = fullName
                it[UsersTable.role] = role.name
                it[isActive] = true
                if (phone != null) {
                    it[UsersTable.phone] = phone
                }
            }
        }
    }
}

private fun ResultRow.toAuthUserDto(): AuthUserDto {
    return AuthUserDto(
        id = this[UsersTable.id],
        studentTicket = this[UsersTable.studentTicket],
        fullName = this[UsersTable.fullName],
        role = UserRole.valueOf(this[UsersTable.role]),
    )
}

