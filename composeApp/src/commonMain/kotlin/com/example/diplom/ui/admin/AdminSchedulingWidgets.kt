package com.example.diplom.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.diplom.time.AppTimeZone
import com.example.diplom.time.appZoneLocalDateTimeToEpochMillis
import com.example.diplom.time.epochMillisToAppZoneHour
import com.example.diplom.time.epochMillisToAppZoneYmd
import com.example.diplom.time.epochMillisToUtcIso
import com.example.diplom.time.parseUtcIsoToEpochMillis
import com.example.diplom.time.todayLocalDateInAppZone
import com.example.diplom.time.appZoneLocalDateTimeToEpochMillis
import com.example.diplom.time.epochMillisToUtcIso
import com.example.diplom.time.localDateFromEpochMillis
import com.example.diplom.time.localDateStartEpochMillis
import com.example.diplom.time.parseUtcIsoToEpochMillis
import com.example.diplom.time.appZoneLocalDateTimeToEpochMillis
import com.example.diplom.time.epochMillisToUtcIso
import com.example.diplom.time.localDateFromEpochMillis
import com.example.diplom.time.localDateStartEpochMillis
import com.example.diplom.time.parseUtcIsoToEpochMillis
import com.example.diplom.ui.design.GlassCard
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val uiTz: TimeZone = AppTimeZone

private fun monthNameRu(m: Month): String = when (m) {
    Month.JANUARY -> "января"
    Month.FEBRUARY -> "февраля"
    Month.MARCH -> "марта"
    Month.APRIL -> "апреля"
    Month.MAY -> "мая"
    Month.JUNE -> "июня"
    Month.JULY -> "июля"
    Month.AUGUST -> "августа"
    Month.SEPTEMBER -> "сентября"
    Month.OCTOBER -> "октября"
    Month.NOVEMBER -> "ноября"
    Month.DECEMBER -> "декабря"
}

private fun mondayOnOrBefore(d: LocalDate): LocalDate {
    val ord = d.dayOfWeek.ordinal
    return d.minus(ord.toLong(), DateTimeUnit.DAY)
}

fun todayInMinsk(): LocalDate = todayLocalDateInAppZone()

fun formatInstantRu(iso: String): String {
    val ms = parseUtcIsoToEpochMillis(iso) ?: return "—"
    val ymd = epochMillisToAppZoneYmd(ms)
    val secsOfDay = ((ms / 1000 + 3 * 3600) % 86_400 + 86_400) % 86_400
    val h = (secsOfDay / 3600).toInt()
    val mi = ((secsOfDay % 3600) / 60).toInt()
    return "${ymd.day} ${monthNameRu(Month(ymd.month))} ${ymd.year}, " +
        "${h.toString().padStart(2, '0')}:${mi.toString().padStart(2, '0')}"
}

fun buildIsoForLesson(
    date: LocalDate,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
): Pair<String, String>? {
    if (startHour !in 0..23 || endHour !in 0..23) return null
    if (startMinute !in 0..59 || endMinute !in 0..59) return null
    val startMs = appZoneLocalDateTimeToEpochMillis(
        date.year, date.monthNumber, date.dayOfMonth, startHour, startMinute,
    )
    val endMs = appZoneLocalDateTimeToEpochMillis(
        date.year, date.monthNumber, date.dayOfMonth, endHour, endMinute,
    )
    if (endMs <= startMs) return null
    return epochMillisToUtcIso(startMs) to epochMillisToUtcIso(endMs)
}

@Composable
fun AdminMonthCalendar(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthStart by remember { mutableStateOf(LocalDate(selected.year, selected.month, 1)) }
    val gridStart = remember(monthStart) { mondayOnOrBefore(monthStart) }
    val label = "${monthNameRu(monthStart.month).replaceFirstChar { it.uppercase() }} ${monthStart.year}"
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {
                monthStart = monthStart.minus(1, DateTimeUnit.MONTH)
            }) { Text("‹") }
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = {
                monthStart = monthStart.plus(1, DateTimeUnit.MONTH)
            }) { Text("›") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
                Text(d, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
            repeat(6) { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(7) { day ->
                        val cell = gridStart.plus((week * 7 + day).toLong(), DateTimeUnit.DAY)
                        val inMonth = cell.month == monthStart.month && cell.year == monthStart.year
                        val isSel = cell == selected
                        val today = cell == todayInMinsk()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        today -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.001f)
                                    },
                                )
                                .clickable { onSelect(cell) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cell.dayOfMonth.toString(),
                                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTimeRangePickers(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStart: (Int, Int) -> Unit,
    onEnd: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text("Время", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Начало", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.35f))
            TimeDropdowns(startHour, startMinute, onStart, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Конец", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.35f))
            TimeDropdowns(endHour, endMinute, onEnd, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimeDropdowns(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var openH by remember { mutableStateOf(false) }
    var openM by remember { mutableStateOf(false) }
    val hours = remember { (8..20).toList() }
    val minutes = remember { listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { openH = true }) {
                Text("${hour.toString().padStart(2, '0')} ч")
            }
            DropdownMenu(expanded = openH, onDismissRequest = { openH = false }) {
                hours.forEach { h ->
                    DropdownMenuItem(
                        text = { Text("$h") },
                        onClick = {
                            onPick(h, minute)
                            openH = false
                        },
                    )
                }
            }
        }
        Box {
            OutlinedButton(onClick = { openM = true }) {
                Text("${minute.toString().padStart(2, '0')} мин")
            }
            DropdownMenu(expanded = openM, onDismissRequest = { openM = false }) {
                minutes.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.toString().padStart(2, '0')) },
                        onClick = {
                            onPick(hour, m)
                            openM = false
                        },
                    )
                }
            }
        }
    }
}

fun isoToLocalDatePart(iso: String): String =
    parseUtcIsoToEpochMillis(iso)?.let { localDateFromEpochMillis(it).toString() } ?: iso.trim().take(10)

fun isoToLocalTimePart(iso: String): String {
    val ms = parseUtcIsoToEpochMillis(iso) ?: return "08:00"
    val h = epochMillisToAppZoneHour(ms)
    val secsOfDay = ((ms / 1000 + 3 * 3600) % 86_400 + 86_400) % 86_400
    val m = ((secsOfDay % 3600) / 60).toInt()
    return formatHm(h, m)
}

fun parseHm(s: String): Pair<Int, Int>? {
    val p = s.trim().split(':')
    if (p.size < 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

fun formatHm(h: Int, m: Int): String =
    "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

fun buildUtcIsoFromParts(dateIso: String, hm: String): String? = runCatching {
    val (y, mo, d) = dateIso.split("-").map { it.toInt() }
    val tp = hm.split(':')
    val h = tp[0].toInt()
    val mi = tp[1].toInt()
    epochMillisToUtcIso(appZoneLocalDateTimeToEpochMillis(y, mo, d, h, mi))
}.getOrNull()

/** Нормализует ISO с сервера/БД в формат, который принимает `Instant.parse` на бэкенде. */
fun normalizeInstantIsoForApi(iso: String): String? =
    parseUtcIsoToEpochMillis(iso)?.let { epochMillisToUtcIso(it) }

fun adminGroupLabel(g: com.example.diplom.api.AdminGroupRefDto): String =
    "${g.course} к., гр. ${g.groupNumber} — ${g.name}"
