@file:Suppress("DEPRECATION")

package org.litepal.compat.v3.schema

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

@Deprecated(
    message = "compat-3x-schema is in maintenance mode and will be removed in a future major version."
)
data class DesiredColumn(
    val name: String,
    val nullable: Boolean = true,
    val unique: Boolean = false
)

@Deprecated(
    message = "compat-3x-schema is in maintenance mode and will be removed in a future major version."
)
data class DesiredTable(
    val name: String,
    val columns: List<DesiredColumn>
)

@Deprecated(
    message = "compat-3x-schema is in maintenance mode and will be removed in a future major version."
)
data class SchemaRisk(
    val table: String,
    val column: String,
    val reason: String
)

/**
 * Scans upgrade risk from current schema to desired schema declarations.
 */
@Deprecated(
    message = "compat-3x-schema is in maintenance mode. Use for legacy migration diagnostics only."
)
object Compat3xSchemaScanner {

    @JvmStatic
    fun scan(db: SQLiteDatabase, desiredTables: List<DesiredTable>): List<SchemaRisk> {
        val risks = mutableListOf<SchemaRisk>()
        for (table in desiredTables) {
            val nullableByColumn = queryNullableMap(db, table.name)
            val uniqueColumns = queryUniqueColumns(db, table.name)
            if (nullableByColumn.isEmpty()) {
                continue
            }
            for (desiredColumn in table.columns) {
                val currentNullable = nullableByColumn[desiredColumn.name.lowercase()]
                if (currentNullable == null) {
                    continue
                }
                if (!desiredColumn.nullable && currentNullable) {
                    risks += SchemaRisk(
                        table = table.name,
                        column = desiredColumn.name,
                        reason = "nullable -> not null"
                    )
                }
                if (desiredColumn.unique && !uniqueColumns.contains(desiredColumn.name.lowercase())) {
                    risks += SchemaRisk(
                        table = table.name,
                        column = desiredColumn.name,
                        reason = "non-unique -> unique"
                    )
                }
            }
        }
        return risks
    }

    @JvmStatic
    fun loadGeneratedMigrationReport(): String? {
        val loader = Compat3xSchemaScanner::class.java.classLoader ?: return null
        return runCatching {
            loader.getResourceAsStream("org/litepal/generated/migration-diff-report.txt")
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
    }

    private fun queryNullableMap(db: SQLiteDatabase, table: String): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        cursor.useCursor {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name")).lowercase()
                val notNull = it.getInt(it.getColumnIndexOrThrow("notnull")) == 1
                map[name] = !notNull
            }
        }
        return map
    }

    private fun queryUniqueColumns(db: SQLiteDatabase, table: String): Set<String> {
        val uniqueColumns = mutableSetOf<String>()
        val indexList = db.rawQuery("PRAGMA index_list($table)", null)
        indexList.useCursor { listCursor ->
            while (listCursor.moveToNext()) {
                val isUnique = listCursor.getInt(listCursor.getColumnIndexOrThrow("unique")) == 1
                if (!isUnique) {
                    continue
                }
                val indexName = listCursor.getString(listCursor.getColumnIndexOrThrow("name"))
                val indexInfo = db.rawQuery("PRAGMA index_info($indexName)", null)
                indexInfo.useCursor { infoCursor ->
                    while (infoCursor.moveToNext()) {
                        uniqueColumns += infoCursor.getString(infoCursor.getColumnIndexOrThrow("name")).lowercase()
                    }
                }
            }
        }
        return uniqueColumns
    }
}

private inline fun <T> Cursor.useCursor(block: (Cursor) -> T): T {
    return try {
        block(this)
    } finally {
        close()
    }
}
