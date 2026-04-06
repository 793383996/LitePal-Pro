package org.litepal.sampletest.suite.stress_full

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.sampletest.SampleTestRuntimeBootstrap
import org.litepal.litepalsample.stability.StartupStabilityTestRunner

@LargeTest
@RunWith(AndroidJUnit4::class)
class StressFullSuiteInstrumentationTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
    }

    @Test(timeout = 1_200_000)
    fun fullRunnerHighStress_shouldPassWithoutUntrackedFailures() = runBlocking {
        val report = StartupStabilityTestRunner.runSuite(
            config = StartupStabilityTestRunner.DefaultConfig.value.copy(
                stressLevel = StartupStabilityTestRunner.StressLevel.HIGH
            )
        )

        assertFalse(report.cancelled)
        assertTrue(report.totalCases > 0)
        assertEquals(report.totalCases, report.caseResults.size)
        assertEquals(0, report.failedCount)
        assertEquals(report.totalCases, report.passedCount)
        assertTrue(report.caseResults.all { result -> result.status == StartupStabilityTestRunner.CaseStatus.PASSED })
    }
}
