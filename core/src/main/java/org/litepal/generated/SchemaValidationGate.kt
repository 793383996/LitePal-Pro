package org.litepal.generated

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import org.litepal.LitePalRuntime
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.tablemanager.model.TableModel
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import java.util.Locale

object SchemaValidationGate {

    private const val TAG = "SchemaValidationGate"
    private const val MASTER_TABLE = "litepal_master"
    private const val MASTER_COLUMN_ANCHOR = "anchor"
    private const val MASTER_COLUMN_SCHEMA_VERSION = "schema_version"
    private const val MASTER_COLUMN_SCHEMA_HASH = "schema_hash"
    private const val MASTER_COLUMN_UPDATED_AT = "updated_at"

    private data class ValidationSession(
        val actualTables: Set<String>,
        val tableModelCache: MutableMap<String, TableModel>
    )
    private data class PersistedSchemaMarker(
        val schemaVersion: Int,
        val schemaHash: String
    )

    @JvmStatic
    fun validate(database: SQLiteDatabase) {
        val registry = GeneratedRegistryLocator.registry() ?: return
        val entityMetas = registry.entityMetasByClassName().values
        if (entityMetas.isEmpty()) {
            return
        }
        ensureMasterTable(database)
        if (isSchemaHashMatched(database, registry)) {
            return
        }

        val actualTables = DBUtility.findAllTableNames(database)
            .map { normalizeName(it) }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("sqlite_") }
            .toSet()
        val session = ValidationSession(
            actualTables = actualTables,
            tableModelCache = HashMap()
        )

        val issues = ArrayList<String>()
        for (meta in entityMetas) {
            val normalizedTableName = normalizeName(meta.tableName)
            if (!session.actualTables.contains(normalizedTableName)) {
                issues.add("missing-table:${meta.tableName}")
                continue
            }
            validateColumns(database, meta, issues, session)
        }

        if (issues.isEmpty()) {
            upsertSchemaMarker(database, registry.anchorClassName, registry.schemaVersion, registry.schemaHash)
            return
        }

        val issueSummary = issues.take(12).joinToString(separator = ",")
        val suffix = if (issues.size > 12) ",more=${issues.size - 12}" else ""
        val message = "Schema mismatch detected. issues=[$issueSummary$suffix], expectedHash=${registry.schemaHash}."
        if (LitePalRuntime.shouldThrowOnError()) {
            throw IllegalStateException(message)
        }
        LitePalLog.w(TAG, message)
    }

    private fun ensureMasterTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            create table if not exists $MASTER_TABLE (
                id integer primary key autoincrement,
                $MASTER_COLUMN_ANCHOR text not null unique,
                $MASTER_COLUMN_SCHEMA_VERSION integer not null,
                $MASTER_COLUMN_SCHEMA_HASH text not null,
                $MASTER_COLUMN_UPDATED_AT integer not null
            )
            """.trimIndent()
        )
    }

    private fun isSchemaHashMatched(database: SQLiteDatabase, registry: LitePalGeneratedRegistry): Boolean {
        val marker = readSchemaMarker(database, registry.anchorClassName) ?: return false
        return marker.schemaVersion == registry.schemaVersion && marker.schemaHash == registry.schemaHash
    }

    private fun readSchemaMarker(database: SQLiteDatabase, anchorClassName: String): PersistedSchemaMarker? {
        val anchor = normalizeAnchor(anchorClassName)
        var cursor: android.database.Cursor? = null
        return try {
            cursor = database.rawQuery(
                "select $MASTER_COLUMN_SCHEMA_VERSION, $MASTER_COLUMN_SCHEMA_HASH from $MASTER_TABLE where $MASTER_COLUMN_ANCHOR = ? limit 1",
                arrayOf(anchor)
            )
            if (!cursor.moveToFirst()) {
                null
            } else {
                PersistedSchemaMarker(
                    schemaVersion = cursor.getInt(0),
                    schemaHash = cursor.getString(1).orEmpty()
                )
            }
        } catch (t: Throwable) {
            LitePalLog.w(TAG, "Failed to query schema hash marker, fallback to full validation. ${t.message}")
            null
        } finally {
            cursor?.close()
        }
    }

    private fun upsertSchemaMarker(
        database: SQLiteDatabase,
        anchorClassName: String,
        schemaVersion: Int,
        schemaHash: String
    ) {
        val now = System.currentTimeMillis()
        val anchor = normalizeAnchor(anchorClassName)
        val values = ContentValues().apply {
            put(MASTER_COLUMN_ANCHOR, anchor)
            put(MASTER_COLUMN_SCHEMA_VERSION, schemaVersion)
            put(MASTER_COLUMN_SCHEMA_HASH, schemaHash)
            put(MASTER_COLUMN_UPDATED_AT, now)
        }
        val insertedRowId = database.insertWithOnConflict(
            MASTER_TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (insertedRowId != -1L) {
            return
        }
        database.update(
            MASTER_TABLE,
            values,
            "$MASTER_COLUMN_ANCHOR = ?",
            arrayOf(anchor)
        )
    }

    private fun normalizeAnchor(anchorClassName: String): String {
        return anchorClassName.ifBlank { "__litepal_default_anchor__" }
    }

    private fun validateColumns(
        database: SQLiteDatabase,
        meta: EntityMeta<*>,
        issues: MutableList<String>,
        session: ValidationSession
    ) {
        val tableModel = getTableModel(database, meta.tableName, session)
        val actualColumns = HashMap<String, ColumnModel>()
        for (column in tableModel.getColumnModels()) {
            val key = normalizeName(column.getColumnName())
            if (key.isNotEmpty()) {
                actualColumns[key] = column
            }
        }

        val expectedFields = if (meta.persistedFields.isNotEmpty()) {
            meta.persistedFields
        } else {
            meta.supportedFields.map {
                GeneratedFieldMeta(
                    propertyName = it,
                    columnName = it,
                    typeName = "",
                    columnType = "",
                    nullable = true,
                    unique = false,
                    indexed = false,
                    defaultValue = "",
                    encryptAlgorithm = null
                )
            }
        }

        for (field in expectedFields) {
            val expectedColumnName = normalizeName(DBUtility.convertToValidColumnName(field.columnName))
            val actualColumn = actualColumns[expectedColumnName]
            if (actualColumn == null) {
                issues.add("missing-column:${meta.tableName}.${field.columnName}")
                continue
            }
            if (field.columnType.isNotBlank()) {
                val actualType = normalizeSqlType(actualColumn.getColumnType())
                val expectedType = normalizeSqlType(field.columnType)
                if (actualType.isNotBlank() && expectedType.isNotBlank() && actualType != expectedType) {
                    issues.add(
                        "column-type-mismatch:${meta.tableName}.${field.columnName} expected=$expectedType actual=$actualType"
                    )
                }
            }
            if (!field.nullable && actualColumn.isNullable()) {
                issues.add("column-nullable-mismatch:${meta.tableName}.${field.columnName} expected=NOT_NULL")
            }
            if (field.unique && !actualColumn.isUnique()) {
                issues.add("column-unique-missing:${meta.tableName}.${field.columnName}")
            }
            if (field.indexed && !actualColumn.hasIndex()) {
                issues.add("column-index-missing:${meta.tableName}.${field.columnName}")
            }
            val expectedDefault = field.defaultValue.trim()
            if (expectedDefault.isNotEmpty()) {
                val normalizedExpectedDefault = normalizeDefaultValue(expectedDefault)
                val normalizedActualDefault = normalizeDefaultValue(actualColumn.getDefaultValue())
                if (normalizedActualDefault.isEmpty()) {
                    issues.add(
                        "column-default-mismatch:${meta.tableName}.${field.columnName} expected=$normalizedExpectedDefault actual=<empty>"
                    )
                } else if (normalizedExpectedDefault != normalizedActualDefault) {
                    issues.add(
                        "column-default-mismatch:${meta.tableName}.${field.columnName} expected=$normalizedExpectedDefault actual=$normalizedActualDefault"
                    )
                }
            }
        }
    }

    private fun getTableModel(
        database: SQLiteDatabase,
        tableName: String,
        session: ValidationSession
    ): TableModel {
        val normalizedTableName = normalizeName(tableName)
        return session.tableModelCache.getOrPut(normalizedTableName) {
            DBUtility.findPragmaTableInfo(tableName, database)
        }
    }

    private fun normalizeName(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return ""
        }
        return BaseUtility.changeCase(raw)
            ?.trim()
            ?.trim('`', '"', '\'', '[', ']')
            ?.lowercase(Locale.US)
            .orEmpty()
    }

    private fun normalizeSqlType(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return ""
        }
        return raw.trim().lowercase(Locale.US)
    }

    private fun normalizeDefaultValue(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return ""
        }
        return raw.trim().trim('"', '\'').lowercase(Locale.US)
    }
}
