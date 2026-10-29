package com.example.diplom.attendance

import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.JournalUpdateRequest
import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.api.NfcMarkRequestDto
import com.example.diplom.api.NfcMarkResultDto
import com.example.diplom.api.SessionRosterRowDto
import com.example.diplom.api.StartAttendanceSessionRequestDto
import com.example.diplom.api.StopAttendanceSessionRequestDto
import com.example.diplom.api.TeacherLessonCheckDto
import com.example.diplom.api.AdminRescheduleDecisionRequestDto
import com.example.diplom.api.AdminRescheduleRejectRequestDto
import com.example.diplom.api.AdminRescheduleRequestDto
import com.example.diplom.api.RescheduleRequestCreateDto
import com.example.diplom.api.StudentAbsenceDto
import com.example.diplom.api.StudentJournalRowDto
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.api.UserProfileDto
import com.example.diplom.db.AttendanceSessionsTable
import com.example.diplom.db.AttendanceTable
import com.example.diplom.db.CoursesTable
import com.example.diplom.db.DepartmentsTable
import com.example.diplom.db.FacultiesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.NotificationsTable
import com.example.diplom.db.RescheduleRequestsTable
import com.example.diplom.db.RoomsTable
import com.example.diplom.db.SpecialtiesTable
import com.example.diplom.db.StudentsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.UsersTable
import com.example.diplom.schedule.JournalLessonExpander
import com.example.diplom.domain.UserRole
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AttendanceService {
    private val zone = ZoneId.of("Europe/Minsk")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val journalShortDateFmt = DateTimeFormatter.ofPattern("dd.MM")

    fun teacherGroups(ticket: String): List<TeacherGroupCourseDto> = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            ?: return@transaction emptyList()
        val teacherId = teacherUser[UsersTable.id]
        if (TeachersTable.selectAll().where { TeachersTable.userId eq teacherId }.empty()) return@transaction emptyList()

        val taughtCourses = CoursesTable.selectAll().where { CoursesTable.teacherId eq teacherId }
        taughtCourses.flatMap { course ->
            val lessons = LessonsTable.selectAll().where { LessonsTable.courseId eq course[CoursesTable.id] }
            val groupIds = lessons.map { it[LessonsTable.groupId] }.distinct()
            groupIds.mapNotNull { gid ->
                val group = GroupsTable.selectAll().where { GroupsTable.id eq gid }.singleOrNull() ?: return@mapNotNull null
                TeacherGroupCourseDto(
                    groupId = gid,
                    groupName = group[GroupsTable.name],
                    courseId = course[CoursesTable.id],
                    courseName = course[CoursesTable.name],
                    course = group[GroupsTable.course],
                    groupNumber = group[GroupsTable.groupNumber],
                )
            }
        }.distinctBy { "${it.groupId}-${it.courseId}" }
    }

    fun studentAbsences(ticket: String): List<StudentAbsenceDto> = transaction {
        val studentUser = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            ?: return@transaction emptyList()
        val studentId = studentUser[UsersTable.id]
        if (StudentsTable.selectAll().where { StudentsTable.userId eq studentId }.empty()) return@transaction emptyList()

        val coursesById = CoursesTable.selectAll().associateBy { it[CoursesTable.id] }
        val usersById = UsersTable.selectAll().associateBy { it[UsersTable.id] }
        val lessonsById = LessonsTable.selectAll().associateBy { it[LessonsTable.id] }

        AttendanceTable.selectAll().where { AttendanceTable.studentId eq studentId }
            .mapNotNull { att ->
                val reason = att[AttendanceTable.reason] ?: return@mapNotNull null
                val lesson = lessonsById[att[AttendanceTable.lessonId]] ?: return@mapNotNull null
                val course = coursesById[lesson[LessonsTable.courseId]] ?: return@mapNotNull null
                val start = ZonedDateTime.ofInstant(lesson[LessonsTable.startTime], zone)
                val end = ZonedDateTime.ofInstant(lesson[LessonsTable.endTime], zone)
                val teacher = usersById[course[CoursesTable.teacherId]]?.get(UsersTable.fullName) ?: "—"
                StudentAbsenceDto(
                    lessonId = lesson[LessonsTable.id],
                    subject = course[CoursesTable.name],
                    dateIso = start.format(dateFmt),
                    timeRange = "${start.format(timeFmt)}-${end.format(timeFmt)}",
                    reason = reason,
                    teacher = teacher,
                )
            }
            .sortedBy { "${it.dateIso}-${it.timeRange}" }
    }

    fun journalFor(ticket: String, groupId: Long, courseId: Long): List<LessonJournalEntryDto> = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            ?: return@transaction emptyList()
        val teacherId = teacherUser[UsersTable.id]
        val canTeach = CoursesTable.selectAll().where {
            (CoursesTable.id eq courseId) and (CoursesTable.teacherId eq teacherId)
        }.singleOrNull() != null
        if (!canTeach) return@transaction emptyList()

        sealGhostSlotsAfterMoves(groupId, courseId)
        JournalLessonExpander.ensureSemesterOccurrences(groupId, courseId)

        val students = (StudentsTable.selectAll().where { StudentsTable.groupId eq groupId }).mapNotNull { st ->
            val user = UsersTable.selectAll().where { UsersTable.id eq st[StudentsTable.userId] }.singleOrNull() ?: return@mapNotNull null
            st[StudentsTable.userId] to (user[UsersTable.studentTicket] to user[UsersTable.fullName])
        }

        LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and (LessonsTable.courseId eq courseId)
        }.mapNotNull { lesson ->
            val roomRow = RoomsTable.selectAll().where { RoomsTable.id eq lesson[LessonsTable.roomId] }.single()
            val roomName = roomRow[RoomsTable.name]
            var start = ZonedDateTime.ofInstant(lesson[LessonsTable.startTime], zone)
            var end = ZonedDateTime.ofInstant(lesson[LessonsTable.endTime], zone)
            val healed = healCorruptLessonWindowIfNeeded(lesson[LessonsTable.id], start, end)
            start = healed.first
            end = healed.second
            val lessonStatus = lesson[LessonsTable.status]
            val subjectName = CoursesTable.selectAll().where { CoursesTable.id eq courseId }.single()[CoursesTable.name]
            if (lessonStatus == "CANCELLED") return@mapNotNull null
            if (
                lessonStatus == "SCHEDULED" &&
                subjectName.contains("компьютерные сети", ignoreCase = true) &&
                start.dayOfWeek != java.time.DayOfWeek.THURSDAY
            ) {
                return@mapNotNull null
            }
            /** Подпись колонки — только даты (время в карточке занятия). */
            val journalColumnLabel =
                if (start.toLocalDate() != end.toLocalDate()) {
                    "${start.format(journalShortDateFmt)}–${end.format(journalShortDateFmt)}"
                } else {
                    null
                }
            val rows = students.map { (studentUserId, base) ->
                val att = AttendanceTable.selectAll().where {
                    (AttendanceTable.lessonId eq lesson[LessonsTable.id]) and (AttendanceTable.studentId eq studentUserId)
                }
                    .orderBy(AttendanceTable.id to SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()
                val status = att?.get(AttendanceTable.status)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                val reasonRaw = att?.get(AttendanceTable.reason)?.trim()?.takeIf { it.isNotEmpty() }
                StudentJournalRowDto(
                    studentTicket = base.first,
                    fullName = base.second,
                    absenceReason = reasonRaw,
                    isPresent = att != null && status == "PRESENT",
                    attendanceStatus = status,
                )
            }
            LessonJournalEntryDto(
                lessonId = lesson[LessonsTable.id],
                subject = subjectName,
                lessonKind = lesson[LessonsTable.lessonKind],
                dateIso = start.format(dateFmt),
                timeRange = "${start.format(timeFmt)}-${end.format(timeFmt)}",
                journalColumnLabel = journalColumnLabel,
                room = roomName,
                roomNfcUid = roomRow[RoomsTable.nfcUid],
                rows = rows,
            )
        }
            .sortedBy { "${it.dateIso}-${it.timeRange}" }
    }

    fun updateJournal(ticket: String, req: JournalUpdateRequest): Boolean = transaction {
        val teacherTicket = ticket.trim()
        val studentTicket = req.studentTicket.trim()
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq teacherTicket }.singleOrNull() ?: return@transaction false
        val teacherId = teacherUser[UsersTable.id]
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq req.lessonId }.singleOrNull() ?: return@transaction false
        val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.singleOrNull() ?: return@transaction false
        if (course[CoursesTable.teacherId] != teacherId) return@transaction false
        val student = UsersTable.selectAll().where { UsersTable.studentTicket eq studentTicket }.singleOrNull() ?: return@transaction false
        val studentId = student[UsersTable.id]

        val rawWire = req.absenceReason?.trim()
        if (rawWire.equals("CLEAR", ignoreCase = true)) {
            AttendanceTable.deleteWhere {
                (AttendanceTable.lessonId eq req.lessonId) and (AttendanceTable.studentId eq studentId)
            }
            return@transaction true
        }
        // «Присутствует»: null/пустая строка на проводе, либо явный маркер (часть клиентов/JSON дают "" вместо null).
        val isPresentMark =
            rawWire.isNullOrEmpty() || rawWire.equals("PRESENT", ignoreCase = true)
        val absenceDb: String? = if (isPresentMark) null else rawWire

        // Одна ячейка журнала = одна строка в БД: снимаем дубликаты (lesson_id, student_id) и любые гонки insert/update.
        AttendanceTable.deleteWhere {
            (AttendanceTable.lessonId eq req.lessonId) and (AttendanceTable.studentId eq studentId)
        }
        AttendanceTable.insert {
            it[AttendanceTable.lessonId] = req.lessonId
            it[AttendanceTable.studentId] = studentId
            it[AttendanceTable.timestamp] = java.time.Instant.now()
            it[AttendanceTable.status] = if (isPresentMark) "PRESENT" else "ABSENT_MANUAL"
            it[AttendanceTable.reason] = absenceDb
        }
        true
    }

    fun profile(studentTicket: String): UserProfileDto? = transaction {
        val user = UsersTable.selectAll().where { UsersTable.studentTicket eq studentTicket }.singleOrNull() ?: return@transaction null
        val uid = user[UsersTable.id]
        val role = user[UsersTable.role]
        val group = StudentsTable.selectAll().where { StudentsTable.userId eq uid }.singleOrNull()
            ?.let { st -> GroupsTable.selectAll().where { GroupsTable.id eq st[StudentsTable.groupId] }.singleOrNull() }
        val specId = group?.get(GroupsTable.specialtyId)
        val specRow = specId?.let { sid ->
            SpecialtiesTable.selectAll().where { SpecialtiesTable.id eq sid }.singleOrNull()
        }
        val facultyName = specRow?.get(SpecialtiesTable.facultyId)?.let { fid ->
            FacultiesTable.selectAll().where { FacultiesTable.id eq fid }.singleOrNull()?.get(FacultiesTable.name)
        }
        val teacherRow = TeachersTable.selectAll().where { TeachersTable.userId eq user[UsersTable.id] }.singleOrNull()
        val degree = teacherRow?.get(TeachersTable.degree)?.takeIf { it.isNotBlank() }
        val departmentName = teacherRow?.get(TeachersTable.departmentId)?.let { did ->
            DepartmentsTable.selectAll().where { DepartmentsTable.id eq did }.singleOrNull()
                ?.get(DepartmentsTable.name)
        }
        UserProfileDto(
            studentTicket = user[UsersTable.studentTicket],
            fullName = user[UsersTable.fullName],
            role = role,
            groupName = group?.get(GroupsTable.name),
            course = group?.get(GroupsTable.course),
            groupNumber = group?.get(GroupsTable.groupNumber),
            phone = user[UsersTable.phone],
            photoHex = user[UsersTable.photoHex],
            degree = degree,
            specialtyCode = specRow?.get(SpecialtiesTable.code),
            specialtyName = specRow?.get(SpecialtiesTable.name),
            facultyName = facultyName,
            departmentName = departmentName,
        )
    }

    fun rescheduleLessonAndNotify(lessonId: Long, newStartIso: String, newEndIso: String): Boolean = transaction {
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq lessonId }.singleOrNull() ?: return@transaction false
        val newStart = java.time.Instant.parse(newStartIso)
        val newEnd = java.time.Instant.parse(newEndIso)
        applyLessonMove(lesson, newStart, newEnd, lesson[LessonsTable.roomId])
        val courseName = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.single()[CoursesTable.name]
        StudentsTable.selectAll().where { StudentsTable.groupId eq lesson[LessonsTable.groupId] }.forEach { st ->
            NotificationsTable.insert {
                it[userId] = st[StudentsTable.userId]
                it[title] = "Перенос занятия"
                it[message] = "Занятие «$courseName» перенесено. Новое время: $newStartIso"
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = true
            }
        }
        true
    }

    fun createRescheduleRequest(ticket: String, req: RescheduleRequestCreateDto): Boolean = transaction {
        val actor = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull() ?: return@transaction false
        val actorId = actor[UsersTable.id]
        val role = runCatching { UserRole.valueOf(actor[UsersTable.role]) }.getOrElse { UserRole.STUDENT }

        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq req.lessonId }.singleOrNull() ?: return@transaction false
        val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.singleOrNull() ?: return@transaction false
        val courseTeacherId = course[CoursesTable.teacherId]

        when (role) {
            UserRole.TEACHER -> {
                if (TeachersTable.selectAll().where { TeachersTable.userId eq actorId }.empty()) return@transaction false
                if (courseTeacherId != actorId) return@transaction false
            }
            UserRole.ADMIN -> { /* админ инициирует заявку; FK — user_id преподавателя курса */ }
            else -> return@transaction false
        }

        if (TeachersTable.selectAll().where { TeachersTable.userId eq courseTeacherId }.empty()) return@transaction false

        val date = runCatching { LocalDate.parse(req.desiredDateIso) }.getOrNull() ?: return@transaction false
        val from = runCatching { LocalTime.parse(req.desiredTimeFrom) }.getOrNull() ?: return@transaction false
        val to = runCatching { LocalTime.parse(req.desiredTimeTo) }.getOrNull() ?: return@transaction false
        if (!from.isBefore(to)) return@transaction false

        val proposedStart = ZonedDateTime.of(date, from, zone).toInstant()
        val proposedEnd = ZonedDateTime.of(date, to, zone).toInstant()
        RescheduleRequestsTable.insert {
            it[lessonId] = req.lessonId
            it[requestedByTeacherId] = courseTeacherId
            it[proposedStartTime] = proposedStart
            it[proposedEndTime] = proposedEnd
            it[status] = "PENDING"
            it[comment] = req.comment
        }
        val courseName = course[CoursesTable.name]
        val proposedLabel = ZonedDateTime.ofInstant(proposedStart, zone).format(
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
        )
        UsersTable.selectAll().where { UsersTable.role eq UserRole.ADMIN.name }.forEach { admin ->
            NotificationsTable.insert {
                it[userId] = admin[UsersTable.id]
                it[title] = "Заявка на перенос"
                it[message] = "Преподаватель запросил перенос «$courseName» на $proposedLabel. Раздел «Заявки на перенос»."
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = true
            }
        }
        UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()?.let { requester ->
            NotificationsTable.insert {
                it[userId] = requester[UsersTable.id]
                it[title] = "Запрос на перенос отправлен"
                it[message] = "«$courseName» — ожидает решения администратора ($proposedLabel)."
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = false
            }
        }
        true
    }

    fun listRescheduleRequests(status: String? = "PENDING"): List<AdminRescheduleRequestDto> = transaction {
        val requests = if (status.isNullOrBlank()) {
            RescheduleRequestsTable.selectAll().toList()
        } else {
            RescheduleRequestsTable.selectAll().where { RescheduleRequestsTable.status eq status }.toList()
        }
        requests.mapNotNull { r ->
            val lesson = LessonsTable.selectAll().where { LessonsTable.id eq r[RescheduleRequestsTable.lessonId] }.singleOrNull()
                ?: return@mapNotNull null
            val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.singleOrNull()
                ?: return@mapNotNull null
            val teacher = UsersTable.selectAll().where { UsersTable.id eq course[CoursesTable.teacherId] }.singleOrNull()
                ?: return@mapNotNull null
            val group = GroupsTable.selectAll().where { GroupsTable.id eq lesson[LessonsTable.groupId] }.singleOrNull()
                ?: return@mapNotNull null
            val room = RoomsTable.selectAll().where { RoomsTable.id eq lesson[LessonsTable.roomId] }.singleOrNull()
                ?: return@mapNotNull null
            val rawStart = r[RescheduleRequestsTable.proposedStartTime]
            val rawEnd = r[RescheduleRequestsTable.proposedEndTime]
            val (fixedStart, fixedEnd) = repairRescheduleWindow(lesson, rawStart, rawEnd)
            if (fixedStart != rawStart || fixedEnd != rawEnd) {
                RescheduleRequestsTable.update({ RescheduleRequestsTable.id eq r[RescheduleRequestsTable.id] }) {
                    it[proposedStartTime] = fixedStart
                    it[proposedEndTime] = fixedEnd
                }
            }
            val zStart = java.time.ZonedDateTime.ofInstant(fixedStart, zone)
            val zEnd = java.time.ZonedDateTime.ofInstant(fixedEnd, zone)
            val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            AdminRescheduleRequestDto(
                id = r[RescheduleRequestsTable.id],
                lessonId = lesson[LessonsTable.id],
                teacherTicket = teacher[UsersTable.studentTicket],
                teacherName = teacher[UsersTable.fullName],
                subject = course[CoursesTable.name],
                groupName = group[GroupsTable.name],
                roomName = room[RoomsTable.name],
                currentStartIso = instantToApiIso(lesson[LessonsTable.startTime]),
                currentEndIso = instantToApiIso(lesson[LessonsTable.endTime]),
                proposedStartIso = instantToApiIso(fixedStart),
                proposedEndIso = instantToApiIso(fixedEnd),
                proposedDateIso = zStart.toLocalDate().toString(),
                proposedTimeFrom = zStart.format(hm),
                proposedTimeTo = zEnd.format(hm),
                status = r[RescheduleRequestsTable.status],
                comment = r[RescheduleRequestsTable.comment],
            )
        }
    }

    fun approveRescheduleRequest(adminTicket: String, req: AdminRescheduleDecisionRequestDto): String? = transaction {
        val admin = UsersTable.selectAll().where { UsersTable.studentTicket eq adminTicket }.singleOrNull()
            ?: return@transaction "Пользователь не найден"
        if (admin[UsersTable.role] != "ADMIN") return@transaction "Нет прав администратора"
        val request = RescheduleRequestsTable.selectAll().where { RescheduleRequestsTable.id eq req.requestId }.singleOrNull()
            ?: return@transaction "Заявка не найдена"
        if (request[RescheduleRequestsTable.status] != "PENDING") return@transaction "Заявка уже обработана"
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq request[RescheduleRequestsTable.lessonId] }.singleOrNull()
            ?: return@transaction "Занятие не найдено"
        val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.singleOrNull()
            ?: return@transaction "Дисциплина не найдена"
        val proposedStart = request[RescheduleRequestsTable.proposedStartTime]
        val proposedEnd = request[RescheduleRequestsTable.proposedEndTime]
        val repaired = repairRescheduleWindow(lesson, proposedStart, proposedEnd)
        val (start, end) = resolveApproveWindow(req.newStartIso, req.newEndIso, repaired.first, repaired.second)
            ?: return@transaction "Некорректное время переноса"
        if (!start.isBefore(end)) return@transaction "Время окончания должно быть позже начала"
        if (java.time.Duration.between(start, end).toMinutes() > 8 * 60) {
            return@transaction "Слишком длинный интервал занятия — укажите корректное время"
        }

        val targetRoomId = req.roomId
            ?: request[RescheduleRequestsTable.proposedRoomId]
            ?: lesson[LessonsTable.roomId]
        val excludeLessonIds = parallelLessonIds(lesson).toSet()
        if (hasScheduleConflictMoving(
                excludeLessonIds = excludeLessonIds,
                teacherId = course[CoursesTable.teacherId],
                movingGroupIds = setOf(lesson[LessonsTable.groupId]),
                roomId = targetRoomId,
                start = start,
                end = end,
            )
        ) {
            return@transaction "Конфликт расписания: в это время заняты преподаватель, группа или аудитория"
        }

        applyLessonMove(lesson, start, end, targetRoomId)
        RescheduleRequestsTable.update({ RescheduleRequestsTable.id eq req.requestId }) {
            it[status] = "APPROVED"
            it[adminId] = admin[UsersTable.id]
            it[decisionTime] = java.time.Instant.now()
            it[comment] = req.comment ?: request[RescheduleRequestsTable.comment]
        }
        notifyGroupMove(lesson[LessonsTable.groupId], course[CoursesTable.name], start.toString())
        val teacherUserId = course[CoursesTable.teacherId]
        NotificationsTable.insert {
            it[userId] = teacherUserId
            it[title] = "Перенос одобрен"
                it[message] = "Заявка №${req.requestId}: «${course[CoursesTable.name]}» перенесена администратором."
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = true
        }
        null
    }

    fun rejectRescheduleRequest(adminTicket: String, req: AdminRescheduleRejectRequestDto): String? = transaction {
        val admin = UsersTable.selectAll().where { UsersTable.studentTicket eq adminTicket }.singleOrNull()
            ?: return@transaction "Пользователь не найден"
        if (admin[UsersTable.role] != "ADMIN") return@transaction "Нет прав администратора"
        val request = RescheduleRequestsTable.selectAll().where { RescheduleRequestsTable.id eq req.requestId }.singleOrNull()
            ?: return@transaction "Заявка не найдена"
        if (request[RescheduleRequestsTable.status] != "PENDING") return@transaction "Заявка уже обработана"
        RescheduleRequestsTable.update({ RescheduleRequestsTable.id eq req.requestId }) {
            it[status] = "REJECTED"
            it[adminId] = admin[UsersTable.id]
            it[decisionTime] = java.time.Instant.now()
            it[comment] = req.comment ?: request[RescheduleRequestsTable.comment]
        }
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq request[RescheduleRequestsTable.lessonId] }.singleOrNull()
        val course = lesson?.let { l ->
            CoursesTable.selectAll().where { CoursesTable.id eq l[LessonsTable.courseId] }.singleOrNull()
        }
        course?.let { c ->
            NotificationsTable.insert {
                it[userId] = c[CoursesTable.teacherId]
                it[title] = "Перенос отклонён"
                it[message] = "Заявка №${req.requestId} по «${c[CoursesTable.name]}» отклонена администратором."
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = true
            }
        }
        null
    }

    private fun parallelLessonIds(baseLesson: org.jetbrains.exposed.sql.ResultRow): List<Long> {
        val cid = baseLesson[LessonsTable.courseId]
        val rid = baseLesson[LessonsTable.roomId]
        val st = baseLesson[LessonsTable.startTime]
        val en = baseLesson[LessonsTable.endTime]
        return LessonsTable.selectAll().where {
            (LessonsTable.courseId eq cid) and
                (LessonsTable.roomId eq rid) and
                (LessonsTable.startTime eq st) and
                (LessonsTable.endTime eq en)
        }.map { it[LessonsTable.id] }
    }

    /** Для общей лекции в нескольких группах: строка занятия именно группы студента. */
    private fun resolveLessonForStudentGroup(
        baseLesson: org.jetbrains.exposed.sql.ResultRow,
        studentGroupId: Long,
    ): org.jetbrains.exposed.sql.ResultRow? {
        if (baseLesson[LessonsTable.groupId] == studentGroupId) return baseLesson
        val cid = baseLesson[LessonsTable.courseId]
        val rid = baseLesson[LessonsTable.roomId]
        val st = baseLesson[LessonsTable.startTime]
        val en = baseLesson[LessonsTable.endTime]
        return LessonsTable.selectAll().where {
            (LessonsTable.courseId eq cid) and
                (LessonsTable.roomId eq rid) and
                (LessonsTable.startTime eq st) and
                (LessonsTable.endTime eq en) and
                (LessonsTable.groupId eq studentGroupId)
        }.singleOrNull()
    }

    private fun normalizeNfcUid(raw: String): String =
        raw.trim().uppercase()
            .replace(":", "")
            .replace("\uFF1A", "") // полноширинное «:» с клавиатуры/сканера
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")

    /**
     * Поджимает окно «демо»-пары с типом NFC_DEMO / NFC_LIVE к текущему моменту, чтобы проверка преподавателя
     * не зависела от жёстких названий курса или аудитории.
     */
    private fun bumpNfcLiveLessonWindow(teacherId: Long) {
        val courseIds = CoursesTable.selectAll().where { CoursesTable.teacherId eq teacherId }
            .map { it[CoursesTable.id] }
        if (courseIds.isEmpty()) return
        val liveRow = LessonsTable.selectAll().where {
            (LessonsTable.courseId inList courseIds) and
                (LessonsTable.lessonKind inList listOf("NFC_LIVE", "NFC_DEMO"))
        }.minByOrNull { it[LessonsTable.id] } ?: return
        val now = java.time.Instant.now()
        val startBumped = now.minus(Duration.ofMinutes(15))
        val endBumped = now.plus(Duration.ofHours(8))
        LessonsTable.update({ LessonsTable.id eq liveRow[LessonsTable.id] }) {
            it[LessonsTable.startTime] = startBumped
            it[LessonsTable.endTime] = endBumped
            it[LessonsTable.lessonKind] = "NFC_LIVE"
        }
    }

    fun teacherCurrentLessonCheck(teacherTicket: String, roomNfcUid: String?): TeacherLessonCheckDto? = transaction {
        val ticket = teacherTicket.trim()
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            ?: return@transaction null
        val teacherId = teacherUser[UsersTable.id]
        if (TeachersTable.selectAll().where { TeachersTable.userId eq teacherId }.empty()) return@transaction null
        bumpNfcLiveLessonWindow(teacherId)

        val roomFilter = roomNfcUid?.trim()?.let { normalizeNfcUid(it) }?.takeIf { it.isNotEmpty() }
        val now = java.time.Instant.now()

        data class Cand(
            val lessonId: Long,
            val subject: String,
            val groupName: String,
            val room: String,
            val timeRange: String,
            val start: java.time.Instant,
            val end: java.time.Instant,
            val roomNfcUnconfiguredWarning: Boolean = false,
        )

        val courseIds = CoursesTable.selectAll().where { CoursesTable.teacherId eq teacherId }
            .map { it[CoursesTable.id] }
        if (courseIds.isEmpty()) return@transaction null

        val lessons = LessonsTable.selectAll().where { LessonsTable.courseId inList courseIds }.toList()
        val coursesById = CoursesTable.selectAll().where { CoursesTable.teacherId eq teacherId }
            .associateBy { it[CoursesTable.id] }
        val roomsById = RoomsTable.selectAll().associateBy { it[RoomsTable.id] }
        val groupsById = GroupsTable.selectAll().associateBy { it[GroupsTable.id] }

        val candsRaw = lessons.mapNotNull { lessonRow ->
            val course = coursesById[lessonRow[LessonsTable.courseId]] ?: return@mapNotNull null
            val roomRow = roomsById[lessonRow[LessonsTable.roomId]] ?: return@mapNotNull null
            val groupRow = groupsById[lessonRow[LessonsTable.groupId]] ?: return@mapNotNull null
            val rUid = roomRow[RoomsTable.nfcUid]?.let { normalizeNfcUid(it) }.orEmpty()
            if (roomFilter != null) {
                if (rUid.isNotEmpty() && rUid != roomFilter) return@mapNotNull null
            }
            val roomBypass = roomFilter != null && rUid.isEmpty()
            val start = lessonRow[LessonsTable.startTime]
            val end = lessonRow[LessonsTable.endTime]
            val zStart = ZonedDateTime.ofInstant(start, zone)
            val zEnd = ZonedDateTime.ofInstant(end, zone)
            val tr = "${zStart.format(timeFmt)}-${zEnd.format(timeFmt)}"
            Cand(
                lessonId = lessonRow[LessonsTable.id],
                subject = course[CoursesTable.name],
                groupName = groupRow[GroupsTable.name],
                room = roomRow[RoomsTable.name],
                timeRange = tr,
                start = start,
                end = end,
                roomNfcUnconfiguredWarning = roomBypass,
            )
        }

        val cands = candsRaw
            .groupBy { listOf(it.start.toEpochMilli(), it.end.toEpochMilli(), it.room, it.subject).joinToString("|") }
            .values
            .map { g ->
                val head = g.minBy { it.lessonId }
                val gnames = g.map { it.groupName }.distinct().sorted().joinToString(" и ")
                Cand(
                    lessonId = head.lessonId,
                    subject = head.subject,
                    groupName = gnames,
                    room = head.room,
                    timeRange = head.timeRange,
                    start = head.start,
                    end = head.end,
                    roomNfcUnconfiguredWarning = g.any { it.roomNfcUnconfiguredWarning },
                )
            }

        val inProgress = cands.filter { !now.isBefore(it.start) && now.isBefore(it.end) }
            .minByOrNull { it.start }
        if (inProgress != null) {
            return@transaction TeacherLessonCheckDto(
                lessonId = inProgress.lessonId,
                subject = inProgress.subject,
                groupName = inProgress.groupName,
                room = inProgress.room,
                timeRange = inProgress.timeRange,
                phase = "IN_PROGRESS",
                startEpochMillis = inProgress.start.toEpochMilli(),
                endEpochMillis = inProgress.end.toEpochMilli(),
                roomNfcUnconfiguredWarning = inProgress.roomNfcUnconfiguredWarning,
            )
        }

        val upcoming = cands.filter { cand ->
            if (!now.isBefore(cand.start)) return@filter false
            val untilMin = Duration.between(now, cand.start).toMinutes()
            val slotMin = Duration.between(cand.start, cand.end).toMinutes()
            val maxUpcomingMin = if (slotMin >= 180) 72 * 60 else 30
            untilMin <= maxUpcomingMin
        }.minByOrNull { it.start }
        if (upcoming != null) {
            return@transaction TeacherLessonCheckDto(
                lessonId = upcoming.lessonId,
                subject = upcoming.subject,
                groupName = upcoming.groupName,
                room = upcoming.room,
                timeRange = upcoming.timeRange,
                phase = "UPCOMING",
                startEpochMillis = upcoming.start.toEpochMilli(),
                endEpochMillis = upcoming.end.toEpochMilli(),
                roomNfcUnconfiguredWarning = upcoming.roomNfcUnconfiguredWarning,
            )
        }
        null
    }

    fun startAttendanceSession(teacherTicket: String, req: StartAttendanceSessionRequestDto): AttendanceSessionDto? = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq teacherTicket }.singleOrNull()
            ?: return@transaction null
        val teacherId = teacherUser[UsersTable.id]
        if (TeachersTable.selectAll().where { TeachersTable.userId eq teacherId }.empty()) return@transaction null

        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq req.lessonId }.singleOrNull()
            ?: return@transaction null
        val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.singleOrNull()
            ?: return@transaction null
        if (course[CoursesTable.teacherId] != teacherId) return@transaction null

        val now = java.time.Instant.now()
        AttendanceSessionsTable.update({
            (AttendanceSessionsTable.teacherId eq teacherId) and
                (AttendanceSessionsTable.isActive eq true) and
                (AttendanceSessionsTable.expiresAt lessEq now)
        }) {
            it[AttendanceSessionsTable.isActive] = false
        }

        val lessonStart = lesson[LessonsTable.startTime]
        var lessonEnd = lesson[LessonsTable.endTime]
        val durationMin = Duration.between(lessonStart, lessonEnd).toMinutes()
        if (durationMin > 4 * 60) {
            lessonEnd = lessonStart.plus(Duration.ofMinutes(90))
        }
        if (!lessonEnd.isAfter(now)) return@transaction null
        val earliestOpen = lessonStart.minus(Duration.ofMinutes(15))
        if (now.isBefore(earliestOpen)) return@transaction null
        // Бизнес-правило:
        // - запуск до начала пары (но не раньше 15 минут): до lessonStart + 15 минут
        // - запуск во время пары: ровно 15 минут от момента запуска
        val expiresAt = if (now.isBefore(lessonStart)) {
            lessonStart.plus(Duration.ofMinutes(15))
        } else {
            now.plus(Duration.ofMinutes(15))
        }
        if (!expiresAt.isAfter(now)) return@transaction null

        AttendanceSessionsTable.update({ AttendanceSessionsTable.teacherId eq teacherId }) {
            it[AttendanceSessionsTable.isActive] = false
        }

        val sessionId = AttendanceSessionsTable.insert {
            it[AttendanceSessionsTable.lessonId] = req.lessonId
            it[AttendanceSessionsTable.teacherId] = teacherId
            it[AttendanceSessionsTable.startedAt] = now
            it[AttendanceSessionsTable.expiresAt] = expiresAt
            it[AttendanceSessionsTable.isActive] = true
        } get AttendanceSessionsTable.id

        sessionDto(sessionId, lesson, course, teacherUser, expiresAt)
    }

    fun stopAttendanceSession(teacherTicket: String, req: StopAttendanceSessionRequestDto): Boolean = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq teacherTicket }.singleOrNull()
            ?: return@transaction false
        val teacherId = teacherUser[UsersTable.id]
        val session = AttendanceSessionsTable.selectAll().where {
            (AttendanceSessionsTable.id eq req.sessionId) and
                (AttendanceSessionsTable.teacherId eq teacherId)
        }.singleOrNull() ?: return@transaction false
        AttendanceSessionsTable.update({ AttendanceSessionsTable.id eq req.sessionId }) {
            it[AttendanceSessionsTable.isActive] = false
        }
        finalizeSessionAttendance(session[AttendanceSessionsTable.lessonId])
        true
    }

    /** После «Завершить лекцию» — все без отметки «присутствует» получают неявку. */
    private fun finalizeSessionAttendance(lessonId: Long) {
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq lessonId }.singleOrNull() ?: return
        val groupId = lesson[LessonsTable.groupId]
        val studentIds = StudentsTable.selectAll()
            .where { StudentsTable.groupId eq groupId }
            .map { it[StudentsTable.userId] }
        val now = java.time.Instant.now()
        for (studentId in studentIds) {
            val att = AttendanceTable.selectAll().where {
                (AttendanceTable.lessonId eq lessonId) and (AttendanceTable.studentId eq studentId)
            }
                .orderBy(AttendanceTable.id to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
            val status = att?.get(AttendanceTable.status)?.trim()?.uppercase()
            val reason = att?.get(AttendanceTable.reason)?.trim()
            if (status == "PRESENT") continue
            if (reason.equals("EXCUSED", ignoreCase = true)) continue
            if (reason.equals("UNEXCUSED", ignoreCase = true)) continue
            AttendanceTable.deleteWhere {
                (AttendanceTable.lessonId eq lessonId) and (AttendanceTable.studentId eq studentId)
            }
            AttendanceTable.insert {
                it[AttendanceTable.lessonId] = lessonId
                it[AttendanceTable.studentId] = studentId
                it[AttendanceTable.timestamp] = now
                it[AttendanceTable.status] = "ABSENT_MANUAL"
                it[AttendanceTable.reason] = "UNEXCUSED"
            }
        }
    }

    fun activeSessionForTeacher(teacherTicket: String): AttendanceSessionDto? = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq teacherTicket }.singleOrNull()
            ?: return@transaction null
        val teacherId = teacherUser[UsersTable.id]
        val now = java.time.Instant.now()
        AttendanceSessionsTable.update({
            (AttendanceSessionsTable.teacherId eq teacherId) and
                (AttendanceSessionsTable.isActive eq true) and
                (AttendanceSessionsTable.expiresAt lessEq now)
        }) {
            it[AttendanceSessionsTable.isActive] = false
        }
        val session = AttendanceSessionsTable.selectAll().where {
            (AttendanceSessionsTable.teacherId eq teacherId) and
                (AttendanceSessionsTable.isActive eq true) and
                (AttendanceSessionsTable.expiresAt greater now)
        }.firstOrNull() ?: return@transaction null
        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq session[AttendanceSessionsTable.lessonId] }.single()
        val course = CoursesTable.selectAll().where { CoursesTable.id eq lesson[LessonsTable.courseId] }.single()
        sessionDto(
            session[AttendanceSessionsTable.id],
            lesson,
            course,
            teacherUser,
            session[AttendanceSessionsTable.expiresAt],
        )
    }

    fun activeSessionForStudent(studentTicket: String): AttendanceSessionDto? = transaction {
        val studentUser = UsersTable.selectAll().where { UsersTable.studentTicket eq studentTicket }.singleOrNull()
            ?: return@transaction null
        val studentId = studentUser[UsersTable.id]
        val groupId = StudentsTable.selectAll().where { StudentsTable.userId eq studentId }.singleOrNull()
            ?.get(StudentsTable.groupId) ?: return@transaction null

        val now = java.time.Instant.now()
        val session = AttendanceSessionsTable.selectAll().where {
            (AttendanceSessionsTable.isActive eq true) and (AttendanceSessionsTable.expiresAt greater now)
        }.toList().firstOrNull { sess ->
            val baseLesson = LessonsTable.selectAll().where { LessonsTable.id eq sess[AttendanceSessionsTable.lessonId] }.singleOrNull()
                ?: return@firstOrNull false
            resolveLessonForStudentGroup(baseLesson, groupId) != null
        } ?: return@transaction null

        val baseLesson = LessonsTable.selectAll().where { LessonsTable.id eq session[AttendanceSessionsTable.lessonId] }.single()
        val studentLesson = resolveLessonForStudentGroup(baseLesson, groupId)!!
        val course = CoursesTable.selectAll().where { CoursesTable.id eq studentLesson[LessonsTable.courseId] }.single()
        val teacher = UsersTable.selectAll().where { UsersTable.id eq session[AttendanceSessionsTable.teacherId] }.single()
        sessionDto(
            session[AttendanceSessionsTable.id],
            studentLesson,
            course,
            teacher,
            session[AttendanceSessionsTable.expiresAt],
        )
    }

    private fun sessionDto(
        sessionId: Long,
        lesson: org.jetbrains.exposed.sql.ResultRow,
        course: org.jetbrains.exposed.sql.ResultRow,
        teacherUser: org.jetbrains.exposed.sql.ResultRow,
        expiresAt: java.time.Instant,
    ): AttendanceSessionDto {
        val groupLabel = run {
            val ids = parallelLessonIds(lesson)
            val names = ids.mapNotNull { lid ->
                val row = LessonsTable.selectAll().where { LessonsTable.id eq lid }.singleOrNull() ?: return@mapNotNull null
                val gid = row[LessonsTable.groupId]
                GroupsTable.selectAll().where { GroupsTable.id eq gid }.singleOrNull()?.get(GroupsTable.name)
            }.distinct().sorted()
            when {
                names.size > 1 -> names.joinToString(", ")
                names.size == 1 -> names[0]
                else -> "—"
            }
        }
        val roomName = RoomsTable.selectAll().where { RoomsTable.id eq lesson[LessonsTable.roomId] }.single()[RoomsTable.name]
        return AttendanceSessionDto(
            sessionId = sessionId,
            lessonId = lesson[LessonsTable.id],
            teacherName = teacherUser[UsersTable.fullName],
            teacherPhotoHex = teacherUser[UsersTable.photoHex],
            subject = course[CoursesTable.name],
            groupName = groupLabel,
            roomName = roomName,
            expiresAtIso = expiresAt.toString(),
            isActive = true,
        )
    }

    fun markByNfc(req: NfcMarkRequestDto): NfcMarkResultDto = transaction {
        val now = java.time.Instant.now()
        val session = AttendanceSessionsTable.selectAll().where { AttendanceSessionsTable.id eq req.sessionId }.singleOrNull()
            ?: return@transaction NfcMarkResultDto(false, message = "Сессия не найдена", code = "no_session")
        if (!session[AttendanceSessionsTable.isActive] || session[AttendanceSessionsTable.expiresAt] <= now) {
            return@transaction NfcMarkResultDto(false, message = "Сессия закрыта", code = "expired")
        }
        val uid = normalizeNfcUid(req.cardUid)
        if (uid.isEmpty()) return@transaction NfcMarkResultDto(false, message = "Пустой UID", code = "bad_uid")

        val ticket = req.studentTicket?.trim().orEmpty()
        if (ticket.isEmpty()) {
            return@transaction NfcMarkResultDto(false, message = "Укажите билет студента", code = "bad_ticket")
        }

        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq session[AttendanceSessionsTable.lessonId] }.single()
        val roomRow = RoomsTable.selectAll().where { RoomsTable.id eq lesson[LessonsTable.roomId] }.single()
        val roomNorm = roomRow[RoomsTable.nfcUid]?.let { normalizeNfcUid(it) }.orEmpty()
        if (roomNorm.isEmpty() || roomNorm != uid) {
            return@transaction NfcMarkResultDto(
                false,
                message = "Метка не привязана к аудитории",
                code = "unknown_card",
            )
        }

        val userRow = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            ?: return@transaction NfcMarkResultDto(false, message = "Билет не найден", code = "bad_ticket")
        if (userRow[UsersTable.role] != "STUDENT") {
            return@transaction NfcMarkResultDto(false, message = "Не студент", code = "wrong_group")
        }
        val studentId = userRow[UsersTable.id]
        val studentGroup = StudentsTable.selectAll().where { StudentsTable.userId eq studentId }.singleOrNull()
            ?: return@transaction NfcMarkResultDto(false, message = "Не студент", code = "wrong_group")
        val markLesson = resolveLessonForStudentGroup(lesson, studentGroup[StudentsTable.groupId])
            ?: return@transaction NfcMarkResultDto(false, message = "Другая группа", code = "wrong_group")

        val user = UsersTable.selectAll().where { UsersTable.id eq studentId }.single()
        val existing = AttendanceTable.selectAll().where {
            (AttendanceTable.lessonId eq markLesson[LessonsTable.id]) and (AttendanceTable.studentId eq studentId)
        }.singleOrNull()
        if (existing == null) {
            AttendanceTable.insert {
                it[lessonId] = markLesson[LessonsTable.id]
                it[AttendanceTable.studentId] = studentId
                it[timestamp] = now
                it[status] = "PRESENT"
                it[reason] = null
            }
        } else {
            AttendanceTable.update({ AttendanceTable.id eq existing[AttendanceTable.id] }) {
                it[timestamp] = now
                it[status] = "PRESENT"
                it[reason] = null
            }
        }
        NfcMarkResultDto(success = true, studentName = user[UsersTable.fullName])
    }

    fun sessionLiveRoster(teacherTicket: String, sessionId: Long): List<SessionRosterRowDto> = transaction {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq teacherTicket }.singleOrNull()
            ?: return@transaction emptyList()
        val teacherId = teacherUser[UsersTable.id]
        val session = AttendanceSessionsTable.selectAll().where { AttendanceSessionsTable.id eq sessionId }.singleOrNull()
            ?: return@transaction emptyList()
        if (session[AttendanceSessionsTable.teacherId] != teacherId) return@transaction emptyList()

        val lesson = LessonsTable.selectAll().where { LessonsTable.id eq session[AttendanceSessionsTable.lessonId] }.single()
        val parallelIds = parallelLessonIds(lesson)
        val groupIds = LessonsTable.selectAll().where { LessonsTable.id inList parallelIds }
            .map { it[LessonsTable.groupId] }
            .distinct()
        val students = groupIds.flatMap { gid ->
            StudentsTable.selectAll().where { StudentsTable.groupId eq gid }
        }.distinctBy { it[StudentsTable.userId] }
        students.mapNotNull { st ->
            val uid = st[StudentsTable.userId]
            val u = UsersTable.selectAll().where { UsersTable.id eq uid }.singleOrNull() ?: return@mapNotNull null
            val att = if (parallelIds.size == 1) {
                AttendanceTable.selectAll().where {
                    (AttendanceTable.lessonId eq parallelIds[0]) and (AttendanceTable.studentId eq uid)
                }.singleOrNull()
            } else {
                AttendanceTable.selectAll().where {
                    (AttendanceTable.lessonId inList parallelIds) and (AttendanceTable.studentId eq uid)
                }.firstOrNull()
            }
            val stRaw = att?.get(AttendanceTable.status)?.trim()?.uppercase()
            val present = att != null && stRaw == "PRESENT"
            SessionRosterRowDto(
                studentTicket = u[UsersTable.studentTicket],
                fullName = u[UsersTable.fullName],
                present = present,
            )
        }
    }

    private fun instantToApiIso(instant: java.time.Instant): String =
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant)

    /**
     * Перенос = обновление той же строки занятия + блокировка старого слота (чтобы expander не создал дубль).
     */
    private fun applyLessonMove(
        lesson: org.jetbrains.exposed.sql.ResultRow,
        newStart: java.time.Instant,
        newEnd: java.time.Instant,
        targetRoomId: Long,
    ) {
        val lessonId = lesson[LessonsTable.id]
        val oldStart = lesson[LessonsTable.startTime]
        val oldEnd = lesson[LessonsTable.endTime]
        if (oldStart != newStart || oldEnd != newEnd) {
            markSlotCancelledIfFree(
                groupId = lesson[LessonsTable.groupId],
                courseId = lesson[LessonsTable.courseId],
                roomId = lesson[LessonsTable.roomId],
                lessonKind = lesson[LessonsTable.lessonKind],
                weekParity = lesson[LessonsTable.weekParity],
                start = oldStart,
                end = oldEnd,
                excludeLessonId = lessonId,
            )
        }
        LessonsTable.update({ LessonsTable.id eq lessonId }) {
            it[startTime] = newStart
            it[endTime] = newEnd
            it[LessonsTable.roomId] = targetRoomId
            it[status] = "RESCHEDULED"
        }
        resetAttendanceForMovedLesson(lessonId)
    }

    /** После переноса журнал снова «пустой» (точки), без старых галочек и крестиков. */
    private fun resetAttendanceForMovedLesson(lessonId: Long) {
        AttendanceTable.deleteWhere { AttendanceTable.lessonId eq lessonId }
        AttendanceSessionsTable.deleteWhere { AttendanceSessionsTable.lessonId eq lessonId }
    }

    private fun markSlotCancelledIfFree(
        groupId: Long,
        courseId: Long,
        roomId: Long,
        lessonKind: String,
        weekParity: String,
        start: java.time.Instant,
        end: java.time.Instant,
        excludeLessonId: Long,
    ) {
        val occupied = LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and
                (LessonsTable.courseId eq courseId) and
                (LessonsTable.startTime eq start) and
                (LessonsTable.endTime eq end) and
                (LessonsTable.id neq excludeLessonId)
        }.any()
        if (occupied) return
        LessonsTable.insert {
            it[LessonsTable.groupId] = groupId
            it[LessonsTable.courseId] = courseId
            it[LessonsTable.roomId] = roomId
            it[LessonsTable.startTime] = start
            it[LessonsTable.endTime] = end
            it[LessonsTable.status] = "CANCELLED"
            it[LessonsTable.lessonKind] = lessonKind
            it[LessonsTable.weekParity] = weekParity
        }
    }

    /** Убирает дубли и «призраки» после переносов (старые MOVED + лишние SCHEDULED). */
    private fun sealGhostSlotsAfterMoves(groupId: Long, courseId: Long) {
        val all = LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and (LessonsTable.courseId eq courseId)
        }.toList()
        all.groupBy { it[LessonsTable.startTime] to it[LessonsTable.endTime] }
            .filter { it.value.size > 1 }
            .forEach { (_, rows) ->
                val keep = rows.maxBy { row ->
                    AttendanceTable.selectAll().where { AttendanceTable.lessonId eq row[LessonsTable.id] }.count()
                }
                rows.filter { it[LessonsTable.id] != keep[LessonsTable.id] }.forEach { dup ->
                    LessonsTable.update({ LessonsTable.id eq dup[LessonsTable.id] }) {
                        it[status] = "CANCELLED"
                    }
                }
            }
        val moved = all.filter { it[LessonsTable.status] == "MOVED" }
        if (moved.isEmpty()) return
        moved.forEach { m ->
            val mStart = m[LessonsTable.startTime]
            val mEnd = m[LessonsTable.endTime]
            val mZ = java.time.ZonedDateTime.ofInstant(mStart, zone)
            val mFrom = mZ.toLocalTime()
            val mTo = java.time.ZonedDateTime.ofInstant(mEnd, zone).toLocalTime()
            all.filter { row ->
                row[LessonsTable.id] != m[LessonsTable.id] &&
                    row[LessonsTable.status] == "SCHEDULED" &&
                    java.time.ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone).toLocalTime() == mFrom &&
                    java.time.ZonedDateTime.ofInstant(row[LessonsTable.endTime], zone).toLocalTime() == mTo &&
                    java.time.ZonedDateTime.ofInstant(row[LessonsTable.startTime], zone).toLocalDate() !=
                    mZ.toLocalDate()
            }.forEach { ghost ->
                LessonsTable.update({ LessonsTable.id eq ghost[LessonsTable.id] }) {
                    it[status] = "CANCELLED"
                }
            }
            LessonsTable.update({ LessonsTable.id eq m[LessonsTable.id] }) {
                it[status] = "SCHEDULED"
            }
        }
    }

    /**
     * После ошибочного переноса в БД остаётся окно 19:10–23:15 и т.п. — подправляем до 90 минут.
     */
    private fun healCorruptLessonWindowIfNeeded(
        lessonId: Long,
        start: ZonedDateTime,
        end: ZonedDateTime,
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val durationMin = Duration.between(start, end).toMinutes()
        if (durationMin in 10..(4 * 60)) return start to end
        val healedEnd = start.plusMinutes(90)
        LessonsTable.update({ LessonsTable.id eq lessonId }) {
            it[LessonsTable.endTime] = healedEnd.toInstant()
        }
        return start to healedEnd
    }

    private fun repairRescheduleWindow(
        lesson: org.jetbrains.exposed.sql.ResultRow,
        proposedStart: java.time.Instant,
        proposedEnd: java.time.Instant,
    ): Pair<java.time.Instant, java.time.Instant> {
        val lessonStartZ = java.time.ZonedDateTime.ofInstant(lesson[LessonsTable.startTime], zone)
        val lessonEndZ = java.time.ZonedDateTime.ofInstant(lesson[LessonsTable.endTime], zone)
        val propStartZ = java.time.ZonedDateTime.ofInstant(proposedStart, zone)
        val propEndZ = java.time.ZonedDateTime.ofInstant(proposedEnd, zone)
        val durationMin = java.time.Duration.between(proposedStart, proposedEnd).toMinutes()
        val lessonDurationMin = java.time.Duration.between(lessonStartZ, lessonEndZ).toMinutes()
            .coerceIn(30, 8 * 60)
        val suspicious =
            durationMin > 4 * 60 ||
                durationMin < 10 ||
                (propStartZ.toLocalTime().hour <= 2 && propEndZ.toLocalTime().hour >= 15)
        if (!suspicious) return proposedStart to proposedEnd

        val targetDate = propStartZ.toLocalDate()
        val localFrom = propStartZ.toLocalTime()
        val localTo = propEndZ.toLocalTime()
        if (localTo.isAfter(localFrom) && durationMin in 15..(4 * 60)) {
            return proposedStart to proposedEnd
        }
        var newStart = java.time.ZonedDateTime.of(targetDate, lessonStartZ.toLocalTime(), zone).toInstant()
        var newEnd = java.time.ZonedDateTime.of(targetDate, lessonEndZ.toLocalTime(), zone).toInstant()
        if (!newEnd.isAfter(newStart)) {
            newEnd = newStart.plus(java.time.Duration.ofMinutes(lessonDurationMin))
        }
        return newStart to newEnd
    }

    /**
     * Если клиент прислал «раздутое» окно (ошибка TZ в UI), берём время из заявки в БД.
     */
    private fun resolveApproveWindow(
        clientStartIso: String,
        clientEndIso: String,
        proposedStart: java.time.Instant,
        proposedEnd: java.time.Instant,
    ): Pair<java.time.Instant, java.time.Instant>? {
        val clientStart = runCatching { java.time.Instant.parse(clientStartIso) }.getOrNull()
        val clientEnd = runCatching { java.time.Instant.parse(clientEndIso) }.getOrNull()
        if (clientStart == null || clientEnd == null || !clientStart.isBefore(clientEnd)) {
            return proposedStart to proposedEnd
        }
        val proposedMinutes = java.time.Duration.between(proposedStart, proposedEnd).toMinutes()
        val clientMinutes = java.time.Duration.between(clientStart, clientEnd).toMinutes()
        val startSkewMinutes = kotlin.math.abs(
            java.time.Duration.between(clientStart, proposedStart).toMinutes(),
        )
        val suspectClient =
            clientMinutes > 8 * 60 ||
                proposedMinutes in 1..8 * 60 && clientMinutes > proposedMinutes + 120 ||
                startSkewMinutes > 3 * 60
        return if (suspectClient) proposedStart to proposedEnd else clientStart to clientEnd
    }

    /** Перенос: конфликт только с активными занятиями (SCHEDULED), не с отменёнными. */
    private fun hasScheduleConflictMoving(
        excludeLessonIds: Set<Long>,
        teacherId: Long,
        movingGroupIds: Set<Long>,
        roomId: Long,
        start: java.time.Instant,
        end: java.time.Instant,
    ): Boolean {
        if (excludeLessonIds.isEmpty()) return false
        val excludeList = excludeLessonIds.toList()
        val overlappingLessons = LessonsTable.selectAll().where {
            (LessonsTable.id notInList excludeList) and
                (LessonsTable.status inList listOf("SCHEDULED", "RESCHEDULED")) and
                (LessonsTable.startTime less end) and
                (LessonsTable.endTime greater start)
        }.toList()
        return overlappingLessons.any { other ->
            val otherCourse = CoursesTable.selectAll().where { CoursesTable.id eq other[LessonsTable.courseId] }.singleOrNull()
            val teacherConflict = otherCourse?.get(CoursesTable.teacherId) == teacherId
            val groupConflict = other[LessonsTable.groupId] in movingGroupIds
            val roomConflict = other[LessonsTable.roomId] == roomId
            teacherConflict || groupConflict || roomConflict
        }
    }

    private fun notifyGroupMove(groupId: Long, subject: String, newStartIso: String) {
        StudentsTable.selectAll().where { StudentsTable.groupId eq groupId }.forEach { st ->
            NotificationsTable.insert {
                it[userId] = st[StudentsTable.userId]
                it[title] = "Изменение по заявке на перенос"
                it[message] = "Занятие «$subject» администратором перенесено на $newStartIso"
                it[createdAt] = java.time.Instant.now()
                it[isImportant] = true
            }
        }
    }
}

