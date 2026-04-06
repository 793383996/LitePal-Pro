package org.litepal.litepalsample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeScreen(
    navController: NavHostController,
    dashboardState: TestDashboardUiState,
    testDashboardViewModel: TestDashboardViewModel
) {
    val entries = listOf(
        "CRUD Demo" to Routes.Crud,
        "Aggregate Demo" to Routes.Aggregate,
        "Tables" to Routes.Tables,
        "Runtime & Diagnostics" to Routes.Diagnostics
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TestTaskDashboardCard(
                dashboard = dashboardState,
                onCancel = { testDashboardViewModel.cancelRun() },
                onRerun = { testDashboardViewModel.rerunFull() },
                onSelectHistory = { runId -> testDashboardViewModel.selectHistory(runId) }
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LitePal Compose Sample",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Core features + runtime controls in one screen flow.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        items(entries) { (title, route) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(route) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Open $title screen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TestTaskDashboardCard(
    dashboard: TestDashboardUiState,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onSelectHistory: (String) -> Unit
) {
    var expandedStackCaseId by remember { mutableStateOf<String?>(null) }
    val selectedHistory = dashboard.history.firstOrNull { history -> history.runId == dashboard.selectedHistoryRunId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "测试任务模块（Auto Full）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "状态: ${dashboard.runStatus}  RunId: ${dashboard.runId ?: "-"}",
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "进度: ${dashboard.progressPercent}%  完成 ${dashboard.completedCases}/${dashboard.totalCases}  通过 ${dashboard.passedCount}  失败 ${dashboard.failedCount}",
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            LinearProgressIndicator(
                progress = { (dashboard.progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!dashboard.currentCaseName.isNullOrBlank()) {
                Text(
                    text = "当前Case: ${dashboard.currentCaseName} | 已耗时 ${dashboard.currentCaseElapsedMs}ms",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                    enabled = dashboard.runStatus == TestRunUiStatus.RUNNING || dashboard.runStatus == TestRunUiStatus.CANCELLING
                ) {
                    Text("取消当前任务")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onRerun
                ) {
                    Text("重跑 Full")
                }
            }

            Text("已完成任务", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (dashboard.completedItems.isEmpty()) {
                Text("暂无完成任务", color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                dashboard.completedItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${item.name} | ${item.status} | ${item.costMs}ms", fontWeight = FontWeight.Medium)
                            Text(
                                "assert=${item.assertions}, write=${item.recordsWritten}, update=${item.recordsUpdated}, delete=${item.recordsDeleted}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.threadName.isNotBlank()) {
                                Text("thread=${item.threadName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!item.errorSummary.isNullOrBlank()) {
                                Text("异常: ${item.errorSummary}", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = {
                                    expandedStackCaseId = if (expandedStackCaseId == item.id) null else item.id
                                }) {
                                    Text(if (expandedStackCaseId == item.id) "收起堆栈" else "展开堆栈")
                                }
                                if (expandedStackCaseId == item.id) {
                                    Text(
                                        text = item.errorStack.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("待处理 Case", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (dashboard.pendingCaseNames.isEmpty()) {
                Text("无待处理 Case", color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                dashboard.pendingCaseNames.forEach { name ->
                    Text("- $name", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Text("历史任务", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (dashboard.history.isEmpty()) {
                Text("暂无历史任务", color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                dashboard.history.forEach { history ->
                    val selected = history.runId == dashboard.selectedHistoryRunId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectHistory(history.runId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("RunId: ${history.runId}", fontWeight = FontWeight.Medium)
                            Text(
                                "${history.stressLevel} | ${if (history.cancelled) "CANCELLED" else "FINISHED"} | ${history.passedCount}/${history.totalCases}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "开始 ${formatEpochMs(history.startedAtEpochMs)} -> 结束 ${formatEpochMs(history.endedAtEpochMs)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        dashboard.history.firstOrNull()?.let { latest -> onSelectHistory(latest.runId) }
                    }
                ) {
                    Text("查看历史详情")
                }
            }

            if (selectedHistory != null) {
                Text(
                    "历史详情(${selectedHistory.runId.take(8)}): 完成 ${selectedHistory.cases.count { it.status != TestCaseUiStatus.PENDING && it.status != TestCaseUiStatus.RUNNING }}/${selectedHistory.totalCases}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (selectedHistory.pendingCaseNames.isNotEmpty()) {
                    Text(
                        "历史待处理: ${selectedHistory.pendingCaseNames.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

private fun formatEpochMs(epochMs: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
}
