package org.litepal.litepalsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.litepal.SchemaValidationMode

private object Routes {
    const val Home = "home"
    const val Crud = "crud"
    const val Aggregate = "aggregate"
    const val Tables = "tables"
    const val Structure = "structure"
    const val Diagnostics = "diagnostics"
}

private val SampleColorScheme = lightColorScheme(
    primary = Color(0xFF1E5E96),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E9FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF087A62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBEFE5),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFFB95E00),
    onTertiary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE3ECF6),
    onSurfaceVariant = Color(0xFF3B4652)
)

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

    MaterialTheme(colorScheme = SampleColorScheme) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(topTitle(route), fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    actions = {
                        if (route != Routes.Home) {
                            TextButton(onClick = { navController.popBackStack(Routes.Home, false) }) {
                                Text("Home", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    )
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
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
    }
}

@Composable
private fun HomeScreen(navController: NavHostController) {
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
private fun CrudScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
    var name by remember { mutableStateOf("compose_singer") }
    var age by remember { mutableStateOf("21") }
    var isMale by remember { mutableStateOf(true) }
    var updateId by remember { mutableStateOf("") }
    var updateAge by remember { mutableStateOf("30") }
    var deleteId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Text("Create Singer", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(modifier = Modifier.weight(1f), onClick = { isMale = true }) {
                        Text(if (isMale) "Male" else "Set Male")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { isMale = false }) {
                        Text(if (!isMale) "Female" else "Set Female")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val validAge = age.toIntOrNull() ?: return@Button
                        viewModel.saveSinger(name.trim(), validAge, isMale)
                    }
                ) {
                    Text("Save Singer")
                }
            }
        }

        item {
            SectionCard {
                Text("Update / Delete", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = updateId,
                        onValueChange = { updateId = it },
                        label = { Text("ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = updateAge,
                        onValueChange = { updateAge = it },
                        label = { Text("New Age") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = updateId.toLongOrNull() ?: return@Button
                            val newAge = updateAge.toIntOrNull() ?: return@Button
                            viewModel.updateSingerAge(id, newAge)
                        }
                    ) { Text("Update") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.refreshSingers() }
                    ) { Text("Refresh") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = deleteId,
                    onValueChange = { deleteId = it },
                    label = { Text("Delete ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val id = deleteId.toLongOrNull() ?: return@Button
                        viewModel.deleteSinger(id)
                    }
                ) { Text("Delete Singer") }
            }
        }

        item {
            Text("Recent Singers", fontWeight = FontWeight.Bold)
        }

        if (uiState.singers.isEmpty()) {
            item {
                Text("No singers yet. Create one above to start.")
            }
        } else {
            items(uiState.singers, key = { it.id }) { singer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard {
            Text("Aggregate over Singer.age", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("Name prefix (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.refreshAggregate(prefix.trim()) }
                ) { Text("Calculate") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.refreshAggregate() }
                ) { Text("All Data") }
            }
        }

        SectionCard {
            val summary = uiState.aggregateSummary
            Text("Summary", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Count: ${summary.count}")
            Text("Max: ${summary.max}")
            Text("Min: ${summary.min}")
            Text("Sum: ${summary.sum}")
            Text("Avg: ${"%.2f".format(summary.avg)}")
        }
    }
}

@Composable
private fun TablesScreen(uiState: SampleUiState, viewModel: SampleViewModel, navController: NavHostController) {
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
private fun StructureScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
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

@Composable
private fun DiagnosticsScreen(uiState: SampleUiState, viewModel: SampleViewModel) {
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
            Text("Reflection fallback count: ${summary.reflectionFallbackCount}")
            Text("Main-thread DB block (ms): ${summary.mainThreadBlockMs}")
            Text("Error policy: ${summary.errorPolicy}")
            Text("Crypto policy: ${summary.cryptoPolicy}")
            Text("Generated metadata mode: ${summary.generatedMetadataMode}")
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

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
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
