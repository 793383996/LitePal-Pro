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
import org.litepal.crud.model.AssociationsInfo
import org.litepal.tablemanager.Connector
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog

class Many2ManyAnalyzer : AssociationsAnalyzer() {

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    fun analyze(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        val associatedModels = getAssociatedModels(baseObj, associationInfo)
        declareAssociations(baseObj, associationInfo)
        if (associatedModels != null) {
            for (associatedModel in associatedModels) {
                if (associationInfo.getAssociateSelfFromOtherModel().isNullOrBlank()) {
                    continue
                }
                val tempCollection = getReverseAssociatedModels(associatedModel, associationInfo)
                val reverseAssociatedModels = checkAssociatedModelCollection(
                    tempCollection,
                    associationInfo.getAssociateSelfCollectionType()
                )
                addNewModelForAssociatedModel(reverseAssociatedModels, baseObj)
                setReverseAssociatedModels(associatedModel, associationInfo, reverseAssociatedModels)
                dealAssociatedModel(baseObj, associatedModel)
            }
        }
    }

    private fun declareAssociations(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        baseObj.addEmptyModelForJoinTable(getAssociatedTableName(associationInfo))
    }

    private fun addNewModelForAssociatedModel(
        associatedModelCollection: MutableCollection<LitePalSupport>,
        baseObj: LitePalSupport
    ) {
        if (!associatedModelCollection.contains(baseObj)) {
            associatedModelCollection.add(baseObj)
        }
    }

    private fun dealAssociatedModel(baseObj: LitePalSupport, associatedModel: LitePalSupport) {
        if (associatedModel.isSaved()) {
            baseObj.addAssociatedModelForJoinTable(associatedModel.getTableName(), associatedModel.getBaseObjId())
        }
    }

    private fun getAssociatedTableName(associationInfo: AssociationsInfo): String {
        return BaseUtility.changeCase(
            DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName())
        ).orEmpty()
    }

    @Deprecated("")
    private fun isDataExists(baseObj: LitePalSupport, associatedModel: LitePalSupport): Boolean {
        var exists = false
        val db: SQLiteDatabase = Connector.getDatabase()
        var cursor: android.database.Cursor? = null
        try {
            cursor = db.query(
                getJoinTableName(baseObj, associatedModel),
                null,
                getSelection(baseObj, associatedModel),
                getSelectionArgs(baseObj, associatedModel),
                null,
                null,
                null
            )
            exists = cursor.count > 0
        } catch (e: Exception) {
            LitePalLog.e("Many2ManyAnalyzer", "Failed to check join-table existing relation.", e)
            return true
        } finally {
            cursor?.close()
        }
        return exists
    }

    private fun getSelection(baseObj: LitePalSupport, associatedModel: LitePalSupport): String {
        return buildString {
            append(getForeignKeyColumnName(baseObj.getTableName()))
            append(" = ? and ")
            append(getForeignKeyColumnName(associatedModel.getTableName()))
            append(" = ?")
        }
    }

    private fun getSelectionArgs(baseObj: LitePalSupport, associatedModel: LitePalSupport): Array<String> {
        return arrayOf(baseObj.getBaseObjId().toString(), associatedModel.getBaseObjId().toString())
    }

    private fun getJoinTableName(baseObj: LitePalSupport, associatedModel: LitePalSupport): String {
        return getIntermediateTableName(baseObj, associatedModel.getTableName())
    }
}
