package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class FacultyDto(
    val id: Long,
    val name: String,
)

@Serializable
data class DepartmentDto(
    val id: Long,
    val facultyId: Long,
    val name: String,
)

@Serializable
data class SpecialtyDto(
    val id: Long,
    val facultyId: Long,
    val code: String,
    val name: String,
    val profile: String,
)

@Serializable
data class TeacherAdminDto(
    val id: Long,
    val fullName: String,
    val degree: String,
    val departmentId: Long,
)

@Serializable
data class StudentAdminDto(
    val id: Long,
    val fullName: String,
    val course: Int,
    val groupNumber: Int,
    val specialtyId: Long,
)

@Serializable
data class AdminCatalogSnapshotDto(
    val faculties: List<FacultyDto> = emptyList(),
    val departments: List<DepartmentDto> = emptyList(),
    val specialties: List<SpecialtyDto> = emptyList(),
    val teachers: List<TeacherAdminDto> = emptyList(),
    val students: List<StudentAdminDto> = emptyList(),
)

@Serializable
data class CreateFacultyRequest(
    val name: String,
)

@Serializable
data class CreateDepartmentRequest(
    val facultyId: Long,
    val name: String,
)

@Serializable
data class CreateSpecialtyRequest(
    val facultyId: Long,
    val code: String,
    val name: String,
    val profile: String,
)

@Serializable
data class CreateTeacherRequest(
    val fullName: String,
    val degree: String,
    val departmentId: Long,
)

@Serializable
data class CreateStudentRequest(
    val fullName: String,
    val studentTicket: String,
    val initialPassword: String,
    val course: Int,
    val groupNumber: Int,
    val specialtyId: Long,
)
