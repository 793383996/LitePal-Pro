package org.litepal.util.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntime
import org.litepal.exceptions.LitePalSupportException
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CipherUtilTest {

    @Before
    fun setUp() {
        LitePalRuntime.setErrorPolicy(LitePalErrorPolicy.COMPAT)
        LitePalRuntime.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
        CipherUtil.aesKey = "litepal-test-key"
    }

    @Test
    fun aesV2_roundTrip_shouldSucceed() {
        val encrypted = CipherUtil.aesEncrypt("hello-litepal")
        assertNotNull(encrypted)
        assertTrue(encrypted!!.startsWith("v2:"))
        assertEquals("hello-litepal", CipherUtil.aesDecrypt(encrypted))
    }

    @Test
    fun dualRead_shouldReadLegacyCipherText() {
        LitePalRuntime.setCryptoPolicy(LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ)
        val legacyCipherText = CipherUtil.aesEncrypt("legacy-value")
        assertNotNull(legacyCipherText)

        LitePalRuntime.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
        assertEquals("legacy-value", CipherUtil.aesDecrypt(legacyCipherText))
    }

    @Test
    fun dualRead_shouldFallbackToRawValueForPlainText() {
        val rawText = "not-encrypted"
        assertEquals(rawText, CipherUtil.aesDecrypt(rawText))
    }

    @Test(expected = LitePalSupportException::class)
    fun strictMode_shouldThrowWhenV2CipherCorrupted() {
        LitePalRuntime.setErrorPolicy(LitePalErrorPolicy.STRICT)
        CipherUtil.aesDecrypt("v2:corrupted-payload")
    }
}
