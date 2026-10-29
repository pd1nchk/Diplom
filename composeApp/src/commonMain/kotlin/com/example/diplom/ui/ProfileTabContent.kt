package com.example.diplom.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.api.UserProfileDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.domain.UserRole
import com.example.diplom.schedule.ScheduleRepository
import com.example.diplom.state.UserStateRepository
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.decodeImageFromBytes
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.web.pageVerticalScroll
import com.example.diplom.ui.platform.rememberImagePicker
import kotlinx.coroutines.launch

private val ProfileContentMaxWidth = 480.dp
private val ProfilePhotoButtonMaxWidth = 320.dp

@Composable
fun ProfileTabContent(
    role: UserRole,
    fullName: String,
    studentTicket: String,
) {
    val userStateRepository = remember { UserStateRepository() }
    val scheduleRepo = remember { ScheduleRepository() }
    val attendanceRepo = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()

    var profileImageBytes by remember(studentTicket) { mutableStateOf<ByteArray?>(null) }
    var academicGroupLabel by remember(studentTicket) { mutableStateOf<String?>(null) }
    var apiProfile by remember(studentTicket) { mutableStateOf<UserProfileDto?>(null) }
    var teacherGroups by remember(studentTicket) { mutableStateOf<List<TeacherGroupCourseDto>>(emptyList()) }
    var profilePhone by remember(studentTicket) { mutableStateOf("") }
    var loading by remember(studentTicket) { mutableStateOf(true) }

    LaunchedEffect(studentTicket, role) {
        loading = true
        val state = userStateRepository.load(studentTicket)
        val localHex = AppState.getProfileImageHex(studentTicket)
        val serverHex = state?.profileImageHex

        val dto = runCatching { attendanceRepo.profile(studentTicket) }.getOrNull()
        apiProfile = dto
        profilePhone = state?.profilePhone.orEmpty().ifBlank { dto?.phone.orEmpty() }

        val apiPhoto = dto?.photoHex
        profileImageBytes = when {
            !apiPhoto.isNullOrBlank() -> apiPhoto.hexToByteArray()
            !serverHex.isNullOrBlank() -> serverHex.hexToByteArray()
            !localHex.isNullOrBlank() -> localHex.hexToByteArray()
            else -> null
        }

        if (serverHex.isNullOrBlank() && !localHex.isNullOrBlank()) {
            runCatching {
                userStateRepository.save(studentTicket, (state ?: com.example.diplom.api.UserStateDto()).copy(profileImageHex = localHex))
            }
        }

        if (role == UserRole.STUDENT) {
            runCatching { scheduleRepo.weekForUser(studentTicket) }
                .onSuccess { academicGroupLabel = it.groupName }
                .onFailure { academicGroupLabel = null }
            teacherGroups = emptyList()
        } else if (role == UserRole.TEACHER) {
            academicGroupLabel = null
            teacherGroups = runCatching { attendanceRepo.teacherGroups(studentTicket) }.getOrDefault(emptyList())
        } else {
            academicGroupLabel = null
            teacherGroups = emptyList()
        }
        loading = false
    }

    var pendingCropBytes by remember(studentTicket) { mutableStateOf<ByteArray?>(null) }

    fun applyProfilePhoto(bytes: ByteArray) {
        scope.launch {
            val hex = bytes.toHexString()
            AppState.setProfileImageHex(studentTicket, hex)
            profileImageBytes = bytes
            runCatching {
                val existing = userStateRepository.load(studentTicket) ?: com.example.diplom.api.UserStateDto()
                userStateRepository.save(studentTicket, existing.copy(profileImageHex = hex))
            }
        }
    }

    val profileImage = profileImageBytes?.let { decodeImageFromBytes(it) }
    val pickImage = rememberImagePicker { bytes ->
        pendingCropBytes = bytes
    }

    pendingCropBytes?.let { rawBytes ->
        ProfilePhotoCropDialog(
            sourceBytes = rawBytes,
            onDismiss = { pendingCropBytes = null },
            onConfirm = { cropped ->
                pendingCropBytes = null
                applyProfilePhoto(cropped)
            },
        )
    }

    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val accent = themedAccent()

    Column(
        modifier = Modifier
            .pageVerticalScroll()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Мой профиль",
            modifier = Modifier
                .widthIn(max = ProfileContentMaxWidth)
                .fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator(color = accent)
            return@Column
        }

        Box(
            modifier = Modifier
                .widthIn(max = ProfileContentMaxWidth)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(onClick = pickImage),
                contentAlignment = Alignment.Center,
            ) {
                if (profileImage != null) {
                    Image(
                        bitmap = profileImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = fullName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = pickImage,
            modifier = Modifier
                .widthIn(max = ProfilePhotoButtonMaxWidth)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = if (isWebComposeClient()) Icons.Default.PhotoCamera else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (profileImage != null) "Изменить фото профиля" else "Добавить фото профиля",
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (isWebComposeClient()) {
            Text(
                text = "JPG, PNG или WEBP",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            modifier = Modifier
                .widthIn(max = ProfileContentMaxWidth)
                .fillMaxWidth(),
            fillAlpha = if (isLightUi()) 0.78f else 0.1f,
            borderAlpha = if (isLightUi()) 0.35f else 0.12f,
        ) {
            ProfileField("ФИО", fullName)

            when (role) {
                UserRole.TEACHER -> TeacherProfileDetails(
                    ticket = studentTicket,
                    profile = apiProfile,
                    phone = profilePhone,
                    teacherGroups = teacherGroups,
                )
                UserRole.ADMIN -> {
                    ProfileField("Роль", "Администратор")
                    ProfileField("Логин", studentTicket)
                    apiProfile?.phone?.takeIf { it.isNotBlank() }?.let { ProfileField("Телефон", it) }
                    profilePhone.takeIf { it.isNotBlank() }?.let { ProfileField("Телефон (настройки)", it) }
                }
                UserRole.STUDENT -> {
                    ProfileField("Роль", "Студент")
                    ProfileField("Номер студенческого билета", studentTicket)
                    val label = academicGroupLabel
                    if (!label.isNullOrBlank()) {
                        ProfileField("Учебная группа", label)
                        parseGroupCourseAndIndex(label)?.let { (course, index) ->
                            ProfileField("Курс", course.toString())
                            ProfileField("Номер группы", index.toString())
                        }
                    }
                    apiProfile?.facultyName?.let { ProfileField("Факультет", it) }
                    apiProfile?.specialtyName?.let { ProfileField("Специальность", it) }
                    apiProfile?.phone?.takeIf { it.isNotBlank() }?.let { ProfileField("Телефон", it) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TeacherProfileDetails(
    ticket: String,
    profile: UserProfileDto?,
    phone: String,
    teacherGroups: List<TeacherGroupCourseDto>,
) {
    ProfileField("Роль", "Преподаватель")
    ProfileField("Логин", ticket)
    profile?.degree?.takeIf { it.isNotBlank() }?.let { ProfileField("Учёная степень / звание", it) }
    profile?.departmentName?.takeIf { it.isNotBlank() }?.let { ProfileField("Кафедра", it) }

    val displayPhone = phone.ifBlank { profile?.phone.orEmpty() }
    if (displayPhone.isNotBlank()) {
        ProfileField("Телефон", displayPhone)
    }

    val courses = teacherGroups.map { it.courseName }.distinct().sorted()
    if (courses.isNotEmpty()) {
        ProfileField("Дисциплины", courses.joinToString(", "))
    }

    val groups = teacherGroups.map { it.groupName }.distinct().sorted()
    if (groups.isNotEmpty()) {
        ProfileField("Группы", groups.joinToString(", "))
        ProfileField("Всего групп", groups.size.toString())
    }
}

@Composable
private fun ProfileField(label: String, value: String) {
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { byte ->
    byte.toInt().and(0xFF).toString(16).padStart(2, '0')
}

private fun String.hexToByteArray(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun parseGroupCourseAndIndex(groupLabel: String): Pair<Int, Int>? {
    val m = Regex("""(\d+)-(\d+)""").find(groupLabel) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}
