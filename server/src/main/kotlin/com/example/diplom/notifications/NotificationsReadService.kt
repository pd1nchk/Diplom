package com.example.diplom.notifications

import com.example.diplom.api.NotificationDto
import com.example.diplom.db.NotificationsTable
import com.example.diplom.db.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter

class NotificationsReadService {
    fun listForTicket(studentTicket: String): List<NotificationDto> = transaction {
        val userId = UsersTable.selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
            ?.get(UsersTable.id)
            ?: return@transaction emptyList()
        NotificationsTable.selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAt to SortOrder.DESC)
            .map { row ->
                val instant = row[NotificationsTable.createdAt]
                NotificationDto(
                    id = row[NotificationsTable.id],
                    title = row[NotificationsTable.title],
                    message = row[NotificationsTable.message],
                    createdAtIso = DateTimeFormatter.ISO_INSTANT.format(instant),
                    isImportant = row[NotificationsTable.isImportant],
                )
            }
    }
}
