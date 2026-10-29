package com.example.diplom.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/** Одноразовые правки данных после обновления схемы (H2 / PostgreSQL). */
object DbSchemaRepair {

    fun runAfterSeed() = transaction {
        GroupCourseLinks.syncFromLessonsUnsafe()
        backfillPasswordResetUserIds()
    }

    private fun backfillPasswordResetUserIds() {
        PasswordResetRequestsTable.selectAll().where {
            PasswordResetRequestsTable.userId.isNull()
        }.forEach { row ->
            val ticket = row[PasswordResetRequestsTable.studentTicket]
            val uid = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
                ?.get(UsersTable.id) ?: return@forEach
            PasswordResetRequestsTable.update({ PasswordResetRequestsTable.id eq row[PasswordResetRequestsTable.id] }) {
                it[PasswordResetRequestsTable.userId] = uid
            }
        }
    }
}
