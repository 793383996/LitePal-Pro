package org.litepal.litepalsample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.litepal.litepalsample.stability.StartupStabilityTestRunner

class TestDashboardViewModel : ViewModel() {

    companion object {
        private const val MAX_HISTORY_RUNS = 10
    }

    private val _uiState = MutableStateFlow(TestDashboardUiState())
    val uiState: StateFlow<TestDashboardUiState> = _uiState.asStateFlow()
    private var autoRunTriggered = false
    private var testRunJob: Job? = null
    private var caseElapsedTickerJob: Job? = null
    private var activeTestRunId: String? = null
    private val activeCaseMap = LinkedHashMap<String, TestCaseUiItem>()

    fun startAutoFullRunIfNeeded() {
        if (autoRunTriggered) {
            return
        }
        autoRunTriggered = true
        rerunFull()
    }

    fun rerunFull() {
        if (testRunJob?.isActive == true) {
            _uiState.update { current -> current.copy(message = "Test suite is already running.") }
            return
        }
        testRunJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                StartupStabilityTestRunner.runSuite(
                    config = StartupStabilityTestRunner.DefaultConfig.value.copy(
                        stressLevel = StartupStabilityTestRunner.StressLevel.HIGH
                    ),
                    observer = ::handleTestRunEvent
                )
            }.onFailure { throwable ->
                val runId = activeTestRunId ?: "unknown"
                val crashedCase = TestCaseUiItem(
                    id = "run_crash",
                    name = "Run crashed",
                    status = TestCaseUiStatus.FAILED,
                    errorSummary = throwable.message,
                    errorStack = throwable.stackTraceToString()
                )
                _uiState.update { current ->
                    current.copy(
                        runStatus = TestRunUiStatus.CRASHED,
                        runId = runId,
                        lastFailure = crashedCase,
                        currentCaseName = null,
                        message = "Full suite crashed: ${throwable.message.orEmpty()}"
                    )
                }
            }
        }
    }

    fun cancelRun() {
        val runId = activeTestRunId ?: return
        StartupStabilityTestRunner.cancelCurrentRun(runId)
        _uiState.update { current ->
            current.copy(
                runStatus = TestRunUiStatus.CANCELLING
            )
        }
    }

    fun selectHistory(runId: String) {
        _uiState.update { current ->
            current.copy(selectedHistoryRunId = runId)
        }
    }

    fun clearMessage() {
        _uiState.update { current -> current.copy(message = null) }
    }

    override fun onCleared() {
        activeTestRunId?.let { runId ->
            StartupStabilityTestRunner.cancelCurrentRun(runId)
        }
        stopCaseElapsedTicker()
        testRunJob?.cancel()
        super.onCleared()
    }

    private fun handleTestRunEvent(event: StartupStabilityTestRunner.TestRunEvent) {
        when (event) {
            is StartupStabilityTestRunner.TestRunEvent.RunStarted -> {
                stopCaseElapsedTicker()
                activeTestRunId = event.trace.runId
                activeCaseMap.clear()
                event.pendingCaseNames.forEach { caseName ->
                    activeCaseMap[caseName] = TestCaseUiItem(
                        id = caseName,
                        name = caseName,
                        status = TestCaseUiStatus.PENDING
                    )
                }
                _uiState.update { current ->
                    current
                        .copy(
                            runStatus = TestRunUiStatus.RUNNING,
                            runId = event.trace.runId,
                            stressLevel = event.stressLevel.name,
                            totalCases = event.totalCases,
                            currentCaseName = null,
                            currentCaseElapsedMs = 0L
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
            }

            is StartupStabilityTestRunner.TestRunEvent.CaseStarted -> {
                val startedAt = event.trace.timestampEpochMs
                val caseId = event.trace.caseId ?: event.caseName
                val existing = activeCaseMap[event.trace.caseId]
                activeCaseMap[caseId] = (existing ?: TestCaseUiItem(
                    id = caseId,
                    name = event.caseName
                )).copy(
                    status = TestCaseUiStatus.RUNNING,
                    threadName = event.trace.threadName,
                    startEpochMs = startedAt,
                    endEpochMs = null,
                    costMs = 0L,
                    errorSummary = null,
                    errorStack = null
                )
                _uiState.update { current ->
                    current
                        .copy(
                            currentCaseName = event.caseName,
                            currentCaseElapsedMs = 0L
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
                ensureCaseElapsedTicker()
            }

            is StartupStabilityTestRunner.TestRunEvent.Checkpoint -> {
                val caseId = event.trace.caseId ?: return
                val existing = activeCaseMap[caseId] ?: return
                val nextCost = existing.startEpochMs?.let { start ->
                    (event.trace.timestampEpochMs - start).coerceAtLeast(existing.costMs)
                } ?: (existing.costMs + event.costMs)
                activeCaseMap[caseId] = existing.copy(costMs = nextCost)
                _uiState.update { current ->
                    current
                        .copy(
                            currentCaseName = existing.name,
                            currentCaseElapsedMs = nextCost
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
            }

            is StartupStabilityTestRunner.TestRunEvent.CasePassed -> {
                stopCaseElapsedTicker()
                val caseItem = event.result.toCaseUiItem()
                activeCaseMap[event.result.caseId] = caseItem
                _uiState.update { current ->
                    current
                        .copy(
                            currentCaseName = null,
                            currentCaseElapsedMs = 0L
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
            }

            is StartupStabilityTestRunner.TestRunEvent.CaseFailed -> {
                stopCaseElapsedTicker()
                val caseItem = event.result.toCaseUiItem()
                activeCaseMap[event.result.caseId] = caseItem
                _uiState.update { current ->
                    current
                        .copy(
                            currentCaseName = null,
                            currentCaseElapsedMs = 0L,
                            lastFailure = caseItem
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
            }

            is StartupStabilityTestRunner.TestRunEvent.RunFinished -> {
                stopCaseElapsedTicker()
                finalizeRun(event.report, TestRunUiStatus.FINISHED)
            }

            is StartupStabilityTestRunner.TestRunEvent.RunCancelled -> {
                stopCaseElapsedTicker()
                finalizeRun(event.report, TestRunUiStatus.CANCELLED)
            }

            is StartupStabilityTestRunner.TestRunEvent.RunCrashed -> {
                stopCaseElapsedTicker()
                val crashItem = TestCaseUiItem(
                    id = "run_crashed",
                    name = "run_crashed",
                    status = TestCaseUiStatus.FAILED,
                    errorSummary = event.error.summary,
                    errorStack = event.error.stackTrace
                )
                _uiState.update { current ->
                    current.copy(
                        runStatus = TestRunUiStatus.CRASHED,
                        runId = event.trace.runId,
                        currentCaseName = null,
                        lastFailure = crashItem,
                        message = "Full suite crashed: ${event.error.summary}"
                    )
                }
                activeTestRunId = null
            }
        }
    }

    private fun finalizeRun(report: StartupStabilityTestRunner.TestRunReport, status: TestRunUiStatus) {
        activeCaseMap.clear()
        report.caseResults.forEach { result ->
            activeCaseMap[result.caseId] = result.toCaseUiItem()
        }
        report.pendingCaseNames.forEach { pendingCase ->
            if (!activeCaseMap.containsKey(pendingCase)) {
                activeCaseMap[pendingCase] = TestCaseUiItem(
                    id = pendingCase,
                    name = pendingCase,
                    status = TestCaseUiStatus.PENDING
                )
            }
        }
        val historyItem = TestRunHistoryItem(
            runId = report.runId,
            stressLevel = report.stressLevel.name,
            startedAtEpochMs = report.startEpochMs,
            endedAtEpochMs = report.endEpochMs,
            cancelled = report.cancelled,
            totalCases = report.totalCases,
            passedCount = report.passedCount,
            failedCount = report.failedCount,
            pendingCaseNames = report.pendingCaseNames,
            cases = activeCaseMap.values.toList()
        )
        _uiState.update { current ->
            val history = (listOf(historyItem) + current.history.filterNot { it.runId == report.runId })
                .take(MAX_HISTORY_RUNS)
            val selectedHistory = current.selectedHistoryRunId ?: history.firstOrNull()?.runId
            current
                .copy(
                    runStatus = status,
                    runId = report.runId,
                    stressLevel = report.stressLevel.name,
                    totalCases = report.totalCases,
                    currentCaseName = null,
                    currentCaseElapsedMs = 0L,
                    history = history,
                    selectedHistoryRunId = selectedHistory
                )
                .mergeFromCaseMap(activeCaseMap)
        }
        activeTestRunId = null
    }

    private fun ensureCaseElapsedTicker() {
        if (caseElapsedTickerJob?.isActive == true) {
            return
        }
        caseElapsedTickerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1_000L)
                val runningCase = activeCaseMap.values.firstOrNull { it.status == TestCaseUiStatus.RUNNING } ?: break
                val startEpochMs = runningCase.startEpochMs ?: continue
                val elapsedMs = (System.currentTimeMillis() - startEpochMs).coerceAtLeast(runningCase.costMs)
                activeCaseMap[runningCase.id] = runningCase.copy(costMs = elapsedMs)
                _uiState.update { current ->
                    current
                        .copy(
                            currentCaseName = runningCase.name,
                            currentCaseElapsedMs = elapsedMs
                        )
                        .mergeFromCaseMap(activeCaseMap)
                }
            }
            caseElapsedTickerJob = null
        }
    }

    private fun stopCaseElapsedTicker() {
        caseElapsedTickerJob?.cancel()
        caseElapsedTickerJob = null
    }

    private fun TestDashboardUiState.mergeFromCaseMap(caseMap: LinkedHashMap<String, TestCaseUiItem>): TestDashboardUiState {
        val orderedCases = caseMap.values.toList()
        val completedItems = orderedCases.filter { item ->
            item.status == TestCaseUiStatus.PASSED ||
                item.status == TestCaseUiStatus.FAILED ||
                item.status == TestCaseUiStatus.CANCELLED
        }
        val pendingCases = orderedCases.filter { item -> item.status == TestCaseUiStatus.PENDING }
        val passedCount = orderedCases.count { item -> item.status == TestCaseUiStatus.PASSED }
        val failedCount = orderedCases.count { item -> item.status == TestCaseUiStatus.FAILED }
        val total = if (totalCases <= 0) orderedCases.size else totalCases
        val progress = if (total <= 0) {
            0
        } else {
            ((completedItems.size * 100.0) / total).toInt().coerceIn(0, 100)
        }
        return copy(
            completedItems = completedItems,
            pendingCaseNames = pendingCases.map { item -> item.name },
            completedCases = completedItems.size,
            passedCount = passedCount,
            failedCount = failedCount,
            progressPercent = progress,
            lastFailure = completedItems.lastOrNull { item -> item.status == TestCaseUiStatus.FAILED } ?: lastFailure
        )
    }

    private fun StartupStabilityTestRunner.TestCaseResult.toCaseUiItem(): TestCaseUiItem {
        return TestCaseUiItem(
            id = caseId,
            name = caseName,
            status = when (status) {
                StartupStabilityTestRunner.CaseStatus.PASSED -> TestCaseUiStatus.PASSED
                StartupStabilityTestRunner.CaseStatus.FAILED -> TestCaseUiStatus.FAILED
                StartupStabilityTestRunner.CaseStatus.CANCELLED -> TestCaseUiStatus.CANCELLED
            },
            threadName = threadName,
            startEpochMs = startEpochMs,
            endEpochMs = endEpochMs,
            costMs = costMs,
            assertions = assertions,
            recordsWritten = recordsWritten,
            recordsUpdated = recordsUpdated,
            recordsDeleted = recordsDeleted,
            errorSummary = error?.summary,
            errorStack = error?.stackTrace
        )
    }
}
