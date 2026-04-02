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

package org.litepal.tablemanager

import android.database.sqlite.SQLiteDatabase
import org.litepal.parser.LitePalAttr
import org.litepal.tablemanager.model.AssociationsModel
import org.litepal.tablemanager.model.GenericModel
import org.litepal.tablemanager.model.TableModel
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import java.util.Locale

abstract class AssociationUpdater : Creator() {

    private var associationModels: Collection<AssociationsModel> = emptyList()
    private val tableModelDbCache = HashMap<String, TableModel>()
    protected lateinit var db: SQLiteDatabase

    internal abstract override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean)

    internal override fun addOrUpdateAssociation(db: SQLiteDatabase, force: Boolean) {
        tableModelDbCache.clear()
        associationModels = getAllAssociations()
        this.db = db
        removeAssociations()
    }

    protected fun getForeignKeyColumns(tableModel: TableModel): List<String> {
        val foreignKeyColumns = ArrayList<String>()
        val columnModels = getTableModelFromDB(tableModel.getTableName()!!).getColumnModels()
        for (columnModel in columnModels) {
            val columnName = columnModel.getColumnName()
            if (isForeignKeyColumnFormat(columnName) && !tableModel.containsColumn(columnName)) {
                LitePalLog.d(TAG, "getForeignKeyColumnNames >> foreign key column is $columnName")
                foreignKeyColumns.add(columnName!!)
            }
        }
        return foreignKeyColumns
    }

    protected fun isForeignKeyColumn(tableModel: TableModel, columnName: String?): Boolean {
        return BaseUtility.containsIgnoreCases(getForeignKeyColumns(tableModel), columnName)
    }

    protected fun getTableModelFromDB(tableName: String): TableModel {
        val cacheKey = normalizeTableNameForCache(tableName)
        val cachedTableModel = tableModelDbCache[cacheKey]
        if (cachedTableModel != null) {
            return copyTableModel(cachedTableModel)
        }
        val tableModel = DBUtility.findPragmaTableInfo(tableName, db)
        val snapshot = copyTableModel(tableModel)
        tableModelDbCache[cacheKey] = snapshot
        return copyTableModel(snapshot)
    }

    protected fun dropTables(dropTableNames: List<String>?, db: SQLiteDatabase) {
        if (!dropTableNames.isNullOrEmpty()) {
            val dropTableSqls = ArrayList<String>()
            for (dropTableName in dropTableNames) {
                dropTableSqls.add(generateDropTableSQL(dropTableName))
            }
            execute(dropTableSqls, db)
            for (dropTableName in dropTableNames) {
                invalidateTableModelCache(dropTableName)
            }
        }
    }

    protected fun removeColumns(removeColumnNames: Collection<String>?, tableName: String?) {
        if (!removeColumnNames.isNullOrEmpty()) {
            execute(getRemoveColumnSQLs(removeColumnNames, tableName!!), db)
            invalidateTableModelCache(tableName)
            invalidateTableModelCache(getTempTableName(tableName))
        }
    }

    protected fun clearCopyInTableSchema(tableNames: List<String>?) {
        if (!tableNames.isNullOrEmpty()) {
            val deleteData = StringBuilder("delete from ")
            deleteData.append(Const.TableSchema.TABLE_NAME).append(" where")
            var needOr = false
            for (tableName in tableNames) {
                if (needOr) {
                    deleteData.append(" or ")
                }
                needOr = true
                deleteData.append(" lower(").append(Const.TableSchema.COLUMN_NAME).append(") ")
                deleteData.append("=").append(" lower('").append(tableName).append("')")
            }
            LitePalLog.d(TAG, "clear table schema value sql is $deleteData")
            val sqls = ArrayList<String>()
            sqls.add(deleteData.toString())
            execute(sqls, db)
            for (tableName in tableNames) {
                DBUtility.noteTableSchemaTypeRemoved(db, tableName)
            }
        }
    }

    private fun removeAssociations() {
        removeForeignKeyColumns()
        removeIntermediateTables()
        removeGenericTables()
    }

    private fun removeForeignKeyColumns() {
        for (className in LitePalAttr.getInstance().getClassNames()) {
            val tableModel = getTableModel(className)
            removeColumns(findForeignKeyToRemove(tableModel), tableModel.getTableName())
        }
    }

    private fun removeIntermediateTables() {
        val tableNamesToDrop = findIntermediateTablesToDrop()
        dropTables(tableNamesToDrop, db)
        clearCopyInTableSchema(tableNamesToDrop)
    }

    private fun removeGenericTables() {
        val tableNamesToDrop = findGenericTablesToDrop()
        dropTables(tableNamesToDrop, db)
        clearCopyInTableSchema(tableNamesToDrop)
    }

    private fun findForeignKeyToRemove(tableModel: TableModel): List<String> {
        val removeRelations = ArrayList<String>()
        val foreignKeyColumns = getForeignKeyColumns(tableModel)
        val selfTableName = tableModel.getTableName()!!
        for (foreignKeyColumn in foreignKeyColumns) {
            val associatedTableName = DBUtility.getTableNameByForeignColumn(foreignKeyColumn)
            if (shouldDropForeignKey(selfTableName, associatedTableName)) {
                removeRelations.add(foreignKeyColumn)
            }
        }
        LitePalLog.d(TAG, "findForeignKeyToRemove >> ${tableModel.getTableName()} $removeRelations")
        return removeRelations
    }

    private fun findIntermediateTablesToDrop(): List<String> {
        val intermediateTables = ArrayList<String>()
        for (tableName in DBUtility.findAllTableNames(db)) {
            if (DBUtility.isIntermediateTable(tableName, db)) {
                var dropIntermediateTable = true
                for (associationModel in associationModels) {
                    if (associationModel.getAssociationType() == Const.Model.MANY_TO_MANY) {
                        val intermediateTableName = DBUtility.getIntermediateTableName(
                            associationModel.getTableName(),
                            associationModel.getAssociatedTableName()
                        )
                        if (tableName.equals(intermediateTableName, ignoreCase = true)) {
                            dropIntermediateTable = false
                        }
                    }
                }
                if (dropIntermediateTable) {
                    intermediateTables.add(tableName)
                }
            }
        }
        LitePalLog.d(TAG, "findIntermediateTablesToDrop >> $intermediateTables")
        return intermediateTables
    }

    private fun findGenericTablesToDrop(): List<String> {
        val genericTablesToDrop = ArrayList<String>()
        for (tableName in DBUtility.findAllTableNames(db)) {
            if (DBUtility.isGenericTable(tableName, db)) {
                var dropGenericTable = true
                for (genericModel in getGenericModels()) {
                    val genericTableName = genericModel.getTableName()
                    if (tableName.equals(genericTableName, ignoreCase = true)) {
                        dropGenericTable = false
                        break
                    }
                }
                if (dropGenericTable) {
                    genericTablesToDrop.add(tableName)
                }
            }
        }
        return genericTablesToDrop
    }

    protected fun generateAlterToTempTableSQL(tableName: String?): String {
        return StringBuilder().append("alter table ").append(tableName).append(" rename to ")
            .append(getTempTableName(tableName)).toString()
    }

    private fun generateCreateNewTableSQL(
        removeColumnNames: Collection<String>,
        tableModel: TableModel
    ): String {
        for (removeColumnName in removeColumnNames) {
            tableModel.removeColumnModelByName(removeColumnName)
        }
        return generateCreateTableSQL(tableModel)
    }

    protected fun generateDataMigrationSQL(tableModel: TableModel): String? {
        val tableName = tableModel.getTableName()
        val columnModels = tableModel.getColumnModels()
        if (columnModels.isEmpty()) {
            return null
        }
        val sql = StringBuilder()
        sql.append("insert into ").append(tableName).append("(")
        var needComma = false
        for (columnModel in columnModels) {
            if (needComma) {
                sql.append(", ")
            }
            needComma = true
            sql.append(columnModel.getColumnName())
        }
        sql.append(") ")
        sql.append("select ")
        needComma = false
        for (columnModel in columnModels) {
            if (needComma) {
                sql.append(", ")
            }
            needComma = true
            sql.append(columnModel.getColumnName())
        }
        sql.append(" from ").append(getTempTableName(tableName))
        return sql.toString()
    }

    protected fun generateDropTempTableSQL(tableName: String?): String {
        return generateDropTableSQL(getTempTableName(tableName))
    }

    protected fun getTempTableName(tableName: String?): String {
        return "${tableName}_temp"
    }

    private fun getRemoveColumnSQLs(removeColumnNames: Collection<String>, tableName: String): List<String> {
        val tableModelFromDB = getTableModelFromDB(tableName)
        val alterToTempTableSQL = generateAlterToTempTableSQL(tableName)
        LitePalLog.d(TAG, "generateRemoveColumnSQL >> $alterToTempTableSQL")
        val createNewTableSQL = generateCreateNewTableSQL(removeColumnNames, tableModelFromDB)
        LitePalLog.d(TAG, "generateRemoveColumnSQL >> $createNewTableSQL")
        val dataMigrationSQL = generateDataMigrationSQL(tableModelFromDB)
        LitePalLog.d(TAG, "generateRemoveColumnSQL >> $dataMigrationSQL")
        val dropTempTableSQL = generateDropTempTableSQL(tableName)
        LitePalLog.d(TAG, "generateRemoveColumnSQL >> $dropTempTableSQL")
        val createIndexSQLs = generateCreateIndexSQLs(tableModelFromDB)
        val sqls = ArrayList<String>()
        sqls.add(alterToTempTableSQL)
        sqls.add(createNewTableSQL)
        sqls.add(dataMigrationSQL ?: "")
        sqls.add(dropTempTableSQL)
        sqls.addAll(createIndexSQLs)
        return sqls
    }

    private fun shouldDropForeignKey(selfTableName: String, associatedTableName: String?): Boolean {
        for (associationModel in associationModels) {
            if (associationModel.getAssociationType() == Const.Model.ONE_TO_ONE) {
                if (selfTableName.equals(
                        associationModel.getTableHoldsForeignKey(),
                        ignoreCase = true
                    )
                ) {
                    if (associationModel.getTableName().equals(selfTableName, ignoreCase = true)) {
                        if (isRelationCorrect(associationModel, selfTableName, associatedTableName)) {
                            return false
                        }
                    } else if (associationModel.getAssociatedTableName()
                            .equals(selfTableName, ignoreCase = true)
                    ) {
                        if (isRelationCorrect(associationModel, associatedTableName, selfTableName)) {
                            return false
                        }
                    }
                }
            } else if (associationModel.getAssociationType() == Const.Model.MANY_TO_ONE) {
                if (isRelationCorrect(associationModel, associatedTableName, selfTableName)) {
                    return false
                }
            }
        }
        return true
    }

    private fun isRelationCorrect(
        associationModel: AssociationsModel,
        tableName1: String?,
        tableName2: String?
    ): Boolean {
        return associationModel.getTableName().equals(tableName1, ignoreCase = true) &&
            associationModel.getAssociatedTableName().equals(tableName2, ignoreCase = true)
    }

    protected fun invalidateTableModelCache(tableName: String?) {
        if (tableName.isNullOrBlank()) {
            return
        }
        tableModelDbCache.remove(normalizeTableNameForCache(tableName))
    }

    protected fun clearTableModelCache() {
        tableModelDbCache.clear()
    }

    private fun normalizeTableNameForCache(tableName: String): String {
        return tableName.lowercase(Locale.US)
    }

    private fun copyTableModel(source: TableModel): TableModel {
        val target = TableModel()
        target.setTableName(source.getTableName())
        target.setClassName(source.getClassName())
        for (columnModel in source.getColumnModels()) {
            target.addColumnModel(columnModel)
        }
        return target
    }

    companion object {
        const val TAG = "AssociationUpdater"
    }
}
