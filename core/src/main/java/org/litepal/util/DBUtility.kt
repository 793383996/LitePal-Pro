/*
 * Copyright (C)  Tony Green, LitePal Framework Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litepal.util

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.util.Pair
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.tablemanager.model.TableModel
import java.lang.reflect.Field
import java.util.Locale
import java.util.regex.Pattern

object DBUtility {

    private const val TAG = "DBUtility"

    private const val SQLITE_KEYWORDS =
        ",abort,add,after,all,alter,and,as,asc,autoincrement,before,begin,between,by,cascade,check,collate,column,commit,conflict,constraint,create,cross,database,deferrable,deferred,delete,desc,distinct,drop,each,end,escape,except,exclusive,exists,foreign,from,glob,group,having,in,index,inner,insert,intersect,into,is,isnull,join,like,limit,match,natural,not,notnull,null,of,offset,on,or,order,outer,plan,pragma,primary,query,raise,references,regexp,reindex,release,rename,replace,restrict,right,rollback,row,savepoint,select,set,table,temp,temporary,then,to,transaction,trigger,union,unique,update,using,vacuum,values,view,virtual,when,where,"
    private const val KEYWORDS_COLUMN_SUFFIX = "_lpcolumn"
    private const val REG_OPERATOR = "\\s*(=|!=|<>|<|>)"
    private const val REG_FUZZY = "\\s+(not\\s+)?(like|between)\\s+"
    private const val REG_COLLECTION = "\\s+(not\\s+)?(in)\\s*\\("
    private val CREATE_TABLE_REGEX = Regex(
        "^\\s*create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([`\"\\[]?[\\w.]+[`\"\\]]?)",
        RegexOption.IGNORE_CASE
    )
    private val DROP_TABLE_REGEX = Regex(
        "^\\s*drop\\s+table\\s+(?:if\\s+exists\\s+)?([`\"\\[]?[\\w.]+[`\"\\]]?)",
        RegexOption.IGNORE_CASE
    )
    private val RENAME_TABLE_REGEX = Regex(
        "^\\s*alter\\s+table\\s+([`\"\\[]?[\\w.]+[`\"\\]]?)\\s+rename\\s+to\\s+([`\"\\[]?[\\w.]+[`\"\\]]?)",
        RegexOption.IGNORE_CASE
    )

    private data class TableSnapshotSession(
        val db: SQLiteDatabase,
        private val tableNames: LinkedHashSet<String>,
        private val normalizedTableNames: HashSet<String>,
        private val tableTypesByName: HashMap<String, Int>
    ) {
        fun contains(tableName: String?): Boolean {
            val normalized = normalizeTableName(tableName)
            if (normalized.isEmpty()) {
                return false
            }
            return normalizedTableNames.contains(normalized)
        }

        fun list(): List<String> = tableNames.toList()

        fun add(tableName: String?) {
            val sanitized = sanitizeTableName(tableName) ?: return
            val normalized = normalizeTableName(sanitized)
            if (normalizedTableNames.add(normalized)) {
                tableNames.add(sanitized)
            }
            tableTypesByName.remove(normalized)
        }

        fun remove(tableName: String?) {
            val sanitized = sanitizeTableName(tableName) ?: return
            val normalized = normalizeTableName(sanitized)
            if (!normalizedTableNames.remove(normalized)) {
                tableTypesByName.remove(normalized)
                return
            }
            val iterator = tableNames.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().equals(sanitized, ignoreCase = true)) {
                    iterator.remove()
                    break
                }
            }
            tableTypesByName.remove(normalized)
        }

        fun rename(oldName: String?, newName: String?) {
            val oldNormalized = normalizeTableName(oldName)
            val oldType = if (oldNormalized.isNotEmpty()) {
                tableTypesByName.remove(oldNormalized)
            } else {
                null
            }
            remove(oldName)
            add(newName)
            val newNormalized = normalizeTableName(newName)
            if (oldType != null && newNormalized.isNotEmpty()) {
                tableTypesByName[newNormalized] = oldType
            }
        }

        fun tableTypeOf(tableName: String?): Int? {
            val normalized = normalizeTableName(tableName)
            if (normalized.isEmpty()) {
                return null
            }
            return tableTypesByName[normalized]
        }

        fun hasTableSchemaEntry(tableName: String?): Boolean {
            val normalized = normalizeTableName(tableName)
            if (normalized.isEmpty()) {
                return false
            }
            return tableTypesByName.containsKey(normalized)
        }

        fun setTableType(tableName: String?, type: Int) {
            val normalized = normalizeTableName(tableName)
            if (normalized.isEmpty()) {
                return
            }
            tableTypesByName[normalized] = type
        }

        fun removeTableType(tableName: String?) {
            val normalized = normalizeTableName(tableName)
            if (normalized.isNotEmpty()) {
                tableTypesByName.remove(normalized)
            }
        }
    }

    private val tableSnapshotSession = ThreadLocal<TableSnapshotSession?>()

    @JvmStatic
    fun getTableNameByClassName(className: String?): String {
        if (!TextUtils.isEmpty(className)) {
            return if (className!![className.length - 1] == '.') {
                ""
            } else {
                className.substring(className.lastIndexOf('.') + 1)
            }
        }
        return ""
    }

    @JvmStatic
    fun getIndexName(tableName: String?, columnName: String?): String {
        if (!TextUtils.isEmpty(tableName) && !TextUtils.isEmpty(columnName)) {
            return "${tableName}_${columnName}_index"
        }
        return ""
    }

    @JvmStatic
    fun getTableNameListByClassNameList(classNames: List<String>?): List<String> {
        val tableNames = ArrayList<String>()
        if (!classNames.isNullOrEmpty()) {
            for (className in classNames) {
                tableNames.add(getTableNameByClassName(className))
            }
        }
        return tableNames
    }

    @JvmStatic
    fun getTableNameByForeignColumn(foreignColumnName: String?): String {
        if (!TextUtils.isEmpty(foreignColumnName)) {
            if (foreignColumnName!!.lowercase(Locale.US).endsWith("_id")) {
                return foreignColumnName.substring(0, foreignColumnName.length - "_id".length)
            }
            return ""
        }
        return ""
    }

    @JvmStatic
    fun getIntermediateTableName(tableName: String?, associatedTableName: String?): String {
        if (!TextUtils.isEmpty(tableName) && !TextUtils.isEmpty(associatedTableName)) {
            return if (tableName!!.lowercase(Locale.US)
                    .compareTo(associatedTableName!!.lowercase(Locale.US)) <= 0
            ) {
                "${tableName}_${associatedTableName}"
            } else {
                "${associatedTableName}_${tableName}"
            }
        }
        return ""
    }

    @JvmStatic
    fun getGenericTableName(className: String, fieldName: String): String {
        val tableName = getTableNameByClassName(className)
        return BaseUtility.changeCase("${tableName}_${fieldName}").orEmpty()
    }

    @JvmStatic
    fun getGenericValueIdColumnName(className: String): String {
        return BaseUtility.changeCase("${getTableNameByClassName(className)}_id").orEmpty()
    }

    @JvmStatic
    fun getM2MSelfRefColumnName(field: Field): String {
        return BaseUtility.changeCase("${field.name}_id").orEmpty()
    }

    @JvmStatic
    fun isIntermediateTable(tableName: String?, db: SQLiteDatabase): Boolean {
        if (TextUtils.isEmpty(tableName)) {
            return false
        }
        try {
            val activeSession = tableSnapshotSession.get()
            if (activeSession != null && activeSession.db === db) {
                val tableType = activeSession.tableTypeOf(tableName)
                if (tableType != null) {
                    return tableType == Const.TableSchema.INTERMEDIATE_JOIN_TABLE
                }
            }
            val tableType = findTableTypeFromSchema(tableName, db) ?: return false
            return tableType == Const.TableSchema.INTERMEDIATE_JOIN_TABLE
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to check intermediate table state.", e)
        }
        return false
    }

    @JvmStatic
    fun isGenericTable(tableName: String?, db: SQLiteDatabase): Boolean {
        if (TextUtils.isEmpty(tableName)) {
            return false
        }
        try {
            val activeSession = tableSnapshotSession.get()
            if (activeSession != null && activeSession.db === db) {
                val tableType = activeSession.tableTypeOf(tableName)
                if (tableType != null) {
                    return tableType == Const.TableSchema.GENERIC_TABLE
                }
            }
            val tableType = findTableTypeFromSchema(tableName, db) ?: return false
            return tableType == Const.TableSchema.GENERIC_TABLE
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to check generic table state.", e)
        }
        return false
    }

    @JvmStatic
    fun isTableExists(tableName: String?, db: SQLiteDatabase): Boolean {
        return try {
            val activeSession = tableSnapshotSession.get()
            if (activeSession != null && activeSession.db === db) {
                activeSession.contains(tableName)
            } else {
                BaseUtility.containsIgnoreCases(findAllTableNames(db), tableName)
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to check whether table exists.", e)
            false
        }
    }

    @JvmStatic
    fun isColumnExists(columnName: String?, tableName: String?, db: SQLiteDatabase): Boolean {
        if (TextUtils.isEmpty(columnName) || TextUtils.isEmpty(tableName)) {
            return false
        }
        var cursor: Cursor? = null
        return try {
            val checkingColumnSQL = "pragma table_info($tableName)"
            cursor = db.rawQuery(checkingColumnSQL, null)
            var exist = false
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (columnName.equals(name, ignoreCase = true)) {
                        exist = true
                        break
                    }
                } while (cursor.moveToNext())
            }
            exist
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to check whether column exists.", e)
            false
        } finally {
            cursor?.close()
        }
    }

    @JvmStatic
    fun findAllTableNames(db: SQLiteDatabase): List<String> {
        val activeSession = tableSnapshotSession.get()
        if (activeSession != null && activeSession.db === db) {
            return activeSession.list()
        }
        return queryAllTableNamesFromMaster(db)
    }

    @JvmStatic
    fun beginTableSnapshotSession(db: SQLiteDatabase) {
        val activeSession = tableSnapshotSession.get()
        if (activeSession != null && activeSession.db === db) {
            return
        }
        val tableNames = queryAllTableNamesFromMaster(db)
        val tableTypesByName = queryTableTypesFromSchema(db, tableNames)
        val orderedTableNames = LinkedHashSet<String>(tableNames.size)
        val normalizedTableNames = HashSet<String>(tableNames.size)
        for (tableName in tableNames) {
            val sanitized = sanitizeTableName(tableName) ?: continue
            val normalized = normalizeTableName(sanitized)
            if (normalizedTableNames.add(normalized)) {
                orderedTableNames.add(sanitized)
            }
        }
        tableSnapshotSession.set(
            TableSnapshotSession(
                db,
                orderedTableNames,
                normalizedTableNames,
                tableTypesByName
            )
        )
    }

    @JvmStatic
    fun endTableSnapshotSession(db: SQLiteDatabase? = null) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (db == null || activeSession.db === db) {
            tableSnapshotSession.remove()
        }
    }

    @JvmStatic
    fun noteTableCreated(db: SQLiteDatabase, tableName: String?) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db === db) {
            activeSession.add(tableName)
        }
    }

    @JvmStatic
    fun noteTableDropped(db: SQLiteDatabase, tableName: String?) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db === db) {
            activeSession.remove(tableName)
        }
    }

    @JvmStatic
    fun noteTableRenamed(db: SQLiteDatabase, oldName: String?, newName: String?) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db === db) {
            activeSession.rename(oldName, newName)
        }
    }

    @JvmStatic
    fun noteTableSchemaType(db: SQLiteDatabase, tableName: String?, tableType: Int) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db === db) {
            activeSession.setTableType(tableName, tableType)
        }
    }

    @JvmStatic
    fun noteTableSchemaTypeRemoved(db: SQLiteDatabase, tableName: String?) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db === db) {
            activeSession.removeTableType(tableName)
        }
    }

    @JvmStatic
    fun hasTableSchemaEntry(tableName: String?, db: SQLiteDatabase): Boolean {
        val activeSession = tableSnapshotSession.get()
        if (activeSession != null && activeSession.db === db) {
            return activeSession.hasTableSchemaEntry(tableName)
        }
        return hasTableSchemaEntryInDatabase(tableName, db)
    }

    @JvmStatic
    fun onSchemaSqlExecuted(db: SQLiteDatabase, sql: String) {
        val activeSession = tableSnapshotSession.get() ?: return
        if (activeSession.db !== db) {
            return
        }
        val normalizedSql = sql.trim()
        RENAME_TABLE_REGEX.find(normalizedSql)?.let { match ->
            val oldName = sanitizeTableName(match.groupValues.getOrNull(1))
            val newName = sanitizeTableName(match.groupValues.getOrNull(2))
            noteTableRenamed(db, oldName, newName)
            return
        }
        CREATE_TABLE_REGEX.find(normalizedSql)?.let { match ->
            val tableName = sanitizeTableName(match.groupValues.getOrNull(1))
            noteTableCreated(db, tableName)
            return
        }
        DROP_TABLE_REGEX.find(normalizedSql)?.let { match ->
            val tableName = sanitizeTableName(match.groupValues.getOrNull(1))
            noteTableDropped(db, tableName)
        }
    }

    private fun queryAllTableNamesFromMaster(db: SQLiteDatabase): List<String> {
        val tableNames = LinkedHashSet<String>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select * from sqlite_master where type = ?", arrayOf("table"))
            if (cursor.moveToFirst()) {
                do {
                    val tableName = cursor.getString(cursor.getColumnIndexOrThrow("tbl_name"))
                    tableNames.add(tableName)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to find all table names.", e)
            throw DatabaseGenerateException(e.message)
        } finally {
            cursor?.close()
        }
        return tableNames.toList()
    }

    private fun queryTableTypesFromSchema(db: SQLiteDatabase, tableNames: List<String>): HashMap<String, Int> {
        val tableTypesByName = HashMap<String, Int>()
        if (!BaseUtility.containsIgnoreCases(tableNames, Const.TableSchema.TABLE_NAME)) {
            return tableTypesByName
        }
        var cursor: Cursor? = null
        try {
            cursor = db.query(Const.TableSchema.TABLE_NAME, null, null, null, null, null, null)
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Const.TableSchema.COLUMN_NAME))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Const.TableSchema.COLUMN_TYPE))
                    val normalizedName = normalizeTableName(name)
                    if (normalizedName.isNotEmpty()) {
                        tableTypesByName[normalizedName] = type
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to query table schema types for snapshot cache.", e)
        } finally {
            cursor?.close()
        }
        return tableTypesByName
    }

    private fun findTableTypeFromSchema(tableName: String?, db: SQLiteDatabase): Int? {
        val sanitized = sanitizeTableName(tableName) ?: return null
        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                Const.TableSchema.TABLE_NAME,
                arrayOf(Const.TableSchema.COLUMN_TYPE),
                "lower(${Const.TableSchema.COLUMN_NAME}) = lower(?)",
                arrayOf(sanitized),
                null,
                null,
                null,
                "1"
            )
            if (cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow(Const.TableSchema.COLUMN_TYPE))
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    private fun hasTableSchemaEntryInDatabase(tableName: String?, db: SQLiteDatabase): Boolean {
        val sanitized = sanitizeTableName(tableName) ?: return false
        var cursor: Cursor? = null
        return try {
            cursor = db.query(
                Const.TableSchema.TABLE_NAME,
                arrayOf(Const.TableSchema.COLUMN_NAME),
                "lower(${Const.TableSchema.COLUMN_NAME}) = lower(?)",
                arrayOf(sanitized),
                null,
                null,
                null,
                "1"
            )
            cursor.moveToFirst()
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    private fun sanitizeTableName(rawTableName: String?): String? {
        if (rawTableName.isNullOrBlank()) {
            return null
        }
        return rawTableName
            .trim()
            .trim('`', '"', '\'', '[', ']')
    }

    private fun normalizeTableName(tableName: String?): String {
        val sanitized = sanitizeTableName(tableName) ?: return ""
        return sanitized.lowercase(Locale.US)
    }

    @JvmStatic
    fun findPragmaTableInfo(tableName: String, db: SQLiteDatabase): TableModel {
        if (!isTableExists(tableName, db)) {
            throw DatabaseGenerateException(
                DatabaseGenerateException.TABLE_DOES_NOT_EXIST_WHEN_EXECUTING + tableName
            )
        }
        val indexPair = findIndexedColumns(tableName, db)
        val indexColumns = indexPair.first ?: emptySet()
        val uniqueColumns = indexPair.second ?: emptySet()
        val tableModelDB = TableModel()
        tableModelDB.setTableName(tableName)
        val checkingColumnSQL = "pragma table_info($tableName)"
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(checkingColumnSQL, null)
            if (cursor.moveToFirst()) {
                do {
                    val columnModel = ColumnModel()
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    val nullable = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 1
                    val unique = uniqueColumns.contains(name)
                    val hasIndex = indexColumns.contains(name)
                    var defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    columnModel.setColumnName(name)
                    columnModel.setColumnType(type)
                    columnModel.setNullable(nullable)
                    columnModel.setUnique(unique)
                    columnModel.setHasIndex(hasIndex)
                    defaultValue = defaultValue?.replace("'", "") ?: ""
                    columnModel.setDefaultValue(defaultValue)
                    tableModelDB.addColumnModel(columnModel)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to inspect pragma table info.", e)
            throw DatabaseGenerateException(e.message)
        } finally {
            cursor?.close()
        }
        return tableModelDB
    }

    @JvmStatic
    fun findIndexedColumns(tableName: String, db: SQLiteDatabase): Pair<Set<String>, Set<String>> {
        val indexColumns = HashSet<String>()
        val uniqueColumns = HashSet<String>()
        var cursor: Cursor? = null
        var innerCursor: Cursor? = null
        try {
            cursor = db.rawQuery("pragma index_list($tableName)", null)
            if (cursor.moveToFirst()) {
                do {
                    val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    innerCursor?.close()
                    innerCursor = db.rawQuery("pragma index_info($name)", null)
                    val indexColumnNames = ArrayList<String>()
                    if (innerCursor.moveToFirst()) {
                        do {
                            val columnName = innerCursor.getString(innerCursor.getColumnIndexOrThrow("name"))
                            if (!columnName.isNullOrBlank()) {
                                indexColumnNames.add(columnName)
                            }
                        } while (innerCursor.moveToNext())
                    }
                    if (indexColumnNames.size == 1) {
                        val columnName = indexColumnNames[0]
                        if (unique) {
                            uniqueColumns.add(columnName)
                        } else {
                            indexColumns.add(columnName)
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to inspect indexed columns.", e)
            throw DatabaseGenerateException(e.message)
        } finally {
            cursor?.close()
            innerCursor?.close()
        }
        return Pair(indexColumns, uniqueColumns)
    }

    @JvmStatic
    fun isFieldNameConflictWithSQLiteKeywords(fieldName: String?): Boolean {
        if (!TextUtils.isEmpty(fieldName)) {
            val fieldNameWithComma = ",${fieldName!!.lowercase(Locale.US)},"
            return SQLITE_KEYWORDS.contains(fieldNameWithComma)
        }
        return false
    }

    @JvmStatic
    fun convertToValidColumnName(columnName: String?): String? {
        if (isFieldNameConflictWithSQLiteKeywords(columnName)) {
            return columnName + KEYWORDS_COLUMN_SUFFIX
        }
        return columnName
    }

    @JvmStatic
    fun convertWhereClauseToColumnName(whereClause: String?): String? {
        if (!TextUtils.isEmpty(whereClause)) {
            try {
                val convertedWhereClause = StringBuffer()
                val p = Pattern.compile(
                    "(\\w+$REG_OPERATOR|\\w+$REG_FUZZY|\\w+$REG_COLLECTION)"
                )
                val m = p.matcher(whereClause)
                while (m.find()) {
                    val matches = m.group()
                    var column = matches.replace("($REG_OPERATOR|$REG_FUZZY|$REG_COLLECTION)".toRegex(), "")
                    val rest = matches.replace(column, "")
                    column = convertToValidColumnName(column) ?: column
                    m.appendReplacement(convertedWhereClause, column + rest)
                }
                m.appendTail(convertedWhereClause)
                return convertedWhereClause.toString()
            } catch (e: Exception) {
                LitePalLog.e(TAG, "Failed to convert where clause to valid column names.", e)
            }
        }
        return whereClause
    }

    @JvmStatic
    fun convertSelectClauseToValidNames(columns: Array<String>?): Array<String>? {
        if (!columns.isNullOrEmpty()) {
            val convertedColumns = Array(columns.size) { "" }
            for (i in columns.indices) {
                convertedColumns[i] = convertToValidColumnName(columns[i]) ?: columns[i]
            }
            return convertedColumns
        }
        return null
    }

    @JvmStatic
    fun convertOrderByClauseToValidName(orderBy: String?): String? {
        if (!TextUtils.isEmpty(orderBy)) {
            var normalized = orderBy!!.trim().lowercase(Locale.US)
            if (normalized.contains(",")) {
                val orderByItems = normalized.split(",")
                val builder = StringBuilder()
                var needComma = false
                for (orderByItem in orderByItems) {
                    if (needComma) {
                        builder.append(",")
                    }
                    builder.append(convertOrderByItem(orderByItem))
                    needComma = true
                }
                normalized = builder.toString()
            } else {
                normalized = convertOrderByItem(normalized)
            }
            return normalized
        }
        return null
    }

    private fun convertOrderByItem(orderByItem: String): String {
        val normalizedItem = orderByItem.trim()
        val column: String
        val append: String
        if (normalizedItem.endsWith("asc")) {
            column = normalizedItem.replace("asc", "").trim()
            append = " asc"
        } else if (normalizedItem.endsWith("desc")) {
            column = normalizedItem.replace("desc", "").trim()
            append = " desc"
        } else {
            column = normalizedItem
            append = ""
        }
        return (convertToValidColumnName(column) ?: column) + append
    }
}
