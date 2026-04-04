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

import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.LitePalSupportException
import org.litepal.generated.GeneratedGenericFieldMeta
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility

class DeleteHandler(db: SQLiteDatabase) : DataHandler() {

    private var foreignKeyTableToDelete: MutableList<String>? = null

    init {
        mDatabase = db
    }

    fun onDelete(baseObj: LitePalSupport): Int {
        if (baseObj.isSaved()) {
            val supportedGenericFields = getSupportedGenericFields(baseObj.getClassName())
            deleteGenericData(baseObj.javaClass, supportedGenericFields, baseObj.getBaseObjId())
            val associationInfos = analyzeAssociations(baseObj)
            var rowsAffected = deleteCascade(baseObj)
            rowsAffected += mDatabase.delete(
                baseObj.getTableName(),
                "id = ?",
                arrayOf(baseObj.getBaseObjId().toString())
            )
            clearAssociatedModelSaveState(baseObj, associationInfos)
            return rowsAffected
        }
        return 0
    }

    fun onDelete(modelClass: Class<*>, id: Long): Int {
        val supportedGenericFields = getSupportedGenericFields(modelClass.name)
        deleteGenericData(modelClass, supportedGenericFields, id)
        analyzeAssociations(modelClass)
        var rowsAffected = deleteCascade(modelClass, id)
        rowsAffected += mDatabase.delete(getTableName(modelClass), "id = ?", arrayOf(id.toString()))
        getForeignKeyTableToDelete().clear()
        return rowsAffected
    }

    fun onDeleteAll(tableName: String, vararg conditions: String): Int {
        val conditionArray = arrayOf(*conditions)
        BaseUtility.checkConditionsCorrect(*conditionArray)
        if (conditionArray.isNotEmpty()) {
            conditionArray[0] = DBUtility.convertWhereClauseToColumnName(conditionArray[0]).orEmpty()
        }
        return mDatabase.delete(
            tableName,
            getWhereClause(*conditionArray),
            getWhereArgs(*conditionArray)
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun onDeleteAll(modelClass: Class<*>, vararg conditions: String): Int {
        val conditionArray = arrayOf(*conditions)
        BaseUtility.checkConditionsCorrect(*conditionArray)
        if (conditionArray.isNotEmpty()) {
            conditionArray[0] = DBUtility.convertWhereClauseToColumnName(conditionArray[0]).orEmpty()
        }
        val matchedIds = findMatchedIds(modelClass, *conditionArray)
        val supportedGenericFields = getSupportedGenericFields(modelClass.name)
        if (supportedGenericFields.isNotEmpty() && matchedIds.isNotEmpty()) {
            deleteGenericData(modelClass, supportedGenericFields, *matchedIds)
        }
        analyzeAssociations(modelClass)
        var rowsAffected = deleteAllCascade(modelClass, matchedIds)
        rowsAffected += mDatabase.delete(
            getTableName(modelClass),
            getWhereClause(*conditionArray),
            getWhereArgs(*conditionArray)
        )
        getForeignKeyTableToDelete().clear()
        return rowsAffected
    }

    private fun analyzeAssociations(modelClass: Class<*>) {
        val associationInfos = getAssociationInfo(modelClass.name)
        for (associationInfo in associationInfos) {
            val associatedTableName =
                DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName()).orEmpty()
            if (associationInfo.getAssociationType() == Const.Model.MANY_TO_ONE ||
                associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE
            ) {
                val classHoldsForeignKey = associationInfo.getClassHoldsForeignKey()
                if (modelClass.name != classHoldsForeignKey) {
                    getForeignKeyTableToDelete().add(associatedTableName)
                }
            } else if (associationInfo.getAssociationType() == Const.Model.MANY_TO_MANY) {
                var joinTableName = DBUtility.getIntermediateTableName(getTableName(modelClass), associatedTableName)
                joinTableName = BaseUtility.changeCase(joinTableName).orEmpty()
                getForeignKeyTableToDelete().add(joinTableName)
            }
        }
    }

    private fun deleteCascade(modelClass: Class<*>, id: Long): Int {
        var rowsAffected = 0
        for (associatedTableName in getForeignKeyTableToDelete()) {
            val fkName = getForeignKeyColumnName(getTableName(modelClass))
            rowsAffected += mDatabase.delete(associatedTableName, "$fkName = ?", arrayOf(id.toString()))
        }
        return rowsAffected
    }

    private fun deleteAllCascade(modelClass: Class<*>, ids: LongArray): Int {
        if (ids.isEmpty()) {
            return 0
        }
        var rowsAffected = 0
        for (associatedTableName in getForeignKeyTableToDelete()) {
            val tableName = getTableName(modelClass)
            val fkName = getForeignKeyColumnName(tableName).orEmpty()
            rowsAffected += deleteRowsByIds(associatedTableName, fkName, ids)
        }
        return rowsAffected
    }

    private fun analyzeAssociations(baseObj: LitePalSupport): Collection<AssociationsInfo> {
        return try {
            val associationInfos = getAssociationInfo(baseObj.getClassName())
            analyzeAssociatedModels(baseObj, associationInfos)
            associationInfos
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    private fun clearAssociatedModelSaveState(
        baseObj: LitePalSupport,
        associationInfos: Collection<AssociationsInfo>
    ) {
        try {
            for (associationInfo in associationInfos) {
                if (associationInfo.getAssociationType() == Const.Model.MANY_TO_ONE &&
                    !baseObj.getClassName().equals(associationInfo.getClassHoldsForeignKey())
                ) {
                    val associatedModels = getAssociatedModels(baseObj, associationInfo)
                    if (!associatedModels.isNullOrEmpty()) {
                        for (model in associatedModels) {
                            model?.clearSavedState()
                        }
                    }
                } else if (associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE) {
                    val model = getAssociatedModel(baseObj, associationInfo)
                    model?.clearSavedState()
                }
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    private fun deleteCascade(baseObj: LitePalSupport): Int {
        var rowsAffected = deleteAssociatedForeignKeyRows(baseObj)
        rowsAffected += deleteAssociatedJoinTableRows(baseObj)
        return rowsAffected
    }

    private fun deleteAssociatedForeignKeyRows(baseObj: LitePalSupport): Int {
        var rowsAffected = 0
        val associatedModelMap = baseObj.getAssociatedModelsMapWithFK()
        for (associatedTableName in associatedModelMap.keys) {
            val fkName = getForeignKeyColumnName(baseObj.getTableName())
            rowsAffected += mDatabase.delete(
                associatedTableName,
                "$fkName = ?",
                arrayOf(baseObj.getBaseObjId().toString())
            )
        }
        return rowsAffected
    }

    private fun deleteAssociatedJoinTableRows(baseObj: LitePalSupport): Int {
        var rowsAffected = 0
        val associatedTableNames = baseObj.getAssociatedModelsMapForJoinTable().keys
        for (associatedTableName in associatedTableNames) {
            val joinTableName = DBUtility.getIntermediateTableName(baseObj.getTableName(), associatedTableName)
            val fkName = getForeignKeyColumnName(baseObj.getTableName())
            rowsAffected += mDatabase.delete(
                joinTableName.orEmpty(),
                "$fkName = ?",
                arrayOf(baseObj.getBaseObjId().toString())
            )
        }
        return rowsAffected
    }

    private fun getForeignKeyTableToDelete(): MutableList<String> {
        if (foreignKeyTableToDelete == null) {
            foreignKeyTableToDelete = ArrayList()
        }
        return foreignKeyTableToDelete!!
    }

    private fun deleteGenericData(
        modelClass: Class<*>,
        supportedGenericFields: List<GeneratedGenericFieldMeta>,
        vararg ids: Long
    ) {
        if (ids.isEmpty()) {
            return
        }
        for (field in supportedGenericFields) {
            val tableName = DBUtility.getGenericTableName(modelClass.name, field.propertyName)
            val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(modelClass.name)
            val maxExpressionCount = 500
            val length = ids.size
            val loopCount = (length - 1) / maxExpressionCount
            for (i in 0..loopCount) {
                val begin = maxExpressionCount * i
                val end = minOf(maxExpressionCount * (i + 1), length)
                if (begin >= end) {
                    continue
                }
                val placeholders = Array(end - begin) { "?" }.joinToString(",")
                val whereClause = "$genericValueIdColumnName IN ($placeholders)"
                val whereArgs = Array(end - begin) { index ->
                    ids[begin + index].toString()
                }
                if (!TextUtils.isEmpty(whereClause)) {
                    mDatabase.delete(tableName.orEmpty(), whereClause, whereArgs)
                }
            }
        }
    }

    private fun findMatchedIds(modelClass: Class<*>, vararg conditions: String): LongArray {
        return queryIds(
            modelClass,
            getWhereClause(*conditions),
            getWhereArgs(*conditions),
            "id"
        )
    }

    private fun deleteRowsByIds(tableName: String, columnName: String, ids: LongArray): Int {
        val maxExpressionCount = 500
        var rowsAffected = 0
        val loopCount = (ids.size - 1) / maxExpressionCount
        for (i in 0..loopCount) {
            val begin = maxExpressionCount * i
            val end = minOf(maxExpressionCount * (i + 1), ids.size)
            if (begin >= end) {
                continue
            }
            val placeholders = Array(end - begin) { "?" }.joinToString(",")
            val whereClause = "$columnName IN ($placeholders)"
            val whereArgs = Array(end - begin) { index ->
                ids[begin + index].toString()
            }
            rowsAffected += mDatabase.delete(tableName, whereClause, whereArgs)
        }
        return rowsAffected
    }
}
