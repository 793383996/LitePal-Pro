package org.litepal.litepalsample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
internal fun HomeScreen(
    navController: NavHostController,
    dashboardState: TestDashboardUiState,
    testDashboardViewModel: TestDashboardViewModel
) {
    var modulesVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        modulesVisible = true
    }
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
            AnimatedVisibility(
                visible = modulesVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 280)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 280),
                        initialOffsetY = { fullHeight -> fullHeight / 4 }
                    )
            ) {
                TestTaskDashboardCard(
                    dashboard = dashboardState,
                    onCancel = { testDashboardViewModel.cancelRun() },
                    onRerun = { testDashboardViewModel.rerunFull() }
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = modulesVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = 80)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 300, delayMillis = 80),
                        initialOffsetY = { fullHeight -> fullHeight / 5 }
                    )
            ) {
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
        }
        itemsIndexed(entries) { index, (title, route) ->
            AnimatedVisibility(
                visible = modulesVisible,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 280, delayMillis = 150 + index * 60)
                ) + slideInVertically(
                    animationSpec = tween(durationMillis = 280, delayMillis = 150 + index * 60),
                    initialOffsetY = { fullHeight -> fullHeight / 6 }
                )
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.985f else 1f,
                    animationSpec = tween(durationMillis = 120),
                    label = "moduleCardScale"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { navController.navigate(route) },
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
}

@Composable
private fun TestTaskDashboardCard(
    dashboard: TestDashboardUiState,
    onCancel: () -> Unit,
    onRerun: () -> Unit
) {
    var expandedStackCaseId by remember { mutableStateOf<String?>(null) }
    val shouldCollapseCompletedTasks = dashboard.runStatus == TestRunUiStatus.FINISHED &&
        dashboard.totalCases > 0 &&
        dashboard.pendingCaseNames.isEmpty() &&
        dashboard.completedCases == dashboard.totalCases &&
        dashboard.failedCount == 0 &&
        dashboard.passedCount == dashboard.totalCases
    var completedTasksExpanded by remember(dashboard.runId, shouldCollapseCompletedTasks) {
        mutableStateOf(!shouldCollapseCompletedTasks)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 260)),
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

            AnimatedVisibility(
                visible = !dashboard.currentCaseName.isNullOrBlank(),
                enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                    expandVertically(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                    shrinkVertically(animationSpec = tween(durationMillis = 120))
            ) {
                Text(
                    text = "当前Case: ${dashboard.currentCaseName} | 已耗时 ${dashboard.currentCaseElapsedMs}ms",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedActionButton(
                    modifier = Modifier.weight(1f),
                    text = "取消当前任务",
                    onClick = onCancel,
                    enabled = dashboard.runStatus == TestRunUiStatus.RUNNING || dashboard.runStatus == TestRunUiStatus.CANCELLING
                )
                AnimatedActionButton(
                    modifier = Modifier.weight(1f),
                    text = "重跑 Full",
                    onClick = onRerun
                )
            }

            Text("已完成任务", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (dashboard.completedItems.isEmpty()) {
                Text("暂无完成任务", color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                AnimatedVisibility(
                    visible = shouldCollapseCompletedTasks && !completedTasksExpanded,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                        expandVertically(animationSpec = tween(durationMillis = 180)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                        shrinkVertically(animationSpec = tween(durationMillis = 120))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnimatedTextActionButton(
                            text = "查看已完成任务",
                            onClick = { completedTasksExpanded = true }
                        )
                        Text(
                            "所有测试用例执行完成且全部成功，已折叠已完成任务。",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !shouldCollapseCompletedTasks || completedTasksExpanded,
                    enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                        expandVertically(animationSpec = tween(durationMillis = 220)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                        shrinkVertically(animationSpec = tween(durationMillis = 160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shouldCollapseCompletedTasks) {
                            AnimatedTextActionButton(
                                text = "收起已完成任务",
                                onClick = { completedTasksExpanded = false }
                            )
                        }
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
                                        AnimatedTextActionButton(
                                            text = if (expandedStackCaseId == item.id) "收起堆栈" else "展开堆栈",
                                            onClick = {
                                                expandedStackCaseId = if (expandedStackCaseId == item.id) null else item.id
                                            }
                                        )
                                        AnimatedVisibility(
                                            visible = expandedStackCaseId == item.id,
                                            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                                                expandVertically(animationSpec = tween(durationMillis = 180)),
                                            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                                                shrinkVertically(animationSpec = tween(durationMillis = 120))
                                        ) {
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
        }
    }
}

@Composable
private fun AnimatedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "actionButtonScale"
    )
    Button(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        Text(text)
    }
}

@Composable
private fun AnimatedTextActionButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "textActionButtonScale"
    )
    TextButton(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Text(text)
    }
}
