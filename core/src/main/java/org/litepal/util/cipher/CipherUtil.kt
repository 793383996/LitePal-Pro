/*
 * Copyright (C)  Tony Green, LitePal Framework Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litepal.util.cipher

import android.text.TextUtils
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalRuntime
import org.litepal.util.LitePalLog
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object CipherUtil {

    private const val TAG = "CipherUtil"

    private val DIGITS_UPPER = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )

    @Volatile
    private var hasLoggedMd5Warning = false

    @JvmField
    var aesKey: String = "LitePalKey"

    @JvmStatic
    fun aesEncrypt(plainText: String?): String? {
        if (TextUtils.isEmpty(plainText)) {
            return plainText
        }
        return try {
            when (LitePalRuntime.getCryptoPolicy()) {
                LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ -> AESCrypt.encryptLegacy(aesKey, plainText!!)
                LitePalCryptoPolicy.V2_WRITE_DUAL_READ -> AESCrypt.encryptV2(aesKey, plainText!!)
            }
        } catch (e: Exception) {
            LitePalRuntime.onError(TAG, "aesEncrypt", e)
            null
        }
    }

    @JvmStatic
    fun aesDecrypt(encryptedText: String?): String? {
        if (TextUtils.isEmpty(encryptedText)) {
            return encryptedText
        }
        return try {
            when (LitePalRuntime.getCryptoPolicy()) {
                LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ -> AESCrypt.decryptLegacy(aesKey, encryptedText!!)
                LitePalCryptoPolicy.V2_WRITE_DUAL_READ -> dualReadDecrypt(encryptedText!!)
            }
        } catch (e: Exception) {
            LitePalRuntime.onError(TAG, "aesDecrypt", e)
            null
        }
    }

    @JvmStatic
    fun md5Encrypt(plainText: String): String {
        logMd5Deprecation()
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(plainText.toByteArray(Charset.defaultCharset()))
            String(toHex(digest.digest()))
        } catch (e: NoSuchAlgorithmException) {
            LitePalRuntime.onError(TAG, "md5Encrypt", e)
            ""
        }
    }

    private fun dualReadDecrypt(cipherText: String): String {
        if (AESCrypt.isV2CipherText(cipherText)) {
            return AESCrypt.decryptV2(aesKey, cipherText)
        }
        return try {
            AESCrypt.decryptLegacy(aesKey, cipherText)
        } catch (_: Exception) {
            // Dual-read compatibility: keep original value if it is not legacy ciphertext.
            cipherText
        }
    }

    private fun logMd5Deprecation() {
        if (hasLoggedMd5Warning) {
            return
        }
        synchronized(this) {
            if (hasLoggedMd5Warning) {
                return
            }
            LitePalLog.w(TAG, "@Encrypt(MD5) is deprecated. Use it only for one-way digest scenarios.")
            hasLoggedMd5Warning = true
        }
    }

    private fun toHex(data: ByteArray): CharArray {
        val out = CharArray(data.size shl 1)
        var i = 0
        var j = 0
        while (i < data.size) {
            out[j++] = DIGITS_UPPER[(0xF0 and data[i].toInt()) ushr 4]
            out[j++] = DIGITS_UPPER[0x0F and data[i].toInt()]
            i++
        }
        return out
    }
}
