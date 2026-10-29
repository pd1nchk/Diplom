package com.example.diplom.schedule

import com.example.diplom.api.DayScheduleColumnDto
import com.example.diplom.api.ScheduledLessonDto
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.db.CoursesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.RoomsTable
import com.example.diplom.db.StudentsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.UsersTable
import com.example.diplom.domain.UserRole
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

class WeekScheduleService {
    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class RawLesson(
        val dow: DayOfWeek,
        val lessonId: Long,
        val groupId: Long,
        val courseId: Long,
        val courseName: String,
        val groupLabel: String?,
        val groupCompact: String?,
        val groupCourse: Int,
        val groupNumber: Int,
        val roomId: Long,
        val roomName: String,
        val start: java.time.Instant,
        val end: java.time.Instant,
        val timeRange: String,
        val teacherName: String,
        val teacherTicket: String?,
        val lessonKind: String,
        val weekParity: String,
    )

    private val dayOrder = listOf(
        DayOfWeek.MONDAY to "Пн",
        DayOfWeek.TUESDAY to "Вт",
        DayOfWeek.WEDNESDAY to "Ср",
        DayOfWeek.THURSDAY to "Чт",
        DayOfWeek.FRIDAY to "Пт",
        DayOfWeek.SATURDAY to "Сб",
        DayOfWeek.SUNDAY to "Вс",
    )

    fun weekForTicket(studentTicket: String): WeekScheduleDto = transaction {
        val monday = ZonedDateTime.now(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(zone).toInstant()
        val weekEnd = monday.plusDays(7).atStartOfDay(zone).toInstant()
        val isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val weekBasedYear = monday.get(IsoFields.WEEK_BASED_YEAR)
        val parityLabel = if (isoWeek % 2L == 0L) "EVEN" else "ODD"
        val isEvenWeek = parityLabel == "EVEN"
        val weekNumber = isoWeek.toInt()
        val weekYear = weekBasedYear
        val weekParityHint = "Неделя $weekNumber, $weekYear год · ${if (isEvenWeek) "чётная" else "нечётная"}"

        fun weekDto(groupName: String?, columns: List<DayScheduleColumnDto>) = WeekScheduleDto(
            groupName = groupName,
            columns = columns,
            weekNumber = weekNumber,
            weekYear = weekYear,
            isEvenWeek = isEvenWeek,
            weekParityHint = weekParityHint,
        )

        val userRow = UsersTable.selectAll().where { UsersTable.studentTicket eq studentTicket }.singleOrNull()
            ?: return@transaction weekDto(groupName = null, columns = emptyColumns())

        val userId = userRow[UsersTable.id]
        val role = runCatching { UserRole.valueOf(userRow[UsersTable.role]) }.getOrElse { UserRole.STUDENT }

        val lessonRows: List<Pair<org.jetbrains.exposed.sql.ResultRow, String?>> = when (role) {
            UserRole.STUDENT -> {
                val groupId = StudentsTable.selectAll().where { StudentsTable.userId eq userId }.singleOrNull()
                    ?.get(StudentsTable.groupId)
                    ?: return@transaction weekDto(groupName = null, columns = emptyColumns())
                val groupName = GroupsTable.selectAll().where { GroupsTable.id eq groupId }.singleOrNull()
                    ?.get(GroupsTable.name)
                LessonsTable.selectAll().where {
                    (LessonsTable.groupId eq groupId) and
                        (LessonsTable.startTime greaterEq weekStart) and
                        (LessonsTable.startTime less weekEnd) and
                        ((LessonsTable.weekParity eq "ANY") or (LessonsTable.weekParity eq parityLabel))
                }.map { it to groupName }
            }
            UserRole.TEACHER -> {
                if (TeachersTable.selectAll().where { TeachersTable.userId eq userId }.empty()) {
                    return@transaction weekDto(groupName = null, columns = emptyColumns())
                }
                (LessonsTable innerJoin CoursesTable)
                    .selectAll()
                    .where {
                        (CoursesTable.teacherId eq userId) and
                            (LessonsTable.startTime greaterEq weekStart) and
                            (LessonsTable.startTime less weekEnd) and
                            ((LessonsTable.weekParity eq "ANY") or (LessonsTable.weekParity eq parityLabel))
                    }
                    .map { row ->
                        val gid = row[LessonsTable.groupId]
                        val gname = GroupsTable.selectAll().where { GroupsTable.id eq gid }.singleOrNull()
                            ?.get(GroupsTable.name)
                        row to gname
                    }
            }
            UserRole.ADMIN -> {
                LessonsTable.selectAll().where {
                    (LessonsTable.startTime greaterEq weekStart) and (LessonsTable.startTime less weekEnd) and
                        ((LessonsTable.weekParity eq "ANY") or (LessonsTable.weekParity eq parityLabel))
                }.map { row ->
                    val gid = row[LessonsTable.groupId]
                    val gname = GroupsTable.selectAll().where { GroupsTable.id eq gid }.singleOrNull()
                        ?.get(GroupsTable.name)
                    row to gname
                }
            }
        }

        if (lessonRows.isEmpty()) {
            val groupOnly = if (role == UserRole.STUDENT) {
                StudentsTable.selectAll().where { StudentsTable.userId eq userId }.singleOrNull()?.let { st ->
                    GroupsTable.selectAll().where { GroupsTable.id eq st[StudentsTable.groupId] }.singleOrNull()
                        ?.get(GroupsTable.name)
                }
            } else {
                null
            }
            return@transaction weekDto(groupName = groupOnly, columns = emptyColumns())
        }

        val coursesById = CoursesTable.selectAll().associateBy({ it[CoursesTable.id] }, { it })
        val roomsById = RoomsTable.selectAll().associateBy({ it[RoomsTable.id] }, { it })
        val usersById = UsersTable.selectAll().associateBy({ it[UsersTable.id] }, { it })
        val groupIds = lessonRows.map { it.first[LessonsTable.groupId] }.distinct()
        val groupsById = if (groupIds.isEmpty()) {
            emptyMap()
        } else {
            GroupsTable.selectAll().where { GroupsTable.id inList groupIds }.associateBy({ it[GroupsTable.id] }, { it })
        }

        val rawLessons = lessonRows.mapNotNull { (row, groupLabel) ->
            val lessonKind = row[LessonsTable.lessonKind]
            if (lessonKind == "NFC_DEMO") return@mapNotNull null
            val courseId = row[LessonsTable.courseId]
            val courseRow = coursesById[courseId] ?: return@mapNotNull null
            val courseName = courseRow[CoursesTable.name]
            val roomRow = roomsById[row[LessonsTable.roomId]] ?: return@mapNotNull null
            val teacherUid = courseRow[CoursesTable.teacherId]
            val teacherName = usersById[teacherUid]?.get(UsersTable.fullName) ?: "—"
            val teacherTicket = usersById[teacherUid]?.get(UsersTable.studentTicket)
            val start = row[LessonsTable.startTime]
            val end = row[LessonsTable.endTime]
            val zStart = ZonedDateTime.ofInstant(start, zone)
            val zEnd = ZonedDateTime.ofInstant(end, zone)
            val dow = zStart.dayOfWeek
            val lessonStatus = row[LessonsTable.status]
            if (
                courseName.contains("компьютерные сети", ignoreCase = true) &&
                dow != DayOfWeek.THURSDAY &&
                lessonStatus !in listOf("MOVED", "RESCHEDULED")
            ) {
                return@mapNotNull null
            }
            val timeRange = "${zStart.format(timeFmt)}–${zEnd.format(timeFmt)}"
            val gid = row[LessonsTable.groupId]
            val gRow = groupsById[gid]
            val gc = gRow?.get(GroupsTable.course) ?: 0
            val gn = gRow?.get(GroupsTable.groupNumber) ?: 0
            val groupCompact = if (gRow != null) "$gc-$gn" else null
            RawLesson(
                dow = dow,
                lessonId = row[LessonsTable.id],
                groupId = gid,
                courseId = courseId,
                courseName = courseName,
                groupLabel = groupLabel,
                groupCompact = groupCompact,
                groupCourse = gc,
                groupNumber = gn,
                roomId = row[LessonsTable.roomId],
                roomName = roomRow[RoomsTable.name],
                start = start,
                end = end,
                timeRange = timeRange,
                teacherName = teacherName,
                teacherTicket = teacherTicket,
                lessonKind = lessonKind,
                weekParity = row[LessonsTable.weekParity],
            )
        }

        /** Одна строка расписания для нескольких групп с одинаковым слотом (курс+аудитория+время). */
        val scheduled = rawLessons
            .groupBy { r ->
                listOf(
                    r.dow.name,
                    r.start.toEpochMilli(),
                    r.end.toEpochMilli(),
                    r.roomId,
                    r.courseId,
                ).joinToString("|")
            }
            .values
            .map { bucket ->
                val head = bucket.minBy { it.lessonId }
                val ids = bucket.map { it.lessonId }.distinct().sorted()
                val distinctGroupIds = bucket.map { it.groupId }.distinct()
                val mergedGroupsLabel = formatGroupsAudience(bucket)
                val subjectMerged = when (role) {
                    UserRole.STUDENT -> head.courseName
                    UserRole.TEACHER, UserRole.ADMIN -> head.courseName
                }
                head.dow to ScheduledLessonDto(
                    id = head.lessonId,
                    subject = subjectMerged,
                    timeRange = head.timeRange,
                    room = head.roomName,
                    teacher = head.teacherName,
                    teacherTicket = head.teacherTicket,
                    lessonKind = head.lessonKind,
                    weekParity = head.weekParity,
                    startTimeEpochMillis = head.start.toEpochMilli(),
                    endTimeEpochMillis = head.end.toEpochMilli(),
                    mergedLessonIds = ids,
                    mergedGroupsLabel = mergedGroupsLabel,
                )
            }

        val groupName = when (role) {
            UserRole.STUDENT -> lessonRows.firstNotNullOfOrNull { it.second }
            else -> null
        }

        val byDay = scheduled.groupBy({ it.first }, { it.second })
        val columns = dayOrder.map { (dow, label) ->
            DayScheduleColumnDto(
                dayOfWeek = label,
                lessons = byDay[dow].orEmpty().sortedBy { it.startTimeEpochMillis },
            )
        }
        weekDto(groupName = groupName, columns = columns)
    }

    private fun emptyColumns(): List<DayScheduleColumnDto> =
        dayOrder.map { (_, label) -> DayScheduleColumnDto(dayOfWeek = label) }

    /** «4 курс • группы: 1, 2, 3, 7, 8» или «1 курс • группа 8». */
    private fun formatGroupsAudience(bucket: List<RawLesson>): String? {
        val distinct = bucket.distinctBy { it.groupId }
        if (distinct.isEmpty()) return null
        val courses = distinct.map { it.groupCourse }.filter { it > 0 }.distinct()
        return if (distinct.size == 1) {
            val g = distinct.first()
            if (g.groupCourse > 0) "${g.groupCourse} курс • группа ${g.groupNumber}" else g.groupLabel
        } else if (courses.size == 1) {
            val nums = distinct
                .sortedWith(compareBy({ it.groupNumber }))
                .map { it.groupNumber }
                .distinct()
                .joinToString(", ")
            "${courses.first()} курс • группы: $nums"
        } else {
            distinct
                .sortedWith(compareBy({ it.groupCourse }, { it.groupNumber }))
                .mapNotNull { it.groupCompact }
                .distinct()
                .joinToString(", ")
                .let { if (it.isBlank()) null else "группы: $it" }
        }
    }
}
