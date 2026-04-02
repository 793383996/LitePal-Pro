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

import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import org.litepal.LitePalBase
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.parser.LitePalAttr
import org.litepal.tablemanager.model.AssociationsModel
import org.litepal.tablemanager.model.TableModel
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility

abstract class Generator : LitePalBase() {

    private var tableModels: MutableList<TableModel>? = null
    private var allRelationModels: Collection<AssociationsModel>? = null

    protected fun getAllTableModels(): Collection<TableModel> {
        if (tableModels == null) {
            tableModels = mutableListOf()
        }
        if (!canUseCache()) {
            tableModels!!.clear()
            for (className in LitePalAttr.getInstance().getClassNames()) {
                tableModels!!.add(getTableModel(className))
            }
        }
        return tableModels!!
    }

    protected fun getAllAssociations(): Collection<AssociationsModel> {
        if (allRelationModels == null || allRelationModels!!.isEmpty()) {
            allRelationModels = getAssociations(LitePalAttr.getInstance().getClassNames())
        }
        return allRelationModels!!
    }

    protected fun execute(sqls: List<String>?, db: SQLiteDatabase) {
        var throwSql = ""
        try {
            if (!sqls.isNullOrEmpty()) {
                for (sql in sqls) {
                    if (!TextUtils.isEmpty(sql)) {
                        throwSql = BaseUtility.changeCase(sql).orEmpty()
                        db.execSQL(throwSql)
                        DBUtility.onSchemaSqlExecuted(db, throwSql)
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseGenerateException(DatabaseGenerateException.SQL_ERROR + throwSql)
        }
    }

    private fun canUseCache(): Boolean {
        val models = tableModels ?: return false
        return models.size == LitePalAttr.getInstance().getClassNames().size
    }

    internal abstract fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean)
    internal abstract fun addOrUpdateAssociation(db: SQLiteDatabase, force: Boolean)

    companion object {
        const val TAG = "Generator"

        private fun addAssociation(db: SQLiteDatabase, force: Boolean) {
            val associationsCreator: AssociationCreator = Creator()
            associationsCreator.addOrUpdateAssociation(db, force)
        }

        private fun updateAssociations(db: SQLiteDatabase) {
            val associationUpgrader: AssociationUpdater = Upgrader()
            associationUpgrader.addOrUpdateAssociation(db, false)
        }

        private fun upgradeTables(db: SQLiteDatabase) {
            val upgrader = Upgrader()
            upgrader.createOrUpgradeTable(db, false)
        }

        private fun create(db: SQLiteDatabase, force: Boolean) {
            val creator = Creator()
            creator.createOrUpgradeTable(db, force)
        }

        private fun drop(db: SQLiteDatabase) {
            val dropper = Dropper()
            dropper.createOrUpgradeTable(db, false)
        }

        @JvmStatic
        fun create(db: SQLiteDatabase) {
            DBUtility.beginTableSnapshotSession(db)
            try {
                create(db, true)
                addAssociation(db, true)
            } finally {
                DBUtility.endTableSnapshotSession(db)
            }
        }

        @JvmStatic
        fun upgrade(db: SQLiteDatabase) {
            DBUtility.beginTableSnapshotSession(db)
            try {
                drop(db)
                create(db, false)
                updateAssociations(db)
                upgradeTables(db)
                addAssociation(db, false)
            } finally {
                DBUtility.endTableSnapshotSession(db)
            }
        }
    }
}
