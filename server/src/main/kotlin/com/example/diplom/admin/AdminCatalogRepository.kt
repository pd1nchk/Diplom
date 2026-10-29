package com.example.diplom.admin

import com.example.diplom.api.AdminCatalogSnapshotDto
import com.example.diplom.api.DepartmentDto
import com.example.diplom.api.FacultyDto
import com.example.diplom.api.ForgotPasswordAdminItemDto
import com.example.diplom.api.SpecialtyDto
import com.example.diplom.api.StudentAdminDto
import com.example.diplom.api.TeacherAdminDto
import com.example.diplom.db.DepartmentsTable
import com.example.diplom.db.FacultiesTable
import com.example.diplom.db.GroupsTable
import com.example.diplom.db.PasswordResetRequestsTable
import com.example.diplom.db.SpecialtiesTable
import com.example.diplom.db.StudentsTable
import com.example.diplom.db.TeachersTable
import com.example.diplom.db.UsersTable
import com.example.diplom.domain.UserRole
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.update
import com.example.diplom.auth.PasswordHasher

class AdminCatalogRepository {
    fun snapshot(): AdminCatalogSnapshotDto = transaction {
        AdminCatalogSnapshotDto(
            faculties = FacultiesTable.selectAll().map { FacultyDto(it[FacultiesTable.id], it[FacultiesTable.name]) },
            departments = DepartmentsTable.selectAll().map {
                DepartmentDto(it[DepartmentsTable.id], it[DepartmentsTable.facultyId], it[DepartmentsTable.name])
            },
            specialties = SpecialtiesTable.selectAll().map {
                SpecialtyDto(
                    id = it[SpecialtiesTable.id],
                    facultyId = it[SpecialtiesTable.facultyId],
                    code = it[SpecialtiesTable.code],
                    name = it[SpecialtiesTable.name],
                    profile = it[SpecialtiesTable.profile],
                )
            },
            teachers = (UsersTable innerJoin TeachersTable)
                .selectAll()
                .map {
                    TeacherAdminDto(
                        id = it[TeachersTable.userId],
                        fullName = it[UsersTable.fullName],
                        degree = it[TeachersTable.degree],
                        departmentId = it[TeachersTable.departmentId],
                    )
                },
            students = (UsersTable innerJoin StudentsTable innerJoin GroupsTable)
                .selectAll()
                .map {
                    StudentAdminDto(
                        id = it[StudentsTable.userId],
                        fullName = it[UsersTable.fullName],
                        course = it[GroupsTable.course],
                        groupNumber = it[GroupsTable.groupNumber],
                        specialtyId = it[GroupsTable.specialtyId] ?: 0L,
                    )
                },
        )
    }

    fun addFaculty(name: String) = transaction {
        FacultiesTable.insert { it[FacultiesTable.name] = name.trim() }
    }

    fun deleteFaculty(id: Long) = transaction {
        FacultiesTable.deleteWhere { FacultiesTable.id eq id }
    }

    fun addDepartment(facultyId: Long, name: String) = transaction {
        DepartmentsTable.insert {
            it[DepartmentsTable.facultyId] = facultyId
            it[DepartmentsTable.name] = name.trim()
        }
    }

    fun deleteDepartment(id: Long) = transaction {
        DepartmentsTable.deleteWhere { DepartmentsTable.id eq id }
    }

    fun addSpecialty(facultyId: Long, code: String, name: String, profile: String) = transaction {
        SpecialtiesTable.insert {
            it[SpecialtiesTable.facultyId] = facultyId
            it[SpecialtiesTable.code] = code.trim()
            it[SpecialtiesTable.name] = name.trim()
            it[SpecialtiesTable.profile] = profile.trim()
        }
    }

    fun deleteSpecialty(id: Long) = transaction {
        SpecialtiesTable.deleteWhere { SpecialtiesTable.id eq id }
    }

    fun addTeacher(fullName: String, degree: String, departmentId: Long) = transaction {
        val trimmedName = fullName.trim()
        val uid = UsersTable.insert {
            it[UsersTable.studentTicket] = "tch${System.nanoTime()}"
            it[UsersTable.passwordHash] = PasswordHasher.hash("1234")
            it[UsersTable.fullName] = trimmedName
            it[UsersTable.role] = UserRole.TEACHER.name
            it[UsersTable.isActive] = true
        } get UsersTable.id
        TeachersTable.insert {
            it[TeachersTable.userId] = uid
            it[TeachersTable.departmentId] = departmentId
            it[TeachersTable.degree] = degree.trim()
        }
    }

    fun deleteTeacher(userId: Long) = transaction {
        TeachersTable.deleteWhere { TeachersTable.userId eq userId }
        UsersTable.deleteWhere { UsersTable.id eq userId }
    }

    fun addStudent(
        fullName: String,
        studentTicket: String,
        initialPassword: String,
        course: Int,
        groupNumber: Int,
        specialtyId: Long,
    ) = transaction {
        val normalizedTicket = studentTicket.trim()
        require(normalizedTicket.isNotBlank()) { "Билет/логин не указан" }
        require(initialPassword.length >= 4) { "Пароль должен быть не короче 4 символов" }

        val userId = UsersTable.selectAll()
            .where { UsersTable.studentTicket eq normalizedTicket }
            .singleOrNull()
            ?.get(UsersTable.id)
            ?: (UsersTable.insert {
                it[UsersTable.studentTicket] = normalizedTicket
                it[UsersTable.passwordHash] = PasswordHasher.hash(initialPassword)
                it[UsersTable.fullName] = fullName.trim()
                it[UsersTable.role] = "STUDENT"
                it[UsersTable.isActive] = true
            } get UsersTable.id)

        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.fullName] = fullName.trim()
            it[UsersTable.passwordHash] = PasswordHasher.hash(initialPassword)
            it[UsersTable.role] = "STUDENT"
            it[UsersTable.isActive] = true
        }

        val groupId = GroupsTable.selectAll()
            .where {
                (GroupsTable.course eq course) and
                    (GroupsTable.groupNumber eq groupNumber) and
                    (GroupsTable.specialtyId eq specialtyId)
            }
            .singleOrNull()
            ?.get(GroupsTable.id)
            ?: (GroupsTable.insert {
                it[GroupsTable.name] = "$course-$groupNumber"
                it[GroupsTable.specialtyId] = specialtyId
                it[GroupsTable.course] = course
                it[GroupsTable.groupNumber] = groupNumber
                it[GroupsTable.shift] = if (course >= 3) "FIRST" else "SECOND"
            } get GroupsTable.id)

        val existingStudent = StudentsTable.selectAll().where { StudentsTable.userId eq userId }.singleOrNull()
        if (existingStudent == null) {
            StudentsTable.insert {
                it[StudentsTable.userId] = userId
                it[StudentsTable.groupId] = groupId
            }
        } else {
            StudentsTable.update({ StudentsTable.userId eq userId }) {
                it[StudentsTable.groupId] = groupId
            }
        }
    }

    fun deleteStudent(userId: Long) = transaction {
        StudentsTable.deleteWhere { StudentsTable.userId eq userId }
        UsersTable.deleteWhere { UsersTable.id eq userId }
    }

    fun createForgotPasswordRequest(studentTicket: String, contact: String, comment: String) = transaction {
        val normalized = studentTicket.trim()
        require(normalized.isNotBlank()) { "Укажите билет или логин" }
        val user = UsersTable.selectAll().where { UsersTable.studentTicket eq normalized }.singleOrNull()
            ?: throw IllegalArgumentException("Пользователь с таким билетом или логином не найден")
        PasswordResetRequestsTable.insert {
            it[PasswordResetRequestsTable.userId] = user[UsersTable.id]
            it[PasswordResetRequestsTable.studentTicket] = normalized
            it[PasswordResetRequestsTable.contact] = contact.trim()
            it[PasswordResetRequestsTable.comment] = comment.trim()
            it[PasswordResetRequestsTable.status] = "PENDING"
            it[PasswordResetRequestsTable.createdAt] = CurrentTimestamp
        }
    }

    fun listForgotPasswordRequests(status: String? = "PENDING"): List<ForgotPasswordAdminItemDto> = transaction {
        PasswordResetRequestsTable
            .selectAll()
            .let { query ->
                if (status.isNullOrBlank()) query else query.where { PasswordResetRequestsTable.status eq status }
            }
            .map {
                ForgotPasswordAdminItemDto(
                    id = it[PasswordResetRequestsTable.id],
                    studentTicket = it[PasswordResetRequestsTable.studentTicket],
                    contact = it[PasswordResetRequestsTable.contact],
                    comment = it[PasswordResetRequestsTable.comment],
                    status = it[PasswordResetRequestsTable.status],
                    createdAtIso = it[PasswordResetRequestsTable.createdAt].toString(),
                    resolvedAtIso = it[PasswordResetRequestsTable.resolvedAt]?.toString(),
                )
            }
    }

    fun resolveForgotPasswordRequest(adminTicket: String, requestId: Long, adminComment: String) = transaction {
        val admin = UsersTable.selectAll()
            .where { UsersTable.studentTicket eq adminTicket }
            .singleOrNull()
            ?: return@transaction false
        if (admin[UsersTable.role] != "ADMIN") return@transaction false

        val request = PasswordResetRequestsTable.selectAll()
            .where {
                (PasswordResetRequestsTable.id eq requestId) and (PasswordResetRequestsTable.status eq "PENDING")
            }
            .singleOrNull()
            ?: return@transaction false

        val studentTicket = request[PasswordResetRequestsTable.studentTicket]
        val reset = UsersTable.update({ UsersTable.studentTicket eq studentTicket }) {
            it[UsersTable.passwordHash] = PasswordHasher.hash(studentTicket)
        }
        if (reset == 0) return@transaction false

        val updated = PasswordResetRequestsTable.update({
            (PasswordResetRequestsTable.id eq requestId) and (PasswordResetRequestsTable.status eq "PENDING")
        }) {
            it[status] = "RESOLVED"
            it[resolvedAt] = CurrentTimestamp
            it[PasswordResetRequestsTable.adminId] = admin[UsersTable.id]
            it[PasswordResetRequestsTable.adminTicket] = adminTicket
            it[PasswordResetRequestsTable.adminComment] = adminComment.trim().ifBlank {
                "Пароль сброшен до номера билета"
            }
        }
        updated > 0
    }

    fun seedIfEmpty() = transaction {
        if (SpecialtiesTable.selectAll().limit(1).count() > 0) return@transaction

        val fitId = FacultiesTable.selectAll().firstOrNull()?.get(FacultiesTable.id)
            ?: (FacultiesTable.insert { it[name] = "ФИТ (Информационные технологии)" } get FacultiesTable.id)

        val deptRows = DepartmentsTable.selectAll().where { DepartmentsTable.facultyId eq fitId }.toList()
        val depIst: Long
        val depWeb: Long
        val depSe: Long
        if (deptRows.isEmpty()) {
            depIst = DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра информационных систем и технологий" } get DepartmentsTable.id
            depWeb = DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра информатики и веб-дизайна" } get DepartmentsTable.id
            depSe = DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра программной инженерии" } get DepartmentsTable.id
            DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра физики" }
            DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра инженерной графики" }
            DepartmentsTable.insert { it[facultyId] = fitId; it[name] = "Кафедра высшей математики" }
        } else {
            val ids = deptRows.map { it[DepartmentsTable.id] }
            depIst = ids[0]
            depWeb = ids.getOrElse(1) { ids[0] }
            depSe = ids.getOrElse(2) { ids[0] }
        }

        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "6-05-0611-01"
            it[name] = "Информационные системы и технологии"
            it[profile] = "Информационные системы; Цифровой дизайн"
        }

        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "6-05-0612-01"
            it[name] = "Программная инженерия"
            it[profile] = "Программное обеспечение информационных технологий"
        }

        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "1-40 05 01"
            it[name] = "Информационные системы и технологии"
            it[profile] = "Классический профиль"
        }
        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "1-98 01 03"
            it[name] = "ПО информационной безопасности мобильных систем"
            it[profile] = "ПОИБМС"
        }
        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "1-40 01 01"
            it[name] = "ПО информационных технологий"
            it[profile] = "ПОИТ"
        }
        SpecialtiesTable.insert {
            it[facultyId] = fitId
            it[code] = "1-47 01 02"
            it[name] = "Дизайн электронных и веб-изданий"
            it[profile] = "Дизайн"
        }

        if (TeachersTable.selectAll().limit(1).count() == 0L) {
            fun seedTeacher(fullName: String, degree: String, departmentId: Long) {
                val uid = UsersTable.insert {
                    it[UsersTable.studentTicket] = "seed_tch_${fullName.hashCode()}"
                    it[UsersTable.passwordHash] = PasswordHasher.hash("1234")
                    it[UsersTable.fullName] = fullName
                    it[UsersTable.role] = UserRole.TEACHER.name
                    it[UsersTable.isActive] = true
                } get UsersTable.id
                TeachersTable.insert {
                    it[TeachersTable.userId] = uid
                    it[TeachersTable.departmentId] = departmentId
                    it[TeachersTable.degree] = degree
                }
            }
            seedTeacher("Сапожников Петр Петрович", "к.т.н., доцент", depIst)
            seedTeacher("Жданов Игорь Николаевич", "старший преподаватель", depIst)
            seedTeacher("Гуров Андрей Васильевич", "магистр, ассистент", depSe)
            seedTeacher("Климова Мария Олеговна", "к.п.н., доцент", depWeb)
        }
    }
}
