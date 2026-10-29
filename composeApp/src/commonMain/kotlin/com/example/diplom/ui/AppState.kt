package com.example.diplom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.diplom.storage.KeyValueStore

object AppState {
    private const val KEY_DARK_THEME = "app.dark_theme"
    private const val KEY_LANGUAGE = "app.language"
    private const val PROFILE_IMAGE_PREFIX = "profile.image."

    private var initialized = false

    private var isDarkThemeState by mutableStateOf(false)
    private var languageState by mutableStateOf(Language.RUSSIAN)
    var overlayScreen by mutableStateOf(OverlayScreen.NONE)

    /** После перехода из NFC-сканера открыть вкладку NFC в админ-центре (один раз). */
    var openAdminNfcTab by mutableStateOf(false)

    /** Открыть админ-центр на конкретном разделе (один раз). */
    var pendingAdminNav: String? = null

    var isDarkTheme: Boolean
        get() = isDarkThemeState
        set(value) {
            isDarkThemeState = value
            if (initialized) {
                KeyValueStore.putString(KEY_DARK_THEME, value.toString())
            }
        }

    var language: Language
        get() = languageState
        set(value) {
            languageState = value
            if (initialized) {
                KeyValueStore.putString(KEY_LANGUAGE, value.name)
            }
        }

    fun initStorage(platformContext: Any? = null) {
        if (initialized) return
        KeyValueStore.init(platformContext)
        isDarkTheme = KeyValueStore.getString(KEY_DARK_THEME)?.toBooleanStrictOrNull() ?: false
        language = KeyValueStore.getString(KEY_LANGUAGE)
            ?.let { name -> Language.entries.firstOrNull { it.name == name } }
            ?: Language.RUSSIAN
        initialized = true
    }

    fun getProfileImageHex(userTicket: String): String? {
        return KeyValueStore.getString(PROFILE_IMAGE_PREFIX + userTicket)
    }

    fun setProfileImageHex(userTicket: String, imageHex: String) {
        KeyValueStore.putString(PROFILE_IMAGE_PREFIX + userTicket, imageHex)
    }
}

enum class Language(val displayName: String) {
    RUSSIAN("Русский"),
    BELARUSIAN("Беларуская"),
    ENGLISH("English"),
}
