package org.litepal.litepalsample.ui

enum class TestCaseUiStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    CANCELLED
}

enum class TestRunUiStatus {
    IDLE,
    RUNNING,
    CANCELLING,
    FINISHED,
    CANCELLED,
    CRASHED
}

data class TestCaseUiItem(
    val id: String,
    val name: String,
    val status: TestCaseUiStatus = TestCaseUiStatus.PENDING,
    val threadName: String = "",
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val costMs: Long = 0L,
    val assertions: Int = 0,
    val recordsWritten: Int = 0,
    val recordsUpdated: Int = 0,
    val recordsDeleted: Int = 0,
    val errorSummary: String? = null,
    val errorStack: String? = null
)

data class TestRunHistoryItem(
    val runId: String,
    val stressLevel: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val cancelled: Boolean,
    val totalCases: Int,
    val passedCount: Int,
    val failedCount: Int,
    val pendingCaseNames: List<String>,
    val cases: List<TestCaseUiItem>
)

data class TestDashboardUiState(
    val runStatus: TestRunUiStatus = TestRunUiStatus.IDLE,
    val runId: String? = null,
    val stressLevel: String = "HIGH",
    val totalCases: Int = 0,
    val completedCases: Int = 0,
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val progressPercent: Int = 0,
    val currentCaseName: String? = null,
    val currentCaseElapsedMs: Long = 0L,
    val completedItems: List<TestCaseUiItem> = emptyList(),
    val pendingCaseNames: List<String> = emptyList(),
    val lastFailure: TestCaseUiItem? = null,
    val history: List<TestRunHistoryItem> = emptyList(),
    val selectedHistoryRunId: String? = null,
    val message: String? = null
)
