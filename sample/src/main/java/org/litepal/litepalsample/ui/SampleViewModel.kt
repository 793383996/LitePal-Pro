package org.litepal.litepalsample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.litepal.LitePal
import org.litepal.litepalsample.model.Singer
import org.litepal.util.DBUtility

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
    val runtimeOptions: String = ""
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

    private val _uiState = MutableStateFlow(SampleUiState())
    val uiState: StateFlow<SampleUiState> = _uiState.asStateFlow()

    init {
        refreshSingers()
        refreshTableNames()
        refreshDiagnostics()
        refreshAggregate()
    }

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

    fun updateSingerAge(id: Long, age: Int) {
        runDb("Update singer") {
            Singer().apply { this.age = age }.update(id)
            refreshSingers()
            refreshAggregate()
            refreshDiagnostics()
        }
    }

    fun deleteSinger(id: Long) {
        runDb("Delete singer") {
            LitePal.delete(Singer::class.java, id)
            refreshSingers()
            refreshAggregate()
            refreshDiagnostics()
        }
    }

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

    fun refreshDiagnostics() {
        runDb("Diagnostics") {
            val options = LitePal.getRuntimeOptions()
            _uiState.update { current ->
                current.copy(
                    diagnosticsSummary = DiagnosticsSummary(
                        generatedHitCount = LitePal.getGeneratedPathHitCount(),
                        reflectionFallbackCount = LitePal.getReflectionFallbackCount(),
                        mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs(),
                        runtimeOptions = options.toString()
                    )
                )
            }
        }
    }

    fun resetDiagnostics() {
        LitePal.resetRuntimeMetrics()
        refreshDiagnostics()
    }

    fun preloadDatabase() {
        LitePal.preloadDatabase()
        refreshDiagnostics()
    }

    fun clearMessage() {
        _uiState.update { current -> current.copy(message = null) }
    }

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
