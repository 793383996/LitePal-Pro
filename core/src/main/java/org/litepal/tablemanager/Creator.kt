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
import org.litepal.tablemanager.model.TableModel
import org.litepal.util.Const
import org.litepal.util.DBUtility
import java.util.ArrayList

open class Creator : AssociationCreator() {

    internal override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean) {
        for (tableModel in getAllTableModels()) {
            createOrUpgradeTable(tableModel, db, force)
        }
    }

    protected fun createOrUpgradeTable(tableModel: TableModel, db: SQLiteDatabase, force: Boolean) {
        execute(getCreateTableSQLs(tableModel, db, force), db)
        giveTableSchemaACopy(tableModel.getTableName(), Const.TableSchema.NORMAL_TABLE, db)
    }

    protected fun getCreateTableSQLs(
        tableModel: TableModel,
        db: SQLiteDatabase,
        force: Boolean
    ): List<String>? {
        val sqls: MutableList<String> = ArrayList()
        if (force) {
            sqls.add(generateDropTableSQL(tableModel))
            sqls.add(generateCreateTableSQL(tableModel))
        } else if (DBUtility.isTableExists(tableModel.getTableName(), db)) {
            return null
        } else {
            sqls.add(generateCreateTableSQL(tableModel))
        }
        sqls.addAll(generateCreateIndexSQLs(tableModel))
        return sqls
    }

    private fun generateDropTableSQL(tableModel: TableModel): String {
        return generateDropTableSQL(tableModel.getTableName())
    }

    fun generateCreateTableSQL(tableModel: TableModel): String {
        return generateCreateTableSQL(tableModel.getTableName().orEmpty(), tableModel.getColumnModels(), true)
    }

    fun generateCreateIndexSQLs(tableModel: TableModel): List<String> {
        return generateCreateIndexSQLs(tableModel.getTableName().orEmpty(), tableModel.getColumnModels())
    }
}
