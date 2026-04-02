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

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.tablemanager.model.TableModel
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog

class Upgrader : AssociationUpdater() {

    protected lateinit var tableModel: TableModel
    protected lateinit var tableModelDB: TableModel
    private var hasConstraintChanged = false

    override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean) {
        this.db = db
        clearTableModelCache()
        for (model in getAllTableModels()) {
            tableModel = model
            tableModelDB = getTableModelFromDB(model.getTableName()!!)
            LitePalLog.d(TAG, "createOrUpgradeTable: model is ${tableModel.getTableName()}")
            upgradeTable()
        }
    }

    private fun upgradeTable() {
        val unsafeConstraintColumns = findUnsafeConstraintColumns()
        if (unsafeConstraintColumns.isNotEmpty()) {
            if (isTableEmpty()) {
                createOrUpgradeTable(tableModel, db, true)
                invalidateTableModelCache(tableModel.getTableName())
            } else {
                throw DatabaseGenerateException(
                    DatabaseGenerateException.UNSAFE_MIGRATION +
                        "table=${tableModel.getTableName()}, columns=$unsafeConstraintColumns. " +
                        "Migrate data manually or split migration into multiple versions."
                )
            }
            val associationsInfo: Collection<AssociationsInfo> =
                getAssociationInfo(tableModel.getClassName()!!)
            for (info in associationsInfo) {
                if (info.getAssociationType() == Const.Model.MANY_TO_ONE ||
                    info.getAssociationType() == Const.Model.ONE_TO_ONE
                ) {
                    if (info.getClassHoldsForeignKey().equals(tableModel.getClassName(), ignoreCase = true)) {
                        val associatedTableName = DBUtility.getTableNameByClassName(info.getAssociatedClassName())
                        addForeignKeyColumn(
                            tableModel.getTableName(),
                            associatedTableName,
                            tableModel.getTableName(),
                            db
                        )
                        invalidateTableModelCache(tableModel.getTableName())
                    }
                }
            }
        } else {
            hasConstraintChanged = false
            removeColumns(findColumnsToRemove())
            addColumns(findColumnsToAdd())
            changeColumnsType(findColumnTypesToChange())
            changeColumnsConstraints()
        }
    }

    private fun findUnsafeConstraintColumns(): List<String> {
        val unsafeColumns = ArrayList<String>()
        for (columnModel in tableModel.getColumnModels()) {
            if (columnModel.isIdColumn()) {
                continue
            }
            val columnModelDB = tableModelDB.getColumnModelByName(columnModel.getColumnName())
            if (columnModel.isUnique() && (columnModelDB == null || !columnModelDB.isUnique())) {
                unsafeColumns.add("${columnModel.getColumnName()}(unique)")
            }
            if (columnModelDB != null && !columnModel.isNullable() && columnModelDB.isNullable()) {
                unsafeColumns.add("${columnModel.getColumnName()}(not null)")
            }
        }
        return unsafeColumns
    }

    private fun isTableEmpty(): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery("select count(1) from ${tableModel.getTableName()}", null)
            if (cursor.moveToFirst()) {
                cursor.getInt(0) == 0
            } else {
                true
            }
        } finally {
            cursor?.close()
        }
    }

    private fun findColumnsToAdd(): List<ColumnModel> {
        val columnsToAdd = ArrayList<ColumnModel>()
        for (columnModel in tableModel.getColumnModels()) {
            val columnName = columnModel.getColumnName()
            if (!tableModelDB.containsColumn(columnName)) {
                columnsToAdd.add(columnModel)
            }
        }
        return columnsToAdd
    }

    private fun findColumnsToRemove(): List<String> {
        val removeColumns = ArrayList<String>()
        for (columnModel in tableModelDB.getColumnModels()) {
            val dbColumnName = columnModel.getColumnName()
            if (isNeedToRemove(dbColumnName)) {
                removeColumns.add(dbColumnName!!)
            }
        }
        LitePalLog.d(TAG, "remove columns from ${tableModel.getTableName()} >> $removeColumns")
        return removeColumns
    }

    private fun findColumnTypesToChange(): List<ColumnModel> {
        val columnsToChangeType = ArrayList<ColumnModel>()
        for (columnModelDB in tableModelDB.getColumnModels()) {
            for (columnModel in tableModel.getColumnModels()) {
                if (columnModelDB.getColumnName().equals(columnModel.getColumnName(), ignoreCase = true)) {
                    if (!columnModelDB.getColumnType().equals(columnModel.getColumnType(), ignoreCase = true)) {
                        if (columnModel.getColumnType().equals("blob", ignoreCase = true) &&
                            TextUtils.isEmpty(columnModelDB.getColumnType())
                        ) {
                            // ignore binary array legacy upgrade case
                        } else {
                            columnsToChangeType.add(columnModel)
                        }
                    }
                    if (!hasConstraintChanged) {
                        LitePalLog.d(
                            TAG,
                            "default value db is:${columnModelDB.getDefaultValue()}, default value is:${columnModel.getDefaultValue()}"
                        )
                        if (columnModelDB.isNullable() != columnModel.isNullable() ||
                            !columnModelDB.getDefaultValue()
                                .equals(columnModel.getDefaultValue(), ignoreCase = true) ||
                            columnModelDB.hasIndex() != columnModel.hasIndex() ||
                            (columnModelDB.isUnique() && !columnModel.isUnique())
                        ) {
                            hasConstraintChanged = true
                        }
                    }
                }
            }
        }
        return columnsToChangeType
    }

    private fun isNeedToRemove(columnName: String?): Boolean {
        return isRemovedFromClass(columnName) && !isIdColumn(columnName) &&
            !isForeignKeyColumn(tableModel, columnName)
    }

    private fun isRemovedFromClass(columnName: String?): Boolean {
        return !tableModel.containsColumn(columnName)
    }

    private fun generateAddColumnSQLs(columnModel: ColumnModel): List<String> {
        val sqls = ArrayList<String>()
        sqls.add(generateAddColumnSQL(tableModel.getTableName(), columnModel))
        if (columnModel.hasIndex()) {
            sqls.add(generateCreateIndexSQL(tableModel.getTableName(), columnModel))
        }
        return sqls
    }

    private fun getAddColumnSQLs(columnModelList: List<ColumnModel>): List<String> {
        val sqls = ArrayList<String>()
        for (columnModel in columnModelList) {
            sqls.addAll(generateAddColumnSQLs(columnModel))
        }
        return sqls
    }

    private fun removeColumns(removeColumnNames: List<String>) {
        LitePalLog.d(TAG, "do removeColumns $removeColumnNames")
        removeColumns(removeColumnNames, tableModel.getTableName())
        for (columnName in removeColumnNames) {
            tableModelDB.removeColumnModelByName(columnName)
        }
    }

    private fun addColumns(columnModelList: List<ColumnModel>) {
        LitePalLog.d(TAG, "do addColumn")
        execute(getAddColumnSQLs(columnModelList), db)
        invalidateTableModelCache(tableModel.getTableName())
        for (columnModel in columnModelList) {
            tableModelDB.addColumnModel(columnModel)
        }
    }

    private fun changeColumnsType(columnModelList: List<ColumnModel>) {
        LitePalLog.d(TAG, "do changeColumnsType")
        val columnNames = ArrayList<String>()
        for (columnModel in columnModelList) {
            columnNames.add(columnModel.getColumnName()!!)
        }
        removeColumns(columnNames)
        addColumns(columnModelList)
    }

    private fun changeColumnsConstraints() {
        if (hasConstraintChanged) {
            LitePalLog.d(TAG, "do changeColumnsConstraints")
            execute(getChangeColumnsConstraintsSQL(), db)
            invalidateTableModelCache(tableModel.getTableName())
        }
    }

    private fun getChangeColumnsConstraintsSQL(): List<String> {
        val alterToTempTableSQL = generateAlterToTempTableSQL(tableModel.getTableName())
        val createNewTableSQL = generateCreateTableSQL(tableModel)
        val addForeignKeySQLs = generateAddForeignKeySQL()
        val dataMigrationSQL = generateDataMigrationSQL(tableModelDB)
        val dropTempTableSQL = generateDropTempTableSQL(tableModel.getTableName())
        val createIndexSQLs = generateCreateIndexSQLs(tableModel)
        val sqls = ArrayList<String>()
        sqls.add(alterToTempTableSQL)
        sqls.add(createNewTableSQL)
        sqls.addAll(addForeignKeySQLs)
        if (!dataMigrationSQL.isNullOrEmpty()) {
            sqls.add(dataMigrationSQL)
        }
        sqls.add(dropTempTableSQL)
        sqls.addAll(createIndexSQLs)
        LitePalLog.d(TAG, "generateChangeConstraintSQL >> ")
        for (sql in sqls) {
            LitePalLog.d(TAG, sql)
        }
        LitePalLog.d(TAG, "<< generateChangeConstraintSQL")
        return sqls
    }

    private fun generateAddForeignKeySQL(): List<String> {
        val addForeignKeySQLs = ArrayList<String>()
        val foreignKeyColumns = getForeignKeyColumns(tableModel)
        for (foreignKeyColumn in foreignKeyColumns) {
            if (!tableModel.containsColumn(foreignKeyColumn)) {
                val columnModel = ColumnModel()
                columnModel.setColumnName(foreignKeyColumn)
                columnModel.setColumnType("integer")
                addForeignKeySQLs.add(generateAddColumnSQL(tableModel.getTableName(), columnModel))
            }
        }
        return addForeignKeySQLs
    }
}
