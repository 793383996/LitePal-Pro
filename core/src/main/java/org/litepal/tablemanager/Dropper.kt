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
import org.litepal.util.Const
import org.litepal.util.LitePalLog
import java.util.Locale

class Dropper : AssociationUpdater() {

    private var tableNameLookup: Set<String> = emptySet()

    internal override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean) {
        tableNameLookup = getAllTableModels()
            .mapNotNull { it.getTableName()?.lowercase(Locale.US) }
            .toSet()
        this.db = db
        dropTables()
    }

    private fun dropTables() {
        val tableNamesToDrop = findTablesToDrop()
        dropTables(tableNamesToDrop, db)
        clearCopyInTableSchema(tableNamesToDrop)
    }

    private fun findTablesToDrop(): List<String> {
        val dropTableNames = ArrayList<String>()
        var cursor: Cursor? = null
        try {
            cursor = db.query(Const.TableSchema.TABLE_NAME, null, null, null, null, null, null)
            if (cursor.moveToFirst()) {
                do {
                    val tableName = cursor.getString(cursor.getColumnIndexOrThrow(Const.TableSchema.COLUMN_NAME))
                    val tableType = cursor.getInt(cursor.getColumnIndexOrThrow(Const.TableSchema.COLUMN_TYPE))
                    if (shouldDropThisTable(tableName, tableType)) {
                        LitePalLog.d(TAG, "need to drop $tableName")
                        dropTableNames.add(tableName)
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to find tables that should be dropped.", e)
        } finally {
            cursor?.close()
        }
        return dropTableNames
    }

    private fun shouldDropThisTable(tableName: String, tableType: Int): Boolean {
        return !tableNameLookup.contains(tableName.lowercase(Locale.US)) &&
            tableType == Const.TableSchema.NORMAL_TABLE
    }
}
