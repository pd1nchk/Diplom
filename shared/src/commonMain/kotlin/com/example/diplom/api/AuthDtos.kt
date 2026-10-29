package com.example.diplom.api

import com.example.diplom.domain.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val studentTicket: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val studentTicket: String,
    val password: String,
)

@Serializable
data class ChangePasswordRequest(
    val studentTicket: String,
    val oldPassword: String,
    val newPassword: String,
)

@Serializable
data class LogoutRequest(
    val studentTicket: String,
    val token: String,
)

@Serializable
data class AuthUserDto(
    val id: Long,
    val studentTicket: String,
    val fullName: String,
    val role: UserRole,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: AuthUserDto,
)

@Serializable
data class ForgotPasswordRequest(
    val studentTicket: String,
    val contact: String = "",
    val comment: String = "",
)

@Serializable
data class ForgotPasswordAdminItemDto(
    val id: Long,
    val studentTicket: String,
    val contact: String,
    val comment: String,
    val status: String,
    val createdAtIso: String,
    val resolvedAtIso: String? = null,
)

@Serializable
data class ForgotPasswordResolveRequest(
    val requestId: Long,
    val adminComment: String = "",
)

@Serializable
data class PasswordResetPhoneStartRequest(
    val studentTicket: String,
)

@Serializable
data class PasswordResetPhoneStartResponse(
    val maskedPhone: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
data class PasswordResetPhoneConfirmRequest(
    val studentTicket: String,
    val code: String,
    val newPassword: String,
)

