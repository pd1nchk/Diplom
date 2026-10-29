package com.example.diplom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.PasswordResetPhoneConfirmRequest
import com.example.diplom.api.PasswordResetPhoneStartRequest
import com.example.diplom.auth.AuthRepository
import com.example.diplom.auth.AuthState
import com.example.diplom.ui.design.APP_DISPLAY_VERSION
import com.example.diplom.ui.design.FitBrandMark
import com.example.diplom.ui.design.GhostTextButton
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.GradientPrimaryButton
import com.example.diplom.ui.design.MeshGradientBackground
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedCyan
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedMutedText
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    state: AuthState,
    onLogin: (String, String) -> Unit,
    onForgotPassword: (String, String, String) -> Unit,
) {
    var studentTicket by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showResetCodeDialog by remember { mutableStateOf(false) }
    var resetTicket by remember { mutableStateOf("") }
    var forgotComment by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }
    var resetMaskedPhone by remember { mutableStateOf("") }
    var resetLoading by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val mutedText = themedMutedText()
    val accent = themedAccent()
    val cyan = themedCyan()
    val border = themedBorder()
    val errorColor = themedError()
    val light = isLightUi()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        focusedBorderColor = accent,
        unfocusedBorderColor = border,
        focusedLabelColor = accent,
        unfocusedLabelColor = textSecondary,
        cursorColor = cyan,
        focusedContainerColor = if (light) Color.White.copy(alpha = 0.42f) else Color.Transparent,
        unfocusedContainerColor = if (light) Color.White.copy(alpha = 0.30f) else Color.Transparent,
    )

    Surface(color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            MeshGradientBackground(Modifier.fillMaxSize())
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cardMax = maxWidth.coerceAtMost(440.dp)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = cardMax)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                FitBrandMark(showTagline = true)
                Spacer(Modifier.height(32.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    fillAlpha = 0.08f,
                    borderAlpha = 0.12f,
                ) {
                    Text(
                        text = "Добро пожаловать",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Современная система расписания и посещаемости",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = studentTicket,
                        onValueChange = { v -> studentTicket = v.filter { ch -> ch.isLetterOrDigit() }.take(32) },
                        label = { Text("Билет или логин", color = textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль", color = textSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            fillAlpha = 0.12f,
                            borderAlpha = 0.15f,
                            contentPadding = 12.dp,
                        ) {
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = errorColor,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    GradientPrimaryButton(
                        text = "Войти",
                        onClick = { onLogin(studentTicket.trim(), password) },
                        enabled = studentTicket.isNotBlank() && password.isNotBlank() && !state.isLoading,
                        loading = state.isLoading,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostTextButton(
                        text = "Забыл пароль",
                        onClick = {
                            resetTicket = studentTicket
                            resetMessage = null
                            resetCode = ""
                            resetNewPassword = ""
                            showForgotDialog = true
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Language.entries.forEach { lang ->
                        val active = AppState.language == lang
                        Text(
                            text = lang.displayName,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) accent.copy(alpha = 0.16f) else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { AppState.language = lang }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (active) textPrimary else textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Версия $APP_DISPLAY_VERSION",
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedText,
                )
                }
            }
        }
    }
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Восстановление пароля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Введите логин или номер билета. Если в профиле есть телефон, на него будет отправлен код подтверждения.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = resetTicket,
                        onValueChange = { v -> resetTicket = v.filter { ch -> ch.isLetterOrDigit() }.take(32) },
                        label = { Text("Билет или логин") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            val ticket = resetTicket.trim()
                            if (ticket.isBlank()) {
                                resetMessage = "Введите билет или логин"
                                return@TextButton
                            }
                            resetLoading = true
                            resetMessage = null
                            scope.launch {
                                val result = authRepository.startPhonePasswordReset(
                                    PasswordResetPhoneStartRequest(ticket),
                                )
                                result.fold(
                                    onSuccess = { response ->
                                        resetMaskedPhone = response.maskedPhone
                                        resetCode = ""
                                        resetNewPassword = ""
                                        resetMessage = null
                                        showForgotDialog = false
                                        showResetCodeDialog = true
                                    },
                                    onFailure = {
                                        resetMessage = it.message
                                            ?: "Телефон не указан. Отправьте заявку администратору."
                                    },
                                )
                                resetLoading = false
                            }
                        },
                        enabled = !resetLoading,
                    ) {
                        Text("Отправить код на телефон")
                    }
                    Text(
                        "Если телефон не указан, отправьте заявку администратору. Администратор сбросит пароль до номера билета.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                    )
                    OutlinedTextField(
                        value = forgotComment,
                        onValueChange = { forgotComment = it.take(180) },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    resetMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if ("ошиб" in it.lowercase() || "не " in it.lowercase()) errorColor else textSecondary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onForgotPassword(resetTicket.trim(), "", forgotComment)
                        showForgotDialog = false
                        forgotComment = ""
                    },
                    enabled = resetTicket.isNotBlank() && !state.isLoading && !resetLoading,
                ) { Text("Заявка администратору") }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Отмена") }
            },
        )
    }
    if (showResetCodeDialog) {
        AlertDialog(
            onDismissRequest = { showResetCodeDialog = false },
            title = { Text("Введите код") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Демо-SMS с кодом 4321 отправлено на $resetMaskedPhone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = resetCode,
                        onValueChange = { resetCode = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Код подтверждения") },
                        singleLine = true,
                        isError = resetCode.isNotEmpty() && resetCode.length != 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = resetNewPassword,
                        onValueChange = { resetNewPassword = it },
                        label = { Text("Новый пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = resetNewPassword.isNotEmpty() && resetNewPassword.length < 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    resetMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if ("изменён" in it.lowercase()) textSecondary else errorColor,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ticket = resetTicket.trim()
                        when {
                            resetCode.length != 4 -> resetMessage = "Код должен состоять из 4 цифр"
                            resetNewPassword.length < 4 -> resetMessage = "Пароль должен быть не короче 4 символов"
                            else -> {
                                resetLoading = true
                                resetMessage = null
                                scope.launch {
                                    val result = authRepository.confirmPhonePasswordReset(
                                        PasswordResetPhoneConfirmRequest(
                                            studentTicket = ticket,
                                            code = resetCode,
                                            newPassword = resetNewPassword,
                                        ),
                                    )
                                    result.fold(
                                        onSuccess = {
                                            password = resetNewPassword
                                            resetMessage = "Пароль изменён. Теперь можно войти."
                                            showResetCodeDialog = false
                                        },
                                        onFailure = {
                                            resetMessage = it.message ?: "Не удалось изменить пароль"
                                        },
                                    )
                                    resetLoading = false
                                }
                            }
                        }
                    },
                    enabled = !resetLoading,
                ) { Text("Сменить пароль") }
            },
            dismissButton = {
                TextButton(onClick = { showResetCodeDialog = false }) { Text("Отмена") }
            },
        )
    }
}
