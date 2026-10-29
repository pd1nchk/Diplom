package com.example.diplom.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.TeacherLessonCheckDto
import com.example.diplom.api.SessionRosterRowDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.domain.UserRole
import com.example.diplom.nfc.NfcScanUiState
import com.example.diplom.nfc.NfcScanViewModel
import com.example.diplom.nfc.NfcStudentPhase
import com.example.diplom.nfc.NfcTeacherPhase
import com.example.diplom.nfc.NfcUidNormalizer
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.GradientPrimaryButton
import com.example.diplom.ui.design.MeshGradientBackground
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedCyan
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.web.WebScrollHost
import com.example.diplom.ui.platform.decodeImageFromBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    studentTicket: String,
    userRole: UserRole,
) {
    val snackHost = remember { SnackbarHostState() }

    when (userRole) {
        UserRole.ADMIN -> {
            Box(Modifier.fillMaxSize()) {
                MeshGradientBackground(Modifier.fillMaxSize())
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackHost) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "NFC — администратор",
                                    color = DiplomColors.TextPrimary,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = DiplomColors.TextPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DiplomColors.DarkSurface.copy(alpha = 0.94f),
                                titleContentColor = DiplomColors.TextPrimary,
                                navigationIconContentColor = DiplomColors.TextPrimary,
                            ),
                        )
                    },
                ) { padding ->
                    if (isWebComposeClient()) {
                        WebScrollHost(Modifier.fillMaxSize().padding(padding)) {
                            AdminNfcRedirectContent(
                                padding = PaddingValues(0.dp),
                                onOpenAdminNfc = {
                                    AppState.openAdminNfcTab = true
                                    AppState.overlayScreen = OverlayScreen.ADMIN_CENTER
                                },
                            )
                        }
                    } else {
                        AdminNfcRedirectContent(
                            padding = padding,
                            onOpenAdminNfc = {
                                AppState.openAdminNfcTab = true
                                AppState.overlayScreen = OverlayScreen.ADMIN_CENTER
                            },
                        )
                    }
                }
            }
        }
        else -> {
            val vm = remember(studentTicket, userRole) {
                NfcScanViewModel(AttendanceRepository(), studentTicket, userRole)
            }
            DisposableEffect(vm) {
                onDispose { vm.dispose() }
            }

            val ui by vm.ui.collectAsState()

            LaunchedEffect(ui.snackbar) {
                val t = ui.snackbar ?: return@LaunchedEffect
                snackHost.showSnackbar(t)
                vm.consumeSnackbar()
            }

            Box(Modifier.fillMaxSize()) {
                MeshGradientBackground(Modifier.fillMaxSize())
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackHost) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    if (userRole == UserRole.TEACHER) "NFC — преподаватель" else "NFC — студент",
                                    color = DiplomColors.TextPrimary,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = DiplomColors.TextPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DiplomColors.DarkSurface.copy(alpha = 0.94f),
                                titleContentColor = DiplomColors.TextPrimary,
                                navigationIconContentColor = DiplomColors.TextPrimary,
                            ),
                        )
                    },
                ) { padding ->
                    val body: @Composable () -> Unit = {
                        if (userRole == UserRole.TEACHER) {
                            TeacherNfcContent(
                                padding = if (isWebComposeClient()) PaddingValues(0.dp) else padding,
                                ui = ui,
                                onRoomUid = vm::onRoomUidChange,
                                onCheck = vm::requestTeacherLessonCheck,
                                onRetryNoLesson = vm::checkTeacherLessonAgain,
                                onStopSession = vm::stopTeacherSession,
                            )
                        } else {
                            StudentNfcContent(
                                padding = if (isWebComposeClient()) PaddingValues(0.dp) else padding,
                                ui = ui,
                                onUidChange = vm::onRoomUidChange,
                                onManualCheck = vm::studentManualOpenConfirm,
                            )
                        }
                    }
                    if (isWebComposeClient()) {
                        WebScrollHost(Modifier.fillMaxSize().padding(padding)) { body() }
                    } else {
                        body()
                    }
                }

                if (ui.showTeacherStartDialog && ui.teacherCheck != null) {
                    TeacherStartDialog(
                        lesson = ui.teacherCheck!!,
                        onDismiss = vm::dismissTeacherStartDialog,
                        onStart = vm::startSessionFromDialog,
                    )
                }

                if (ui.showStudentConfirm && ui.pendingSession != null && ui.pendingUid != null) {
                    StudentConfirmDialog(
                        session = ui.pendingSession!!,
                        uid = ui.pendingUid!!,
                        onDismiss = vm::dismissStudentConfirm,
                        onConfirm = vm::confirmStudentPresence,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminNfcRedirectContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    onOpenAdminNfc: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Привязка NFC к кабинету",
            style = MaterialTheme.typography.titleLarge,
            color = themedTextPrimary(),
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Выберите аудиторию, поднесите метку и нажмите «Привязать метку к кабинету» — это делается в разделе «Админ-центр» → NFC.",
            color = themedTextSecondary(),
            style = MaterialTheme.typography.bodyMedium,
        )
        GradientPrimaryButton(
            text = "Открыть админ-центр → NFC",
            onClick = onOpenAdminNfc,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TeacherNfcContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    ui: NfcScanUiState,
    onRoomUid: (String) -> Unit,
    onCheck: () -> Unit,
    onRetryNoLesson: () -> Unit,
    onStopSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(16.dp),
    ) {
        val title = when (ui.teacherPhase) {
            NfcTeacherPhase.CheckingLesson, NfcTeacherPhase.StartingSession -> "Проверка…"
            NfcTeacherPhase.NoLesson -> "Нет пары сейчас"
            NfcTeacherPhase.Error -> "Ошибка"
            NfcTeacherPhase.SessionActive -> "Сессия NFC"
            NfcTeacherPhase.ReadyForSession -> "Пара найдена"
            NfcTeacherPhase.Idle -> "Преподаватель"
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = if (ui.teacherPhase == NfcTeacherPhase.Error) themedError() else themedTextPrimary(),
        )
        Spacer(Modifier.height(8.dp))
        when (ui.teacherPhase) {
            NfcTeacherPhase.NoLesson -> {
                TeacherNoLessonCard(onRetry = onRetryNoLesson)
            }
            else -> {
                val sub = when {
                    ui.teacherPhase == NfcTeacherPhase.Error -> ui.teacherError.orEmpty()
                    ui.teacherHint.isNotBlank() -> ui.teacherHint
                    else -> "Поднесите метку аудитории к телефону: UID подставится, пара найдётся по метке, сессия откроется сама. Или введите UID и нажмите «Проверить»."
                }
                Text(
                    text = sub,
                    color = if (ui.teacherPhase == NfcTeacherPhase.Error) themedError() else themedTextSecondary(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.roomUidDraft,
            onValueChange = onRoomUid,
            label = { Text("NFC аудитории (необязательно)", color = themedTextSecondary()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = themedTextPrimary(),
                unfocusedTextColor = themedTextPrimary(),
                focusedBorderColor = themedAccent(),
                unfocusedBorderColor = themedBorder(),
                focusedLabelColor = themedAccent(),
                unfocusedLabelColor = themedTextSecondary(),
                cursorColor = themedCyan(),
            ),
        )
        Spacer(Modifier.height(8.dp))
        GradientPrimaryButton(
            text = "Проверить: есть ли у меня здесь пара",
            onClick = onCheck,
            enabled = ui.teacherPhase != NfcTeacherPhase.CheckingLesson &&
                ui.teacherPhase != NfcTeacherPhase.StartingSession,
            modifier = Modifier.fillMaxWidth(),
        )

        ui.activeSession?.let { session ->
            Spacer(Modifier.height(20.dp))
            TeacherSessionPanel(
                session = session,
                roster = ui.roster,
                remainingSec = ui.sessionRemainingSeconds,
                onStop = onStopSession,
            )
        }
    }
}

@Composable
private fun TeacherNoLessonCard(onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        fillAlpha = 0.12f,
        borderAlpha = 0.18f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.EventBusy, contentDescription = null, tint = themedAccent(), modifier = Modifier.size(28.dp))
                Text(
                    "Сейчас нет активной пары",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = themedTextPrimary(),
                )
            }
            Text(
                "По расписанию и метке аудитории занятие не найдено. Проверьте время, предмет или введите UID метки NFC-аудитории и нажмите «Проверить» ещё раз.",
                style = MaterialTheme.typography.bodyMedium,
                color = themedTextSecondary(),
            )
            GradientPrimaryButton(
                text = "Повторить проверку",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TeacherSessionPanel(
    session: AttendanceSessionDto,
    roster: List<SessionRosterRowDto>,
    remainingSec: Long?,
    onStop: () -> Unit,
) {
    val total = roster.size.coerceAtLeast(1)
    val marked = roster.count { it.present }
    val progress = marked.toFloat() / total.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Активная сессия: ${session.subject} · ${session.groupName}",
            fontWeight = FontWeight.SemiBold,
            color = themedTextPrimary(),
        )
        Text(
            "Аудитория: ${session.roomName ?: "—"} · до ${session.expiresAtIso}",
            style = MaterialTheme.typography.bodySmall,
            color = themedTextSecondary(),
        )
        val rem = remainingSec
        if (rem != null) {
            val mm = (rem / 60).coerceAtLeast(0)
            val ss = (rem % 60).coerceAtLeast(0)
            Text(
                "Осталось: ${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                color = themedCyan(),
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                color = themedAccent(),
                trackColor = themedBorder().copy(alpha = 0.35f),
            )
            Text(
                "Отмечено NFC: $marked из $total",
                style = MaterialTheme.typography.bodySmall,
                color = themedTextSecondary(),
            )
        }
        GradientPrimaryButton(text = "Завершить отметку", onClick = onStop, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Список группы", style = MaterialTheme.typography.titleSmall, color = themedCyan())
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            items(roster, key = { it.studentTicket }) { row ->
                Text(
                    "${row.fullName} — ${if (row.present) "присутствует (NFC)" else "не отмечен"}",
                    color = if (row.present) DiplomColors.Success else themedTextSecondary(),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StudentNfcContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    ui: NfcScanUiState,
    onUidChange: (String) -> Unit,
    onManualCheck: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "nfcPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val title = when (ui.studentPhase) {
            NfcStudentPhase.Reading -> "Обработка…"
            NfcStudentPhase.Success -> "Готово"
            NfcStudentPhase.Error -> "Ошибка"
            else -> "NFC-сканер"
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = if (ui.studentPhase == NfcStudentPhase.Error) themedError() else themedTextPrimary(),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(2.dp, themedAccent().copy(alpha = 0.7f), CircleShape)
                .background(themedAccent().copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("NFC", style = MaterialTheme.typography.headlineSmall, color = themedAccent(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = ui.studentMessage,
            color = if (ui.studentPhase == NfcStudentPhase.Error) themedError() else themedTextSecondary(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.roomUidDraft,
            onValueChange = onUidChange,
            label = { Text("UID метки", color = themedTextSecondary()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = themedTextPrimary(),
                unfocusedTextColor = themedTextPrimary(),
                focusedBorderColor = themedAccent(),
                unfocusedBorderColor = themedBorder(),
                focusedLabelColor = themedAccent(),
                unfocusedLabelColor = themedTextSecondary(),
                cursorColor = themedCyan(),
            ),
        )
        Spacer(Modifier.height(8.dp))
        GradientPrimaryButton(text = "Проверить занятие", onClick = onManualCheck, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TeacherStartDialog(
    lesson: TeacherLessonCheckDto,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Занятие в этой аудитории") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lesson.subject, fontWeight = FontWeight.Bold)
                Text("Группа: ${lesson.groupName}", color = themedTextSecondary())
                Text("Аудитория: ${lesson.room} · ${lesson.timeRange}", color = themedTextSecondary())
                Text(
                    if (lesson.phase == "IN_PROGRESS") "Пара идёт сейчас." else "До начала пары меньше 30 минут (можно открыть NFC-сессию).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "После «Начать» сессия NFC открыта 20 минут: в журнале ячейки пустые (не отмечено), отметка «присутствует» — только после NFC студента.",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onStart) { Text("Начать отметку") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun StudentConfirmDialog(
    session: AttendanceSessionDto,
    uid: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val image = session.teacherPhotoHex?.let { runCatching { decodeImageFromBytes(it.hexToByteArray()) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердить присутствие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            modifier = Modifier
                                .height(56.dp)
                                .fillMaxWidth(0.22f)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = session.teacherName.firstOrNull()?.uppercase() ?: "П",
                            modifier = Modifier
                                .background(Color(0xFFE8ECF5), CircleShape)
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column {
                        Text("Преподаватель: ${session.teacherName}")
                        Text("Предмет: ${session.subject}")
                        Text("Группа: ${session.groupName}")
                        Text("Аудитория: ${session.roomName ?: "—"}")
                    }
                }
                Text(
                    "Метка: ${NfcUidNormalizer.canonicalColonUid(uid) ?: uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
                Text("Подтвердить присутствие на занятии?")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Подтвердить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun String.hexToByteArray(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
