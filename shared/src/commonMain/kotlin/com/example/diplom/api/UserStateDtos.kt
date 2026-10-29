package com.example.diplom.api

import com.example.diplom.domain.RescheduleStatus
import kotlinx.serialization.Serializable

@Serializable
data class UserStateDto(
    val profileImageHex: String? = null,
    val profilePhone: String = "",
    val profileGroup: Int = 0,
    val profileCourse: Int = 0,
    val profileStudentId: String = "",
    val calendarEvents: List<CalendarEventDto> = emptyList(),
    val schedules: List<CourseScheduleDto> = emptyList(),
    val rescheduleRequests: List<RescheduleRequestItemDto> = emptyList(),
    val attendance: List<AttendanceRecordDto> = emptyList(),
    val grades: List<GradeRecordDto> = emptyList(),
    val notifications: List<AppNotificationDto> = emptyList(),
    val faculties: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val students: List<StudentDirectoryItemDto> = emptyList(),
)

@Serializable
data class CalendarEventDto(
    val id: Long,
    val date: String,
    val title: String,
    val ownerTicket: String,
    val description: String = "",
    /** violet | blue | green | red | yellow */
    val color: String = "violet",
    val time: String = "",
    val reminder: String = "",
    val pinned: Boolean = false,
)

@Serializable
data class LessonDto(
    val id: Long,
    val subject: String,
    val time: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: String,
)

@Serializable
data class CourseScheduleDto(
    val course: Int,
    val group: Int,
    val faculty: String,
    val lessons: List<LessonDto> = emptyList(),
)

@Serializable
data class RescheduleRequestItemDto(
    val id: Long,
    val lesson: LessonDto,
    val requestedBy: String,
    val proposedDate: String,
    val proposedTime: String,
    val comment: String,
    val status: RescheduleStatus = RescheduleStatus.PENDING,
)

@Serializable
data class AttendanceRecordDto(
    val id: Long,
    val date: String,
    val subject: String,
    val time: String,
    val reason: String,
)

@Serializable
data class GradeRecordDto(
    val id: Long,
    val subject: String,
    val score: Int,
    val maxScore: Int,
    val teacher: String,
    val date: String,
)

@Serializable
data class AppNotificationDto(
    val id: Long,
    val title: String,
    val message: String,
    val time: String,
    val isImportant: Boolean,
)

@Serializable
data class StudentDirectoryItemDto(
    val id: Long,
    val fullName: String,
    val faculty: String,
    val course: Int,
    val group: Int,
)
