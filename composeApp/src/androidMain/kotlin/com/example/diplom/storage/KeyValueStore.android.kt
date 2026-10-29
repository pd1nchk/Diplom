package com.example.diplom.storage

import android.content.Context

actual object KeyValueStore {
    private var prefs: android.content.SharedPreferences? = null
    private val memoryFallback = linkedMapOf<String, String>()

    actual fun init(platformContext: Any?) {
        val context = platformContext as? Context ?: return
        prefs = context.getSharedPreferences("diplom_prefs", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String): String? {
        return prefs?.getString(key, null) ?: memoryFallback[key]
    }

    actual fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply() ?: run { memoryFallback[key] = value }
    }
}
