package com.example.diplom

import com.example.diplom.api.AuthResponse
import com.example.diplom.api.CreateDepartmentRequest
import com.example.diplom.api.CreateFacultyRequest
import com.example.diplom.api.CreateSpecialtyRequest
import com.example.diplom.api.CreateStudentRequest
import com.example.diplom.api.CreateTeacherRequest
import com.example.diplom.api.ChangePasswordRequest
import com.example.diplom.api.ForgotPasswordRequest
import com.example.diplom.api.ForgotPasswordResolveRequest
import com.example.diplom.api.JsonErrorBody
import com.example.diplom.api.LoginRequest
import com.example.diplom.api.LogoutRequest
import com.example.diplom.api.JournalUpdateRequest
import com.example.diplom.api.NfcMarkRequestDto
import com.example.diplom.api.PasswordResetPhoneConfirmRequest
import com.example.diplom.api.PasswordResetPhoneStartRequest
import com.example.diplom.api.PasswordResetPhoneStartResponse
import com.example.diplom.api.RegisterRequest
import com.example.diplom.api.StartAttendanceSessionRequestDto
import com.example.diplom.api.StopAttendanceSessionRequestDto
import com.example.diplom.api.TeacherLessonCheckRequestDto
import com.example.diplom.api.AdminBindRoomNfcRequestDto
import com.example.diplom.api.AdminCourseCreateRequestDto
import com.example.diplom.api.AdminCourseCreateResponseDto
import com.example.diplom.api.AdminCourseGroupsRequestDto
import com.example.diplom.api.AdminCourseUpdateRequestDto
import com.example.diplom.api.AdminLessonCreateRequestDto
import com.example.diplom.api.AdminLessonCreateResponseDto
import com.example.diplom.api.AdminLessonUpdateRequestDto
import com.example.diplom.api.AdminRescheduleDecisionRequestDto
import com.example.diplom.api.AdminRescheduleRejectRequestDto
import com.example.diplom.api.RescheduleLessonRequestDto
import com.example.diplom.api.RescheduleRequestCreateDto
import com.example.diplom.api.ServerDebugInfoDto
import com.example.diplom.api.UserStateDto
import com.example.diplom.attendance.AttendanceService
import com.example.diplom.state.seedDefaultUserStates
import com.example.diplom.academic.DomainSeed
import com.example.diplom.db.DbSchemaRepair
import com.example.diplom.admin.AdminCatalogRepository
import com.example.diplom.admin.AdminScheduleService
import com.example.diplom.auth.DbAuthRepository
import com.example.diplom.auth.DevSessionRegistry
import com.example.diplom.auth.PasswordResetCodeRegistry
import com.example.diplom.auth.SmsSenderFactory
import com.example.diplom.db.AttendanceSessionsTable
import com.example.diplom.db.AttendanceTable
import com.example.diplom.db.CoursesTable
import com.example.diplom.db.DatabaseFactory
import com.example.diplom.db.DepartmentsTable
import com.example.diplom.db.FacultiesTable
import com.example.diplom.db.GroupCoursesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.NotificationsTable
import com.example.diplom.db.PasswordResetRequestsTable
import com.example.diplom.db.RescheduleRequestsTable
import com.example.diplom.db.RoomsTable
import com.example.diplom.db.SpecialtiesTable
import com.example.diplom.db.StudentsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.CalendarEventsTable
import com.example.diplom.db.UsersTable
import com.example.diplom.notifications.NotificationsReadService
import com.example.diplom.schedule.WeekScheduleService
import com.example.diplom.state.UserStateStorage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    initDatabase()
    monitor.subscribe(ApplicationStopping) {
        DatabaseFactory.close()
    }
    val authRepository = DbAuthRepository().also { it.seedDefaultsIfEmpty() }
    val adminCatalogRepository = AdminCatalogRepository().also { it.seedIfEmpty() }
    val smsSender = SmsSenderFactory.fromEnvironment()
    DomainSeed.ensure()
    DbSchemaRepair.runAfterSeed()
    val weekScheduleService = WeekScheduleService()
    val notificationsReadService = NotificationsReadService()
    val attendanceService = AttendanceService()
    val adminScheduleService = AdminScheduleService()
    val userStateStorage = UserStateStorage().also { seedDefaultUserStates(it) }

    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        // Browser web target calls backend from a different origin during local development.
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(CallLogging) {
        format { call ->
            val st = call.response.status()
            "${call.request.httpMethod.value} ${call.request.local.uri} -> ${st?.value ?: "-"}"
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("${call.request.httpMethod.value} ${call.request.local.uri}: unhandled", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                JsonErrorBody(error = cause.message ?: "unknown"),
            )
        }
    }

    routing {
        get("/debug/info") {
            val port = application.environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
            call.respond(
                ServerDebugInfoDto(
                    databaseKind = DatabaseFactory.databaseKindLabel(),
                    jdbcUrl = DatabaseFactory.jdbcUrlForDiagnostics(),
                    jdbcDriver = DatabaseFactory.lastDriverClassName,
                    ktorPort = port,
                ),
            )
        }
        get("/") {
            call.respondText("Attendance server is running")
        }

        post("/auth/register") {
            val request = call.receive<RegisterRequest>()
            val result = authRepository.register(
                studentTicket = request.studentTicket,
                rawPassword = request.password,
            )
            result.fold(
                onSuccess = { dto ->
                    call.respond(
                        AuthResponse(token = DevSessionRegistry.issueToken(dto.id), user = dto),
                    )
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.Conflict, e.message ?: "Registration failed")
                },
            )
        }

        post("/auth/login") {
            val request = call.receive<LoginRequest>()
            val dto = runCatching {
                authRepository.login(request.studentTicket, request.password)
            }.onFailure {
                environment.log.error("Login failed for ${request.studentTicket}", it)
            }.getOrNull()
            if (dto == null) {
                call.respond(HttpStatusCode.Unauthorized, "Неверный билет или пароль")
            } else {
                call.respond(AuthResponse(token = DevSessionRegistry.issueToken(dto.id), user = dto))
            }
        }

        post("/auth/password") {
            val request = call.receive<ChangePasswordRequest>()
            val ok = authRepository.changePassword(
                studentTicket = request.studentTicket,
                oldPassword = request.oldPassword,
                newPassword = request.newPassword,
            )
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest, "Неверный текущий пароль или слишком короткий новый")
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/auth/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            runCatching {
                adminCatalogRepository.createForgotPasswordRequest(
                    studentTicket = request.studentTicket,
                    contact = request.contact,
                    comment = request.comment,
                )
            }.onSuccess {
                call.respond(HttpStatusCode.Accepted, "Заявка отправлена администратору")
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Не удалось отправить заявку")
            }
        }

        post("/auth/password-reset/phone/start") {
            val request = call.receive<PasswordResetPhoneStartRequest>()
            val ticket = request.studentTicket.trim()
            if (ticket.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Укажите билет или логин")
                return@post
            }
            val phone = authRepository.phoneForTicket(ticket)
            if (phone.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound, "Для пользователя не указан номер телефона")
                return@post
            }
            val code = PasswordResetCodeRegistry.issue(ticket)
            val smsResult = smsSender.sendPasswordResetCode(phone, code.code)
            if (!smsResult.sent) {
                environment.log.error("SMS sending failed for $ticket to $phone: ${smsResult.message}")
                call.respond(HttpStatusCode.ServiceUnavailable, smsResult.message)
                return@post
            }
            environment.log.info("Password reset SMS sent for $ticket to ${phone.maskPhoneForUi()}")
            call.respond(
                PasswordResetPhoneStartResponse(
                    maskedPhone = phone.maskPhoneForUi(),
                    expiresAtEpochMillis = code.expiresAtEpochMillis,
                ),
            )
        }

        post("/auth/password-reset/phone/confirm") {
            val request = call.receive<PasswordResetPhoneConfirmRequest>()
            val ticket = request.studentTicket.trim()
            if (ticket.isBlank() || request.code.isBlank() || request.newPassword.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Заполните билет, код и новый пароль")
                return@post
            }
            if (!request.code.matches(Regex("\\d{4}"))) {
                call.respond(HttpStatusCode.BadRequest, "Код должен состоять из 4 цифр")
                return@post
            }
            if (request.newPassword.length < 4) {
                call.respond(HttpStatusCode.BadRequest, "Пароль должен быть не короче 4 символов")
                return@post
            }
            if (!PasswordResetCodeRegistry.consume(ticket, request.code)) {
                call.respond(HttpStatusCode.BadRequest, "Неверный или устаревший код подтверждения")
                return@post
            }
            if (!authRepository.resetPassword(ticket, request.newPassword)) {
                call.respond(HttpStatusCode.NotFound, "Пользователь не найден")
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/auth/logout") {
            val body = call.receive<LogoutRequest>()
            val userId: Long? = transaction {
                UsersTable
                    .selectAll()
                    .where { UsersTable.studentTicket eq body.studentTicket }
                    .singleOrNull()
                    ?.let { row -> row[UsersTable.id] }
            }
            if (userId == null) {
                call.respond(HttpStatusCode.NotFound, "Пользователь не найден")
                return@post
            }
            val revoked = DevSessionRegistry.revokeSession(userId, body.token)
            if (!revoked) {
                call.respond(HttpStatusCode.Unauthorized, "Неверный токен или сессия уже завершена")
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/state/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            val raw = userStateStorage.load(ticket)
            val dbPhone = transaction {
                UsersTable
                    .selectAll()
                    .where { UsersTable.studentTicket eq ticket }
                    .singleOrNull()
                    ?.get(UsersTable.phone)
            }
            val state = if (raw == null) {
                UserStateDto()
            } else {
                runCatching { Json.decodeFromString<UserStateDto>(raw) }.getOrElse { UserStateDto() }
            }
            call.respond(state.copy(profilePhone = dbPhone ?: state.profilePhone))
        }

        put("/state/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@put
            }
            val request = call.receive<UserStateDto>()
            userStateStorage.save(ticket, Json.encodeToString(request))
            transaction {
                UsersTable.update({ UsersTable.studentTicket eq ticket }) {
                    it[photoHex] = request.profileImageHex
                    it[phone] = request.profilePhone.ifBlank { null }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/schedule/week/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            call.respond(weekScheduleService.weekForTicket(ticket))
        }

        get("/notifications/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            call.respond(notificationsReadService.listForTicket(ticket))
        }

        get("/attendance/teacher-groups/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            call.respond(attendanceService.teacherGroups(ticket))
        }

        get("/attendance/student/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            call.respond(attendanceService.studentAbsences(ticket))
        }

        get("/attendance/journal/{ticket}") {
            val ticket = call.parameters["ticket"]
            val groupId = call.request.queryParameters["groupId"]?.toLongOrNull()
            val courseId = call.request.queryParameters["courseId"]?.toLongOrNull()
            if (ticket.isNullOrBlank() || groupId == null || courseId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket/groupId/courseId")
                return@get
            }
            call.respond(attendanceService.journalFor(ticket, groupId, courseId))
        }

        post("/attendance/journal/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@post
            }
            val request = call.receive<JournalUpdateRequest>()
            if (!attendanceService.updateJournal(ticket, request)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/attendance/teacher-lesson-check/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            val roomNfc = call.request.queryParameters["roomNfc"]?.trim()
            val dto = attendanceService.teacherCurrentLessonCheck(ticket, roomNfc)
            if (dto == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(dto)
            }
        }

        post("/attendance/teacher-lesson-check/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@post
            }
            val body = runCatching { call.receive<TeacherLessonCheckRequestDto>() }.getOrNull()
            val roomNfc = body?.roomNfc?.trim()?.takeIf { it.isNotEmpty() }
                ?: call.request.queryParameters["roomNfc"]?.trim()
            val dto = attendanceService.teacherCurrentLessonCheck(ticket, roomNfc)
            if (dto == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(dto)
            }
        }

        get("/attendance/session/active-teacher/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            val dto = attendanceService.activeSessionForTeacher(ticket)
            if (dto == null) call.respond(HttpStatusCode.NotFound) else call.respond(dto)
        }

        get("/attendance/session/active-student/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            val dto = attendanceService.activeSessionForStudent(ticket)
            if (dto == null) call.respond(HttpStatusCode.NotFound) else call.respond(dto)
        }

        get("/attendance/session/roster/{ticket}/{sessionId}") {
            val ticket = call.parameters["ticket"]
            val sessionId = call.parameters["sessionId"]?.toLongOrNull()
            if (ticket.isNullOrBlank() || sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket/sessionId")
                return@get
            }
            call.respond(attendanceService.sessionLiveRoster(ticket, sessionId))
        }

        post("/attendance/session/start/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@post
            }
            val request = call.receive<StartAttendanceSessionRequestDto>()
            val dto = attendanceService.startAttendanceSession(ticket, request)
            if (dto == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(dto)
            }
        }

        post("/attendance/session/stop/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@post
            }
            val request = call.receive<StopAttendanceSessionRequestDto>()
            if (!attendanceService.stopAttendanceSession(ticket, request)) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/attendance/nfc/mark") {
            val request = call.receive<NfcMarkRequestDto>()
            call.respond(attendanceService.markByNfc(request))
        }

        get("/profile/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@get
            }
            val profile = attendanceService.profile(ticket)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(profile)
        }

        post("/schedule/reschedule") {
            val request = call.receive<RescheduleLessonRequestDto>()
            val ok = attendanceService.rescheduleLessonAndNotify(
                lessonId = request.lessonId,
                newStartIso = request.newStartIso,
                newEndIso = request.newEndIso,
            )
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/schedule/reschedule-request/{ticket}") {
            val ticket = call.parameters["ticket"]
            if (ticket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing ticket")
                return@post
            }
            val request = call.receive<RescheduleRequestCreateDto>()
            val ok = attendanceService.createRescheduleRequest(ticket, request)
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            call.respond(HttpStatusCode.Accepted)
        }

        get("/admin/reschedule-requests") {
            val status = call.request.queryParameters["status"]
            call.respond(attendanceService.listRescheduleRequests(status))
        }

        post("/admin/reschedule-requests/{adminTicket}/approve") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@post
            }
            val req = call.receive<AdminRescheduleDecisionRequestDto>()
            val err = attendanceService.approveRescheduleRequest(adminTicket, req)
            if (err != null) {
                call.respond(HttpStatusCode.BadRequest, err)
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/admin/reschedule-requests/{adminTicket}/reject") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@post
            }
            val req = call.receive<AdminRescheduleRejectRequestDto>()
            val err = attendanceService.rejectRescheduleRequest(adminTicket, req)
            if (err != null) {
                call.respond(HttpStatusCode.BadRequest, err)
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/admin/catalog/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]?.trim().orEmpty()
            if (adminTicket.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@get
            }
            if (!adminScheduleService.isAdmin(adminTicket)) {
                call.respond(HttpStatusCode.Forbidden, "Нет прав")
                return@get
            }
            call.respond(adminCatalogRepository.snapshot())
        }
        post("/admin/faculties") {
            val req = call.receive<CreateFacultyRequest>()
            adminCatalogRepository.addFaculty(req.name)
            call.respond(HttpStatusCode.Created)
        }
        delete("/admin/faculties/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            adminCatalogRepository.deleteFaculty(id)
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/departments") {
            val req = call.receive<CreateDepartmentRequest>()
            adminCatalogRepository.addDepartment(req.facultyId, req.name)
            call.respond(HttpStatusCode.Created)
        }
        delete("/admin/departments/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            adminCatalogRepository.deleteDepartment(id)
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/specialties") {
            val req = call.receive<CreateSpecialtyRequest>()
            adminCatalogRepository.addSpecialty(req.facultyId, req.code, req.name, req.profile)
            call.respond(HttpStatusCode.Created)
        }
        delete("/admin/specialties/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            adminCatalogRepository.deleteSpecialty(id)
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/teachers") {
            val req = call.receive<CreateTeacherRequest>()
            adminCatalogRepository.addTeacher(req.fullName, req.degree, req.departmentId)
            call.respond(HttpStatusCode.Created)
        }
        delete("/admin/teachers/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            adminCatalogRepository.deleteTeacher(id)
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/students") {
            val req = call.receive<CreateStudentRequest>()
            adminCatalogRepository.addStudent(
                fullName = req.fullName,
                studentTicket = req.studentTicket,
                initialPassword = req.initialPassword,
                course = req.course,
                groupNumber = req.groupNumber,
                specialtyId = req.specialtyId,
            )
            call.respond(HttpStatusCode.Created)
        }
        delete("/admin/students/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            adminCatalogRepository.deleteStudent(id)
            call.respond(HttpStatusCode.OK)
        }
        get("/admin/forgot-password-requests") {
            val status = call.request.queryParameters["status"]
            call.respond(adminCatalogRepository.listForgotPasswordRequests(status))
        }
        post("/admin/forgot-password-requests/{adminTicket}/resolve") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@post
            }
            val request = call.receive<ForgotPasswordResolveRequest>()
            val ok = adminCatalogRepository.resolveForgotPasswordRequest(
                adminTicket = adminTicket,
                requestId = request.requestId,
                adminComment = request.adminComment,
            )
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, "Заявка не найдена или уже обработана")
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/admin/rooms/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@get
            }
            call.respond(adminScheduleService.listRooms(adminTicket))
        }
        get("/admin/schedule-refs/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@get
            }
            val refs = adminScheduleService.listScheduleRefs(adminTicket)
            if (refs == null) {
                call.respond(HttpStatusCode.Forbidden, "Нет прав")
                return@get
            }
            call.respond(refs)
        }
        get("/admin/lessons/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@get
            }
            if (adminScheduleService.listScheduleRefs(adminTicket) == null) {
                call.respond(HttpStatusCode.Forbidden, "Нет прав")
                return@get
            }
            call.respond(adminScheduleService.listLessons(adminTicket))
        }
        post("/admin/courses/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@post
            }
            val body = call.receive<AdminCourseCreateRequestDto>()
            val id = adminScheduleService.createCourse(adminTicket, body)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Не удалось создать предмет: проверьте название и преподавателя")
                return@post
            }
            call.respond(AdminCourseCreateResponseDto(courseId = id))
        }
        put("/admin/courses/{adminTicket}/{courseId}") {
            val adminTicket = call.parameters["adminTicket"]
            val courseId = call.parameters["courseId"]?.toLongOrNull()
            if (adminTicket.isNullOrBlank() || courseId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket or courseId")
                return@put
            }
            val body = call.receive<AdminCourseUpdateRequestDto>()
            val ok = adminScheduleService.updateCourse(adminTicket, courseId, body)
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest, "Нет прав, предмет не найден или преподаватель не из списка teachers")
                return@put
            }
            call.respond(HttpStatusCode.OK)
        }
        delete("/admin/courses/{adminTicket}/{courseId}") {
            val adminTicket = call.parameters["adminTicket"]
            val courseId = call.parameters["courseId"]?.toLongOrNull()
            if (adminTicket.isNullOrBlank() || courseId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket or courseId")
                return@delete
            }
            val ok = adminScheduleService.deleteCourse(adminTicket, courseId)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, "Предмет не найден или нет прав")
                return@delete
            }
            call.respond(HttpStatusCode.OK)
        }
        put("/admin/courses/{adminTicket}/{courseId}/groups") {
            val adminTicket = call.parameters["adminTicket"]
            val courseId = call.parameters["courseId"]?.toLongOrNull()
            if (adminTicket.isNullOrBlank() || courseId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket or courseId")
                return@put
            }
            val body = call.receive<AdminCourseGroupsRequestDto>()
            val ok = adminScheduleService.setCourseGroups(adminTicket, courseId, body)
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest, "Нет прав, предмет не найден или указаны несуществующие группы")
                return@put
            }
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/rooms/{adminTicket}/{roomId}/nfc") {
            val adminTicket = call.parameters["adminTicket"]
            val roomId = call.parameters["roomId"]?.toLongOrNull()
            if (adminTicket.isNullOrBlank() || roomId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket or roomId")
                return@post
            }
            val body = call.receive<AdminBindRoomNfcRequestDto>()
            val ok = adminScheduleService.bindRoomNfc(adminTicket, roomId, body)
            if (!ok) {
                call.respond(HttpStatusCode.Forbidden, "Нет прав или аудитория не найдена")
                return@post
            }
            call.respond(HttpStatusCode.OK)
        }
        post("/admin/lessons/{adminTicket}") {
            val adminTicket = call.parameters["adminTicket"]
            if (adminTicket.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket")
                return@post
            }
            val body = call.receive<AdminLessonCreateRequestDto>()
            val id = adminScheduleService.createLesson(adminTicket, body)
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Не удалось создать: проверьте курс, группу, аудиторию и время ISO")
                return@post
            }
            call.respond(AdminLessonCreateResponseDto(lessonId = id))
        }
        put("/admin/lessons/{adminTicket}/{lessonId}") {
            val adminTicket = call.parameters["adminTicket"]
            val lessonId = call.parameters["lessonId"]?.toLongOrNull()
            if (adminTicket.isNullOrBlank() || lessonId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing adminTicket or lessonId")
                return@put
            }
            val body = call.receive<AdminLessonUpdateRequestDto>()
            val ok = adminScheduleService.updateLesson(adminTicket, lessonId, body)
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest, "Нет прав или занятие не найдено")
                return@put
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun jdbcDriverForUrl(jdbcUrl: String): String = when {
    jdbcUrl.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
    jdbcUrl.startsWith("jdbc:h2:") -> "org.h2.Driver"
    else -> error("Unsupported jdbcUrl prefix; add mapping in jdbcDriverForUrl()")
}

private fun String.maskPhoneForUi(): String {
    val digits = filter { it.isDigit() }
    if (digits.length <= 4) return "****"
    return "**** ${digits.takeLast(4)}"
}

private fun Application.initDatabase() {
    val dbConfig = environment.config.config("database")
    val jdbcUrl = dbConfig.property("jdbcUrl").getString()
    val driver = jdbcDriverForUrl(jdbcUrl)
    DatabaseFactory.init(
        jdbcUrl = jdbcUrl,
        user = dbConfig.property("user").getString(),
        password = dbConfig.property("password").getString(),
        driver = driver,
    )
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            GroupsTable,
            StudentsTable,
            TeachersTable,
            RoomsTable,
            CoursesTable,
            GroupCoursesTable,
            LessonsTable,
            NotificationsTable,
            AttendanceTable,
            AttendanceSessionsTable,
            RescheduleRequestsTable,
            CalendarEventsTable,
            FacultiesTable,
            DepartmentsTable,
            SpecialtiesTable,
            PasswordResetRequestsTable,
        )
    }
}
