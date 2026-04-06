package org.litepal.sampletest.suite.dashboard_orchestration

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.sampletest.SampleTestRuntimeBootstrap
import org.litepal.litepalsample.stability.StartupStabilityTestRunner
import java.util.concurrent.atomic.AtomicBoolean

@MediumTest
@RunWith(AndroidJUnit4::class)
class DashboardOrchestrationInstrumentationTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
    }

    @Test(timeout = 900_000)
    fun lowStressRun_shouldProduceLifecycleClosedLoop() = runBlocking {
        val events = mutableListOf<StartupStabilityTestRunner.TestRunEvent>()
        val report = StartupStabilityTestRunner.runSuite(
            config = StartupStabilityTestRunner.DefaultConfig.value.copy(
                stressLevel = StartupStabilityTestRunner.StressLevel.LOW
            ),
            observer = { event ->
                synchronized(events) {
                    events += event
                }
            }
        )

        assertFalse(report.cancelled)
        assertTrue(report.totalCases > 0)
        assertEquals(report.totalCases, report.caseResults.size)
        assertEquals(report.totalCases, report.passedCount + report.failedCount)
        assertEquals(1, events.filterIsInstance<StartupStabilityTestRunner.TestRunEvent.RunStarted>().size)
        assertTrue(events.lastOrNull() is StartupStabilityTestRunner.TestRunEvent.RunFinished)

        val startedCaseIds = events
            .filterIsInstance<StartupStabilityTestRunner.TestRunEvent.CaseStarted>()
            .map { event -> event.trace.caseId ?: event.caseName }
            .toSet()
        val resultCaseIds = report.caseResults.map { result -> result.caseId }.toSet()
        assertEquals(resultCaseIds, startedCaseIds)
        assertEquals(report.caseResults.size, resultCaseIds.size)
    }

    @Test(timeout = 900_000)
    fun cancelledRun_shouldKeepPendingAndCompletedConsistent() = runBlocking {
        val cancelIssued = AtomicBoolean(false)
        var observedRunId: String? = null

        val report = StartupStabilityTestRunner.runSuite(
            config = StartupStabilityTestRunner.DefaultConfig.value.copy(
                stressLevel = StartupStabilityTestRunner.StressLevel.HIGH
            ),
            observer = { event ->
                when (event) {
                    is StartupStabilityTestRunner.TestRunEvent.RunStarted -> {
                        observedRunId = event.trace.runId
                        if (cancelIssued.compareAndSet(false, true)) {
                            StartupStabilityTestRunner.cancelCurrentRun(event.trace.runId)
                        }
                    }

                    is StartupStabilityTestRunner.TestRunEvent.CaseStarted -> {
                        val runId = event.trace.runId
                        if (cancelIssued.compareAndSet(false, true)) {
                            observedRunId = runId
                            StartupStabilityTestRunner.cancelCurrentRun(runId)
                        }
                    }

                    else -> Unit
                }
            }
        )

        assertTrue(cancelIssued.get())
        assertNotNull(observedRunId)
        assertEquals(observedRunId, report.runId)
        assertTrue(report.cancelled)
        assertEquals(report.totalCases, report.caseResults.size + report.pendingCaseNames.size)
        assertTrue(
            report.pendingCaseNames.isNotEmpty() ||
                report.caseResults.any { result -> result.status == StartupStabilityTestRunner.CaseStatus.CANCELLED }
        )
    }
}
