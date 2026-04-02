package com.litepaltest.test.stress

import android.util.Log
import kotlin.math.roundToLong

object StressTestReporter {

    private const val TAG = "LitePalAndroidStress"
    private val records = mutableListOf<CaseRecord>()
    private val lock = Any()

    data class CaseRecord(
        val suite: String,
        val caseName: String,
        val seed: Long,
        val batchId: String,
        val threadId: Long,
        val success: Boolean,
        val costMs: Long,
        val error: String?
    )

    fun logProgress(suite: String, caseName: String, step: String, detail: String = "") {
        val payload = if (detail.isBlank()) {
            "CASE_PROGRESS|suite=$suite|caseName=$caseName|step=$step"
        } else {
            "CASE_PROGRESS|suite=$suite|caseName=$caseName|step=$step|detail=${sanitize(detail)}"
        }
        Log.i(TAG, payload)
    }

    fun runCase(
        suite: String,
        caseName: String,
        seed: Long,
        batchId: String,
        block: () -> Unit
    ) {
        val start = System.currentTimeMillis()
        var success = false
        var error: String? = null
        try {
            block()
            success = true
        } catch (t: Throwable) {
            val stack = t.stackTrace.firstOrNull()?.toString() ?: "n/a"
            error = "${t.javaClass.simpleName}: ${t.message ?: "no message"} @ $stack"
            throw t
        } finally {
            val record = CaseRecord(
                suite = suite,
                caseName = caseName,
                seed = seed,
                batchId = batchId,
                threadId = Thread.currentThread().id,
                success = success,
                costMs = System.currentTimeMillis() - start,
                error = error
            )
            synchronized(lock) {
                records.add(record)
            }
            val logLine = StringBuilder()
                .append("CASE_RESULT")
                .append("|suite=").append(suite)
                .append("|caseName=").append(caseName)
                .append("|seed=").append(seed)
                .append("|batch=").append(batchId)
                .append("|threadId=").append(record.threadId)
                .append("|success=").append(success)
                .append("|costMs=").append(record.costMs)
            if (error != null) {
                logLine.append("|error=").append(sanitize(error))
                Log.e(TAG, logLine.toString())
            } else {
                Log.i(TAG, logLine.toString())
            }
        }
    }

    fun logSuiteSummary(suite: String) {
        val suiteRecords = synchronized(lock) { records.filter { it.suite == suite } }
        logSummaryInternal(suite, suiteRecords)
    }

    fun logGlobalSummary() {
        val snapshot = synchronized(lock) { records.toList() }
        logSummaryInternal("ALL_SUPPLEMENT_CASES", snapshot)
    }

    private fun percentile(sortedValues: List<Long>, p: Double): Long {
        if (sortedValues.isEmpty()) {
            return 0L
        }
        val rank = ((p / 100.0) * (sortedValues.size - 1)).roundToLong().toInt()
        return sortedValues[rank.coerceIn(0, sortedValues.lastIndex)]
    }

    private fun sanitize(text: String): String {
        return text.replace("|", "/").replace('\n', ' ').replace('\r', ' ')
    }

    private fun logSummaryInternal(suite: String, summaryRecords: List<CaseRecord>) {
        if (summaryRecords.isEmpty()) {
            Log.i(TAG, "RUN_SUMMARY|suite=$suite|totalCases=0|passed=0|failed=0|totalMs=0|avgMs=0|minMs=0|maxMs=0|p50=0|p95=0")
            return
        }
        val costs = summaryRecords.map { it.costMs }.sorted()
        val total = costs.sum()
        val avg = total / costs.size
        val min = costs.first()
        val max = costs.last()
        val p50 = percentile(costs, 50.0)
        val p95 = percentile(costs, 95.0)
        val failed = summaryRecords.filterNot { it.success }

        Log.i(
            TAG,
            "RUN_SUMMARY|suite=$suite|totalCases=${summaryRecords.size}|passed=${summaryRecords.size - failed.size}|failed=${failed.size}|totalMs=$total|avgMs=$avg|minMs=$min|maxMs=$max|p50=$p50|p95=$p95"
        )

        val slowTop = summaryRecords.sortedByDescending { it.costMs }.take(5)
        for ((index, item) in slowTop.withIndex()) {
            Log.i(
                TAG,
                "SLOW_TOP|suite=$suite|rank=${index + 1}|caseName=${item.caseName}|batch=${item.batchId}|costMs=${item.costMs}"
            )
        }

        if (failed.isEmpty()) {
            Log.i(TAG, "FAILURES|suite=$suite|count=0")
            return
        }
        Log.e(TAG, "FAILURES|suite=$suite|count=${failed.size}")
        for (item in failed) {
            Log.e(
                TAG,
                "FAILURE_DETAIL|suite=$suite|caseName=${item.caseName}|batch=${item.batchId}|seed=${item.seed}|error=${sanitize(item.error ?: "unknown")}"
            )
        }
    }
}
