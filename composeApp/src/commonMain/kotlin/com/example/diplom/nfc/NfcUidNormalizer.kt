package com.example.diplom.nfc

/**
 * Приводит UID метки к каноническому виду с двоеточиями между байтами (как в демо `04:49:29:5E:C1:2A:81`).
 * Убирает пробелы, полноширинные двоеточия, дефисы и любые не-hex символы.
 */
object NfcUidNormalizer {

    fun digitsOnlyHex(raw: String): String =
        raw.asSequence()
            .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            .joinToString("")
            .uppercase()

    /**
     * @return null если слишком мало hex-цифр для разумного UID.
     */
    fun canonicalColonUid(raw: String): String? {
        val d = digitsOnlyHex(raw)
        if (d.length < 4) return null
        val even = if (d.length % 2 == 0) d else d.dropLast(1)
        if (even.length < 4) return null
        return even.chunked(2).joinToString(":")
    }

    /** Для сравнения с сервером: только hex, upper, без разделителей. */
    fun compactHex(raw: String): String? =
        canonicalColonUid(raw)?.replace(":", "")?.takeIf { it.isNotEmpty() }
}
