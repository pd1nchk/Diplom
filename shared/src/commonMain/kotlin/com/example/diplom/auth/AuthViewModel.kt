package com.example.diplom.auth

import com.example.diplom.api.AuthResponse
import com.example.diplom.api.ForgotPasswordRequest
import com.example.diplom.api.LoginRequest
import com.example.diplom.api.LogoutRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val authResponse: AuthResponse? = null,
)

private fun humanizeAuthError(raw: String): String {
    val t = raw.trim()
    if (t.startsWith("<!DOCTYPE", ignoreCase = true) ||
        t.startsWith("<html", ignoreCase = true) ||
        t.contains("Cannot POST", ignoreCase = true) ||
        t.contains("trying to proxy", ignoreCase = true)
    ) {
        return "Сервер не ответил как API. Запустите Ktor на порту 8080 и откройте веб-клиент с другого порта (Gradle, обычно http://localhost:8081). В адресной строке не должен быть тот же порт, что у API, если там не Ktor."
    }
    return t
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun login(studentTicket: String, password: String) {
        val ticket = studentTicket.trim()
        when {
            ticket.isBlank() ->
                _state.value = _state.value.copy(error = "Введите номер билета или логин", isLoading = false)
            password.isBlank() ->
                _state.value = _state.value.copy(error = "Введите пароль", isLoading = false)
            else -> {
                _state.value = _state.value.copy(isLoading = true, error = null)
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        repository.login(LoginRequest(ticket, password))
                    }
                    _state.value = result.fold(
                        onSuccess = { AuthState(authResponse = it, isLoading = false, error = null) },
                        onFailure = {
                            AuthState(
                                isLoading = false,
                                error = humanizeAuthError(it.message ?: "Не удалось войти"),
                            )
                        },
                    )
                }
            }
        }
    }

    fun forgotPassword(studentTicket: String, contact: String, comment: String) {
        val ticket = studentTicket.trim()
        when {
            ticket.isBlank() ->
                _state.value = _state.value.copy(error = "Введите номер билета или логин", isLoading = false)
            else -> {
                _state.value = _state.value.copy(isLoading = true, error = null)
                scope.launch {
                    val request = ForgotPasswordRequest(
                        studentTicket = ticket,
                        contact = contact.trim(),
                        comment = comment.trim(),
                    )
                    val result = repository.submitForgotPassword(request)
                    _state.value = result.fold(
                        onSuccess = {
                            _state.value.copy(
                                isLoading = false,
                                error = "Заявка отправлена администратору. Ожидайте новый пароль.",
                            )
                        },
                        onFailure = {
                            _state.value.copy(
                                isLoading = false,
                                error = it.message ?: "Не удалось отправить заявку администратору",
                            )
                        },
                    )
                }
            }
        }
    }

    fun logout() {
        val response = _state.value.authResponse ?: return
        _state.value = _state.value.copy(isLoading = true, error = null)
        scope.launch {
            try {
                repository.logout(
                    LogoutRequest(
                        studentTicket = response.user.studentTicket,
                        token = response.token,
                    ),
                )
            } finally {
                _state.value = AuthState()
            }
        }
    }
}

