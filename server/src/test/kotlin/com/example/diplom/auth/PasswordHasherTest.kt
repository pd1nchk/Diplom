package com.example.diplom.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun bcryptHashAndVerify() {
        val hash = PasswordHasher.hash("1234")
        assertTrue(hash.startsWith("$2"))
        assertTrue(PasswordHasher.verify(hash, "1234"))
        assertFalse(PasswordHasher.verify(hash, "wrong"))
    }

    @Test
    fun legacySha256StillVerifies() {
        val legacy = "03ac674216f3e15c761ee1a5e255f067953623c8b463972133ad26d6f6a8a56a2" // SHA-256("1234")
        assertTrue(PasswordHasher.verify(legacy, "1234"))
        assertTrue(PasswordHasher.needsUpgrade(legacy))
    }
}
