package com.example.diplom.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    @Volatile
    var lastJdbcUrl: String = ""
        private set

    @Volatile
    var lastDriverClassName: String = ""
        private set

    /** Для GET /debug/info: без пароля и без user:pass в URL. */
    fun jdbcUrlForDiagnostics(): String {
        val u = lastJdbcUrl
        if (u.isBlank()) return "not_connected"
        return u
            .replace(Regex("(?i)(password|passwd)\\s*=\\s*[^;&]+"), "$1=***")
            .replace(Regex("://([^/:@]+):([^@/]+)@"), "://$1:***@")
    }

    fun databaseKindLabel(): String = when {
        lastJdbcUrl.contains("postgresql", ignoreCase = true) -> "postgresql"
        lastJdbcUrl.contains("jdbc:h2", ignoreCase = true) -> "h2"
        lastJdbcUrl.isBlank() -> "unknown"
        else -> "other"
    }

    fun init(
        jdbcUrl: String,
        user: String,
        password: String,
        driver: String = "org.postgresql.Driver",
    ) {
        lastJdbcUrl = jdbcUrl
        lastDriverClassName = driver
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.driverClassName = driver
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        close()
        val ds = HikariDataSource(config)
        dataSource = ds
        Database.connect(ds)
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}

