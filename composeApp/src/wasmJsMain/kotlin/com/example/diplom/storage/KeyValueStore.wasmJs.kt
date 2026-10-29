package com.example.diplom.storage

actual object KeyValueStore {
    private val values = linkedMapOf<String, String>()

    actual fun init(platformContext: Any?) = Unit

    actual fun getString(key: String): String? = values[key]

    actual fun putString(key: String, value: String) {
        values[key] = value
    }
}
