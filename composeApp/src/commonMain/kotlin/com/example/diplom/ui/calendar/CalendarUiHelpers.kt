package com.example.diplom.ui.calendar

import com.example.diplom.api.ScheduledLessonDto
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.time.Ymd
import com.example.diplom.time.epochMillisToAppZoneYmd
import com.example.diplom.time.todayLocalDateInAppZone
import com.example.diplom.time.ymdToLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import androidx.compose.ui.graphics.Color

data class CalendarDayCell(val ymd: String, val day: Int)

fun ymdToIso(y: Int, m: Int, d: Int): String =
    "${y}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

fun parseYmd(iso: String): Ymd? {
    val p = iso.split("-")
    if (p.size < 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return Ymd(y, m, d)
}

fun todayYmd(): Ymd {
    val t = todayLocalDateInAppZone()
    return Ymd(t.year, t.monthNumber, t.dayOfMonth)
}

fun monthTitleRu(year: Int, month: Int): String {
    val names = listOf(
        "", "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    return "${names.getOrElse(month) { month.toString() }} $year"
}

fun buildMonthGrid(year: Int, month: Int): List<CalendarDayCell?> {
    val first = LocalDate(year, Month(month), 1)
    val daysInMonth = first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth
    val startOffset = first.weekdayIndexMondayZero()
    val pad = startOffset
    val cells = mutableListOf<CalendarDayCell?>()
    repeat(pad) { cells.add(null) }
    for (d in 1..daysInMonth) {
        cells.add(CalendarDayCell(ymdToIso(year, month, d), d))
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

fun scheduledLessonsOnDate(schedule: WeekScheduleDto, ymd: String): List<ScheduledLessonDto> =
    schedule.columns
        .flatMap { it.lessons }
        .filter { lesson ->
            if (lesson.startTimeEpochMillis > 0L) {
                val y = epochMillisToAppZoneYmd(lesson.startTimeEpochMillis)
                ymdToIso(y.year, y.month, y.day) == ymd
            } else {
                false
            }
        }
        .distinctBy { it.id }

fun noteColorCompose(key: String): Color = when (key.lowercase()) {
    "blue" -> Color(0xFF3B82F6)
    "green" -> Color(0xFF10B981)
    "red" -> Color(0xFFEF4444)
    "yellow" -> Color(0xFFF59E0B)
    else -> Color(0xFF8B5CF6)
}

private fun LocalDate.weekdayIndexMondayZero(): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
}

fun lessonKindShortRu(kind: String): String = when (kind.uppercase()) {
    "LECTURE" -> "Лекция"
    "PRACTICE" -> "Практика"
    "LAB" -> "Лаб."
    "SEMINAR" -> "Сем."
    else -> kind.take(8)
}
