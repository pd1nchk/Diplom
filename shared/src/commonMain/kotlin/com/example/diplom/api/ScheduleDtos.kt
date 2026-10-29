package com.example.diplom.api

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledLessonDto(
    val id: Long,
    val subject: String,
    val timeRange: String,
    val room: String,
    val teacher: String,
    val teacherTicket: String? = null,
    val lessonKind: String,
    /** ANY, EVEN, ODD — для отображения метки недели */
    val weekParity: String = "ANY",
    val startTimeEpochMillis: Long = 0L,
    val endTimeEpochMillis: Long = 0L,
    /** Несколько lesson.id при объединении параллельных занятий (одна пара, несколько групп) */
    val mergedLessonIds: List<Long> = emptyList(),
    /** Если пара общая для нескольких групп: краткие метки через « и », напр. «1-8 и 1-4» */
    val mergedGroupsLabel: String? = null,
)

@Serializable
data class DayScheduleColumnDto(
    val dayOfWeek: String,
    val lessons: List<ScheduledLessonDto> = emptyList(),
)

@Serializable
data class WeekScheduleDto(
    val groupName: String?,
    val columns: List<DayScheduleColumnDto> = emptyList(),
    /** Номер недели в году (ISO) */
    val weekNumber: Int? = null,
    /** Год, к которому относится неделя */
    val weekYear: Int? = null,
    /** true — чётная неделя, false — нечётная */
    val isEvenWeek: Boolean? = null,
    /** Устаревшее текстовое описание недели; предпочтительны weekNumber / weekYear / isEvenWeek */
    val weekParityHint: String? = null,
)

/** Пустая сетка недели (например, если сервер без маршрута /schedule или нет занятий). */
fun emptyWeekSchedule(): WeekScheduleDto {
    val labels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    return WeekScheduleDto(
        groupName = null,
        columns = labels.map { DayScheduleColumnDto(dayOfWeek = it) },
        weekParityHint = null,
    )
}
