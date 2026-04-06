package org.litepal.litepalsample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.litepal.SchemaValidationMode

@Composable
internal fun DiagnosticsScreen(uiState: SampleDataUiState, viewModel: SampleDataViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }
    val summary = uiState.diagnosticsSummary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard {
            Text("Runtime Diagnostics", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Generated hit count: ${summary.generatedHitCount}")
            Text("Generated contract violation count: ${summary.generatedContractViolationCount}")
            Text("Main-thread DB block (ms): ${summary.mainThreadBlockMs}")
            Text("Error policy: ${summary.errorPolicy}")
            Text("Crypto policy: ${summary.cryptoPolicy}")
            Text("Schema validation mode: ${summary.schemaValidationMode}")
            Text("Listener registered: ${summary.listenerRegistered}")
            Text("Preload status: ${summary.preloadStatus}")
            Text("Active DB path: ${summary.activeDatabasePath}")
            Text("Runtime options: ${summary.runtimeOptions}")
        }

        SectionCard {
            Text("Basic Actions", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.refreshDiagnostics() }) {
                    Text("Refresh")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.resetDiagnostics() }) {
                    Text("Reset Metrics")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.preloadDatabase() }) {
                Text("Preload DB")
            }
        }

        SectionCard {
            Text("Runtime Policies", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.applyStrictRuntimePolicies() }) {
                    Text("Apply Strict")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.applyCompatRuntimePolicies() }) {
                    Text("Apply Compat")
                }
            }
        }

        SectionCard {
            Text("Schema Validation", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.applySchemaValidationMode(SchemaValidationMode.STRICT) }) {
                    Text("STRICT")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.applySchemaValidationMode(SchemaValidationMode.LOG) }) {
                    Text("LOG")
                }
            }
        }

        SectionCard {
            Text("Database Listener", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.registerDatabaseLifecycleListener() }) {
                    Text("Register")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.unregisterDatabaseLifecycleListener() }) {
                    Text("Unregister")
                }
            }
        }

        SectionCard {
            Text("Multi Database", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.useSandboxDatabase() }) {
                    Text("Use Sandbox")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.useDefaultDatabase() }) {
                    Text("Use Default")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.deleteSandboxDatabase() }) {
                    Text("Delete Sandbox")
                }
                Button(modifier = Modifier.weight(1f), onClick = { viewModel.refreshTableNames() }) {
                    Text("Refresh Tables")
                }
            }
        }

        SectionCard {
            Text("Runtime Event Log", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            if (summary.eventLog.isEmpty()) {
                Text("No runtime events yet.")
            } else {
                summary.eventLog.forEach { event ->
                    Text("- $event")
                }
            }
        }
    }
}
