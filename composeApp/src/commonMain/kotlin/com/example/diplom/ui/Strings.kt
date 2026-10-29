package com.example.diplom.ui

object Strings {
    fun settings() = when (AppState.language) {
        Language.RUSSIAN -> "Настройки"
        Language.BELARUSIAN -> "Налады"
        Language.ENGLISH -> "Settings"
    }
    fun darkTheme() = when (AppState.language) {
        Language.RUSSIAN -> "Тёмная тема"
        Language.BELARUSIAN -> "Цёмная тэма"
        Language.ENGLISH -> "Dark theme"
    }
    fun language() = when (AppState.language) {
        Language.RUSSIAN -> "Язык"
        Language.BELARUSIAN -> "Мова"
        Language.ENGLISH -> "Language"
    }
    fun home() = when (AppState.language) {
        Language.RUSSIAN -> "Главная"
        Language.BELARUSIAN -> "Галоўная"
        Language.ENGLISH -> "Home"
    }
    fun profile() = when (AppState.language) {
        Language.RUSSIAN -> "Профиль"
        Language.BELARUSIAN -> "Профіль"
        Language.ENGLISH -> "Profile"
    }
}
