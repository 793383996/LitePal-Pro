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

package org.litepal.crud

import org.litepal.LitePalRuntime
import org.litepal.Operator
import org.litepal.exceptions.LitePalSupportException
import org.litepal.tablemanager.Connector
import org.litepal.tablemanager.DatabaseRuntimeLock
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility

open class LitePalSupport protected constructor() {

    private var baseObjId: Long = 0

    private var associatedModelsMapWithFK: MutableMap<String, MutableSet<Long>>? = null
    private var associatedModelsMapWithoutFK: MutableMap<String, Long>? = null
    private var associatedModelsMapForJoinTable: MutableMap<String, MutableList<Long>>? = null
    private var listToClearSelfFK: MutableList<String>? = null
    private var listToClearAssociatedFK: MutableList<String>? = null
    private var fieldsToSetToDefault: MutableList<String>? = null

    fun delete(): Int {
        return Operator.runOnTransactionExecutor {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val deleteHandler = DeleteHandler(db)
                    val rowsAffected = deleteHandler.onDelete(this)
                    baseObjId = 0
                    db.setTransactionSuccessful()
                    rowsAffected
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    fun update(id: Long): Int {
        return Operator.runOnTransactionExecutor {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val updateHandler = UpdateHandler(db)
                    val rowsAffected = updateHandler.onUpdate(this, id)
                    getFieldsToSetToDefault().clear()
                    db.setTransactionSuccessful()
                    rowsAffected
                } catch (e: Exception) {
                    throw LitePalSupportException(e.message, e)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    fun updateAll(vararg conditions: String): Int {
        return Operator.runOnTransactionExecutor {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val updateHandler = UpdateHandler(db)
                    val rowsAffected = updateHandler.onUpdateAll(this, *conditions)
                    getFieldsToSetToDefault().clear()
                    db.setTransactionSuccessful()
                    rowsAffected
                } catch (e: Exception) {
                    throw LitePalSupportException(e.message, e)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    fun save(): Boolean {
        return try {
            saveThrows()
            true
        } catch (e: Exception) {
            LitePalRuntime.onError("LitePalSupport", "save", e)
            false
        }
    }


    fun saveThrows() {
        Operator.runOnTransactionExecutor {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val saveHandler = SaveHandler(db)
                    saveHandler.onSave(this)
                    clearAssociatedData()
                    db.setTransactionSuccessful()
                } catch (e: Exception) {
                    throw LitePalSupportException(e.message, e)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun saveOrUpdate(vararg conditions: String): Boolean {
        return Operator.runOnTransactionExecutor {
            DatabaseRuntimeLock.withReadLock {
                if (conditions.isEmpty()) {
                    return@withReadLock save()
                }
                val list = Operator.runOnQueryExecutor {
                    Operator.where(*conditions).find(javaClass) as List<LitePalSupport>
                }
                if (list.isEmpty()) {
                    return@withReadLock save()
                }
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    for (support in list) {
                        baseObjId = support.baseObjId
                        val saveHandler = SaveHandler(db)
                        saveHandler.onSave(this)
                        clearAssociatedData()
                    }
                    db.setTransactionSuccessful()
                    true
                } catch (e: Exception) {
                    LitePalRuntime.onError("LitePalSupport", "saveOrUpdate", e)
                    false
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    fun isSaved(): Boolean {
        return baseObjId > 0
    }

    fun clearSavedState() {
        baseObjId = 0
    }

    fun setToDefault(fieldName: String) {
        getFieldsToSetToDefault().add(fieldName)
    }

    fun assignBaseObjId(baseObjId: Long) {
        this.baseObjId = baseObjId
    }

    fun getBaseObjId(): Long {
        return baseObjId
    }

    fun getClassName(): String {
        return javaClass.name
    }

    fun getTableName(): String {
        return BaseUtility.changeCase(DBUtility.getTableNameByClassName(getClassName())).orEmpty()
    }

    fun getFieldsToSetToDefault(): MutableList<String> {
        if (fieldsToSetToDefault == null) {
            fieldsToSetToDefault = ArrayList()
        }
        return fieldsToSetToDefault!!
    }

    fun addAssociatedModelWithFK(associatedTableName: String, associatedId: Long) {
        val associatedIdsWithFKSet = getAssociatedModelsMapWithFK()[associatedTableName]
        if (associatedIdsWithFKSet == null) {
            val set = HashSet<Long>()
            set.add(associatedId)
            associatedModelsMapWithFK!![associatedTableName] = set
        } else {
            associatedIdsWithFKSet.add(associatedId)
        }
    }

    fun getAssociatedModelsMapWithFK(): MutableMap<String, MutableSet<Long>> {
        if (associatedModelsMapWithFK == null) {
            associatedModelsMapWithFK = HashMap()
        }
        return associatedModelsMapWithFK!!
    }

    fun addAssociatedModelForJoinTable(associatedModelName: String, associatedId: Long) {
        val associatedIdsM2MSet = getAssociatedModelsMapForJoinTable()[associatedModelName]
        if (associatedIdsM2MSet == null) {
            val list = ArrayList<Long>()
            list.add(associatedId)
            associatedModelsMapForJoinTable!![associatedModelName] = list
        } else {
            associatedIdsM2MSet.add(associatedId)
        }
    }

    fun addEmptyModelForJoinTable(associatedModelName: String) {
        val associatedIdsM2MSet = getAssociatedModelsMapForJoinTable()[associatedModelName]
        if (associatedIdsM2MSet == null) {
            associatedModelsMapForJoinTable!![associatedModelName] = ArrayList()
        }
    }

    fun getAssociatedModelsMapForJoinTable(): MutableMap<String, MutableList<Long>> {
        if (associatedModelsMapForJoinTable == null) {
            associatedModelsMapForJoinTable = HashMap()
        }
        return associatedModelsMapForJoinTable!!
    }

    fun addAssociatedModelWithoutFK(associatedTableName: String, associatedId: Long) {
        getAssociatedModelsMapWithoutFK()[associatedTableName] = associatedId
    }

    fun getAssociatedModelsMapWithoutFK(): MutableMap<String, Long> {
        if (associatedModelsMapWithoutFK == null) {
            associatedModelsMapWithoutFK = HashMap()
        }
        return associatedModelsMapWithoutFK!!
    }

    fun addFKNameToClearSelf(foreignKeyName: String) {
        val list = getListToClearSelfFK()
        if (!list.contains(foreignKeyName)) {
            list.add(foreignKeyName)
        }
    }

    fun getListToClearSelfFK(): MutableList<String> {
        if (listToClearSelfFK == null) {
            listToClearSelfFK = ArrayList()
        }
        return listToClearSelfFK!!
    }

    fun addAssociatedTableNameToClearFK(associatedTableName: String) {
        val list = getListToClearAssociatedFK()
        if (!list.contains(associatedTableName)) {
            list.add(associatedTableName)
        }
    }

    fun getListToClearAssociatedFK(): MutableList<String> {
        if (listToClearAssociatedFK == null) {
            listToClearAssociatedFK = ArrayList()
        }
        return listToClearAssociatedFK!!
    }

    fun clearAssociatedData() {
        clearIdOfModelWithFK()
        clearIdOfModelWithoutFK()
        clearIdOfModelForJoinTable()
        clearFKNameList()
    }

    private fun clearIdOfModelWithFK() {
        for (associatedModelName in getAssociatedModelsMapWithFK().keys) {
            associatedModelsMapWithFK!![associatedModelName]?.clear()
        }
        associatedModelsMapWithFK!!.clear()
    }

    private fun clearIdOfModelWithoutFK() {
        getAssociatedModelsMapWithoutFK().clear()
    }

    private fun clearIdOfModelForJoinTable() {
        for (associatedModelName in getAssociatedModelsMapForJoinTable().keys) {
            associatedModelsMapForJoinTable!![associatedModelName]?.clear()
        }
        associatedModelsMapForJoinTable!!.clear()
    }

    private fun clearFKNameList() {
        getListToClearSelfFK().clear()
        getListToClearAssociatedFK().clear()
    }

    companion object {
        @Deprecated(
            message = "MD5 is deprecated for reversible field encryption. Use it only for one-way digest scenarios."
        )
        const val MD5 = "MD5"

        const val AES = "AES"
    }
}

