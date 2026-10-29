package com.example.diplom.attendance

import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.api.JournalUpdateRequest
import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.api.NfcMarkRequestDto
import com.example.diplom.api.NfcMarkResultDto
import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.RescheduleRequestCreateDto
import com.example.diplom.api.RescheduleLessonRequestDto
import com.example.diplom.api.SessionRosterRowDto
import com.example.diplom.api.StartAttendanceSessionRequestDto
import com.example.diplom.api.StopAttendanceSessionRequestDto
import com.example.diplom.api.StudentAbsenceDto
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.api.TeacherLessonCheckDto
import com.example.diplom.api.TeacherLessonCheckRequestDto
import com.example.diplom.api.UserProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AttendanceRepository {
    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            connectTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
            socketTimeoutMillis = HTTP_CLIENT_TIMEOUT_MS
        }
    }

    suspend fun teacherGroups(ticket: String): List<TeacherGroupCourseDto> = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/teacher-groups/$ticket")
        if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
        response.body<List<TeacherGroupCourseDto>>()
    }.getOrElse { emptyList() }

    suspend fun studentAbsences(ticket: String): List<StudentAbsenceDto> = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/student/$ticket")
        if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
        response.body<List<StudentAbsenceDto>>()
    }.getOrElse { emptyList() }

    suspend fun journal(ticket: String, groupId: Long, courseId: Long): List<LessonJournalEntryDto> = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/journal/$ticket") {
            parameter("groupId", groupId)
            parameter("courseId", courseId)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
        response.body<List<LessonJournalEntryDto>>()
    }.getOrElse { emptyList() }

    /**
     * @return null on success, or a short Russian message for the user.
     */
    suspend fun updateJournal(ticket: String, request: JournalUpdateRequest): String? = runCatching {
        val response = client.post("$SERVER_BASE_URL/attendance/journal/$ticket") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        when (response.status) {
            HttpStatusCode.OK -> null
            HttpStatusCode.Forbidden ->
                "Нет доступа к этому занятию (проверьте, что вы ведёте эту дисциплину) или студент не найден."
            HttpStatusCode.BadRequest ->
                "Некорректный запрос: ${response.bodyAsText().ifBlank { "проверьте данные" }}"
            else ->
                "Сервер ответил ${response.status.value}: ${response.bodyAsText().take(120).ifBlank { "без текста" }}"
        }
    }.getOrElse { e -> "Сеть: ${e.message ?: "ошибка"}" }

    suspend fun profile(ticket: String): UserProfileDto? = runCatching {
        val response = client.get("$SERVER_BASE_URL/profile/$ticket")
        if (response.status != HttpStatusCode.OK) return@runCatching null
        response.body<UserProfileDto>()
    }.getOrNull()

    suspend fun reschedule(request: RescheduleLessonRequestDto): Boolean = runCatching {
        val response = client.post("$SERVER_BASE_URL/schedule/reschedule") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        response.status == HttpStatusCode.OK
    }.getOrElse { false }

    suspend fun createRescheduleRequest(ticket: String, request: RescheduleRequestCreateDto): Boolean = runCatching {
        val response = client.post("$SERVER_BASE_URL/schedule/reschedule-request/$ticket") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        // Сервер отвечает 202 Accepted; на части клиентов/прокси встречается 200
        response.status == HttpStatusCode.Accepted || response.status == HttpStatusCode.OK
    }.getOrElse { false }

    suspend fun teacherLessonCheck(ticket: String, roomNfc: String? = null): TeacherLessonCheckDto? = runCatching {
        val trimmed = roomNfc?.trim()?.takeIf { it.isNotEmpty() }
        val response = client.post("$SERVER_BASE_URL/attendance/teacher-lesson-check/$ticket") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(TeacherLessonCheckRequestDto(roomNfc = trimmed))
        }
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        if (response.status != HttpStatusCode.OK) return@runCatching null
        response.body<TeacherLessonCheckDto>()
    }.getOrNull()

    suspend fun activeSessionForTeacher(ticket: String): AttendanceSessionDto? = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/session/active-teacher/$ticket")
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        if (response.status != HttpStatusCode.OK) return@runCatching null
        response.body<AttendanceSessionDto>()
    }.getOrNull()

    suspend fun activeSessionForStudent(ticket: String): AttendanceSessionDto? = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/session/active-student/$ticket")
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        if (response.status != HttpStatusCode.OK) return@runCatching null
        response.body<AttendanceSessionDto>()
    }.getOrNull()

    suspend fun sessionRoster(ticket: String, sessionId: Long): List<SessionRosterRowDto> = runCatching {
        val response = client.get("$SERVER_BASE_URL/attendance/session/roster/$ticket/$sessionId")
        if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
        response.body<List<SessionRosterRowDto>>()
    }.getOrElse { emptyList() }

    suspend fun startAttendanceSession(ticket: String, request: StartAttendanceSessionRequestDto): AttendanceSessionDto? = runCatching {
        val response = client.post("$SERVER_BASE_URL/attendance/session/start/$ticket") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        response.body<AttendanceSessionDto>()
    }.getOrNull()

    suspend fun stopAttendanceSession(ticket: String, request: StopAttendanceSessionRequestDto): Boolean = runCatching {
        val response = client.post("$SERVER_BASE_URL/attendance/session/stop/$ticket") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        response.status == HttpStatusCode.OK
    }.getOrElse { false }

    suspend fun markByNfc(request: NfcMarkRequestDto): NfcMarkResultDto = runCatching {
        val response = client.post("$SERVER_BASE_URL/attendance/nfc/mark") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }
        response.body<NfcMarkResultDto>()
    }.getOrElse { e ->
        NfcMarkResultDto(success = false, message = e.message ?: "Сеть", code = "network")
    }

}

