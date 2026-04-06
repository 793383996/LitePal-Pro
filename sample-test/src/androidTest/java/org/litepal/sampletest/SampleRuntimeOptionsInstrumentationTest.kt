package org.litepal.sampletest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePal
import org.litepal.LitePalRuntime
import org.litepal.MainThreadViolationPolicy
import org.litepal.SchemaValidationMode

@RunWith(AndroidJUnit4::class)
class SampleRuntimeOptionsInstrumentationTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
    }

    @Test
    fun runtimeOptions_shouldUseStrictDefaultsForSample() {
        val options = LitePal.getRuntimeOptions()

        assertFalse(options.allowMainThreadAccess)
        assertEquals(MainThreadViolationPolicy.THROW, options.mainThreadViolationPolicy)
        assertEquals(SchemaValidationMode.STRICT, options.schemaValidationMode)
        assertNotNull(options.queryExecutor)
        assertNotNull(options.transactionExecutor)
        assertEquals(LitePalErrorPolicy.STRICT, LitePalRuntime.getErrorPolicy())
        assertEquals(LitePalCryptoPolicy.V2_WRITE_DUAL_READ, LitePalRuntime.getCryptoPolicy())
    }
}




