package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: Long,
    val title: String,
    val message: String,
    val createdAtIso: String,
    val isImportant: Boolean,
)
