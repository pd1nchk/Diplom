package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class AdminRoomDto(
    val id: Long,
    val name: String,
    val nfcUid: String? = null,
)

@Serializable
data class AdminBindRoomNfcRequestDto(
    val nfcUid: String,
)

@Serializable
data class AdminLessonCreateRequestDto(
    val courseId: Long,
    val groupId: Long,
    val roomName: String,
    val startIso: String,
    val endIso: String,
    val lessonKind: String = "LECTURE",
    val weekParity: String = "ANY",
    val status: String = "SCHEDULED",
)

@Serializable
data class AdminLessonUpdateRequestDto(
    val roomName: String? = null,
    val startIso: String? = null,
    val endIso: String? = null,
    val lessonKind: String? = null,
    val weekParity: String? = null,
    val status: String? = null,
)

@Serializable
data class AdminLessonCreateResponseDto(
    val lessonId: Long,
)

@Serializable
data class AdminTeacherRefDto(
    val userId: Long,
    val fullName: String,
)

@Serializable
data class AdminCourseRefDto(
    val id: Long,
    val name: String,
    val teacherFullName: String,
    /** 0 — если ответ от старого сервера без поля; клиент подставит преподавателя по ФИО при наличии списка teachers. */
    val teacherUserId: Long = 0L,
    val linkedGroupIds: List<Long> = emptyList(),
)

@Serializable
data class AdminCourseCreateRequestDto(
    val name: String,
    val teacherUserId: Long,
)

@Serializable
data class AdminCourseCreateResponseDto(
    val courseId: Long,
)

@Serializable
data class AdminCourseUpdateRequestDto(
    val name: String? = null,
    val teacherUserId: Long? = null,
)

@Serializable
data class AdminCourseGroupsRequestDto(
    val groupIds: List<Long> = emptyList(),
)

@Serializable
data class AdminGroupRefDto(
    val id: Long,
    val name: String,
    val course: Int,
    val groupNumber: Int,
)

@Serializable
data class AdminScheduleRefsDto(
    val courses: List<AdminCourseRefDto> = emptyList(),
    val groups: List<AdminGroupRefDto> = emptyList(),
    val teachers: List<AdminTeacherRefDto> = emptyList(),
)

/** Сводка занятия для админ-панели (время в ISO — только для клиентского парсинга, не для показа пользователю). */
@Serializable
data class AdminLessonSummaryDto(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val teacherName: String,
    val groupId: Long,
    val groupDisplayName: String,
    val roomName: String,
    val startIso: String,
    val endIso: String,
    val status: String,
    val lessonKind: String,
)

/** Результат [com.example.diplom.admin.AdminScheduleRepository.listScheduleRefs] (не JSON API). */
data class AdminScheduleRefsLoad(
    val refs: AdminScheduleRefsDto?,
    val error: String?,
)

data class AdminLessonsLoad(
    val lessons: List<AdminLessonSummaryDto>,
    val error: String?,
)

data class AdminRoomsLoad(
    val rooms: List<AdminRoomDto>,
    val error: String?,
)
