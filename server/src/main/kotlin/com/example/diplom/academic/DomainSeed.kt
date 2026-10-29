package com.example.diplom.academic

import com.example.diplom.db.CoursesTable
import com.example.diplom.db.FacultiesTable
import com.example.diplom.db.GroupCoursesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.LessonsTable
import com.example.diplom.db.NotificationsTable
import com.example.diplom.db.DepartmentsTable
import com.example.diplom.schedule.JournalLessonExpander
import com.example.diplom.db.RoomsTable
import com.example.diplom.db.SpecialtiesTable
import com.example.diplom.db.StudentsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import com.example.diplom.auth.PasswordHasher
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object DomainSeed {
    private val zone: ZoneId = ZoneId.of("Europe/Minsk")

    /**
     * Пары для NFC-сканера: **всегда** после миграции билетов, до каталога специальностей.
     * Иначе при `return@transaction` из-за пустого admin seed блок с NFC в конце `ensure()` никогда не выполнялся — на телефоне «нет пары».
     */
    private fun seedNfcDemoLessonsEarly() {
        val teacherUser = UsersTable.selectAll().where { UsersTable.studentTicket eq "teacher" }.singleOrNull()
            ?: return
        val teacherId = teacherUser[UsersTable.id]
        if (TeachersTable.selectAll().where { TeachersTable.userId eq teacherId }.empty()) {
            val depId = DepartmentsTable.selectAll().firstOrNull()?.get(DepartmentsTable.id) ?: return
            TeachersTable.insert {
                it[TeachersTable.userId] = teacherId
                it[TeachersTable.departmentId] = depId
            }
        }

        val demoGroupId = GroupsTable.selectAll().where { GroupsTable.name eq "1-8 ПОИБМС" }.singleOrNull()?.get(GroupsTable.id)
            ?: GroupsTable.insert {
                it[GroupsTable.name] = "1-8 ПОИБМС"
                it[GroupsTable.specialtyId] = null
                it[GroupsTable.course] = 1
                it[GroupsTable.groupNumber] = 8
                it[GroupsTable.shift] = "SECOND"
            } get GroupsTable.id

        val demoCourseId = CoursesTable.selectAll().where {
            (CoursesTable.name eq "Компьютерные сети") and (CoursesTable.teacherId eq teacherId)
        }.singleOrNull()?.get(CoursesTable.id)
            ?: CoursesTable.insert {
                it[CoursesTable.name] = "Компьютерные сети"
                it[CoursesTable.teacherId] = teacherId
            } get CoursesTable.id

        val gcExists = GroupCoursesTable.selectAll().where {
            (GroupCoursesTable.groupId eq demoGroupId) and (GroupCoursesTable.courseId eq demoCourseId)
        }.singleOrNull()
        if (gcExists == null) {
            GroupCoursesTable.insert {
                it[GroupCoursesTable.groupId] = demoGroupId
                it[GroupCoursesTable.courseId] = demoCourseId
            }
        }
    }

    private fun ensureAtLeastOneSpecialty() {
        if (SpecialtiesTable.selectAll().limit(1).count() > 0L) return
        val fid = FacultiesTable.selectAll().firstOrNull()?.get(FacultiesTable.id)
            ?: (FacultiesTable.insert { it[name] = "Сид (авто-факультет)" } get FacultiesTable.id)
        SpecialtiesTable.insert {
            it[SpecialtiesTable.facultyId] = fid
            it[SpecialtiesTable.code] = "AUTO-SEED-01"
            it[SpecialtiesTable.name] = "Специальность (автосид)"
            it[SpecialtiesTable.profile] = "Создано автоматически, чтобы основной сид не останавливался."
        }
    }

    /** Удаляет устаревшую аудиторию «NFC-Лаб», переносит занятия в 315-2. */
    private fun removeLegacyNfcLabRoom() {
        val legacy = RoomsTable.selectAll().where { RoomsTable.name eq "NFC-Лаб" }.singleOrNull() ?: return
        val legacyId = legacy[RoomsTable.id]
        val fallbackName = "315-2"
        val fallback = RoomsTable.selectAll().where { RoomsTable.name eq fallbackName }.singleOrNull()
        if (fallback == null) {
            RoomsTable.insert {
                it[RoomsTable.name] = fallbackName
                legacy[RoomsTable.nfcUid]?.let { uid -> it[RoomsTable.nfcUid] = uid }
            }
        }
        val fallbackId = RoomsTable.selectAll().where { RoomsTable.name eq fallbackName }.single()[RoomsTable.id]
        val legacyNfc = legacy[RoomsTable.nfcUid]
        if (!legacyNfc.isNullOrBlank()) {
            val fb = RoomsTable.selectAll().where { RoomsTable.id eq fallbackId }.single()
            if (fb[RoomsTable.nfcUid].isNullOrBlank()) {
                RoomsTable.update({ RoomsTable.id eq fallbackId }) { it[RoomsTable.nfcUid] = legacyNfc }
            }
        }
        LessonsTable.update({ LessonsTable.roomId eq legacyId }) { it[LessonsTable.roomId] = fallbackId }
        RoomsTable.deleteWhere { RoomsTable.id eq legacyId }
    }

    fun ensure() = transaction {
        migrateLegacyStudentTicket()
        removeLegacyNfcLabRoom()
        seedNfcDemoLessonsEarly()
        ensureAtLeastOneSpecialty()

        val specRows = SpecialtiesTable.selectAll().toList()
        val specIdByCode = specRows.associate { it[SpecialtiesTable.code] to it[SpecialtiesTable.id] }
        fun pickSpec(preferredCode: String, fallbackIndex: Int): Long {
            specIdByCode[preferredCode]?.let { return it }
            return specRows[fallbackIndex % specRows.size][SpecialtiesTable.id]
        }
        val specPoit = pickSpec("1-40 01 01", 0)
        val specIst = pickSpec("6-05-0611-01", 1)
        val specPoibms = pickSpec("1-98 01 03", 2)
        val specDesign = pickSpec("1-47 01 02", 3)

        fun room(name: String, nfc: String? = null) {
            val existing = RoomsTable.selectAll().where { RoomsTable.name eq name }.singleOrNull()
            if (existing != null) {
                if (nfc != null) {
                    RoomsTable.update({ RoomsTable.id eq existing[RoomsTable.id] }) {
                        it[RoomsTable.nfcUid] = nfc
                    }
                }
                return
            }
            RoomsTable.insert {
                it[RoomsTable.name] = name
                if (nfc != null) it[RoomsTable.nfcUid] = nfc
            }
        }
        room("421-1", nfc = "04:11:22:33:44:55")
        room("310-2")
        room("205-1")
        room("112-4")
        room("7-3")
        room("404-1")
        /** NFC-метка для демо-пары «Компьютерные сети» (1-8 ПОИБМС, четверг). */
        room("315-2", nfc = "04:49:29:5E:C1:2A:81")
        room("501-4")

        fun roomId(name: String): Long =
            RoomsTable.selectAll().where { RoomsTable.name eq name }.single()[RoomsTable.id]

        fun ensureGroup(course: Int, num: Int, specId: Long, shift: String, label: String): Long {
            val fullName = "$course-$num $label"
            val existing = GroupsTable.selectAll().where { GroupsTable.name eq fullName }.singleOrNull()
            if (existing != null) return existing[GroupsTable.id]
            return GroupsTable.insert {
                it[GroupsTable.name] = "$course-$num $label"
                it[GroupsTable.specialtyId] = specId
                it[GroupsTable.course] = course
                it[GroupsTable.groupNumber] = num
                it[GroupsTable.shift] = shift
            } get GroupsTable.id
        }

        // 1 курс
        val g1i4 = ensureGroup(1, 4, specIst, "SECOND", "ИСиТ")
        val g1b8 = ensureGroup(1, 8, specPoibms, "SECOND", "ПОИБМС")
        // 4 курс: 10 групп (3 ПОИТ, 3 ИСиТ, 2 ПОИБМС, 2 Дизайн)
        val g4 = listOf(
            ensureGroup(4, 1, specPoit, "FIRST", "ПОИТ"),
            ensureGroup(4, 2, specPoit, "FIRST", "ПОИТ"),
            ensureGroup(4, 3, specPoit, "FIRST", "ПОИТ"),
            ensureGroup(4, 4, specIst, "FIRST", "ИСиТ"),
            ensureGroup(4, 5, specIst, "FIRST", "ИСиТ"),
            ensureGroup(4, 6, specIst, "FIRST", "ИСиТ"),
            ensureGroup(4, 7, specPoibms, "FIRST", "ПОИБМС"),
            ensureGroup(4, 8, specPoibms, "FIRST", "ПОИБМС"),
            ensureGroup(4, 9, specDesign, "FIRST", "Дизайн"),
            ensureGroup(4, 10, specDesign, "FIRST", "Дизайн"),
        )

        val deptRows = DepartmentsTable.selectAll().toList()
        val depDefault = deptRows.firstOrNull()?.get(DepartmentsTable.id)
            ?: return@transaction
        val depByName = deptRows.associate { it[DepartmentsTable.name] to it[DepartmentsTable.id] }

        fun ensureUserTeacher(
            ticket: String,
            fullName: String,
            departmentId: Long = depDefault,
            degree: String = "",
        ): Long {
            val existing = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            val defaultPhone = if (ticket == "teacher") "+375296392619" else "+375 (29) 200-00-01"
            if (existing != null) {
                val uid = existing[UsersTable.id]
                val currentPhone = existing[UsersTable.phone]
                UsersTable.update({ UsersTable.id eq uid }) {
                    it[UsersTable.fullName] = fullName
                    if (currentPhone.isNullOrBlank() || currentPhone == "+375 (29) 200-00-01") {
                        it[UsersTable.phone] = defaultPhone
                    }
                }
                val teacherRow = TeachersTable.selectAll().where { TeachersTable.userId eq uid }.singleOrNull()
                if (teacherRow == null) {
                    TeachersTable.insert {
                        it[TeachersTable.userId] = uid
                        it[TeachersTable.departmentId] = departmentId
                        it[TeachersTable.degree] = degree
                    }
                } else {
                    TeachersTable.update({ TeachersTable.userId eq uid }) {
                        it[TeachersTable.departmentId] = departmentId
                        if (degree.isNotBlank()) it[TeachersTable.degree] = degree
                    }
                }
                return uid
            }
            val uid = UsersTable.insert {
                it[UsersTable.studentTicket] = ticket
                it[UsersTable.passwordHash] = PasswordHasher.hash("1234")
                it[UsersTable.fullName] = fullName
                it[UsersTable.role] = "TEACHER"
                it[UsersTable.isActive] = true
                it[UsersTable.phone] = defaultPhone
            } get UsersTable.id
            TeachersTable.insert {
                it[TeachersTable.userId] = uid
                it[TeachersTable.departmentId] = departmentId
                it[TeachersTable.degree] = degree
            }
            return uid
        }

        val depIst = depByName["Кафедра информационных систем и технологий"] ?: depDefault
        val depSe = depByName["Кафедра программной инженерии"] ?: depDefault
        val depWeb = depByName["Кафедра информатики и веб-дизайна"] ?: depDefault

        val t1 = ensureUserTeacher("teacher", "Жданов Игорь Николаевич", depIst, "старший преподаватель")
        val t2 = ensureUserTeacher("lect_sapozhnikov", "Сапожников Петр Петрович", depIst, "к.т.н., доцент")
        val t3 = ensureUserTeacher("lect_gurov", "Гуров Андрей Васильевич", depSe, "магистр, ассистент")
        val t4 = ensureUserTeacher("lect_klimova", "Климова Мария Олеговна", depWeb, "к.п.н., доцент")
        val t5 = ensureUserTeacher("lect_history", "Николаев Виктор Васильевич", depDefault)

        fun ensureCourse(name: String, teacherUserId: Long): Long {
            val existing = CoursesTable.selectAll().where {
                (CoursesTable.name eq name) and (CoursesTable.teacherId eq teacherUserId)
            }.singleOrNull()
            if (existing != null) return existing[CoursesTable.id]
            return CoursesTable.insert {
                it[CoursesTable.name] = name
                it[CoursesTable.teacherId] = teacherUserId
            } get CoursesTable.id
        }

        val cDb = ensureCourse("Базы данных", t2)
        val cProg = ensureCourse("Программирование", t1)
        val cNet = ensureCourse("Компьютерные сети", t1)
        val cAlgo = ensureCourse("Алгоритмы и структуры данных", t3)
        val cMath = ensureCourse("Математический анализ", t4)
        val cProj = ensureCourse("Управление ИТ-проектами", t2)
        val cHist = ensureCourse("История Беларуси", t5)

        fun linkCourse(groupId: Long, courseId: Long) {
            val exists = GroupCoursesTable.selectAll().where {
                (GroupCoursesTable.groupId eq groupId) and (GroupCoursesTable.courseId eq courseId)
            }.singleOrNull()
            if (exists != null) return
            GroupCoursesTable.insert {
                it[GroupCoursesTable.groupId] = groupId
                it[GroupCoursesTable.courseId] = courseId
            }
        }

        /** Только «Компьютерные сети» (Жданов) для 1-8 — журнал и NFC. Остальной поток: одна общая лекция (другой преподаватель). */
        linkCourse(g1b8, cNet)
        val streamGroups = listOf(g1i4) + g4
        streamGroups.forEach { gid -> linkCourse(gid, cHist) }

        val monday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun at(dayOffset: Int, hour: Int, minute: Int): ZonedDateTime =
            ZonedDateTime.of(monday.plusDays(dayOffset.toLong()), LocalTime.of(hour, minute), zone)

        fun insLesson(
            courseId: Long,
            groupId: Long,
            room: String,
            start: ZonedDateTime,
            end: ZonedDateTime,
            kind: String,
            weekParity: String = "ANY",
        ) {
            val exists = LessonsTable.selectAll().where {
                (LessonsTable.courseId eq courseId) and
                    (LessonsTable.groupId eq groupId) and
                    (LessonsTable.startTime eq start.toInstant())
            }.singleOrNull()
            if (exists != null) return
            LessonsTable.insert {
                it[LessonsTable.courseId] = courseId
                it[LessonsTable.groupId] = groupId
                it[LessonsTable.roomId] = roomId(room)
                it[LessonsTable.startTime] = start.toInstant()
                it[LessonsTable.endTime] = end.toInstant()
                it[LessonsTable.status] = "SCHEDULED"
                it[LessonsTable.lessonKind] = kind
                it[LessonsTable.weekParity] = weekParity
            }
        }

        /** Демо-занятия только при отсутствии такого слота; расписание из админки при перезапуске не стирается. Общая лекция «История Беларуси» — параллельные строки по группам потока. */
        streamGroups.forEach { gid ->
            insLesson(cHist, gid, "112-4", at(0, 10, 30), at(0, 12, 0), "LECTURE", "ANY")
        }

        /** Компьютерные сети — четверг 09:45: только 4 курс (1,2,3,7,8). */
        listOf(g4[0], g4[1], g4[2], g4[6], g4[7]).forEach { gid ->
            insLesson(cNet, gid, "315-2", at(3, 9, 45), at(3, 11, 15), "LECTURE", "ANY")
        }
        /** 1-8 ПОИБМС — только четверг 15:45 в 315-2. */
        insLesson(cNet, g1b8, "315-2", at(3, 15, 45), at(3, 17, 15), "LECTURE", "ANY")
        JournalLessonExpander.purgeComputerNetworksBeforeSpringStart(cNet)
        JournalLessonExpander.purgeComputerNetworksOffThursday(cNet)
        JournalLessonExpander.purgeComputerNetworksMorningForGroup(cNet, g1b8)
        insLesson(cProg, g1i4, "112-4", at(2, 12, 0), at(2, 13, 30), "LECTURE", "ANY")

        /** Год демо-колонок журнала: календарный год (Minsk), не «понедельник ISO-недели». */
        val demoY = LocalDate.now(zone).year

        /**
         * Журнал «Компьютерные сети» + 1-8 ПОИБМС: слоты в 315-2.
         * `upsertLessonExactStart` — если строка с этим start уже есть (любой косяк сида), перезаписываем конец/аудиторию/тип.
         */
        fun upsertLessonExactStart(
            courseId: Long,
            groupId: Long,
            roomName: String,
            start: ZonedDateTime,
            end: ZonedDateTime,
            kind: String,
            weekParity: String = "ANY",
        ) {
            val rid = roomId(roomName)
            val st = start.toInstant()
            val en = end.toInstant()
            val existing = LessonsTable.selectAll().where {
                (LessonsTable.courseId eq courseId) and
                    (LessonsTable.groupId eq groupId) and
                    (LessonsTable.startTime eq st)
            }.singleOrNull()
            if (existing != null) {
                LessonsTable.update({ LessonsTable.id eq existing[LessonsTable.id] }) {
                    it[LessonsTable.endTime] = en
                    it[LessonsTable.roomId] = rid
                    it[LessonsTable.lessonKind] = kind
                    it[LessonsTable.weekParity] = weekParity
                    it[LessonsTable.status] = "SCHEDULED"
                }
            } else {
                LessonsTable.insert {
                    it[LessonsTable.courseId] = courseId
                    it[LessonsTable.groupId] = groupId
                    it[LessonsTable.roomId] = rid
                    it[LessonsTable.startTime] = st
                    it[LessonsTable.endTime] = en
                    it[LessonsTable.status] = "SCHEDULED"
                    it[LessonsTable.lessonKind] = kind
                    it[LessonsTable.weekParity] = weekParity
                }
            }
        }

        /** Демо-журнал 1-8: четверги с ~15 мая, только 15:45. */
        val springStart = LocalDate.of(demoY, Month.MAY, 15)
        val firstThu = if (springStart.dayOfWeek == DayOfWeek.THURSDAY) {
            springStart
        } else {
            springStart.with(TemporalAdjusters.next(DayOfWeek.THURSDAY))
        }
        val secondThu = firstThu.plusWeeks(1)
        upsertLessonExactStart(
            cNet, g1b8, "315-2",
            ZonedDateTime.of(firstThu, LocalTime.of(15, 45), zone),
            ZonedDateTime.of(firstThu, LocalTime.of(17, 15), zone),
            "NFC_DEMO",
            "ANY",
        )
        upsertLessonExactStart(
            cNet, g1b8, "315-2",
            ZonedDateTime.of(secondThu, LocalTime.of(15, 45), zone),
            ZonedDateTime.of(secondThu, LocalTime.of(17, 15), zone),
            "NFC_DEMO",
            "ANY",
        )
        JournalLessonExpander.purgeComputerNetworksBeforeSpringStart(cNet)
        JournalLessonExpander.purgeComputerNetworksOffThursday(cNet)
        JournalLessonExpander.purgeComputerNetworksMorningForGroup(cNet, g1b8)

        fun ensureStudent(ticket: String, fullName: String, groupId: Long) {
            val existing = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
            val uid = existing?.get(UsersTable.id) ?: (UsersTable.insert {
                it[UsersTable.studentTicket] = ticket
                it[UsersTable.passwordHash] = PasswordHasher.hash("1234")
                it[UsersTable.fullName] = fullName
                it[UsersTable.role] = "STUDENT"
                it[UsersTable.isActive] = true
            } get UsersTable.id)
            UsersTable.update({ UsersTable.id eq uid }) { it[UsersTable.fullName] = fullName }
            if (StudentsTable.selectAll().where { StudentsTable.userId eq uid }.singleOrNull() == null) {
                StudentsTable.insert { it[StudentsTable.userId] = uid; it[StudentsTable.groupId] = groupId }
            } else {
                StudentsTable.update({ StudentsTable.userId eq uid }) { it[StudentsTable.groupId] = groupId }
            }
        }

        /** 1-8 ПОИБМС — журнал «Компьютерные сети» и NFC-демо. */
        val g1b8Students = listOf(
            "1100001" to "Ковалёва Анастасия Сергеевна",
            "1100002" to "Борисенко Максим Игоревич",
            "1100003" to "Луценко Дарья Андреевна",
            "1100004" to "Петровский Артём Владимирович",
            "1100005" to "Михальчук Екатерина Олеговна",
            "1100006" to "Жук Никита Дмитриевич",
            "1100007" to "Сидоренко Полина Александровна",
            "1100008" to "Гринкевич Владислав Евгеньевич",
            "1100009" to "Яковлева Вероника Игоревна",
            "1100010" to "Козак Денис Сергеевич",
            "1100011" to "Романчук Алина Викторовна",
            "1100012" to "Шевченко Илья Павлович",
            "1100013" to "Василенко Мария Дмитриевна",
            "1100014" to "Заяц Андрей Николаевич",
            "1100015" to "Савицкая Ольга Константиновна",
        )
        g1b8Students.forEach { (ticket, name) -> ensureStudent(ticket, name, g1b8) }

        /** По 3 студента в каждой группе 4 курса — билет: 7 цифр. */
        val g4StudentNames = listOf(
            listOf("Алехно Дмитрий Сергеевич", "Батура Елена Владимировна", "Воронович Кирилл Александрович"),
            listOf("Герасименко Анна Игоревна", "Дорошенко Павел Викторович", "Ефимов Артём Олегович"),
            listOf("Ждановский Артём Николаевич", "Зайцев Михаил Александрович", "Ильинская Юлия Викторовна"),
            listOf("Иванова Светлана Павловна", "Каминский Денис Юрьевич", "Лазарев Алексей Игоревич"),
            listOf("Лисовский Роман Витальевич", "\u041c\u0430\u0440\u0447\u0443\u043a \u041e\u043b\u044c\u0433\u0430 \u041f\u0435\u0442\u0440\u043e\u0432\u043d\u0430", "\u041f\u0440\u0438\u0442\u044b\u0446\u043a\u0438\u0439 \u041a\u043e\u043d\u0441\u0442\u0430\u043d\u0442\u0438\u043d \u0410\u043d\u0434\u0440\u0435\u0435\u0432\u0438\u0447"),
            listOf("Новик Татьяна Сергеевна", "\u0411\u043e\u0440\u0442\u043d\u0438\u043a \u0410\u043b\u0435\u043a\u0441\u0430\u043d\u0434\u0440 \u0412\u043b\u0430\u0434\u0438\u043c\u0438\u0440\u043e\u0432\u0438\u0447", "\u0427\u0435\u0440\u0432\u044f\u043a\u043e\u0432 \u0418\u0433\u043e\u0440\u044c \u0421\u0435\u0440\u0433\u0435\u0435\u0432\u0438\u0447"),
            listOf("Павлович Игорь Анатольевич", "\u0422\u043a\u0430\u0447\u0435\u043d\u043a\u043e \u0415\u0432\u0433\u0435\u043d\u0438\u044f \u0410\u043d\u0430\u0442\u043e\u043b\u044c\u0435\u0432\u043d\u0430", "\u041e\u0441\u0442\u0440\u043e\u0432\u0441\u043a\u0430\u044f \u041d\u0430\u0442\u0430\u043b\u044c\u044f \u0418\u0433\u043e\u0440\u0435\u0432\u043d\u0430"),
            listOf("Семенова Кристина Дмитриевна", "\u042f\u043a\u0443\u0431\u043e\u0432\u0438\u0447 \u0412\u043b\u0430\u0434\u0438\u0441\u043b\u0430\u0432 \u041e\u043b\u0435\u0433\u043e\u0432\u0438\u0447", "\u041a\u0440\u0443\u043a \u0412\u0438\u043a\u0442\u043e\u0440\u0438\u044f \u041f\u0430\u0432\u043b\u043e\u0432\u043d\u0430"),
            listOf("Уласевич Николай Олегович", "\u0424\u0435\u0434\u043e\u0440\u043e\u0432\u0438\u0447 \u0410\u043d\u0434\u0440\u0435\u0439 \u0412\u0430\u0441\u0438\u043b\u044c\u0435\u0432\u0438\u0447", "\u041a\u0443\u0447\u0435\u0440\u044f\u0432\u0430\u044f \u0415\u043a\u0430\u0442\u0435\u0440\u0438\u043d\u0430 \u0414\u043c\u0438\u0442\u0440\u0438\u0435\u0432\u043d\u0430"),
            listOf("\u0411\u0435\u043b\u043e\u0443\u0441 \u0410\u0440\u0442\u0451\u043c \u0412\u0430\u043b\u0435\u0440\u044c\u0435\u0432\u0438\u0447", "\u0413\u0430\u043b\u0438\u043d\u0441\u043a\u0430\u044f \u0421\u043e\u0444\u0438\u044f \u041c\u0430\u043a\u0441\u0438\u043c\u043e\u0432\u043d\u0430", "\u0414\u0430\u043d\u0438\u043b\u043e\u0432\u0438\u0447 \u041c\u0430\u0440\u0438\u043d\u0430 \u0418\u0433\u043e\u0440\u0435\u0432\u043d\u0430"),
        )
        g4.forEachIndexed { idx, gid ->
            val base = 4_000_000 + idx * 100
            g4StudentNames[idx].forEachIndexed { i, name ->
                ensureStudent("%07d".format(base + 1 + i), name, gid)
            }
        }

        // Демо-аккаунт: 4-8 ПОИБМС
        ensureStudent("9400888", "Морозов Василий Павлович", g4[7])

        fun notif(ticket: String, title: String, msg: String, important: Boolean) {
            val userId = UsersTable.selectAll().where { UsersTable.studentTicket eq ticket }.singleOrNull()
                ?.get(UsersTable.id) ?: return
            val exists = NotificationsTable.selectAll().where {
                (NotificationsTable.userId eq userId) and (NotificationsTable.title eq title)
            }.singleOrNull()
            if (exists != null) return
            NotificationsTable.insert {
                it[NotificationsTable.userId] = userId
                it[NotificationsTable.title] = title
                it[NotificationsTable.message] = msg
                it[NotificationsTable.createdAt] = java.time.Instant.now()
                it[NotificationsTable.isImportant] = important
            }
        }
        notif("9400888", "Пропуск занятия", "Отмечено отсутствие на лекции «История Беларуси» без уважительной причины.", true)
        notif("9400888", "Перенос пары", "Занятие «Компьютерные сети» перенесено на пятницу, ауд. 205-1 (уточните у преподавателя).", false)
        notif("teacher", "Новый запрос на перенос", "Администратору отправлены запросы на перенос — проверьте раздел расписания.", false)
    }

    /** Старый логин `student` → демо-билет `9400888`, если новый билет ещё не занят. */
    private fun migrateLegacyStudentTicket() {
        val legacy = UsersTable.selectAll().where { UsersTable.studentTicket eq "student" }.singleOrNull()
            ?: return
        val taken = UsersTable.selectAll().where { UsersTable.studentTicket eq "9400888" }.singleOrNull() != null
        if (taken) return
        val legacyId = legacy[UsersTable.id]
        UsersTable.update({ UsersTable.id eq legacyId }) {
            it[UsersTable.studentTicket] = "9400888"
        }
    }
}
