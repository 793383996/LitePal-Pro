package org.litepal.litepalsample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntime
import org.litepal.SchemaValidationMode
import org.litepal.litepalsample.model.Singer
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener
import org.litepal.util.DBUtility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class SingerRow(
    val id: Long,
    val name: String,
    val age: Int,
    val isMale: Boolean
)

data class TableColumnRow(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val unique: Boolean,
    val indexed: Boolean,
    val defaultValue: String
)

data class AggregateSummary(
    val count: Int = 0,
    val max: Int = 0,
    val min: Int = 0,
    val sum: Int = 0,
    val avg: Double = 0.0
)

data class DiagnosticsSummary(
    val generatedHitCount: Long = 0L,
    val generatedContractViolationCount: Long = 0L,
    val mainThreadBlockMs: Long = 0L,
    val runtimeOptions: String = "",
    val errorPolicy: String = "",
    val cryptoPolicy: String = "",
    val schemaValidationMode: String = "",
    val activeDatabasePath: String = "",
    val preloadStatus: String = "idle",
    val listenerRegistered: Boolean = false,
    val eventLog: List<String> = emptyList()
)

data class SampleDataUiState(
    val loading: Boolean = false,
    val singers: List<SingerRow> = emptyList(),
    val tableNames: List<String> = emptyList(),
    val selectedTable: String? = null,
    val selectedTableColumns: List<TableColumnRow> = emptyList(),
    val aggregateSummary: AggregateSummary = AggregateSummary(),
    val diagnosticsSummary: DiagnosticsSummary = DiagnosticsSummary(),
    val message: String? = null
)

class SampleDataViewModel : ViewModel() {

    companion object {
        private const val SANDBOX_DB_NAME = "sample_sandbox"
        private const val MAX_EVENT_LOG = 12
    }

    private val _uiState = MutableStateFlow(SampleDataUiState())
    val uiState: StateFlow<SampleDataUiState> = _uiState.asStateFlow()
    private val loadingRefCount = AtomicInteger(0)
    @Volatile
    private var listenerRegistered = false
    @Volatile
    private var preloadStatus = "idle"

    private val databaseListener = object : DatabaseListener {
        override fun onCreate() {
            appendEvent("Database lifecycle callback: onCreate")
            refreshDiagnostics()
        }

        override fun onUpgrade(oldVersion: Int, newVersion: Int) {
            appendEvent("Database lifecycle callback: onUpgrade($oldVersion->$newVersion)")
            refreshDiagnostics()
        }
    }

    init {
        // Core flow: load dashboard data eagerly on first view model creation.
        refreshSingers()
        refreshTableNames()
        refreshDiagnostics()
        refreshAggregate()
    }

    fun refreshSingers() {
        runDb("Load singers") {
            loadSingersIntoState()
        }
    }

    fun saveSinger(name: String, age: Int, isMale: Boolean) {
        runDb("Save singer") {
            val singer = Singer().apply {
                this.name = name
                this.age = age
                this.isMale = isMale
            }
            singer.save()
            loadSingersIntoState()
            loadAggregateIntoState()
            loadDiagnosticsIntoState()
        }
    }

    fun updateSingerAge(id: Long, age: Int) {
        runDb("Update singer") {
            Singer().apply { this.age = age }.update(id)
            loadSingersIntoState()
            loadAggregateIntoState()
            loadDiagnosticsIntoState()
        }
    }

    fun deleteSinger(id: Long) {
        runDb("Delete singer") {
            LitePal.delete(Singer::class.java, id)
            loadSingersIntoState()
            loadAggregateIntoState()
            loadDiagnosticsIntoState()
        }
    }

    fun refreshAggregate(prefix: String = "") {
        runDb("Aggregate") {
            loadAggregateIntoState(prefix)
        }
    }

    fun refreshTableNames() {
        runDb("Load tables") {
            loadTableNamesIntoState()
        }
    }

    fun selectTable(tableName: String) {
        _uiState.update { current -> current.copy(selectedTable = tableName) }
        refreshSelectedTableStructure()
    }

    fun refreshSelectedTableStructure() {
        val tableName = _uiState.value.selectedTable
        if (tableName.isNullOrBlank()) {
            _uiState.update { current -> current.copy(selectedTableColumns = emptyList()) }
            return
        }
        runDb("Load table structure") {
            loadSelectedTableStructureIntoState(tableName)
        }
    }

    fun refreshDiagnostics() {
        runDb("Diagnostics") {
            loadDiagnosticsIntoState()
        }
    }

    fun resetDiagnostics() {
        runDb("Reset diagnostics") {
            LitePal.resetRuntimeMetrics()
            loadDiagnosticsIntoState()
        }
    }

    fun preloadDatabase() {
        preloadStatus = "in_progress"
        appendEvent("Database preload requested")
        refreshDiagnostics()
        LitePal.preloadDatabase(object : DatabasePreloadListener {
            override fun onSuccess(path: String) {
                preloadStatus = "success"
                appendEvent("Database preload success: $path")
                runDb("Diagnostics") {
                    loadDiagnosticsIntoState()
                }
            }

            override fun onError(throwable: Throwable) {
                preloadStatus = "error"
                appendEvent("Database preload failed: ${throwable.message.orEmpty()}")
                _uiState.update { current ->
                    current.copy(message = "Preload failed: ${throwable.message.orEmpty()}")
                }
                runDb("Diagnostics") {
                    loadDiagnosticsIntoState()
                }
            }
        })
    }

    fun applyStrictRuntimePolicies() {
        runDb("Apply strict policies") {
            LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
            LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
            appendEvent("Runtime policy switched to STRICT + V2_WRITE_DUAL_READ")
            loadDiagnosticsIntoState()
        }
    }

    fun applyCompatRuntimePolicies() {
        runDb("Apply compatibility policies") {
            LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
            LitePal.setCryptoPolicy(LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ)
            appendEvent("Runtime policy switched to COMPAT + LEGACY_WRITE_LEGACY_READ")
            loadDiagnosticsIntoState()
        }
    }

    fun applySchemaValidationMode(mode: SchemaValidationMode) {
        runDb("Switch schema validation mode") {
            val options = LitePal.getRuntimeOptions().copy(schemaValidationMode = mode)
            LitePal.setRuntimeOptions(options)
            appendEvent("Schema validation mode changed to ${mode.name}")
            loadDiagnosticsIntoState()
        }
    }

    fun registerDatabaseLifecycleListener() {
        runDb("Register database listener") {
            if (listenerRegistered) {
                appendEvent("Database listener already registered")
                loadDiagnosticsIntoState()
                return@runDb
            }
            LitePal.registerDatabaseListener(databaseListener)
            listenerRegistered = true
            appendEvent("Database listener registered")
            loadDiagnosticsIntoState()
        }
    }

    fun unregisterDatabaseLifecycleListener() {
        runDb("Unregister database listener") {
            LitePal.unregisterDatabaseListener()
            listenerRegistered = false
            appendEvent("Database listener unregistered")
            loadDiagnosticsIntoState()
        }
    }

    fun useSandboxDatabase() {
        runDb("Switch to sandbox database") {
            LitePal.use(LitePalDB.fromDefault(SANDBOX_DB_NAME))
            LitePal.getDatabase()
            appendEvent("Using sandbox database: $SANDBOX_DB_NAME")
            refreshDataAfterDatabaseChangeInCurrentTask()
        }
    }

    fun useDefaultDatabase() {
        runDb("Switch to default database") {
            LitePal.useDefault()
            LitePal.getDatabase()
            appendEvent("Switched to default database")
            refreshDataAfterDatabaseChangeInCurrentTask()
        }
    }

    fun deleteSandboxDatabase() {
        runDb("Delete sandbox database") {
            LitePal.useDefault()
            val deleted = LitePal.deleteDatabase(SANDBOX_DB_NAME)
            if (deleted) {
                appendEvent("Sandbox database deleted: $SANDBOX_DB_NAME")
            } else {
                appendEvent("Sandbox database not deleted (not found or busy): $SANDBOX_DB_NAME")
            }
            refreshDataAfterDatabaseChangeInCurrentTask()
        }
    }

    fun clearMessage() {
        _uiState.update { current -> current.copy(message = null) }
    }

    override fun onCleared() {
        if (listenerRegistered) {
            runCatching { LitePal.unregisterDatabaseListener() }
            listenerRegistered = false
        }
        super.onCleared()
    }

    private fun loadSingersIntoState() {
        val singers = LitePal.order("id desc").limit(50).find(Singer::class.java)
        _uiState.update { current ->
            current.copy(
                singers = singers.map { singer ->
                    SingerRow(
                        id = singer.id,
                        name = singer.name.orEmpty(),
                        age = singer.age,
                        isMale = singer.isMale
                    )
                }
            )
        }
    }

    private fun loadAggregateIntoState(prefix: String = "") {
        val likeArg = if (prefix.isBlank()) null else "${prefix}%"
        val count = if (likeArg == null) {
            LitePal.count(Singer::class.java)
        } else {
            LitePal.where("name like ?", likeArg).count(Singer::class.java)
        }
        val max = when {
            count == 0 -> 0
            likeArg == null -> LitePal.max(Singer::class.java, "age", Int::class.java)
            else -> LitePal.where("name like ?", likeArg).max(Singer::class.java, "age", Int::class.java)
        }
        val min = when {
            count == 0 -> 0
            likeArg == null -> LitePal.min(Singer::class.java, "age", Int::class.java)
            else -> LitePal.where("name like ?", likeArg).min(Singer::class.java, "age", Int::class.java)
        }
        val sum = when {
            count == 0 -> 0
            likeArg == null -> LitePal.sum(Singer::class.java, "age", Int::class.java)
            else -> LitePal.where("name like ?", likeArg).sum(Singer::class.java, "age", Int::class.java)
        }
        val avg = when {
            count == 0 -> 0.0
            likeArg == null -> LitePal.average(Singer::class.java, "age")
            else -> LitePal.where("name like ?", likeArg).average(Singer::class.java, "age")
        }
        _uiState.update { current ->
            current.copy(
                aggregateSummary = AggregateSummary(
                    count = count,
                    max = max,
                    min = min,
                    sum = sum,
                    avg = avg
                )
            )
        }
    }

    private fun loadTableNamesIntoState() {
        val names = DBUtility.findAllTableNames(LitePal.getDatabase()).sorted()
        _uiState.update { current -> current.copy(tableNames = names) }
    }

    private fun loadSelectedTableStructureIntoState(tableName: String) {
        val tableModel = DBUtility.findPragmaTableInfo(tableName, LitePal.getDatabase())
        val columns = tableModel.getColumnModels().map { column ->
            TableColumnRow(
                name = column.getColumnName().orEmpty(),
                type = column.getColumnType().orEmpty(),
                nullable = column.isNullable(),
                unique = column.isUnique(),
                indexed = column.hasIndex(),
                defaultValue = column.getDefaultValue().orEmpty()
            )
        }
        _uiState.update { current -> current.copy(selectedTableColumns = columns) }
    }

    private fun loadDiagnosticsIntoState() {
        val options = LitePal.getRuntimeOptions()
        val activePath = runCatching { LitePal.getDatabase().path }.getOrElse { "unavailable" }
        _uiState.update { current ->
            current.copy(
                diagnosticsSummary = DiagnosticsSummary(
                    generatedHitCount = LitePal.getGeneratedPathHitCount(),
                    generatedContractViolationCount = LitePal.getGeneratedContractViolationCount(),
                    mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs(),
                    runtimeOptions = options.toString(),
                    errorPolicy = LitePalRuntime.getErrorPolicy().name,
                    cryptoPolicy = LitePalRuntime.getCryptoPolicy().name,
                    schemaValidationMode = options.schemaValidationMode.name,
                    activeDatabasePath = activePath,
                    preloadStatus = preloadStatus,
                    listenerRegistered = listenerRegistered,
                    eventLog = current.diagnosticsSummary.eventLog
                )
            )
        }
    }

    private fun refreshDataAfterDatabaseChangeInCurrentTask() {
        loadSingersIntoState()
        loadAggregateIntoState()
        loadTableNamesIntoState()
        val selectedTable = _uiState.value.selectedTable
        val tableNames = _uiState.value.tableNames
        if (selectedTable.isNullOrBlank()) {
            _uiState.update { current -> current.copy(selectedTableColumns = emptyList()) }
        } else if (tableNames.contains(selectedTable)) {
            loadSelectedTableStructureIntoState(selectedTable)
        } else {
            _uiState.update { current ->
                current.copy(
                    selectedTable = null,
                    selectedTableColumns = emptyList()
                )
            }
        }
        loadDiagnosticsIntoState()
    }

    private fun appendEvent(message: String) {
        val now = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$now] $message"
        _uiState.update { current ->
            val nextLog = listOf(entry) + current.diagnosticsSummary.eventLog
            current.copy(
                diagnosticsSummary = current.diagnosticsSummary.copy(
                    eventLog = nextLog.take(MAX_EVENT_LOG),
                    listenerRegistered = listenerRegistered,
                    preloadStatus = preloadStatus
                )
            )
        }
    }

    private fun runDb(operation: String, block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val loading = loadingRefCount.incrementAndGet()
            _uiState.update { current -> current.copy(loading = loading > 0) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update { current ->
                    current.copy(message = "$operation failed: ${error.message.orEmpty()}")
                }
            } finally {
                val after = loadingRefCount.decrementAndGet().coerceAtLeast(0)
                if (after == 0) {
                    loadingRefCount.set(0)
                }
                _uiState.update { current -> current.copy(loading = after > 0) }
            }
        }
    }
}
