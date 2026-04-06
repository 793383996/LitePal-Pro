package org.litepal.litepalsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

internal object Routes {
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
fun LitePalSampleApp(
    dataViewModel: SampleDataViewModel,
    testDashboardViewModel: TestDashboardViewModel
) {
    val navController = rememberNavController()
    val dataState by dataViewModel.uiState.collectAsState()
    val dashboardState by testDashboardViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navBackStack by navController.currentBackStackEntryAsState()
    val route = navBackStack?.destination?.route ?: Routes.Home

    LaunchedEffect(dataState.message) {
        val message = dataState.message ?: return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(message) }
        dataViewModel.clearMessage()
    }

    LaunchedEffect(dashboardState.message) {
        val message = dashboardState.message ?: return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(message) }
        testDashboardViewModel.clearMessage()
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
                    if (dataState.loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    NavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        startDestination = Routes.Home
                    ) {
                        composable(Routes.Home) {
                            HomeScreen(
                                navController = navController,
                                dashboardState = dashboardState,
                                testDashboardViewModel = testDashboardViewModel
                            )
                        }
                        composable(Routes.Crud) { CrudScreen(dataState, dataViewModel) }
                        composable(Routes.Aggregate) { AggregateScreen(dataState, dataViewModel) }
                        composable(Routes.Tables) { TablesScreen(dataState, dataViewModel, navController) }
                        composable(Routes.Structure) { StructureScreen(dataState, dataViewModel) }
                        composable(Routes.Diagnostics) { DiagnosticsScreen(dataState, dataViewModel) }
                    }
                }
            }
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

