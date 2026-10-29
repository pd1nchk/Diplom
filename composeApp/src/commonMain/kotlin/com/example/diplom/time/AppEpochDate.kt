package com.example.diplom.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** Смещение AppTimeZone (+03:00) в секундах. */
private const val APP_OFFSET_SECONDS: Int = 3 * 3600

data class Ymd(val year: Int, val month: Int, val day: Int)

/** Дата/время без [kotlinx.datetime.Instant] — на Wasm Instant не линкуется. */
fun epochMillisToAppZoneYmd(epochMillis: Long): Ymd {
    val days = (epochMillis / 1000 + APP_OFFSET_SECONDS) / 86_400
    return civilDaysToYmd(days)
}

fun epochMillisToAppZoneHour(epochMillis: Long): Int {
    val secsOfDay = ((epochMillis / 1000 + APP_OFFSET_SECONDS) % 86_400 + 86_400) % 86_400
    return (secsOfDay / 3600).toInt()
}

fun ymdToLocalDate(ymd: Ymd): LocalDate =
    LocalDate(ymd.year, Month(ymd.month), ymd.day)

fun todayLocalDateInAppZone(): LocalDate =
    ymdToLocalDate(epochMillisToAppZoneYmd(nowEpochMillis()))

fun localDateFromEpochMillis(epochMillis: Long): LocalDate =
    ymdToLocalDate(epochMillisToAppZoneYmd(epochMillis))

fun appZoneDateStartEpochMillis(ymd: Ymd): Long {
    val days = ymdToCivilDays(ymd)
    return (days * 86_400L - APP_OFFSET_SECONDS) * 1000
}

fun localDateStartEpochMillis(date: LocalDate): Long =
    appZoneDateStartEpochMillis(Ymd(date.year, date.monthNumber, date.dayOfMonth))

fun appZoneLocalDateTimeToEpochMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int = 0,
): Long {
    val base = appZoneDateStartEpochMillis(Ymd(year, month, day))
    return base + (hour * 3600L + minute * 60L + second) * 1000
}

fun epochMillisToUtcIso(epochMillis: Long): String {
    val totalSec = epochMillis / 1000
    val ms = (epochMillis % 1000).toInt().coerceIn(0, 999)
    val sec = (totalSec % 60).toInt()
    val min = ((totalSec / 60) % 60).toInt()
    val hour = ((totalSec / 3600) % 24).toInt()
    val days = totalSec / 86_400
    val ymd = civilDaysToYmd(days)
    return buildString {
        append(ymd.year).append('-')
        append(ymd.month.toString().padStart(2, '0')).append('-')
        append(ymd.day.toString().padStart(2, '0')).append('T')
        append(hour.toString().padStart(2, '0')).append(':')
        append(min.toString().padStart(2, '0')).append(':')
        append(sec.toString().padStart(2, '0')).append('.')
        append(ms.toString().padStart(3, '0')).append('Z')
    }
}

/** ISO UTC → epoch ms (без kotlinx Instant). */
fun parseUtcIsoToEpochMillis(iso: String): Long? = runCatching {
    val s = iso.trim().replace(' ', 'T').removeSuffix("Z")
    val datePart = s.substringBefore('T')
    val timePart = s.substringAfter('T', missingDelimiterValue = "")
    if (timePart.isEmpty()) return@runCatching null
    val (y, mo, d) = datePart.split('-').map { it.toInt() }
    val tp = timePart.split(':')
    val h = tp[0].toInt()
    val mi = tp[1].toInt()
    val secRaw = tp.getOrElse(2) { "0" }
    val sec = secRaw.substringBefore('.').toInt()
    val ms = secRaw.substringAfter('.', "").padEnd(3, '0').take(3).toIntOrNull() ?: 0
    utcYmdHmToEpochMillis(y, mo, d, h, mi, sec, ms)
}.getOrNull()

private fun civilDaysToYmd(days: Long): Ymd {
    var z = days + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    var y = (yoe + era * 400).toInt()
    val doy = (doe - (365 * yoe + yoe / 4 - yoe / 100)).toInt()
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + if (mp < 10) 3 else -9
    y += if (m <= 2) 1 else 0
    return Ymd(y, m, d)
}

private fun ymdToCivilDays(ymd: Ymd): Long {
    var y = ymd.year.toLong()
    var m = ymd.month.toLong()
    if (m <= 2) {
        y -= 1
        m += 12
    }
    // Приводим к дням относительно Unix epoch (1970-01-01).
    return 365 * y + y / 4 - y / 100 + y / 400 + (153 * m - 457) / 5 + ymd.day - 306 - 719_163
}

private fun utcYmdHmToEpochMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    millis: Int,
): Long {
    var y = year.toLong()
    var m = month.toLong()
    if (m <= 2) {
        y -= 1
        m += 12
    }
    val daysSinceEpoch = (
        365 * y + y / 4 - y / 100 + y / 400 +
            (153 * m - 457) / 5 + day - 306
        ) - 719_163
    val dayStart = daysSinceEpoch * 86_400
    val timeOfDay = hour * 3600L + minute * 60L + second
  return (dayStart + timeOfDay) * 1000 + millis
}
