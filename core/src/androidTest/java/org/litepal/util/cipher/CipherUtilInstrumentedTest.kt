package org.litepal.util.cipher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal

@RunWith(AndroidJUnit4::class)
class CipherUtilInstrumentedTest {

    private lateinit var originalAesKey: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LitePal.initialize(context)
        originalAesKey = CipherUtil.aesKey
        CipherUtil.aesKey = "LitePalCipherTestKey"
    }

    @After
    fun tearDown() {
        CipherUtil.aesKey = originalAesKey
    }

    @Test
    fun aes_encrypt_and_decrypt_roundtrip_returns_original_plain_text() {
        val plainText = "LitePal Pro 123"
        val encrypted = CipherUtil.aesEncrypt(plainText)

        assertNotNull(encrypted)
        assertNotEquals(plainText, encrypted)
        assertEquals(plainText, CipherUtil.aesDecrypt(encrypted))
    }

    @Test
    fun aes_decrypt_keeps_raw_text_for_invalid_cipher_text_in_dual_read_mode() {
        assertEquals("invalid_cipher_text", CipherUtil.aesDecrypt("invalid_cipher_text"))
    }

    @Test
    fun empty_and_null_inputs_keep_passthrough_behavior() {
        assertNull(CipherUtil.aesEncrypt(null))
        assertEquals("", CipherUtil.aesEncrypt(""))
        assertNull(CipherUtil.aesDecrypt(null))
        assertEquals("", CipherUtil.aesDecrypt(""))
    }
}
