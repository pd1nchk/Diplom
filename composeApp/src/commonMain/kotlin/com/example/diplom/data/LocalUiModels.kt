package com.example.diplom.data

/** Карточка «ближайшее событие» на главном экране (данные с API расписания / уведомлений). */
data class UpcomingEvent(
    val id: Long,
    val title: String,
    val subtitle: String,
    val startTime: String,
    val teacher: String = "",
    val room: String = "",
    val startEpochMillis: Long = 0L,
    val endEpochMillis: Long = 0L,
    val dayLabel: String = "",
    /** Пара идёт прямо сейчас */
    val isOngoing: Boolean = false,
)

/** Локальная модель для экрана посещаемости до подключения журнала к API. */
data class AttendanceRecord(
    val id: Long,
    val date: String,
    val subject: String,
    val time: String,
    val reason: String,
)

/** Локальная модель для экрана оценок до подключения API. */
data class GradeRecord(
    val id: Long,
    val subject: String,
    val score: Int,
    val maxScore: Int,
    val teacher: String,
    val date: String,
)
