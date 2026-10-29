package com.example.diplom.schedule

import com.example.diplom.db.AttendanceSessionsTable
import com.example.diplom.db.AttendanceTable
import com.example.diplom.db.CoursesTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.RescheduleRequestsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Дозаполняет [LessonsTable] повторяющимися датами на учебный год (пн–вс + чётность),
 * чтобы в журнале были все занятия серии (например, каждый четверг).
 * Перенесённое занятие ([LessonsTable.status] = MOVED / RESCHEDULED) — отдельная строка; серия не меняется.
 */
object JournalLessonExpander {
    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val protectedStatuses = setOf("MOVED", "RESCHEDULED")

    private fun isProtectedLesson(row: org.jetbrains.exposed.sql.ResultRow): Boolean =
        row[LessonsTable.status] in protectedStatuses ||
            AttendanceTable.selectAll().where { AttendanceTable.lessonId eq row[LessonsTable.id] }.any()

    private fun isComputerNetworks(courseName: String): Boolean =
        courseName.contains("компьютерные сети", ignoreCase = true)

    /** Убирает у 1-8 утреннюю лекцию 09:45 — остаётся только слот 15:45. */
    fun purgeComputerNetworksMorningForGroup(courseId: Long, groupId: Long) = transaction {
        val morningStart = LocalTime.of(9, 45)
        LessonsTable.selectAll().where {
            (LessonsTable.courseId eq courseId) and
                (LessonsTable.groupId eq groupId) and
                (LessonsTable.lessonKind eq "LECTURE")
        }.forEach { row ->
            if (isProtectedLesson(row)) return@forEach
            val z = ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone)
            if (z.toLocalTime() == morningStart) {
                deleteLessonCascade(row[LessonsTable.id])
            }
        }
    }

    /** Удаляет занятия «Компьютерные сети» до 15 мая текущего весеннего периода. */
    fun purgeComputerNetworksBeforeSpringStart(courseId: Long) = transaction {
        val (springStart, _) = computerNetworksSpringBounds()
        LessonsTable.selectAll().where { LessonsTable.courseId eq courseId }.forEach { row ->
            if (isProtectedLesson(row)) return@forEach
            val date = ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone).toLocalDate()
            if (date.isBefore(springStart)) {
                deleteLessonCascade(row[LessonsTable.id])
            }
        }
    }

    /** Удаляет все занятия «Компьютерные сети», которые не попадают на четверг. */
    fun purgeComputerNetworksOffThursday(courseId: Long) = transaction {
        LessonsTable.selectAll().where { LessonsTable.courseId eq courseId }.forEach { row ->
            if (isProtectedLesson(row)) return@forEach
            val dow = ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone).dayOfWeek
            if (dow != DayOfWeek.THURSDAY) {
                deleteLessonCascade(row[LessonsTable.id])
            }
        }
    }

    private fun deleteLessonCascade(lessonId: Long) {
        AttendanceTable.deleteWhere { AttendanceTable.lessonId eq lessonId }
        AttendanceSessionsTable.deleteWhere { AttendanceSessionsTable.lessonId eq lessonId }
        RescheduleRequestsTable.deleteWhere { RescheduleRequestsTable.lessonId eq lessonId }
        LessonsTable.deleteWhere { LessonsTable.id eq lessonId }
    }

    fun ensureSemesterOccurrences(groupId: Long, courseId: Long) = transaction {
        val courseName = CoursesTable.selectAll().where { CoursesTable.id eq courseId }.singleOrNull()
            ?.get(CoursesTable.name).orEmpty()
        if (isComputerNetworks(courseName)) {
            purgeComputerNetworksBeforeSpringStart(courseId)
            purgeComputerNetworksOffThursday(courseId)
        }

        val existing = LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and (LessonsTable.courseId eq courseId)
        }.toList()
        if (existing.isEmpty()) return@transaction

        val (semesterStart, semesterEnd) = semesterBounds(courseName)
        val templates = existing
            .mapNotNull { row -> toTemplate(row, courseName) }
            .distinctBy { it.key }

        templates.forEach { tmpl ->
            occurrenceDates(tmpl, semesterStart, semesterEnd).forEach { date ->
                val start = ZonedDateTime.of(date, tmpl.startTime, zone)
                val end = start.plusMinutes(tmpl.durationMinutes)
                if (!hasLessonAtStart(existing, groupId, courseId, start)) {
                    LessonsTable.insert {
                        it[LessonsTable.courseId] = courseId
                        it[LessonsTable.groupId] = groupId
                        it[LessonsTable.roomId] = tmpl.roomId
                        it[LessonsTable.startTime] = start.toInstant()
                        it[LessonsTable.endTime] = end.toInstant()
                        it[LessonsTable.status] = "SCHEDULED"
                        it[LessonsTable.lessonKind] = tmpl.lessonKind
                        it[LessonsTable.weekParity] = tmpl.weekParity
                    }
                }
            }
        }
    }

    private data class LessonTemplate(
        val key: String,
        val roomId: Long,
        val lessonKind: String,
        val weekParity: String,
        val dayOfWeek: DayOfWeek,
        val startTime: LocalTime,
        val durationMinutes: Long,
    )

    private fun toTemplate(row: org.jetbrains.exposed.sql.ResultRow, courseName: String): LessonTemplate? {
        if (row[LessonsTable.status] == "CANCELLED" || row[LessonsTable.status] in protectedStatuses) return null
        val kind = row[LessonsTable.lessonKind]
        if (kind == "NFC_DEMO") return null
        val start = ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone)
        if (isComputerNetworks(courseName) && start.dayOfWeek != DayOfWeek.THURSDAY) return null
        val end = ZonedDateTime.ofInstant(row[LessonsTable.endTime], zone)
        val minutes = Duration.between(start, end).toMinutes()
        if (minutes <= 0 || minutes > 8 * 60) return null
        val parity = row[LessonsTable.weekParity]
        val dow = start.dayOfWeek
        val st = start.toLocalTime()
        val key = listOf(dow.name, st.toString(), minutes, parity, row[LessonsTable.roomId], kind).joinToString("|")
        return LessonTemplate(
            key = key,
            roomId = row[LessonsTable.roomId],
            lessonKind = kind,
            weekParity = parity,
            dayOfWeek = dow,
            startTime = st,
            durationMinutes = minutes,
        )
    }

    private fun occurrenceDates(tmpl: LessonTemplate, from: LocalDate, to: LocalDate): List<LocalDate> {
        var d = from.with(TemporalAdjusters.nextOrSame(tmpl.dayOfWeek))
        val out = mutableListOf<LocalDate>()
        while (!d.isAfter(to)) {
            if (parityMatches(d, tmpl.weekParity)) {
                out.add(d)
            }
            d = d.plusWeeks(1)
        }
        return out
    }

    private fun parityMatches(date: LocalDate, weekParity: String): Boolean {
        if (weekParity == "ANY") return true
        val isoWeek = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val even = isoWeek % 2L == 0L
        return when (weekParity) {
            "EVEN" -> even
            "ODD" -> !even
            else -> true
        }
    }

    private fun hasLessonAtStart(
        existing: List<org.jetbrains.exposed.sql.ResultRow>,
        groupId: Long,
        courseId: Long,
        start: ZonedDateTime,
    ): Boolean {
        val target = start.toInstant()
        if (existing.any { row ->
                row[LessonsTable.groupId] == groupId &&
                    row[LessonsTable.courseId] == courseId &&
                    row[LessonsTable.startTime] == target
            }
        ) {
            return true
        }
        return LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and
                (LessonsTable.courseId eq courseId) and
                (LessonsTable.startTime eq target)
        }.any()
    }

    private fun semesterBounds(courseName: String): Pair<LocalDate, LocalDate> {
        if (isComputerNetworks(courseName)) {
            return computerNetworksSpringBounds()
        }
        return academicYearBounds()
    }

    /** Весенний поток «Компьютерные сети»: с ~15 мая до 1 июля. */
    private fun computerNetworksSpringBounds(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(zone)
        var year = today.year
        var start = LocalDate.of(year, 5, 15)
        var end = LocalDate.of(year, 7, 1)
        if (today.isAfter(end)) {
            year += 1
            start = LocalDate.of(year, 5, 15)
            end = LocalDate.of(year, 7, 1)
        }
        return start to end
    }

    private fun academicYearBounds(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(zone)
        val startYear = if (today.monthValue >= 9) today.year else today.year - 1
        val endYear = startYear + 1
        return LocalDate.of(startYear, 9, 1) to LocalDate.of(endYear, 7, 1)
    }
}
