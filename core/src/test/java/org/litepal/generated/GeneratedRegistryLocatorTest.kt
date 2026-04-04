package org.litepal.generated

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePalRuntime
import org.litepal.LitePalRuntimeOptions

class GeneratedRegistryLocatorTest {

    @After
    fun tearDown() {
        System.clearProperty("litepal.generated.registry")
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
    }

    @Test
    fun hasRegistryShouldReturnFalseWhenNoGeneratedClassProvided() {
        assertFalse(GeneratedRegistryLocator.hasRegistry())
    }

    @Test
    fun registryShouldFailFastWhenGeneratedRegistryMissing() {
        System.setProperty("litepal.generated.registry", "org.litepal.generated.NotFoundRegistry")
        GeneratedRegistryLocator.resetForTesting()

        var thrown: Throwable? = null
        try {
            GeneratedRegistryLocator.registry()
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is IllegalStateException)
    }
}
