package org.litepal.litepalsample

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.GeneratedMetadataMode
import org.litepal.LitePal
import org.litepal.MainThreadViolationPolicy

@RunWith(AndroidJUnit4::class)
class SampleRuntimeOptionsInstrumentationTest {

    @Test
    fun runtimeOptions_shouldUseStrictDefaultsForSample() {
        val options = LitePal.getRuntimeOptions()

        assertFalse(options.allowMainThreadAccess)
        assertEquals(MainThreadViolationPolicy.THROW, options.mainThreadViolationPolicy)
        assertEquals(GeneratedMetadataMode.REQUIRED, options.generatedMetadataMode)
        assertNotNull(options.queryExecutor)
        assertNotNull(options.transactionExecutor)
    }
}
