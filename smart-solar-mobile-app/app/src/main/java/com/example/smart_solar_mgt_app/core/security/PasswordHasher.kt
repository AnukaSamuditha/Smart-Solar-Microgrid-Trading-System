package com.example.smart_solar_mgt_app.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * One-way password hashing - PBKDF2WithHmacSHA256, salted, self-describing stored format
 * ("PBKDF2$iterations$saltB64$hashB64") so the iteration count can be bumped later without
 * invalidating existing hashes. No plaintext password is ever persisted.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "PBKDF2"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(digest, Base64.NO_WRAP)
        ).joinToString("$")
    }

    /** Constant-time comparison - never use `==`/`.equals` on password hashes. */
    fun verify(password: CharArray, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = try { Base64.decode(parts[2], Base64.NO_WRAP) } catch (e: IllegalArgumentException) { return false }
        val expected = try { Base64.decode(parts[3], Base64.NO_WRAP) } catch (e: IllegalArgumentException) { return false }

        val actual = pbkdf2(password, salt, iterations, expected.size * 8)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}