package com.example.diplom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diplom.api.UserProfileDto
import com.example.diplom.ui.platform.decodeImageFromBytes

@Composable
fun UserProfileIdCardDialog(
    profile: UserProfileDto,
    onDismiss: () -> Unit,
) {
    val image = profile.photoHex?.let { hex ->
        runCatching { decodeImageFromBytes(hex.hexToByteArray()) }.getOrNull()
    }
    val roleTitle = when (profile.role.uppercase()) {
        "STUDENT" -> "Студент"
        "TEACHER" -> "Преподаватель"
        "ADMIN" -> "Администратор"
        else -> profile.role
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8ECF5)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (image != null) {
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = profile.fullName.firstOrNull()?.uppercase() ?: "—",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A237E),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = profile.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF121212),
                            )
                            Text(text = roleTitle, color = Color(0xFF5C6BC0), fontWeight = FontWeight.SemiBold)
                            profile.degree?.let {
                                Text(text = it, color = Color(0xFF757575), style = MaterialTheme.typography.bodySmall)
                            }
                            profile.groupName?.let {
                                Text(text = it, color = Color(0xFF9E9E9E), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailLine("Билет / логин", profile.studentTicket)
                        profile.facultyName?.let { DetailLine("Факультет", it) }
                        profile.specialtyCode?.let { c ->
                            val spec = profile.specialtyName?.let { "$c — $it" } ?: c
                            DetailLine("Специальность", spec)
                        }
                        profile.course?.let { c ->
                            profile.groupNumber?.let { g ->
                                DetailLine("Курс / группа", "$c курс, №$g")
                            }
                        }
                        profile.phone?.takeIf { it.isNotBlank() }?.let { DetailLine("Телефон", it) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TicketBarcode(ticket = profile.studentTicket)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF5C6BC0),
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Закрыть", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF424242))
    }
}

@Composable
private fun TicketBarcode(ticket: String) {
    val digits = ticket.filter { it.isDigit() }.ifEmpty { "0000000" }.take(7).padEnd(7, '0')
    Canvas(modifier = Modifier.width(120.dp).height(36.dp)) {
        val w = size.width / (digits.length * 2 + 1)
        var x = w
        digits.forEach { ch ->
            val n = ch.digitToInt().coerceIn(0, 9)
            repeat(2) { i ->
                val hFrac = 0.35f + (n + i) * 0.05f
                drawRect(
                    color = Color(0xFF424242),
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height * (1f - hFrac)),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, size.height * hFrac),
                )
                x += w
            }
        }
    }
}

private fun String.hexToByteArray(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
