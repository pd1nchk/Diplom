package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class JsonErrorBody(val error: String)

@Serializable
data class ServerDebugInfoDto(
    val databaseKind: String,
    val jdbcUrl: String,
    val jdbcDriver: String,
    val ktorPort: String,
)
