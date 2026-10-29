package com.example.diplom.domain

import kotlinx.serialization.Serializable

/**
 * Базовые доменные модели, общие для клиента и сервера.
 * Это «язык предметной области», на котором будем строить API и UI.
 */

enum class UserRole {
    STUDENT,
    TEACHER,
    ADMIN,
}

data class User(
    val id: Long,
    val studentTicket: String,
    val fullName: String,
    val role: UserRole,
    val isActive: Boolean = true,
)

data class Group(
    val id: Long,
    val name: String,
)

data class StudentProfile(
    val userId: Long,
    val groupId: Long,
)

data class TeacherProfile(
    val userId: Long,
)

data class Course(
    val id: Long,
    val name: String,
    val teacherId: Long,
)

data class Room(
    val id: Long,
    val name: String,
)

enum class LessonStatus {
    SCHEDULED,
    MOVED,
    CANCELLED,
}

data class Lesson(
    val id: Long,
    val courseId: Long,
    val groupId: Long,
    val roomId: Long,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val status: LessonStatus = LessonStatus.SCHEDULED,
)

enum class AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT_MANUAL,
}

data class AttendanceRecord(
    val id: Long,
    val lessonId: Long,
    val studentId: Long,
    val timestampMillis: Long,
    val status: AttendanceStatus,
)

data class NfcToken(
    val id: Long,
    val studentId: Long,
    val tokenUid: String,
    val isActive: Boolean = true,
)

@Serializable
enum class RescheduleStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

data class RescheduleRequest(
    val id: Long,
    val lessonId: Long,
    val requestedByTeacherId: Long,
    val proposedStartTimeMillis: Long,
    val proposedEndTimeMillis: Long,
    val proposedRoomId: Long?,
    val status: RescheduleStatus = RescheduleStatus.PENDING,
    val adminId: Long? = null,
    val decisionTimeMillis: Long? = null,
    val comment: String? = null,
)

