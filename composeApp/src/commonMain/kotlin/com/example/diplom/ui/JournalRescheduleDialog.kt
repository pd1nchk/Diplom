package com.example.diplom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.api.RescheduleRequestCreateDto
import com.example.diplom.time.localDateFromEpochMillis
import com.example.diplom.time.localDateStartEpochMillis
import com.example.diplom.time.todayLocalDateInAppZone
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedTextSecondary
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalRescheduleDialog(
    lessonId: Long,
    initialDateIso: String,
    initialFrom: String,
    initialTo: String,
    onDismiss: () -> Unit,
    onSubmit: (RescheduleRequestCreateDto) -> Unit,
) {
    var reqDate by remember { mutableStateOf(initialDateIso) }
    var reqFrom by remember { mutableStateOf(initialFrom) }
    var reqTo by remember { mutableStateOf(initialTo) }
    var reqComment by remember { mutableStateOf("") }
    var reqError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запрос на перенос") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reqDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Новая дата") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                )
                OutlinedTextField(
                    value = reqFrom,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("С") },
                    trailingIcon = {
                        IconButton(onClick = { showFromPicker = true }) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showFromPicker = true },
                )
                OutlinedTextField(
                    value = reqTo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("До") },
                    trailingIcon = {
                        IconButton(onClick = { showToPicker = true }) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showToPicker = true },
                )
                OutlinedTextField(
                    value = reqComment,
                    onValueChange = { reqComment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                )
                reqError?.let { Text(it, color = themedError()) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                reqError = null
                when {
                    reqDate.isBlank() -> reqError = "Укажите дату"
                    !reqFrom.contains(':') -> reqError = "Укажите время начала"
                    !reqTo.contains(':') -> reqError = "Укажите время окончания"
                    else -> onSubmit(
                        RescheduleRequestCreateDto(
                            lessonId = lessonId,
                            desiredDateIso = reqDate.trim(),
                            desiredTimeFrom = reqFrom.trim(),
                            desiredTimeTo = reqTo.trim(),
                            comment = reqComment.ifBlank { null },
                        ),
                    )
                }
            }) { Text("Отправить", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )

    if (showDatePicker) {
        val initialMillis = runCatching { LocalDate.parse(reqDate) }.getOrNull()?.let {
            localDateStartEpochMillis(it)
        } ?: localDateStartEpochMillis(todayLocalDateInAppZone())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        reqDate = localDateFromEpochMillis(ms).toString()
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showFromPicker) {
        val p = parseHmJournal(reqFrom) ?: (8 to 0)
        val st = rememberTimePickerState(p.first, p.second, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showFromPicker = false },
            title = { Text("Время начала") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    reqFrom = formatHmJournal(st.hour, st.minute)
                    showFromPicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Отмена") }
            },
        )
    }

    if (showToPicker) {
        val p = parseHmJournal(reqTo) ?: (9 to 30)
        val st = rememberTimePickerState(p.first, p.second, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showToPicker = false },
            title = { Text("Время окончания") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    reqTo = formatHmJournal(st.hour, st.minute)
                    showToPicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Отмена") }
            },
        )
    }
}

private fun parseHmJournal(s: String): Pair<Int, Int>? {
    val parts = s.trim().split(':')
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

private fun formatHmJournal(h: Int, m: Int): String =
    "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
