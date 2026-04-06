package org.litepal.generated

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePalRuntime
import org.litepal.LitePalRuntimeOptions
import org.litepal.crud.LitePalSupport

class GeneratedRegistryLocatorTest {

    @After
    fun tearDown() {
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
        LitePalRuntime.resetMetrics()
    }

    @Test
    fun hasRegistryShouldReturnFalseWhenNoGeneratedClassProvided() {
        assertFalse(GeneratedRegistryLocator.hasRegistry())
    }

    @Test
    fun registryShouldFailFastWhenGeneratedRegistryMissing() {
        LitePalRuntime.resetMetrics()
        var thrown: Throwable? = null
        try {
            GeneratedRegistryLocator.registry()
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is IllegalStateException)
        assertEquals(1L, LitePalRuntime.getGeneratedContractViolationCount())
    }

    @Test
    fun registryShouldReturnInstalledRegistryForTests() {
        GeneratedRegistryLocator.installRegistryForTesting(TestRegistry())
        assertTrue(GeneratedRegistryLocator.hasRegistry())
        assertTrue(GeneratedRegistryLocator.registry() is TestRegistry)
    }

    private class TestRegistry : LitePalGeneratedRegistry {
        override val schemaVersion: Int = 1
        override val schemaJson: String = "{}"
        override val schemaHash: String = "test"
        override val anchorClassName: String = "test.Anchor"
        override val anchorEntities: List<String> = emptyList()
        override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> = emptyMap()
    }
}
