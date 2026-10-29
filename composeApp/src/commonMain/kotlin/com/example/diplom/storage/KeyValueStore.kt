package com.example.diplom.storage

expect object KeyValueStore {
    fun init(platformContext: Any? = null)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
