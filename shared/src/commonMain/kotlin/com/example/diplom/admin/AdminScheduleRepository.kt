package com.example.diplom.admin

import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.api.AdminBindRoomNfcRequestDto
import com.example.diplom.api.AdminCourseCreateRequestDto
import com.example.diplom.api.AdminCourseCreateResponseDto
import com.example.diplom.api.AdminCourseGroupsRequestDto
import com.example.diplom.api.AdminCourseUpdateRequestDto
import com.example.diplom.api.AdminLessonCreateRequestDto
import com.example.diplom.api.AdminLessonCreateResponseDto
import com.example.diplom.api.AdminLessonSummaryDto
import com.example.diplom.api.AdminLessonUpdateRequestDto
import com.example.diplom.api.AdminLessonsLoad
import com.example.diplom.api.AdminRoomDto
import com.example.diplom.api.AdminRoomsLoad
import com.example.diplom.api.AdminScheduleRefsDto
import com.example.diplom.api.AdminScheduleRefsLoad
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AdminScheduleRepository {
    private fun pathTicket(adminTicket: String): String = adminTicket.trim().encodeURLPathPart()

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

    suspend fun listRooms(adminTicket: String): AdminRoomsLoad = runCatching {
        val t = pathTicket(adminTicket)
        val path = "/admin/rooms/$t"
        val r = client.get("$SERVER_BASE_URL$path")
        when (r.status) {
            HttpStatusCode.OK -> {
                val parsed = runCatching { r.body<List<AdminRoomDto>>() }.getOrElse { ex ->
                    return@runCatching AdminRoomsLoad(
                        rooms = emptyList(),
                        error = "Ответ сервера не разобран. $path — ${ex.message}",
                    )
                }
                AdminRoomsLoad(rooms = parsed, error = null)
            }
            HttpStatusCode.Forbidden -> AdminRoomsLoad(
                rooms = emptyList(),
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
            else -> AdminRoomsLoad(
                rooms = emptyList(),
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
        }
    }.getOrElse { ex ->
        AdminRoomsLoad(rooms = emptyList(), error = adminFetchFailureMessage("GET", "/admin/rooms/…", ex))
    }

    suspend fun listScheduleRefs(adminTicket: String): AdminScheduleRefsLoad = runCatching {
        val t = pathTicket(adminTicket)
        val path = "/admin/schedule-refs/$t"
        val r = client.get("$SERVER_BASE_URL$path")
        when (r.status) {
            HttpStatusCode.OK -> {
                val parsed = runCatching { r.body<AdminScheduleRefsDto>() }.getOrElse { ex ->
                    return@runCatching AdminScheduleRefsLoad(
                        refs = null,
                        error = "Ответ сервера не разобран (часто: на сервере старая версия приложения). ${ex.message} ($path)",
                    )
                }
                AdminScheduleRefsLoad(refs = parsed, error = null)
            }
            HttpStatusCode.Forbidden -> AdminScheduleRefsLoad(
                refs = null,
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
            else -> AdminScheduleRefsLoad(
                refs = null,
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
        }
    }.getOrElse { ex ->
        AdminScheduleRefsLoad(
            refs = null,
            error = adminFetchFailureMessage("GET", "/admin/schedule-refs/…", ex),
        )
    }

    suspend fun createCourse(adminTicket: String, body: AdminCourseCreateRequestDto): Pair<Long?, String?> = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.post("$SERVER_BASE_URL/admin/courses/$t") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        if (r.status == HttpStatusCode.OK) {
            val id = r.body<AdminCourseCreateResponseDto>().courseId
            id to null
        } else {
            null to adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { null to (it.message?.takeIf { m -> m.isNotBlank() } ?: "Не удалось подключиться к серверу.") }

    suspend fun updateCourse(adminTicket: String, courseId: Long, body: AdminCourseUpdateRequestDto): String? = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.put("$SERVER_BASE_URL/admin/courses/$t/$courseId") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        when (r.status) {
            HttpStatusCode.OK -> null
            else -> adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { it.message ?: "Не удалось подключиться к серверу." }

    suspend fun setCourseGroups(adminTicket: String, courseId: Long, body: AdminCourseGroupsRequestDto): String? = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.put("$SERVER_BASE_URL/admin/courses/$t/$courseId/groups") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        when (r.status) {
            HttpStatusCode.OK -> null
            else -> adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { it.message ?: "Не удалось подключиться к серверу." }

    suspend fun bindRoomNfc(
        adminTicket: String,
        roomId: Long,
        nfcUid: String,
    ): String? = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.post("$SERVER_BASE_URL/admin/rooms/$t/$roomId/nfc") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(AdminBindRoomNfcRequestDto(nfcUid = nfcUid.trim()))
        }
        when (r.status) {
            HttpStatusCode.OK -> null
            else -> adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { it.message ?: "Не удалось подключиться к серверу." }

    suspend fun listLessons(adminTicket: String): AdminLessonsLoad = runCatching {
        val t = pathTicket(adminTicket)
        val path = "/admin/lessons/$t"
        val r = client.get("$SERVER_BASE_URL$path")
        when (r.status) {
            HttpStatusCode.OK -> {
                val parsed = runCatching { r.body<List<AdminLessonSummaryDto>>() }.getOrElse { ex ->
                    return@runCatching AdminLessonsLoad(
                        lessons = emptyList(),
                        error = "Ответ сервера не разобран. Обновите сервер. ${ex.message} ($path)",
                    )
                }
                AdminLessonsLoad(lessons = parsed, error = null)
            }
            HttpStatusCode.Forbidden -> AdminLessonsLoad(
                lessons = emptyList(),
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
            else -> AdminLessonsLoad(
                lessons = emptyList(),
                error = adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
        }
    }.getOrElse { ex ->
        AdminLessonsLoad(lessons = emptyList(), error = adminFetchFailureMessage("GET", "/admin/lessons/…", ex))
    }

    suspend fun deleteCourse(adminTicket: String, courseId: Long): String? = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.delete("$SERVER_BASE_URL/admin/courses/$t/$courseId")
        when (r.status) {
            HttpStatusCode.OK -> null
            else -> adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { it.message ?: "Не удалось подключиться к серверу." }

    suspend fun createLesson(adminTicket: String, body: AdminLessonCreateRequestDto): Pair<Long?, String?> = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.post("$SERVER_BASE_URL/admin/lessons/$t") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        if (r.status == HttpStatusCode.OK) {
            val id = r.body<AdminLessonCreateResponseDto>().lessonId
            id to null
        } else {
            null to adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { null to (it.message?.takeIf { m -> m.isNotBlank() } ?: "Не удалось подключиться к серверу.") }

    suspend fun updateLesson(adminTicket: String, lessonId: Long, body: AdminLessonUpdateRequestDto): String? = runCatching {
        val t = pathTicket(adminTicket)
        val r = client.put("$SERVER_BASE_URL/admin/lessons/$t/$lessonId") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        when (r.status) {
            HttpStatusCode.OK -> null
            else -> adminHttpUserMessage(r.status, r.bodyAsText())
        }
    }.getOrElse { it.message ?: "Не удалось подключиться к серверу." }
}
