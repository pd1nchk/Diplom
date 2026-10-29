package com.example.diplom.ui

import com.example.diplom.time.appZoneLocalDateTimeToEpochMillis
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.todayLocalDateInAppZone
import com.example.diplom.time.ymdToLocalDate
import com.example.diplom.time.Ymd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.datetime.daysUntil

/** За сколько минут до начала можно открыть сессию (совпадает с сервером). */
const val LESSON_SESSION_EARLY_MINUTES = 15

enum class LessonJournalPhase {
    /** Ещё не началось — ячейки серые, без редактирования. */
    UPCOMING,
    /** Идёт сейчас — можно отмечать посещаемость и открыть NFC-сессию. */
    IN_PROGRESS,
    /** Завершено — ручная отметка в журнале разрешена; перенос обычно недоступен. */
    ENDED,
}

/** Ручная отметка в журнале — всегда доступна преподавателю. */
fun isJournalAttendanceEditable(phase: LessonJournalPhase): Boolean = true

fun computeLessonJournalPhase(
    dateIso: String,
    timeRange: String,
    nowMs: Long = nowEpochMillis(),
): LessonJournalPhase {
    val range = lessonEpochRangeMillis(dateIso, timeRange) ?: return LessonJournalPhase.IN_PROGRESS
    val (startMs, endMs) = range
    return when {
        nowMs < startMs -> LessonJournalPhase.UPCOMING
        nowMs >= endMs -> LessonJournalPhase.ENDED
        else -> LessonJournalPhase.IN_PROGRESS
    }
}

/** Активная NFC-сессия = пара идёт, даже если в БД битое время. */
fun effectiveLessonJournalPhase(
    dateIso: String,
    timeRange: String,
    lessonId: Long,
    sessionLessonId: Long?,
    nowMs: Long = nowEpochMillis(),
): LessonJournalPhase {
    if (sessionLessonId == lessonId) return LessonJournalPhase.IN_PROGRESS
    return computeLessonJournalPhase(dateIso, timeRange, nowMs)
}

fun lessonKindLabelRu(kind: String): String = when (kind.trim().uppercase()) {
    "LECTURE" -> "Лекция"
    "PRACTICE" -> "Практика"
    "LAB" -> "Лабораторная"
    "SEMINAR" -> "Семинар"
    "NFC_DEMO" -> "Демо NFC"
    else -> kind.trim().ifBlank { "Занятие" }
}

fun subjectIconVector(subject: String): ImageVector {
    val s = subject.lowercase()
    return when {
        s.contains("баз") -> Icons.Default.Storage
        s.contains("web") || s.contains("веб") -> Icons.Default.Layers
        s.contains("сет") -> Icons.Default.Schedule
        s.contains("програм") -> Icons.Default.School
        else -> Icons.Default.Book
    }
}

fun subjectAccentColor(subject: String): Color {
    val s = subject.lowercase()
    return when {
        s.contains("сет") -> Color(0xFF3B82F6)
        s.contains("програм") -> Color(0xFF8B5CF6)
        s.contains("баз") -> Color(0xFF10B981)
        s.contains("алгоритм") -> Color(0xFFF59E0B)
        s.contains("матем") -> Color(0xFFEC4899)
        else -> Color(0xFF6366F1)
    }
}

fun formatLessonDateRu(dateIso: String): String {
    val p = dateIso.trim().split("-")
    if (p.size < 3) return dateIso
    val months = listOf(
        "", "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    val d = p[2].toIntOrNull() ?: return dateIso
    val m = p[1].toIntOrNull() ?: return dateIso
    val y = p[0]
    val monthName = months.getOrElse(m) { p[1] }
    return "$d $monthName $y"
}

fun formatLessonTimeRangeDisplay(timeRange: String): String {
    val parts = timeRange.split(Regex("[-–—]")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return timeRange
    return "${parts[0]} — ${parts[1]}"
}

fun lessonEpochRangeMillis(dateIso: String, timeRange: String): Pair<Long, Long>? {
    val ymd = dateIso.trim().split("-")
    if (ymd.size < 3) return null
    val year = ymd[0].toIntOrNull() ?: return null
    val month = ymd[1].toIntOrNull() ?: return null
    val day = ymd[2].toIntOrNull() ?: return null
    val parts = timeRange.split(Regex("[-–—]")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val (h1, m1) = parseHm(parts[0]) ?: return null
    val (h2, m2) = parseHm(parts[1]) ?: return null
    val start = appZoneLocalDateTimeToEpochMillis(year, month, day, h1, m1)
    var end = appZoneLocalDateTimeToEpochMillis(year, month, day, h2, m2)
    if (end > start) {
        val durationMs = end - start
        if (durationMs > 4 * 60 * 60_000L) {
            end = start + 90 * 60_000L
        }
        return start to end
    }
    return null
}

private fun parseDateIsoYmd(dateIso: String): Ymd? {
    val p = dateIso.trim().split("-")
    if (p.size < 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return Ymd(y, m, d)
}

private fun parseHm(s: String): Pair<Int, Int>? {
    val p = s.split(":")
    if (p.size < 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return h to m
}

enum class LessonStatusTone { Neutral, Info, Success, Warning, Error }

data class LessonStartUiState(
    val canStart: Boolean,
    val statusMessage: String?,
    val statusTone: LessonStatusTone,
    val primaryButtonText: String,
    /** Показать кнопку «Завершить лекцию» (активная NFC-сессия). */
    val showEndLessonButton: Boolean = false,
)

fun computeLessonStartUiState(
    dateIso: String,
    timeRange: String,
    roomNfcUid: String?,
    isSessionForThisLesson: Boolean,
    hasOtherActiveSession: Boolean = false,
    otherActiveSessionHint: String? = null,
    nowMs: Long = nowEpochMillis(),
): LessonStartUiState {
    val primaryLabel = "Начать занятие"

    if (isSessionForThisLesson) {
        return LessonStartUiState(
            canStart = false,
            statusMessage = "Активная лекция — студенты отмечаются по NFC или вручную в журнале",
            statusTone = LessonStatusTone.Success,
            primaryButtonText = "Сессия идёт",
            showEndLessonButton = true,
        )
    }

    if (hasOtherActiveSession) {
        val hint = otherActiveSessionHint?.let { " ($it)" } ?: ""
        return LessonStartUiState(
            canStart = false,
            statusMessage = "Сначала завершите текущую активную пару$hint — иначе новую начать нельзя",
            statusTone = LessonStatusTone.Warning,
            primaryButtonText = primaryLabel,
            showEndLessonButton = true,
        )
    }

    val range = lessonEpochRangeMillis(dateIso, timeRange)
    if (range == null) {
        return LessonStartUiState(
            canStart = false,
            statusMessage = "Не удалось определить время занятия",
            statusTone = LessonStatusTone.Error,
            primaryButtonText = primaryLabel,
        )
    }
    val (startMs, endMs) = range
    val earlyMs = startMs - LESSON_SESSION_EARLY_MINUTES * 60_000L

    if (nowMs >= endMs) {
        return LessonStartUiState(
            canStart = false,
            statusMessage = "Занятие уже завершено",
            statusTone = LessonStatusTone.Neutral,
            primaryButtonText = primaryLabel,
        )
    }

    if (nowMs < earlyMs) {
        val lessonDate = parseDateIsoYmd(dateIso)?.let { ymdToLocalDate(it) }
        val today = todayLocalDateInAppZone()
        val days = lessonDate?.let { today.daysUntil(it) }
        val dateRu = formatLessonDateRu(dateIso)
        val msg = when {
            days != null && days > 0 ->
                "Рано: занятие $dateRu. Открыть можно за $LESSON_SESSION_EARLY_MINUTES мин до начала."
            days == 0 ->
                "Рано: открыть занятие можно за $LESSON_SESSION_EARLY_MINUTES мин до начала (${formatLessonTimeRangeDisplay(timeRange)})."
            else ->
                "Рано: занятие запланировано на $dateRu."
        }
        return LessonStartUiState(
            canStart = false,
            statusMessage = msg,
            statusTone = LessonStatusTone.Warning,
            primaryButtonText = primaryLabel,
        )
    }

    val inProgress = nowMs >= startMs
    val nfcHint = if (roomNfcUid.isNullOrBlank()) {
        " Метка NFC в аудитории не привязана — студенты смогут отмечаться вручную."
    } else {
        ""
    }
    return LessonStartUiState(
        canStart = true,
        statusMessage = if (inProgress) {
            "Занятие идёт — откройте сессию посещаемости.$nfcHint"
        } else {
            "Можно открыть сессию (до начала менее $LESSON_SESSION_EARLY_MINUTES мин).$nfcHint"
        },
        statusTone = LessonStatusTone.Info,
        primaryButtonText = primaryLabel,
    )
}
