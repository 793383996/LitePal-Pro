package org.litepal.litepalsample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePal
import org.litepal.LitePalRuntime
import org.litepal.SchemaValidationMode
import org.litepal.litepalsample.model.Singer
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener
import org.litepal.util.DBUtility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val reflectionFallbackCount: Long = 0L,
    val mainThreadBlockMs: Long = 0L,
    val runtimeOptions: String = "",
    val errorPolicy: String = "",
    val cryptoPolicy: String = "",
    val generatedMetadataMode: String = "",
    val schemaValidationMode: String = "",
    val activeDatabasePath: String = "",
    val preloadStatus: String = "idle",
    val listenerRegistered: Boolean = false,
    val eventLog: List<String> = emptyList()
)

data class SampleUiState(
    val loading: Boolean = false,
    val singers: List<SingerRow> = emptyList(),
    val tableNames: List<String> = emptyList(),
    val selectedTable: String? = null,
    val selectedTableColumns: List<TableColumnRow> = emptyList(),
    val aggregateSummary: AggregateSummary = AggregateSummary(),
    val diagnosticsSummary: DiagnosticsSummary = DiagnosticsSummary(),
    val message: String? = null
)

class SampleViewModel : ViewModel() {

    companion object {
        private const val SANDBOX_DB_NAME = "sample_sandbox"
        private const val MAX_EVENT_LOG = 12
    }

    private val _uiState = MutableStateFlow(SampleUiState())
    val uiState: StateFlow<SampleUiState> = _uiState.asStateFlow()
    private var listenerRegistered = false
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
        // 核心流程（Core flow）：ViewModel 创建后立即加载主数据集。
        refreshSingers()
        refreshTableNames()
        refreshDiagnostics()
        refreshAggregate()
    }

    // 核心流程 1（Core flow 1）：首页展示的 CRUD 列表查询路径。
    fun refreshSingers() {
        runDb("Load singers") {
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
    }

    // 核心流程 2（Core flow 2）：创建实体并刷新关联视图数据。
    fun saveSinger(name: String, age: Int, isMale: Boolean) {
        runDb("Save singer") {
            val singer = Singer().apply {
                this.name = name
                this.age = age
                this.isMale = isMale
            }
            singer.save()
            refreshSingers()
            refreshAggregate()
            refreshDiagnostics()
        }
    }

    // 核心流程 3（Core flow 3）：更新路径。
    fun updateSingerAge(id: Long, age: Int) {
        runDb("Update singer") {
            Singer().apply { this.age = age }.update(id)
            refreshSingers()
            refreshAggregate()
            refreshDiagnostics()
        }
    }

    // 核心流程 4（Core flow 4）：删除路径。
    fun deleteSinger(id: Long) {
        runDb("Delete singer") {
            LitePal.delete(Singer::class.java, id)
            refreshSingers()
            refreshAggregate()
            refreshDiagnostics()
        }
    }

    // 核心流程 5（Core flow 5）：聚合 API 演示（count/max/min/sum/avg）。
    fun refreshAggregate(prefix: String = "") {
        runDb("Aggregate") {
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
    }

    // 核心流程 6（Core flow 6）：Schema/表发现演示。
    fun refreshTableNames() {
        runDb("Load tables") {
            val names = DBUtility.findAllTableNames(LitePal.getDatabase()).sorted()
            _uiState.update { current -> current.copy(tableNames = names) }
        }
    }

    fun selectTable(tableName: String) {
        _uiState.update { current -> current.copy(selectedTable = tableName) }
        refreshSelectedTableStructure()
    }

    // 核心流程 7（Core flow 7）：PRAGMA 表结构探查演示。
    fun refreshSelectedTableStructure() {
        val tableName = _uiState.value.selectedTable ?: return
        runDb("Load table structure") {
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
    }

    // 核心流程 8（Core flow 8）：运行时诊断与指标采集。
    fun refreshDiagnostics() {
        runDb("Diagnostics") {
            val options = LitePal.getRuntimeOptions()
            val activePath = runCatching { LitePal.getDatabase().path }.getOrElse { "unavailable" }
            _uiState.update { current ->
                current.copy(
                    diagnosticsSummary = DiagnosticsSummary(
                        generatedHitCount = LitePal.getGeneratedPathHitCount(),
                        reflectionFallbackCount = LitePal.getReflectionFallbackCount(),
                        mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs(),
                        runtimeOptions = options.toString(),
                        errorPolicy = LitePalRuntime.getErrorPolicy().name,
                        cryptoPolicy = LitePalRuntime.getCryptoPolicy().name,
                        generatedMetadataMode = options.generatedMetadataMode.name,
                        schemaValidationMode = options.schemaValidationMode.name,
                        activeDatabasePath = activePath,
                        preloadStatus = preloadStatus,
                        listenerRegistered = listenerRegistered,
                        eventLog = current.diagnosticsSummary.eventLog
                    )
                )
            }
        }
    }

    fun resetDiagnostics() {
        LitePal.resetRuntimeMetrics()
        refreshDiagnostics()
    }

    // 运行时能力演示（Runtime capability demo）：带回调的预加载（成功/失败）。
    fun preloadDatabase() {
        preloadStatus = "in_progress"
        appendEvent("Database preload requested")
        refreshDiagnostics()
        LitePal.preloadDatabase(object : DatabasePreloadListener {
            override fun onSuccess(path: String) {
                preloadStatus = "success"
                appendEvent("Database preload success: $path")
                refreshDiagnostics()
            }

            override fun onError(throwable: Throwable) {
                preloadStatus = "error"
                appendEvent("Database preload failed: ${throwable.message.orEmpty()}")
                _uiState.update { current ->
                    current.copy(message = "Preload failed: ${throwable.message.orEmpty()}")
                }
                refreshDiagnostics()
            }
        })
    }

    // 运行时能力演示（Runtime capability demo）：严格策略配置档。
    fun applyStrictRuntimePolicies() {
        runDb("Apply strict policies") {
            LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
            LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
            appendEvent("Runtime policy switched to STRICT + V2_WRITE_DUAL_READ")
            refreshDiagnostics()
        }
    }

    // 运行时能力演示（Runtime capability demo）：兼容策略配置档。
    fun applyCompatRuntimePolicies() {
        runDb("Apply compatibility policies") {
            LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
            LitePal.setCryptoPolicy(LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ)
            appendEvent("Runtime policy switched to COMPAT + LEGACY_WRITE_LEGACY_READ")
            refreshDiagnostics()
        }
    }

    fun applySchemaValidationMode(mode: SchemaValidationMode) {
        runDb("Switch schema validation mode") {
            val options = LitePal.getRuntimeOptions().copy(schemaValidationMode = mode)
            LitePal.setRuntimeOptions(options)
            appendEvent("Schema validation mode changed to ${mode.name}")
            refreshDiagnostics()
        }
    }

    // 运行时能力演示（Runtime capability demo）：生命周期监听器注册流程。
    fun registerDatabaseLifecycleListener() {
        runDb("Register database listener") {
            if (listenerRegistered) {
                appendEvent("Database listener already registered")
                refreshDiagnostics()
                return@runDb
            }
            LitePal.registerDatabaseListener(databaseListener)
            listenerRegistered = true
            appendEvent("Database listener registered")
            refreshDiagnostics()
        }
    }

    fun unregisterDatabaseLifecycleListener() {
        runDb("Unregister database listener") {
            LitePal.unregisterDatabaseListener()
            listenerRegistered = false
            appendEvent("Database listener unregistered")
            refreshDiagnostics()
        }
    }

    // 运行时能力演示（Runtime capability demo）：多数据库切换流程。
    fun useSandboxDatabase() {
        runDb("Switch to sandbox database") {
            LitePal.use(LitePalDB.fromDefault(SANDBOX_DB_NAME))
            LitePal.getDatabase()
            appendEvent("Using sandbox database: $SANDBOX_DB_NAME")
            refreshDataAfterDatabaseChange()
        }
    }

    fun useDefaultDatabase() {
        runDb("Switch to default database") {
            LitePal.useDefault()
            LitePal.getDatabase()
            appendEvent("Switched to default database")
            refreshDataAfterDatabaseChange()
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
            refreshDataAfterDatabaseChange()
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

    private fun refreshDataAfterDatabaseChange() {
        refreshSingers()
        refreshAggregate()
        refreshTableNames()
        refreshSelectedTableStructure()
        refreshDiagnostics()
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

    // 共享数据库执行管线（Shared DB execution pipeline）：集中处理线程/错误/加载状态。
    private fun runDb(operation: String, block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { current -> current.copy(loading = true) }
            runCatching { block() }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(message = "$operation failed: ${error.message.orEmpty()}")
                    }
                }
            _uiState.update { current -> current.copy(loading = false) }
        }
    }
}
