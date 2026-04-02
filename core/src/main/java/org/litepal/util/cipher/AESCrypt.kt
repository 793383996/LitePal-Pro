/*
 *   Copyright (c) 2014 Scott Alexander-Bown
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.litepal.util.cipher

import android.util.Base64
import android.util.Log
import java.io.UnsupportedEncodingException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESCrypt {
    private const val TAG = "AESCrypt"
    private const val AES_MODE_LEGACY = "AES/CBC/PKCS7Padding"
    private const val AES_MODE_GCM = "AES/GCM/NoPadding"
    private const val CHARSET = "UTF-8"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val V2_PREFIX = "v2:"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private val ivBytes = byteArrayOf(
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00
    )
    private val secureRandom = SecureRandom()

    @JvmField
    var DEBUG_LOG_ENABLED = false

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val bytes = password.toByteArray(Charsets.UTF_8)
        digest.update(bytes, 0, bytes.size)
        val key = digest.digest()
        log("SHA-256 key ", key)
        return SecretKeySpec(key, "AES")
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(password: String, message: String): String {
        return encryptLegacy(password, message)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encryptLegacy(password: String, message: String): String {
        try {
            val key = generateKey(password)
            log("message", message)
            val cipherText = encrypt(key, ivBytes, message.toByteArray(Charsets.UTF_8))
            val encoded = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            log("Base64.NO_WRAP", encoded)
            return encoded
        } catch (e: UnsupportedEncodingException) {
            if (DEBUG_LOG_ENABLED) Log.e(TAG, "UnsupportedEncodingException ", e)
            throw GeneralSecurityException(e)
        }
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(key: SecretKeySpec, iv: ByteArray, message: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE_LEGACY)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val cipherText = cipher.doFinal(message)
        log("cipherText", cipherText)
        return cipherText
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(password: String, base64EncodedCipherText: String): String {
        return decryptLegacy(password, base64EncodedCipherText)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decryptLegacy(password: String, base64EncodedCipherText: String): String {
        try {
            val key = generateKey(password)
            log("base64EncodedCipherText", base64EncodedCipherText)
            val decodedCipherText = Base64.decode(base64EncodedCipherText, Base64.NO_WRAP)
            log("decodedCipherText", decodedCipherText)
            val decryptedBytes = decrypt(key, ivBytes, decodedCipherText)
            log("decryptedBytes", decryptedBytes)
            val message = String(decryptedBytes, Charsets.UTF_8)
            log("message", message)
            return message
        } catch (e: UnsupportedEncodingException) {
            if (DEBUG_LOG_ENABLED) Log.e(TAG, "UnsupportedEncodingException ", e)
            throw GeneralSecurityException(e)
        }
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(key: SecretKeySpec, iv: ByteArray, decodedCipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE_LEGACY)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        val decryptedBytes = cipher.doFinal(decodedCipherText)
        log("decryptedBytes", decryptedBytes)
        return decryptedBytes
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encryptV2(password: String, message: String): String {
        val key = generateKey(password)
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_MODE_GCM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        val cipherBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(cipherBytes, 0, payload, iv.size, cipherBytes.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        return "$V2_PREFIX$encoded"
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decryptV2(password: String, cipherText: String): String {
        if (!isV2CipherText(cipherText)) {
            throw GeneralSecurityException("Cipher text is not v2 format.")
        }
        val encodedPayload = cipherText.removePrefix(V2_PREFIX)
        val payload = Base64.decode(encodedPayload, Base64.NO_WRAP)
        if (payload.size <= GCM_IV_LENGTH) {
            throw GeneralSecurityException("Invalid v2 cipher text payload.")
        }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedData = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val key = generateKey(password)
        val cipher = Cipher.getInstance(AES_MODE_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(encryptedData)
        return String(plainBytes, Charsets.UTF_8)
    }

    @JvmStatic
    fun isV2CipherText(cipherText: String?): Boolean {
        return cipherText != null && cipherText.startsWith(V2_PREFIX)
    }

    private fun log(what: String, bytes: ByteArray) {
        if (DEBUG_LOG_ENABLED) {
            Log.d(TAG, "$what[${bytes.size}] [${bytesToHex(bytes)}]")
        }
    }

    private fun log(what: String, value: String) {
        if (DEBUG_LOG_ENABLED) {
            Log.d(TAG, "$what[${value.length}] [$value]")
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', 'A', 'B', 'C', 'D', 'E', 'F'
        )
        val hexChars = CharArray(bytes.size * 2)
        var j = 0
        while (j < bytes.size) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            j++
        }
        return String(hexChars)
    }
}
