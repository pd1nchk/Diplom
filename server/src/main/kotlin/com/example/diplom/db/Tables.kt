package com.example.diplom.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val studentTicket = varchar("student_ticket", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fullName = varchar("full_name", 255)
    val role = varchar("role", 32)
    val isActive = bool("is_active").default(true)
    val photoHex = text("photo_hex").nullable()
    val phone = varchar("phone", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object GroupsTable : Table("groups") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 128).uniqueIndex()
    val specialtyId = long("specialty_id").references(SpecialtiesTable.id).nullable()
    val course = integer("course").default(1)
    val groupNumber = integer("group_number").default(1)
    /** FIRST = 1-я смена (3–4 курс, с 8:00), SECOND = 2-я смена (1–2 курс, с 14:00) */
    val shift = varchar("shift", 16).default("FIRST")
    override val primaryKey = PrimaryKey(id)
}

object StudentsTable : Table("students") {
    val userId = long("user_id").references(UsersTable.id)
    val groupId = long("group_id").references(GroupsTable.id)
    override val primaryKey = PrimaryKey(userId)
}

object TeachersTable : Table("teachers") {
    val userId = long("user_id").references(UsersTable.id)
    val departmentId = long("department_id").references(DepartmentsTable.id)
    val degree = varchar("degree", 255).default("")
    override val primaryKey = PrimaryKey(userId)
}

object RoomsTable : Table("rooms") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 64).uniqueIndex()
    /** NFC-метка аудитории (как считывает Android reader), для привязки «занятие в этой парте» */
    val nfcUid = varchar("nfc_uid", 128).nullable().uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object CoursesTable : Table("courses") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val teacherId = long("teacher_id").references(TeachersTable.userId)
    override val primaryKey = PrimaryKey(id)
}

object GroupCoursesTable : Table("group_courses") {
    val id = long("id").autoIncrement()
    val groupId = long("group_id").references(GroupsTable.id)
    val courseId = long("course_id").references(CoursesTable.id)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(groupId, courseId)
    }
}

object LessonsTable : Table("lessons") {
    val id = long("id").autoIncrement()
    val courseId = long("course_id").references(CoursesTable.id)
    val groupId = long("group_id").references(GroupsTable.id)
    val roomId = long("room_id").references(RoomsTable.id)
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val status = varchar("status", 32)
    val lessonKind = varchar("lesson_kind", 16).default("LECTURE")
    /** ANY — каждую неделю; EVEN / ODD — только в ISO-неделях с чётным / нечётным номером года */
    val weekParity = varchar("week_parity", 8).default("ANY")
    override val primaryKey = PrimaryKey(id)
}

object NotificationsTable : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val title = varchar("title", 255)
    val message = text("message")
    val createdAt = timestamp("created_at")
    val isImportant = bool("is_important").default(false)
    override val primaryKey = PrimaryKey(id)
}

object AttendanceTable : Table("attendance") {
    val id = long("id").autoIncrement()
    val lessonId = long("lesson_id").references(LessonsTable.id)
    val studentId = long("student_id").references(StudentsTable.userId)
    val timestamp = timestamp("timestamp")
    val status = varchar("status", 32)
    val reason = varchar("reason", 32).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(lessonId, studentId)
    }
}

object AttendanceSessionsTable : Table("attendance_sessions") {
    val id = long("id").autoIncrement()
    val lessonId = long("lesson_id").references(LessonsTable.id)
    val teacherId = long("teacher_id").references(TeachersTable.userId)
    val startedAt = timestamp("started_at")
    val expiresAt = timestamp("expires_at")
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object RescheduleRequestsTable : Table("reschedule_requests") {
    val id = long("id").autoIncrement()
    val lessonId = long("lesson_id").references(LessonsTable.id)
    val requestedByTeacherId = long("requested_by_teacher_id").references(TeachersTable.userId)
    val proposedStartTime = timestamp("proposed_start_time")
    val proposedEndTime = timestamp("proposed_end_time")
    val proposedRoomId = long("proposed_room_id").references(RoomsTable.id).nullable()
    val status = varchar("status", 32)
    val adminId = long("admin_id").references(UsersTable.id).nullable()
    val decisionTime = timestamp("decision_time").nullable()
    val comment = text("comment").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Личные заметки календаря — одна строка на одно событие (1NF). */
object CalendarEventsTable : Table("calendar_events") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val eventDate = varchar("event_date", 16)
    val title = varchar("title", 255)
    val description = text("description").default("")
    val color = varchar("color", 16).default("violet")
    val eventTime = varchar("event_time", 32).default("")
    val reminder = varchar("reminder", 64).default("")
    val pinned = bool("pinned").default(false)
    override val primaryKey = PrimaryKey(id)
}

object FacultiesTable : Table("faculties") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object DepartmentsTable : Table("departments") {
    val id = long("id").autoIncrement()
    val facultyId = long("faculty_id").references(FacultiesTable.id)
    val name = varchar("name", 255).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object SpecialtiesTable : Table("specialties") {
    val id = long("id").autoIncrement()
    val facultyId = long("faculty_id").references(FacultiesTable.id)
    val code = varchar("code", 64).uniqueIndex()
    val name = varchar("name", 255)
    val profile = varchar("profile", 255)
    override val primaryKey = PrimaryKey(id)
}

object PasswordResetRequestsTable : Table("password_reset_requests") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id).nullable()
    val studentTicket = varchar("student_ticket", 64)
    val contact = varchar("contact", 128).default("")
    val comment = text("comment").default("")
    val status = varchar("status", 32).default("PENDING")
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
    val adminId = long("admin_id").references(UsersTable.id).nullable()
    val adminTicket = varchar("admin_ticket", 64).nullable()
    val adminComment = text("admin_comment").nullable()
    override val primaryKey = PrimaryKey(id)
}

