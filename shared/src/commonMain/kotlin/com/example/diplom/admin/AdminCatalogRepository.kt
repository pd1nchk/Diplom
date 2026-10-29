package com.example.diplom.admin

import com.example.diplom.HTTP_CLIENT_TIMEOUT_MS
import com.example.diplom.SERVER_BASE_URL
import com.example.diplom.api.AdminCatalogSnapshotDto
import com.example.diplom.api.AdminRescheduleDecisionRequestDto
import com.example.diplom.api.AdminRescheduleRejectRequestDto
import com.example.diplom.api.AdminRescheduleRequestDto
import com.example.diplom.api.CreateDepartmentRequest
import com.example.diplom.api.CreateFacultyRequest
import com.example.diplom.api.CreateSpecialtyRequest
import com.example.diplom.api.CreateStudentRequest
import com.example.diplom.api.CreateTeacherRequest
import com.example.diplom.api.ForgotPasswordAdminItemDto
import com.example.diplom.api.ForgotPasswordResolveRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AdminCatalogRepository {
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

    suspend fun snapshot(adminTicket: String): AdminCatalogSnapshotDto {
        val t = pathTicket(adminTicket)
        val path = "/admin/catalog/$t"
        val r = client.get("$SERVER_BASE_URL$path")
        if (r.status != HttpStatusCode.OK) {
            throw IllegalStateException(
                adminHttpUserMessage(r.status, r.bodyAsText()) + " GET $SERVER_BASE_URL$path",
            )
        }
        return r.body()
    }

    suspend fun rescheduleRequests(status: String = "PENDING"): List<AdminRescheduleRequestDto> {
        return client.get("$SERVER_BASE_URL/admin/reschedule-requests?status=$status").body()
    }

    suspend fun approveReschedule(adminTicket: String, request: AdminRescheduleDecisionRequestDto): String? {
        val t = pathTicket(adminTicket)
        val path = "/admin/reschedule-requests/$t/approve"
        val response = runCatching {
            client.post("$SERVER_BASE_URL$path") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
        }.getOrElse { e ->
            return adminFetchFailureMessage("POST", path, e)
        }
        if (response.status == HttpStatusCode.OK) return null
        return adminHttpUserMessage(response.status, response.bodyAsText())
    }

    suspend fun rejectReschedule(adminTicket: String, request: AdminRescheduleRejectRequestDto): String? {
        val t = pathTicket(adminTicket)
        val path = "/admin/reschedule-requests/$t/reject"
        val response = runCatching {
            client.post("$SERVER_BASE_URL$path") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
        }.getOrElse { e ->
            return adminFetchFailureMessage("POST", path, e)
        }
        if (response.status == HttpStatusCode.OK) return null
        return adminHttpUserMessage(response.status, response.bodyAsText())
    }

    suspend fun addFaculty(name: String) {
        client.post("$SERVER_BASE_URL/admin/faculties") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(CreateFacultyRequest(name))
        }
    }

    suspend fun deleteFaculty(id: Long) {
        client.delete("$SERVER_BASE_URL/admin/faculties/$id")
    }

    suspend fun addDepartment(facultyId: Long, name: String) {
        client.post("$SERVER_BASE_URL/admin/departments") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(CreateDepartmentRequest(facultyId, name))
        }
    }

    suspend fun deleteDepartment(id: Long) {
        client.delete("$SERVER_BASE_URL/admin/departments/$id")
    }

    suspend fun addSpecialty(facultyId: Long, code: String, name: String, profile: String) {
        client.post("$SERVER_BASE_URL/admin/specialties") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(CreateSpecialtyRequest(facultyId, code, name, profile))
        }
    }

    suspend fun deleteSpecialty(id: Long) {
        client.delete("$SERVER_BASE_URL/admin/specialties/$id")
    }

    suspend fun addTeacher(fullName: String, degree: String, departmentId: Long) {
        client.post("$SERVER_BASE_URL/admin/teachers") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(CreateTeacherRequest(fullName, degree, departmentId))
        }
    }

    suspend fun deleteTeacher(id: Long) {
        client.delete("$SERVER_BASE_URL/admin/teachers/$id")
    }

    suspend fun addStudent(
        fullName: String,
        studentTicket: String,
        initialPassword: String,
        course: Int,
        groupNumber: Int,
        specialtyId: Long,
    ) {
        client.post("$SERVER_BASE_URL/admin/students") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                CreateStudentRequest(
                    fullName = fullName,
                    studentTicket = studentTicket,
                    initialPassword = initialPassword,
                    course = course,
                    groupNumber = groupNumber,
                    specialtyId = specialtyId,
                ),
            )
        }
    }

    suspend fun deleteStudent(id: Long) {
        client.delete("$SERVER_BASE_URL/admin/students/$id")
    }

    suspend fun forgotPasswordRequests(status: String = "PENDING"): List<ForgotPasswordAdminItemDto> {
        return client.get("$SERVER_BASE_URL/admin/forgot-password-requests?status=$status").body()
    }

    suspend fun resolveForgotPassword(adminTicket: String, requestId: Long, adminComment: String = "") {
        client.post("$SERVER_BASE_URL/admin/forgot-password-requests/$adminTicket/resolve") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(ForgotPasswordResolveRequest(requestId = requestId, adminComment = adminComment))
        }
    }
}
