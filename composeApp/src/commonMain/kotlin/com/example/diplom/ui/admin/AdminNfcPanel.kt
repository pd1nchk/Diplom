package com.example.diplom.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AdminRoomDto
import com.example.diplom.nfc.NfcUidNormalizer
import com.example.diplom.ui.platform.NfcBridge
import kotlinx.coroutines.launch

@Composable
fun AdminNfcPanel(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val scannedUid by NfcBridge.lastUid.collectAsState()
    val scannedCompact = remember(scannedUid) {
        scannedUid?.let { NfcUidNormalizer.compactHex(it) }
    }
    val matchedRoom = remember(scannedCompact, state.rooms) {
        if (scannedCompact == null) null
        else state.rooms.firstOrNull { room ->
            val roomCompact = room.nfcUid?.let { NfcUidNormalizer.compactHex(it) }
            roomCompact != null && roomCompact == scannedCompact
        }
    }
    val selectedRoom = state.rooms.firstOrNull { it.id == state.nfcSelectedRoomId }
    val rooms = state.rooms.filter { r ->
        !state.nfcFilterUnlinkedOnly || r.nfcUid.isNullOrBlank()
    }

    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminSectionTitle(
            title = "NFC-метки аудиторий",
            subtitle = "Поднесите метку в аудитории и привяжите к выбранному кабинету",
        )
        Text("UID: ${scannedUid ?: "—"}", fontWeight = FontWeight.SemiBold)
        when {
            scannedUid.isNullOrBlank() -> {
                Text(
                    "Ожидание метки…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            matchedRoom != null -> {
                Text(
                    "Метка привязана: ${matchedRoom.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> {
                Text(
                    "Метка не привязана. Выберите аудиторию и нажмите «Привязать».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        state.nfcMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Только без метки", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.nfcFilterUnlinkedOnly,
                onCheckedChange = vm::setNfcFilterUnlinkedOnly,
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("Аудитория", "NFC UID", ""))
            if (rooms.isEmpty()) {
                AdminEmptyState(
                    title = if (state.nfcFilterUnlinkedOnly) "Все привязаны" else "Нет аудиторий",
                    description = if (state.nfcFilterUnlinkedOnly) "Нет аудиторий без NFC." else "Список пуст.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rooms, key = { it.id }) { room ->
                        NfcRoomRow(room, state.nfcSelectedRoomId == room.id, wide, vm)
                    }
                }
            }
        }
        val rid = state.nfcSelectedRoomId
        val uid = scannedUid
        val canBind = !uid.isNullOrBlank() && rid != null
        Button(
            onClick = {
                if (rid != null && !uid.isNullOrBlank()) {
                    scope.launch {
                        vm.bindNfc(rid, uid) { err ->
                            vm.setNfcMessage(
                                err?.let { "Ошибка: $it" } ?: "Метка аудитории сохранена",
                            )
                            NfcBridge.clearLastUid()
                        }
                    }
                }
            },
            enabled = canBind,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    uid.isNullOrBlank() -> "Поднесите метку"
                    selectedRoom == null -> "Выберите аудиторию"
                    else -> "Привязать к ${selectedRoom.name}"
                },
            )
        }
    }
}

@Composable
private fun NfcRoomRow(
    room: AdminRoomDto,
    selected: Boolean,
    wide: Boolean,
    vm: AdminCenterViewModel,
) {
    AdminSurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (wide) {
                Text(room.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(
                    room.nfcUid ?: "не задано",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (room.nfcUid.isNullOrBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(room.name, fontWeight = FontWeight.Medium)
                    Text("NFC: ${room.nfcUid ?: "не задано"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { vm.setNfcRoom(room.id) }) {
                Text(if (selected) "Выбрано" else "Выбрать")
            }
        }
    }
}
