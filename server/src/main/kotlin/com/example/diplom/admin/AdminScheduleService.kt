package com.example.diplom.admin

import com.example.diplom.api.AdminBindRoomNfcRequestDto
import com.example.diplom.api.AdminCourseCreateRequestDto
import com.example.diplom.api.AdminCourseGroupsRequestDto
import com.example.diplom.api.AdminCourseRefDto
import com.example.diplom.api.AdminCourseUpdateRequestDto
import com.example.diplom.api.AdminGroupRefDto
import com.example.diplom.api.AdminLessonCreateRequestDto
import com.example.diplom.api.AdminLessonSummaryDto
import com.example.diplom.api.AdminLessonUpdateRequestDto
import com.example.diplom.api.AdminRoomDto
import com.example.diplom.api.AdminScheduleRefsDto
import com.example.diplom.api.AdminTeacherRefDto
import com.example.diplom.db.AttendanceSessionsTable
import com.example.diplom.db.AttendanceTable
import com.example.diplom.db.CoursesTable
import com.example.diplom.db.GroupCoursesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.RescheduleRequestsTable
import com.example.diplom.db.RoomsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.UsersTable
import com.example.diplom.domain.UserRole
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class AdminScheduleService {

    private fun requireAdmin(ticket: String): Long? = transaction {
        val u = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket.trim() }.singleOrNull()
            ?: return@transaction null
        val role = parseUserRole(u[UsersTable.role]) ?: return@transaction null
        if (role != UserRole.ADMIN) return@transaction null
        u[UsersTable.id]
    }

    private fun parseUserRole(raw: String): UserRole? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        runCatching { UserRole.valueOf(t) }.getOrNull()?.let { return it }
        return UserRole.entries.firstOrNull { it.name.equals(t, ignoreCase = true) }
    }

    fun isAdmin(adminTicket: String): Boolean = transaction {
        requireAdmin(adminTicket.trim()) != null
    }

    fun listRooms(adminTicket: String): List<AdminRoomDto> = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction emptyList()
        RoomsTable.selectAll().map {
            AdminRoomDto(
                id = it[RoomsTable.id],
                name = it[RoomsTable.name],
                nfcUid = it[RoomsTable.nfcUid],
            )
        }.sortedBy { it.name }
    }

    fun listScheduleRefs(adminTicket: String): AdminScheduleRefsDto? = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction null
        val linksByCourse: Map<Long, List<Long>> = GroupCoursesTable.selectAll()
            .groupBy({ it[GroupCoursesTable.courseId] }, { it[GroupCoursesTable.groupId] })
        val courseRows = CoursesTable.selectAll().toList()
        val teacherIds = courseRows.map { it[CoursesTable.teacherId] }.distinct()
        val teacherNames = if (teacherIds.isEmpty()) {
            emptyMap()
        } else {
            UsersTable.selectAll().where { UsersTable.id inList teacherIds }
                .associate { it[UsersTable.id] to it[UsersTable.fullName] }
        }
        val courses = courseRows.map { row ->
            val tid = row[CoursesTable.teacherId]
            val cid = row[CoursesTable.id]
            AdminCourseRefDto(
                id = cid,
                name = row[CoursesTable.name],
                teacherUserId = tid,
                teacherFullName = teacherNames[tid].orEmpty(),
                linkedGroupIds = linksByCourse[cid].orEmpty().sorted(),
            )
        }.sortedBy { it.name.lowercase() }
        val groups = GroupsTable.selectAll().map {
            AdminGroupRefDto(
                id = it[GroupsTable.id],
                name = it[GroupsTable.name],
                course = it[GroupsTable.course],
                groupNumber = it[GroupsTable.groupNumber],
            )
        }.sortedWith(compareBy({ it.course }, { it.groupNumber }, { it.name.lowercase() }))
        val teachers = (UsersTable innerJoin TeachersTable)
            .selectAll()
            .map {
                AdminTeacherRefDto(
                    userId = it[UsersTable.id],
                    fullName = it[UsersTable.fullName],
                )
            }
            .sortedBy { it.fullName.lowercase() }
        AdminScheduleRefsDto(courses = courses, groups = groups, teachers = teachers)
    }

    fun createCourse(adminTicket: String, req: AdminCourseCreateRequestDto): Long? = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction null
        val name = req.name.trim()
        if (name.isEmpty()) return@transaction null
        val tid = req.teacherUserId
        TeachersTable.selectAll().where { TeachersTable.userId eq tid }.singleOrNull() ?: return@transaction null
        val courseId = CoursesTable.insert {
            it[CoursesTable.name] = name
            it[CoursesTable.teacherId] = tid
        } get CoursesTable.id
        courseId
    }

    fun updateCourse(adminTicket: String, courseId: Long, req: AdminCourseUpdateRequestDto): Boolean = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction false
        CoursesTable.selectAll().where { CoursesTable.id eq courseId }.singleOrNull() ?: return@transaction false
        val tid = req.teacherUserId
        if (tid != null) {
            TeachersTable.selectAll().where { TeachersTable.userId eq tid }.singleOrNull() ?: return@transaction false
        }
        CoursesTable.update({ CoursesTable.id eq courseId }) {
            req.name?.trim()?.takeIf { n -> n.isNotEmpty() }?.let { n -> it[CoursesTable.name] = n }
            if (tid != null) it[CoursesTable.teacherId] = tid
        }
        true
    }

    fun listLessons(adminTicket: String): List<AdminLessonSummaryDto> = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction emptyList()
        val lessonRows = LessonsTable.selectAll().orderBy(LessonsTable.startTime to SortOrder.DESC).toList()
        if (lessonRows.isEmpty()) return@transaction emptyList()
        val courseIds = lessonRows.map { it[LessonsTable.courseId] }.distinct()
        val groupIds = lessonRows.map { it[LessonsTable.groupId] }.distinct()
        val roomIds = lessonRows.map { it[LessonsTable.roomId] }.distinct()
        val courses = CoursesTable.selectAll().where { CoursesTable.id inList courseIds }
            .associateBy { it[CoursesTable.id] }
        val teacherIds = courses.values.map { it[CoursesTable.teacherId] }.distinct()
        val teachers = if (teacherIds.isEmpty()) {
            emptyMap()
        } else {
            UsersTable.selectAll().where { UsersTable.id inList teacherIds }
                .associate { it[UsersTable.id] to it[UsersTable.fullName] }
        }
        val groups = GroupsTable.selectAll().where { GroupsTable.id inList groupIds }
            .associateBy { it[GroupsTable.id] }
        val rooms = RoomsTable.selectAll().where { RoomsTable.id inList roomIds }
            .associateBy { it[RoomsTable.id] }
        lessonRows.mapNotNull { l ->
            val course = courses[l[LessonsTable.courseId]] ?: return@mapNotNull null
            val tid = course[CoursesTable.teacherId]
            val gid = l[LessonsTable.groupId]
            val rid = l[LessonsTable.roomId]
            val g = groups[gid] ?: return@mapNotNull null
            val room = rooms[rid] ?: return@mapNotNull null
            val groupLabel = "${g[GroupsTable.course]} к., гр. ${g[GroupsTable.groupNumber]} — ${g[GroupsTable.name]}"
            AdminLessonSummaryDto(
                id = l[LessonsTable.id],
                courseId = course[CoursesTable.id],
                courseName = course[CoursesTable.name],
                teacherName = teachers[tid].orEmpty(),
                groupId = gid,
                groupDisplayName = groupLabel,
                roomName = room[RoomsTable.name],
                startIso = l[LessonsTable.startTime].toString(),
                endIso = l[LessonsTable.endTime].toString(),
                status = l[LessonsTable.status],
                lessonKind = l[LessonsTable.lessonKind],
            )
        }
    }

    fun deleteCourse(adminTicket: String, courseId: Long): Boolean = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction false
        if (CoursesTable.selectAll().where { CoursesTable.id eq courseId }.singleOrNull() == null) {
            return@transaction false
        }
        val lessonIds = LessonsTable.selectAll().where { LessonsTable.courseId eq courseId }
            .map { it[LessonsTable.id] }
        if (lessonIds.isNotEmpty()) {
            lessonIds.chunked(200).forEach { chunk ->
                AttendanceSessionsTable.deleteWhere { AttendanceSessionsTable.lessonId inList chunk }
                AttendanceTable.deleteWhere { AttendanceTable.lessonId inList chunk }
                RescheduleRequestsTable.deleteWhere { RescheduleRequestsTable.lessonId inList chunk }
            }
            LessonsTable.deleteWhere { LessonsTable.courseId eq courseId }
        }
        GroupCoursesTable.deleteWhere { GroupCoursesTable.courseId eq courseId }
        val removed = CoursesTable.deleteWhere { CoursesTable.id eq courseId }
        removed > 0
    }

    fun setCourseGroups(adminTicket: String, courseId: Long, body: AdminCourseGroupsRequestDto): Boolean = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction false
        CoursesTable.selectAll().where { CoursesTable.id eq courseId }.singleOrNull() ?: return@transaction false
        val distinct = body.groupIds.distinct().filter { it > 0 }
        if (distinct.isNotEmpty()) {
            val existing = GroupsTable.selectAll().where { GroupsTable.id inList distinct }
                .map { it[GroupsTable.id] }
                .toSet()
            if (existing.size != distinct.size) return@transaction false
        }
        GroupCoursesTable.deleteWhere { GroupCoursesTable.courseId eq courseId }
        distinct.forEach { gid ->
            GroupCoursesTable.insert {
                it[GroupCoursesTable.courseId] = courseId
                it[GroupCoursesTable.groupId] = gid
            }
        }
        true
    }

    fun bindRoomNfc(adminTicket: String, roomId: Long, body: AdminBindRoomNfcRequestDto): Boolean = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction false
        val uid = body.nfcUid.trim()
        if (uid.isEmpty()) return@transaction false
        val updated = RoomsTable.update({ RoomsTable.id eq roomId }) {
            it[RoomsTable.nfcUid] = uid
        }
        updated > 0
    }

    fun createLesson(adminTicket: String, req: AdminLessonCreateRequestDto): Long? = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction null
        val room = RoomsTable.selectAll().where { RoomsTable.name eq req.roomName.trim() }.singleOrNull()
            ?: return@transaction null
        CoursesTable.selectAll().where { CoursesTable.id eq req.courseId }.singleOrNull()
            ?: return@transaction null
        GroupsTable.selectAll().where { GroupsTable.id eq req.groupId }.singleOrNull() ?: return@transaction null
        if (!GroupCoursesTable.selectAll().where {
                (GroupCoursesTable.groupId eq req.groupId) and (GroupCoursesTable.courseId eq req.courseId)
            }.any()
        ) {
            return@transaction null
        }
        val start = java.time.Instant.parse(req.startIso.trim())
        val end = java.time.Instant.parse(req.endIso.trim())
        if (!end.isAfter(start)) return@transaction null
        LessonsTable.insert {
            it[LessonsTable.courseId] = req.courseId
            it[LessonsTable.groupId] = req.groupId
            it[LessonsTable.roomId] = room[RoomsTable.id]
            it[LessonsTable.startTime] = start
            it[LessonsTable.endTime] = end
            it[LessonsTable.status] = req.status.ifBlank { "SCHEDULED" }
            it[LessonsTable.lessonKind] = req.lessonKind.ifBlank { "LECTURE" }
            it[LessonsTable.weekParity] = req.weekParity.ifBlank { "ANY" }
        } get LessonsTable.id
    }

    fun updateLesson(adminTicket: String, lessonId: Long, req: AdminLessonUpdateRequestDto): Boolean = transaction {
        if (requireAdmin(adminTicket) == null) return@transaction false
        val existing = LessonsTable.selectAll().where { LessonsTable.id eq lessonId }.singleOrNull()
            ?: return@transaction false
        val roomId = req.roomName?.trim()?.takeIf { it.isNotEmpty() }?.let { rn ->
            RoomsTable.selectAll().where { RoomsTable.name eq rn }.singleOrNull()?.get(RoomsTable.id)
        }
        val newStart = req.startIso?.trim()?.takeIf { it.isNotEmpty() }?.let { java.time.Instant.parse(it) }
        val newEnd = req.endIso?.trim()?.takeIf { it.isNotEmpty() }?.let { java.time.Instant.parse(it) }
        val timeChanged = newStart != null || newEnd != null
        LessonsTable.update({ LessonsTable.id eq lessonId }) {
            if (roomId != null) it[LessonsTable.roomId] = roomId
            if (newStart != null) it[LessonsTable.startTime] = newStart
            if (newEnd != null) it[LessonsTable.endTime] = newEnd
            req.lessonKind?.trim()?.takeIf { it.isNotEmpty() }?.let { v -> it[LessonsTable.lessonKind] = v }
            req.weekParity?.trim()?.takeIf { it.isNotEmpty() }?.let { v -> it[LessonsTable.weekParity] = v }
            req.status?.trim()?.takeIf { it.isNotEmpty() }?.let { v -> it[LessonsTable.status] = v }
        }
        if (timeChanged) {
            AttendanceTable.deleteWhere { AttendanceTable.lessonId eq lessonId }
            AttendanceSessionsTable.deleteWhere { AttendanceSessionsTable.lessonId eq lessonId }
        }
        true
    }
}
