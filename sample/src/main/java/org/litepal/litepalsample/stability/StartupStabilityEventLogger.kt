package org.litepal.litepalsample.stability

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

internal class StartupStabilityEventLogger(
    private val config: StartupStabilityTestRunner.StabilityConfig
) {

    fun showFailureToastIfNeeded(context: Context, metric: StartupStabilityTestRunner.RunMetric) {
        if (!config.showFailureToast || metric.failedCount == 0) {
            return
        }
        val firstFailed = metric.failures.firstOrNull()?.caseName ?: "unknown_case"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "LitePal stability failed: ${metric.failedCount}, first=$firstFailed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun logRun(metric: StartupStabilityTestRunner.RunMetric) {
        Log.i(
            TAG,
            "RUN_START|runId=${metric.runId}|timestamp=${metric.startEpochMs}|buildType=${metric.buildType}|thread=${Thread.currentThread().name}|stressLevel=${metric.stressLevel}"
        )

        for (caseMetric in metric.caseMetrics) {
            val checkpoints = caseMetric.checkpoints.joinToString(",") { "${it.name}:${it.costMs}ms" }
            val base = StringBuilder()
                .append("CASE_RESULT")
                .append("|runId=").append(metric.runId)
                .append("|caseName=").append(caseMetric.caseName)
                .append("|success=").append(caseMetric.success)
                .append("|costMs=").append(caseMetric.costMs)
                .append("|recordsWritten=").append(caseMetric.recordsWritten)
                .append("|recordsUpdated=").append(caseMetric.recordsUpdated)
                .append("|recordsDeleted=").append(caseMetric.recordsDeleted)
                .append("|assertions=").append(caseMetric.assertions)
                .append("|checkpoints=").append(sanitize(checkpoints))

            if (caseMetric.error != null) {
                base.append("|error=").append(sanitize(caseMetric.error))
                Log.e(TAG, base.toString())
            } else {
                Log.i(TAG, base.toString())
            }
        }

        Log.i(
            TAG,
            "RUN_SUMMARY|runId=${metric.runId}|totalCases=${metric.totalCases}|passed=${metric.passedCount}|failed=${metric.failedCount}|cancelled=${metric.cancelled}|pending=${metric.pendingCaseNames.size}|totalMs=${metric.totalMs}|avgMs=${metric.avgMs}|minMs=${metric.minMs}|maxMs=${metric.maxMs}|p50=${metric.p50Ms}|p95=${metric.p95Ms}"
        )

        val topSlow = metric.caseMetrics
            .sortedByDescending { it.costMs }
            .take(config.maxSlowCaseTop.coerceAtLeast(1))
        for ((index, item) in topSlow.withIndex()) {
            Log.i(TAG, "SLOW_TOP|runId=${metric.runId}|rank=${index + 1}|caseName=${item.caseName}|costMs=${item.costMs}")
        }

        if (metric.failures.isEmpty()) {
            Log.i(TAG, "FAILURES|runId=${metric.runId}|count=0")
        } else {
            Log.e(TAG, "FAILURES|runId=${metric.runId}|count=${metric.failures.size}")
            for (failure in metric.failures) {
                Log.e(
                    TAG,
                    "FAILURE_DETAIL|runId=${metric.runId}|caseName=${failure.caseName}|message=${sanitize(failure.message)}|stackHead=${sanitize(failure.stackHead)}"
                )
            }
        }

        Log.i(TAG, "RUN_END|runId=${metric.runId}|timestamp=${metric.endEpochMs}|totalMs=${metric.totalMs}")
    }

    private fun sanitize(text: String): String {
        return text.replace("|", "/").replace('\n', ' ').replace('\r', ' ')
    }

    private companion object {
        private const val TAG = "LitePalStartupStability"
    }
}
