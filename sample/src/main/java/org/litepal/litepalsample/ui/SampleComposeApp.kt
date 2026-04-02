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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val Crud = "crud"
    const val Aggregate = "aggregate"
    const val Tables = "tables"
    const val Structure = "structure"
    const val Diagnostics = "diagnostics"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LitePalSampleApp(viewModel: SampleViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navBackStack by navController.currentBackStackEntryAsState()
    val route = navBackStack?.destination?.route ?: Routes.Home

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(message) }
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topTitle(route)) },
                actions = {
                    if (route != Routes.Home) {
                        TextButton(onClick = { navController.popBackStack(Routes.Home, false) }) {
                            Text("Home")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            NavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                startDestination = Routes.Home
            ) {
                composable(Routes.Home) { HomeScreen(navController) }
                composable(Routes.Crud) { CrudScreen(uiState, viewModel) }
                composable(Routes.Aggregate) { AggregateScreen(uiState, viewModel) }
                composable(Routes.Tables) { TablesScreen(uiState, viewModel, navController) }
                composable(Routes.Structure) { StructureScreen(uiState, viewModel) }
                composable(Routes.Diagnostics) { DiagnosticsScreen(uiState, viewModel) }
            }
        }
    }
}

@Composable
private fun HomeScreen(navController: NavHostController) {
    val entries = listOf(
        "CRUD Demo" to Routes.Crud,
        "Aggregate Demo" to Routes.Aggregate,
        "Tables" to Routes.Tables,
        "Diagnostics" to Routes.Diagnostics
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "LitePal Compose Sample",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Single-activity Navigation Compose demo for Core features.")
        }
        items(entries) { (title, route) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(route) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Open $title screen")
                }
            }
        }
    }
}

@Composable
private fun CrudScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
    var name by remember { mutableStateOf("compose_singer") }
    var age by remember { mutableStateOf("21") }
    var isMale by remember { mutableStateOf(true) }
    var updateId by remember { mutableStateOf("") }
    var updateAge by remember { mutableStateOf("30") }
    var deleteId by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Singer", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        TextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { isMale = true }) { Text(if (isMale) "Gender: Male" else "Set Male") }
            Button(onClick = { isMale = false }) { Text(if (!isMale) "Gender: Female" else "Set Female") }
            Button(onClick = {
                val validAge = age.toIntOrNull() ?: return@Button
                viewModel.saveSinger(name.trim(), validAge, isMale)
            }) { Text("Save") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text("Update / Delete", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = updateId,
                onValueChange = { updateId = it },
                label = { Text("ID") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = updateAge,
                onValueChange = { updateAge = it },
                label = { Text("New Age") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val id = updateId.toLongOrNull() ?: return@Button
                val newAge = updateAge.toIntOrNull() ?: return@Button
                viewModel.updateSingerAge(id, newAge)
            }) { Text("Update") }
            TextField(
                value = deleteId,
                onValueChange = { deleteId = it },
                label = { Text("Delete ID") },
                modifier = Modifier.width(140.dp)
            )
            Button(onClick = {
                val id = deleteId.toLongOrNull() ?: return@Button
                viewModel.deleteSinger(id)
            }) { Text("Delete") }
            Button(onClick = { viewModel.refreshSingers() }) { Text("Refresh") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text("Recent Singers", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uiState.singers, key = { it.id }) { singer ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "id=${singer.id}, ${singer.name}, age=${singer.age}, male=${singer.isMale}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AggregateScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
    var prefix by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aggregate over Singer.age", fontWeight = FontWeight.Bold)
        TextField(
            value = prefix,
            onValueChange = { prefix = it },
            label = { Text("Name prefix (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.refreshAggregate(prefix.trim()) }) { Text("Calculate") }
            Button(onClick = { viewModel.refreshAggregate() }) { Text("All") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val summary = uiState.aggregateSummary
        Text("Count: ${summary.count}")
        Text("Max: ${summary.max}")
        Text("Min: ${summary.min}")
        Text("Sum: ${summary.sum}")
        Text("Avg: ${"%.2f".format(summary.avg)}")
    }
}

@Composable
private fun TablesScreen(uiState: SampleUiState, viewModel: SampleViewModel, navController: NavHostController) {
    LaunchedEffect(Unit) { viewModel.refreshTableNames() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.refreshTableNames() }) { Text("Refresh Tables") }
            Button(onClick = { navController.navigate(Routes.Diagnostics) }) { Text("Diagnostics") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun StructureScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
    LaunchedEffect(uiState.selectedTable) { viewModel.refreshSelectedTableStructure() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val table = uiState.selectedTable ?: "(none)"
        Text("Table: $table", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.refreshSelectedTableStructure() }) { Text("Refresh Structure") }
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun DiagnosticsScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Runtime Diagnostics", fontWeight = FontWeight.Bold)
        Text("Generated hit count: ${uiState.diagnosticsSummary.generatedHitCount}")
        Text("Reflection fallback count: ${uiState.diagnosticsSummary.reflectionFallbackCount}")
        Text("Main-thread DB block (ms): ${uiState.diagnosticsSummary.mainThreadBlockMs}")
        Text("Runtime options: ${uiState.diagnosticsSummary.runtimeOptions}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.refreshDiagnostics() }) { Text("Refresh") }
            Button(onClick = { viewModel.resetDiagnostics() }) { Text("Reset Metrics") }
            Button(onClick = { viewModel.preloadDatabase() }) { Text("Preload DB") }
        }
    }
}

private fun topTitle(route: String): String {
    return when (route) {
        Routes.Crud -> "CRUD"
        Routes.Aggregate -> "Aggregate"
        Routes.Tables -> "Tables"
        Routes.Structure -> "Structure"
        Routes.Diagnostics -> "Diagnostics"
        else -> "LitePal Sample"
    }
}
