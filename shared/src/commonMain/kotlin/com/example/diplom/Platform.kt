package com.example.diplom

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform