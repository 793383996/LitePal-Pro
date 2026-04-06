package org.litepal.litepalsample.stability

import java.util.concurrent.atomic.AtomicBoolean

internal class StartupStabilityRunnerCore(
    private val config: StartupStabilityTestRunner.StabilityConfig,
    private val runId: String,
    private val cancellationSignal: AtomicBoolean,
    private val observer: (StartupStabilityTestRunner.TestRunEvent) -> Unit = {}
) {
    fun runAll(): StartupStabilityTestRunner.RunMetric {
        val suite = StartupStabilityTestRunner.StartupStabilitySuite(
            config = config,
            runId = runId,
            cancellationSignal = cancellationSignal,
            observer = observer
        )
        return suite.runAll()
    }
}
