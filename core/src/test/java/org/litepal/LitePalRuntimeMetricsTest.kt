package org.litepal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LitePalRuntimeMetricsTest {

    @Before
    fun setUp() {
        LitePalRuntime.resetMetrics()
    }

    @After
    fun tearDown() {
        LitePalRuntime.resetMetrics()
    }

    @Test
    fun recordAndResetMetrics_shouldMaintainExpectedCounters() {
        LitePalRuntime.recordGeneratedPathHit("generated.test")
        LitePalRuntime.recordGeneratedPathHit("generated.test")
        LitePalRuntime.recordGeneratedContractViolation("contract.test")
        LitePalRuntime.onMainThreadDatabaseBlock(12)
        LitePalRuntime.onMainThreadDatabaseBlock(0)
        LitePalRuntime.onMainThreadDatabaseBlock(-3)

        assertEquals(2L, LitePalRuntime.getGeneratedPathHitCount())
        assertEquals(1L, LitePalRuntime.getGeneratedContractViolationCount())
        assertEquals(12L, LitePalRuntime.getMainThreadDbBlockTotalMs())

        LitePalRuntime.resetMetrics()

        assertEquals(0L, LitePalRuntime.getGeneratedPathHitCount())
        assertEquals(0L, LitePalRuntime.getGeneratedContractViolationCount())
        assertEquals(0L, LitePalRuntime.getMainThreadDbBlockTotalMs())
    }
}
