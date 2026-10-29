package com.example.diplom.state

import com.example.diplom.api.CalendarEventDto
import com.example.diplom.api.UserStateDto
import com.example.diplom.db.CalendarEventsTable
import com.example.diplom.db.UsersTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class UserStateStorage {

    fun hasData(studentTicket: String): Boolean = transaction {
        val userId = resolveUserId(studentTicket) ?: return@transaction false
        CalendarEventsTable.selectAll().where { CalendarEventsTable.userId eq userId }.any()
    }

    fun load(studentTicket: String): String? = transaction {
        val userRow = UsersTable.selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
        val userId = userRow?.get(UsersTable.id)
        val events = userId?.let { loadCalendarEvents(it, studentTicket) }.orEmpty()
        if (userRow == null && events.isEmpty()) return@transaction null
        val dto = UserStateDto(
            profileImageHex = userRow?.get(UsersTable.photoHex),
            profilePhone = userRow?.get(UsersTable.phone).orEmpty(),
            profileStudentId = studentTicket,
            calendarEvents = events,
        )
        Json.encodeToString(dto)
    }

    fun save(studentTicket: String, stateJson: String) = transaction {
        val userId = resolveUserId(studentTicket) ?: return@transaction
        val dto = Json.decodeFromString<UserStateDto>(stateJson)
        replaceCalendarEvents(
            userId,
            studentTicket,
            dto.calendarEvents.filter { it.ownerTicket == studentTicket },
        )
    }

    fun saveCalendarEvents(studentTicket: String, events: List<CalendarEventDto>) = transaction {
        val userId = resolveUserId(studentTicket) ?: return@transaction
        replaceCalendarEvents(userId, studentTicket, events)
    }

    private fun resolveUserId(studentTicket: String): Long? =
        UsersTable.selectAll()
            .where { UsersTable.studentTicket eq studentTicket }
            .singleOrNull()
            ?.get(UsersTable.id)

    private fun loadCalendarEvents(userId: Long, ownerTicket: String): List<CalendarEventDto> =
        CalendarEventsTable.selectAll()
            .where { CalendarEventsTable.userId eq userId }
            .map { row ->
                CalendarEventDto(
                    id = row[CalendarEventsTable.id],
                    date = row[CalendarEventsTable.eventDate],
                    title = row[CalendarEventsTable.title],
                    ownerTicket = ownerTicket,
                    description = row[CalendarEventsTable.description],
                    color = row[CalendarEventsTable.color],
                    time = row[CalendarEventsTable.eventTime],
                    reminder = row[CalendarEventsTable.reminder],
                    pinned = row[CalendarEventsTable.pinned],
                )
            }

    private fun replaceCalendarEvents(userId: Long, ownerTicket: String, events: List<CalendarEventDto>) {
        CalendarEventsTable.deleteWhere { CalendarEventsTable.userId eq userId }
        events.forEach { event ->
            CalendarEventsTable.insert {
                it[CalendarEventsTable.userId] = userId
                it[CalendarEventsTable.eventDate] = event.date
                it[CalendarEventsTable.title] = event.title
                it[CalendarEventsTable.description] = event.description
                it[CalendarEventsTable.color] = event.color
                it[CalendarEventsTable.eventTime] = event.time
                it[CalendarEventsTable.reminder] = event.reminder
                it[CalendarEventsTable.pinned] = event.pinned
            }
        }
    }
}
