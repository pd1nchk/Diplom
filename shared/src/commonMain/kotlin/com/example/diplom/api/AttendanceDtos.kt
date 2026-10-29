package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class TeacherGroupCourseDto(
    val groupId: Long,
    val groupName: String,
    val courseId: Long,
    val courseName: String,
    val course: Int,
    val groupNumber: Int,
)

@Serializable
data class LessonJournalEntryDto(
    val lessonId: Long,
    val subject: String,
    val lessonKind: String,
    val dateIso: String,
    val timeRange: String,
    /** Если занятие переходит на другой календарный день — готовая подпись колонки журнала */
    val journalColumnLabel: String? = null,
    val room: String,
    /** UID метки аудитории (как в админке); null — метка не привязана */
    val roomNfcUid: String? = null,
    val rows: List<StudentJournalRowDto> = emptyList(),
)

@Serializable
data class StudentJournalRowDto(
    val studentTicket: String,
    val fullName: String,
    val absenceReason: String? = null,
    /** Явная отметка «присутствует» в БД (status PRESENT); при null reason без этого флага — «не отмечено» или логика NFC-сессии */
    val isPresent: Boolean = false,
    /** Сырое значение attendance.status (PRESENT, ABSENT_MANUAL, …) — надёжнее для UI после перезахода */
    val attendanceStatus: String? = null,
)

@Serializable
data class JournalUpdateRequest(
    val lessonId: Long,
    val studentTicket: String,
    val absenceReason: String? = null, // UNEXCUSED | EXCUSED | PRESENT | CLEAR (сброс) | null/пусто → присутствует (сервер нормализует)
)

@Serializable
data class UserProfileDto(
    val studentTicket: String,
    val fullName: String,
    val role: String,
    val groupName: String? = null,
    val course: Int? = null,
    val groupNumber: Int? = null,
    val phone: String? = null,
    val photoHex: String? = null,
    val degree: String? = null,
    val specialtyCode: String? = null,
    val specialtyName: String? = null,
    val facultyName: String? = null,
    val departmentName: String? = null,
)

@Serializable
data class RescheduleLessonRequestDto(
    val lessonId: Long,
    val newStartIso: String,
    val newEndIso: String,
)

@Serializable
data class RescheduleRequestCreateDto(
    val lessonId: Long,
    val desiredDateIso: String, // yyyy-MM-dd
    val desiredTimeFrom: String, // HH:mm
    val desiredTimeTo: String, // HH:mm
    val comment: String? = null,
)

@Serializable
data class AdminRescheduleRequestDto(
    val id: Long,
    val lessonId: Long,
    val teacherTicket: String,
    val teacherName: String,
    val subject: String,
    val groupName: String,
    val roomName: String,
    val currentStartIso: String,
    val currentEndIso: String,
    val proposedStartIso: String,
    val proposedEndIso: String,
    /** Дата/время переноса в поясе Europe/Minsk (для UI без ошибок TZ). */
    val proposedDateIso: String = "",
    val proposedTimeFrom: String = "",
    val proposedTimeTo: String = "",
    val status: String,
    val comment: String? = null,
)

@Serializable
data class AdminRescheduleDecisionRequestDto(
    val requestId: Long,
    val newStartIso: String,
    val newEndIso: String,
    val roomId: Long? = null,
    val comment: String? = null,
)

@Serializable
data class AdminRescheduleRejectRequestDto(
    val requestId: Long,
    val comment: String? = null,
)

@Serializable
data class StudentAbsenceDto(
    val lessonId: Long,
    val subject: String,
    val dateIso: String,
    val timeRange: String,
    val reason: String, // EXCUSED | UNEXCUSED
    val teacher: String,
)

@Serializable
data class StartAttendanceSessionRequestDto(
    val lessonId: Long,
    /** Сколько минут держать сессию открытой (по умолчанию до конца пары, но не меньше 15) */
    val durationMinutes: Int? = null,
)

@Serializable
data class StopAttendanceSessionRequestDto(
    val sessionId: Long,
)

@Serializable
data class AttendanceSessionDto(
    val sessionId: Long,
    val lessonId: Long,
    val teacherName: String,
    val teacherPhotoHex: String? = null,
    val subject: String,
    val groupName: String,
    /** Аудитория занятия (для подтверждения студентом). */
    val roomName: String? = null,
    val expiresAtIso: String,
    val isActive: Boolean = true,
)

@Serializable
data class NfcMarkRequestDto(
    val sessionId: Long,
    /** UID NFC-метки на двери аудитории (привязана к `rooms.nfc_uid`). */
    val cardUid: String,
    /** Билет студента — идентификация при сканировании метки аудитории. */
    val studentTicket: String? = null,
)

@Serializable
data class NfcMarkResultDto(
    val success: Boolean,
    val studentName: String? = null,
    val message: String = "",
    val code: String? = null,
)

/** Тело POST `/attendance/teacher-lesson-check/...` — надёжнее, чем UID в query string. */
@Serializable
data class TeacherLessonCheckRequestDto(
    val roomNfc: String? = null,
)

/** Ответ на проверку «есть ли у преподавателя пара сейчас / скоро в этой аудитории» */
@Serializable
data class TeacherLessonCheckDto(
    val lessonId: Long,
    val subject: String,
    val groupName: String,
    val room: String,
    val timeRange: String,
    /** IN_PROGRESS — идёт сейчас; UPCOMING — в ближайшие 30 минут до начала (можно открыть NFC-сессию) */
    val phase: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    /**
     * true — преподаватель передал NFC аудитории, а у этой пары в БД у комнаты **нет** привязанного `nfc_uid`;
     * совпадение по метке не проверялось. Имеет смысл предупредить администратора.
     */
    val roomNfcUnconfiguredWarning: Boolean = false,
)

@Serializable
data class SessionRosterRowDto(
    val studentTicket: String,
    val fullName: String,
    val present: Boolean,
)

