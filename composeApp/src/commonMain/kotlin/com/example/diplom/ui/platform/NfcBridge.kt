package com.example.diplom.ui.platform

import com.example.diplom.nfc.NfcUidNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Общий для всех таргетов мост: Android пишет UID из NFC ([onTagScanned]),
 * [ScanScreen] подписывается на [lastUid].
 */
object NfcBridge {
    private val _lastUid = MutableStateFlow<String?>(null)
    val lastUid: StateFlow<String?> = _lastUid.asStateFlow()

    fun onTagScanned(uid: String) {
        val normalized = NfcUidNormalizer.canonicalColonUid(uid)
            ?: uid.trim().uppercase().takeIf { it.isNotBlank() }
            ?: return
        _lastUid.value = normalized
    }

    fun clearLastUid() {
        _lastUid.value = null
    }
}
