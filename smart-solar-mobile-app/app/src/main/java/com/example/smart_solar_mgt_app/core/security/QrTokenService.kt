package com.example.smart_solar_mgt_app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Signs/verifies Energy Transfer Pass QR tokens. Payload deliberately carries only opaque IDs
 * and an expiry - never NIC or any business data that could go stale after a booking modify.
 * Signed with an HMAC key that never leaves the Android Keystore.
 */
class QrTokenService {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun hmac(payload: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateKey())
        return mac.doFinal(payload)
    }

    fun sign(transactionId: String, bookingId: String, expiryMillis: Long): String {
        val payload = "$transactionId|$bookingId|$expiryMillis".toByteArray(Charsets.UTF_8)
        val payloadB64 = Base64.encodeToString(payload, FLAGS)
        val signatureB64 = Base64.encodeToString(hmac(payload), FLAGS)
        return "$payloadB64.$signatureB64"
    }

    fun verify(token: String): QrValidationResult {
        val parts = token.split(".")
        if (parts.size != 2) return QrValidationResult.Tampered

        val payload = decodeOrNull(parts[0]) ?: return QrValidationResult.Tampered
        val signature = decodeOrNull(parts[1]) ?: return QrValidationResult.Tampered

        if (!MessageDigest.isEqual(hmac(payload), signature)) return QrValidationResult.Tampered

        val fields = String(payload, Charsets.UTF_8).split("|")
        if (fields.size != 3) return QrValidationResult.Tampered
        val transactionId = fields[0]
        val bookingId = fields[1]
        val expiryMillis = fields[2].toLongOrNull() ?: return QrValidationResult.Tampered

        if (System.currentTimeMillis() > expiryMillis) return QrValidationResult.Expired

        return QrValidationResult.Valid(transactionId, bookingId)
    }

    private fun decodeOrNull(value: String): ByteArray? =
        try { Base64.decode(value, FLAGS) } catch (e: IllegalArgumentException) { null }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "smart_solar_qr_hmac_key"
        const val FLAGS = Base64.NO_WRAP or Base64.URL_SAFE
    }
}