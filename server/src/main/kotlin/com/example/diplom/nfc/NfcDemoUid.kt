package com.example.diplom.nfc

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Детерминированный UID для демо-записей NFC (совместим с форматом Android reader). */
object NfcDemoUid {
    fun fromTicket(ticket: String): String {
        val md = MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray(StandardCharsets.UTF_8))
        return (listOf(0x04) + md.take(3).map { it.toInt() and 0xFF }).joinToString(":") { "%02X".format(it) }
    }
}
