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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.tablemanager.model.AssociationsModel
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.tablemanager.model.GenericModel
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import java.util.Locale

abstract class AssociationCreator : Generator() {

    internal abstract override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean)

    internal override fun addOrUpdateAssociation(db: SQLiteDatabase, force: Boolean) {
        addAssociations(getAllAssociations(), db, force)
    }

    protected fun generateCreateTableSQL(
        tableName: String,
        columnModels: Collection<ColumnModel>,
        autoIncrementId: Boolean
    ): String {
        val createTableSQL = StringBuilder("create table ")
        createTableSQL.append(tableName).append(" (")
        if (autoIncrementId) {
            createTableSQL.append("id integer primary key autoincrement,")
        }
        if (isContainsOnlyIdField(columnModels)) {
            createTableSQL.deleteCharAt(createTableSQL.length - 1)
        }
        var needSeparator = false
        for (columnModel in columnModels) {
            if (columnModel.isIdColumn()) {
                continue
            }
            if (needSeparator) {
                createTableSQL.append(", ")
            }
            needSeparator = true
            createTableSQL.append(columnModel.getColumnName()).append(" ")
                .append(columnModel.getColumnType())
            if (!columnModel.isNullable()) {
                createTableSQL.append(" not null")
            }
            if (columnModel.isUnique()) {
                createTableSQL.append(" unique")
            }
            val defaultValue = columnModel.getDefaultValue()
            if (!TextUtils.isEmpty(defaultValue)) {
                createTableSQL.append(" default ").append(defaultValue)
            }
        }
        createTableSQL.append(")")
        LitePalLog.d(TAG, "create table sql is >> $createTableSQL")
        return createTableSQL.toString()
    }

    protected fun generateCreateIndexSQLs(
        tableName: String,
        columnModels: Collection<ColumnModel>
    ): List<String> {
        val sqls = ArrayList<String>()
        for (columnModel in columnModels) {
            if (columnModel.hasIndex()) {
                sqls.add(generateCreateIndexSQL(tableName, columnModel))
            }
        }
        return sqls
    }

    protected fun generateDropTableSQL(tableName: String?): String {
        return "drop table if exists $tableName"
    }

    protected fun generateAddColumnSQL(tableName: String?, columnModel: ColumnModel): String {
        val addColumnSQL = StringBuilder()
        addColumnSQL.append("alter table ").append(tableName)
        addColumnSQL.append(" add column ").append(columnModel.getColumnName())
        addColumnSQL.append(" ").append(columnModel.getColumnType())
        if (!columnModel.isNullable()) {
            addColumnSQL.append(" not null")
        }
        if (columnModel.isUnique()) {
            addColumnSQL.append(" unique")
        }
        var defaultValue = columnModel.getDefaultValue()
        if (!TextUtils.isEmpty(defaultValue)) {
            addColumnSQL.append(" default ").append(defaultValue)
        } else if (!columnModel.isNullable()) {
            if ("integer".equals(columnModel.getColumnType(), ignoreCase = true)) {
                defaultValue = "0"
            } else if ("text".equals(columnModel.getColumnType(), ignoreCase = true)) {
                defaultValue = "''"
            } else if ("real".equals(columnModel.getColumnType(), ignoreCase = true)) {
                defaultValue = "0.0"
            }
            addColumnSQL.append(" default ").append(defaultValue)
        }
        LitePalLog.d(TAG, "add column sql is >> $addColumnSQL")
        return addColumnSQL.toString()
    }

    protected fun generateCreateIndexSQL(tableName: String?, columnModel: ColumnModel): String {
        val createIndexSQL = StringBuilder()
        if (columnModel.hasIndex()) {
            createIndexSQL.append("create index ")
            createIndexSQL.append(
                DBUtility.getIndexName(
                    tableName,
                    columnModel.getColumnName()
                )
            )
            createIndexSQL.append(" on ")
            createIndexSQL.append(tableName)
            createIndexSQL.append(" (")
            createIndexSQL.append(columnModel.getColumnName())
            createIndexSQL.append(")")
            LitePalLog.d(TAG, "create table index sql is >> $createIndexSQL")
        }
        return createIndexSQL.toString()
    }

    protected fun isForeignKeyColumnFormat(columnName: String?): Boolean {
        if (!TextUtils.isEmpty(columnName)) {
            return columnName!!.lowercase(Locale.US).endsWith("_id") && !columnName.equals(
                "_id",
                ignoreCase = true
            )
        }
        return false
    }

    protected fun giveTableSchemaACopy(tableName: String?, tableType: Int, db: SQLiteDatabase) {
        if (isSpecialTable(tableName)) {
            return
        }
        try {
            if (!DBUtility.hasTableSchemaEntry(tableName, db)) {
                val values = ContentValues()
                values.put(Const.TableSchema.COLUMN_NAME, BaseUtility.changeCase(tableName))
                values.put(Const.TableSchema.COLUMN_TYPE, tableType)
                db.insert(Const.TableSchema.TABLE_NAME, null, values)
            }
            DBUtility.noteTableSchemaType(db, tableName, tableType)
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to copy table schema metadata.", e)
        }
    }

    private fun isSpecialTable(tableName: String?): Boolean {
        return Const.TableSchema.TABLE_NAME.equals(tableName, ignoreCase = true)
    }

    private fun addAssociations(
        associatedModels: Collection<AssociationsModel>,
        db: SQLiteDatabase,
        force: Boolean
    ) {
        for (associationModel in associatedModels) {
            if (Const.Model.MANY_TO_ONE == associationModel.getAssociationType() ||
                Const.Model.ONE_TO_ONE == associationModel.getAssociationType()
            ) {
                addForeignKeyColumn(
                    associationModel.getTableName(),
                    associationModel.getAssociatedTableName(),
                    associationModel.getTableHoldsForeignKey(),
                    db
                )
            } else if (Const.Model.MANY_TO_MANY == associationModel.getAssociationType()) {
                createIntermediateTable(
                    associationModel.getTableName(),
                    associationModel.getAssociatedTableName(),
                    db,
                    force
                )
            }
        }
        for (genericModel in getGenericModels()) {
            createGenericTable(genericModel, db, force)
        }
    }

    private fun createIntermediateTable(
        tableName: String?,
        associatedTableName: String?,
        db: SQLiteDatabase,
        force: Boolean
    ) {
        val columnModelList = ArrayList<ColumnModel>()
        val column1 = ColumnModel()
        column1.setColumnName("${tableName}_id")
        column1.setColumnType("integer")
        val column2 = ColumnModel()
        column2.setColumnName("${associatedTableName}_id")
        column2.setColumnType("integer")
        columnModelList.add(column1)
        columnModelList.add(column2)
        val intermediateTableName = DBUtility.getIntermediateTableName(tableName, associatedTableName)
        val sqls = ArrayList<String>()
        if (DBUtility.isTableExists(intermediateTableName, db)) {
            if (force) {
                sqls.add(generateDropTableSQL(intermediateTableName))
                sqls.add(generateCreateTableSQL(intermediateTableName!!, columnModelList, false))
            }
        } else {
            sqls.add(generateCreateTableSQL(intermediateTableName!!, columnModelList, false))
        }
        sqls.add(
            generateCreateIndexIfNotExistsSQL(
                intermediateTableName,
                column1.getColumnName().orEmpty()
            )
        )
        sqls.add(
            generateCreateIndexIfNotExistsSQL(
                intermediateTableName,
                column2.getColumnName().orEmpty()
            )
        )
        execute(sqls, db)
        giveTableSchemaACopy(intermediateTableName, Const.TableSchema.INTERMEDIATE_JOIN_TABLE, db)
    }

    private fun createGenericTable(genericModel: GenericModel, db: SQLiteDatabase, force: Boolean) {
        val tableName = genericModel.getTableName()!!
        val valueColumnName = genericModel.getValueColumnName()
        val valueColumnType = genericModel.getValueColumnType()
        val valueIdColumnName = genericModel.getValueIdColumnName()
        val columnModelList = ArrayList<ColumnModel>()
        val column1 = ColumnModel()
        column1.setColumnName(valueColumnName)
        column1.setColumnType(valueColumnType)
        val column2 = ColumnModel()
        column2.setColumnName(valueIdColumnName)
        column2.setColumnType("integer")
        columnModelList.add(column1)
        columnModelList.add(column2)
        val sqls = ArrayList<String>()
        if (DBUtility.isTableExists(tableName, db)) {
            if (force) {
                sqls.add(generateDropTableSQL(tableName))
                sqls.add(generateCreateTableSQL(tableName, columnModelList, false))
            }
        } else {
            sqls.add(generateCreateTableSQL(tableName, columnModelList, false))
        }
        sqls.add(
            generateCreateIndexIfNotExistsSQL(
                tableName,
                valueIdColumnName.orEmpty()
            )
        )
        execute(sqls, db)
        giveTableSchemaACopy(tableName, Const.TableSchema.GENERIC_TABLE, db)
    }

    private fun generateCreateIndexIfNotExistsSQL(tableName: String, columnName: String): String {
        val indexName = DBUtility.getIndexName(tableName, columnName)
        return "create index if not exists $indexName on $tableName ($columnName)"
    }

    protected fun addForeignKeyColumn(
        tableName: String?,
        associatedTableName: String?,
        tableHoldsForeignKey: String?,
        db: SQLiteDatabase
    ) {
        if (DBUtility.isTableExists(tableName, db)) {
            if (DBUtility.isTableExists(associatedTableName, db)) {
                var foreignKeyColumn: String? = null
                if (tableName == tableHoldsForeignKey) {
                    foreignKeyColumn = getForeignKeyColumnName(associatedTableName)
                } else if (associatedTableName == tableHoldsForeignKey) {
                    foreignKeyColumn = getForeignKeyColumnName(tableName)
                }
                if (!DBUtility.isColumnExists(foreignKeyColumn, tableHoldsForeignKey, db)) {
                    val columnModel = ColumnModel()
                    columnModel.setColumnName(foreignKeyColumn)
                    columnModel.setColumnType("integer")
                    val sqls = ArrayList<String>()
                    sqls.add(generateAddColumnSQL(tableHoldsForeignKey, columnModel))
                    execute(sqls, db)
                } else {
                    LitePalLog.d(TAG, "column $foreignKeyColumn is already exist, no need to add one")
                }
            } else {
                throw DatabaseGenerateException(
                    DatabaseGenerateException.TABLE_DOES_NOT_EXIST + associatedTableName
                )
            }
        } else {
            throw DatabaseGenerateException(DatabaseGenerateException.TABLE_DOES_NOT_EXIST + tableName)
        }
    }

    private fun isContainsOnlyIdField(columnModels: Collection<ColumnModel>): Boolean {
        for (columnModel in columnModels) {
            if (!columnModel.isIdColumn()) {
                return false
            }
        }
        return true
    }
}
