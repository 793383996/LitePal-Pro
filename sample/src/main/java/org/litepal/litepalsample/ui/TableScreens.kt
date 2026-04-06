package org.litepal.litepalsample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
internal fun TablesScreen(
    uiState: SampleDataUiState,
    viewModel: SampleDataViewModel,
    navController: NavHostController
) {
    LaunchedEffect(Unit) { viewModel.refreshTableNames() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = { viewModel.refreshTableNames() }) {
                Text("Refresh Tables")
            }
            Button(modifier = Modifier.weight(1f), onClick = { navController.navigate(Routes.Diagnostics) }) {
                Text("Diagnostics")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.tableNames) { tableName ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectTable(tableName)
                            navController.navigate(Routes.Structure)
                        }
                ) {
                    Text(tableName, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun StructureScreen(uiState: SampleDataUiState, viewModel: SampleDataViewModel) {
    LaunchedEffect(uiState.selectedTable) { viewModel.refreshSelectedTableStructure() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val table = uiState.selectedTable ?: "(none)"
        Text("Table: $table", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.refreshSelectedTableStructure() }
        ) {
            Text("Refresh Structure")
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.selectedTableColumns) { column ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${column.name} (${column.type})", fontWeight = FontWeight.SemiBold)
                        Text("nullable=${column.nullable}, unique=${column.unique}, indexed=${column.indexed}")
                        Text("default=${column.defaultValue}")
                    }
                }
            }
        }
    }
}
