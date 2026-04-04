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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.LitePalSupportException
import org.litepal.generated.GeneratedFieldMeta
import org.litepal.generated.GeneratedGenericFieldMeta
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.generated.IdAccessor
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog

class SaveHandler(db: SQLiteDatabase) : DataHandler() {

    private val values: ContentValues = ContentValues()

    init {
        mDatabase = db
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    fun onSave(baseObj: LitePalSupport) {
        val className = baseObj.getClassName()
        val supportedFields = getSupportedFields(className)
        val supportedGenericFields = getSupportedGenericFields(className)
        val associationInfos = getAssociationInfo(className)
        if (!baseObj.isSaved()) {
            analyzeAssociatedModels(baseObj, associationInfos)
            doSaveAction(baseObj, supportedFields, supportedGenericFields)
            analyzeAssociatedModels(baseObj, associationInfos)
        } else {
            analyzeAssociatedModels(baseObj, associationInfos)
            doUpdateAction(baseObj, supportedFields, supportedGenericFields)
        }
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    fun <T : LitePalSupport> onSaveAll(collection: Collection<T>?) {
        if (!collection.isNullOrEmpty()) {
            val firstObj = collection.first()
            val className = firstObj.getClassName()
            val supportedFields = getSupportedFields(className)
            val supportedGenericFields = getSupportedGenericFields(className)
            val associationInfos = getAssociationInfo(className)
            for (baseObj in collection) {
                if (!baseObj.isSaved()) {
                    analyzeAssociatedModels(baseObj, associationInfos)
                    doSaveAction(baseObj, supportedFields, supportedGenericFields)
                    analyzeAssociatedModels(baseObj, associationInfos)
                } else {
                    analyzeAssociatedModels(baseObj, associationInfos)
                    doUpdateAction(baseObj, supportedFields, supportedGenericFields)
                }
                baseObj.clearAssociatedData()
            }
        }
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    private fun doSaveAction(
        baseObj: LitePalSupport,
        supportedFields: List<GeneratedFieldMeta>,
        supportedGenericFields: List<GeneratedGenericFieldMeta>
    ) {
        values.clear()
        beforeSave(baseObj, supportedFields, values)
        val id = saving(baseObj, values)
        afterSave(baseObj, supportedGenericFields, id)
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    private fun beforeSave(
        baseObj: LitePalSupport,
        supportedFields: List<GeneratedFieldMeta>,
        values: ContentValues
    ) {
        putFieldsValue(baseObj, supportedFields, values)
        putForeignKeyValue(values, baseObj)
    }

    private fun saving(baseObj: LitePalSupport, values: ContentValues): Long {
        if (values.size() == 0) {
            values.putNull("id")
        }
        return mDatabase.insert(baseObj.getTableName(), null, values)
    }

    @Throws(IllegalAccessException::class)
    private fun afterSave(
        baseObj: LitePalSupport,
        supportedGenericFields: List<GeneratedGenericFieldMeta>,
        id: Long
    ) {
        throwIfSaveFailed(id)
        assignIdValue(baseObj, id)
        updateGenericTables(baseObj, supportedGenericFields, id)
        updateAssociatedTableWithFK(baseObj)
        insertIntermediateJoinTableValue(baseObj, false)
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    private fun doUpdateAction(
        baseObj: LitePalSupport,
        supportedFields: List<GeneratedFieldMeta>,
        supportedGenericFields: List<GeneratedGenericFieldMeta>
    ) {
        values.clear()
        beforeUpdate(baseObj, supportedFields, values)
        updating(baseObj, values)
        afterUpdate(baseObj, supportedGenericFields)
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    private fun beforeUpdate(
        baseObj: LitePalSupport,
        supportedFields: List<GeneratedFieldMeta>,
        values: ContentValues
    ) {
        putFieldsValue(baseObj, supportedFields, values)
        putForeignKeyValue(values, baseObj)
        for (fkName in baseObj.getListToClearSelfFK()) {
            values.putNull(fkName)
        }
    }

    private fun updating(baseObj: LitePalSupport, values: ContentValues) {
        if (values.size() > 0) {
            mDatabase.update(
                baseObj.getTableName(),
                values,
                "id = ?",
                arrayOf(baseObj.getBaseObjId().toString())
            )
        }
    }

    @Throws(IllegalAccessException::class)
    private fun afterUpdate(baseObj: LitePalSupport, supportedGenericFields: List<GeneratedGenericFieldMeta>) {
        updateGenericTables(baseObj, supportedGenericFields, baseObj.getBaseObjId())
        updateAssociatedTableWithFK(baseObj)
        insertIntermediateJoinTableValue(baseObj, true)
        clearFKValueInAssociatedTable(baseObj)
    }

    private fun throwIfSaveFailed(id: Long) {
        if (id == -1L) {
            throw LitePalSupportException(LitePalSupportException.SAVE_FAILED)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun assignIdValue(baseObj: LitePalSupport, id: Long) {
        try {
            giveBaseObjIdValue(baseObj, id)
            val idAccessor = GeneratedRegistryLocator
                .findEntityMeta(baseObj.getClassName())
                ?.idAccessor as? IdAccessor<LitePalSupport>
            if (idAccessor != null) {
                idAccessor.setId(baseObj, id)
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    private fun putForeignKeyValue(values: ContentValues, baseObj: LitePalSupport) {
        val associatedModelMap = baseObj.getAssociatedModelsMapWithoutFK()
        for (associatedTableName in associatedModelMap.keys) {
            values.put(getForeignKeyColumnName(associatedTableName), associatedModelMap[associatedTableName])
        }
    }

    private fun updateAssociatedTableWithFK(baseObj: LitePalSupport) {
        val associatedModelMap = baseObj.getAssociatedModelsMapWithFK()
        val values = ContentValues()
        for (associatedTableName in associatedModelMap.keys) {
            values.clear()
            val fkName = getForeignKeyColumnName(baseObj.getTableName())
            values.put(fkName, baseObj.getBaseObjId())
            val ids = associatedModelMap[associatedTableName]
            if (!ids.isNullOrEmpty()) {
                updateByIds(associatedTableName, values, ids)
            }
        }
    }

    private fun clearFKValueInAssociatedTable(baseObj: LitePalSupport) {
        val associatedTableNames = baseObj.getListToClearAssociatedFK()
        for (associatedTableName in associatedTableNames) {
            val fkColumnName = getForeignKeyColumnName(baseObj.getTableName())
            val values = ContentValues()
            values.putNull(fkColumnName)
            mDatabase.update(
                associatedTableName,
                values,
                "$fkColumnName = ?",
                arrayOf(baseObj.getBaseObjId().toString())
            )
        }
    }

    private fun insertIntermediateJoinTableValue(baseObj: LitePalSupport, isUpdate: Boolean) {
        val associatedIdsM2M = baseObj.getAssociatedModelsMapForJoinTable()
        val values = ContentValues()
        for (associatedTableName in associatedIdsM2M.keys) {
            val joinTableName = getIntermediateTableName(baseObj, associatedTableName)
            if (isUpdate) {
                mDatabase.delete(
                    joinTableName,
                    getWhereForJoinTableToDelete(baseObj),
                    arrayOf(baseObj.getBaseObjId().toString())
                )
            }
            val associatedIdsM2MSet = associatedIdsM2M[associatedTableName]
            if (associatedIdsM2MSet != null) {
                for (associatedId in associatedIdsM2MSet) {
                    values.clear()
                    values.put(getForeignKeyColumnName(baseObj.getTableName()), baseObj.getBaseObjId())
                    values.put(getForeignKeyColumnName(associatedTableName), associatedId)
                    mDatabase.insert(joinTableName, null, values)
                }
            }
        }
    }

    private fun getWhereForJoinTableToDelete(baseObj: LitePalSupport): String {
        return StringBuilder().append(getForeignKeyColumnName(baseObj.getTableName())).append(" = ?")
            .toString()
    }

    private fun updateByIds(
        tableName: String,
        values: ContentValues,
        ids: Collection<Long>
    ): Int {
        val targetIds = ids.toList()
        if (targetIds.isEmpty()) {
            return 0
        }
        val chunkSize = 500
        var rows = 0
        val loopCount = (targetIds.size - 1) / chunkSize
        for (i in 0..loopCount) {
            val begin = chunkSize * i
            val end = minOf(chunkSize * (i + 1), targetIds.size)
            if (begin >= end) {
                continue
            }
            val placeholders = Array(end - begin) { "?" }.joinToString(",")
            val whereClause = "id IN ($placeholders)"
            val whereArgs = Array(end - begin) { index -> targetIds[begin + index].toString() }
            rows += mDatabase.update(tableName, values, whereClause, whereArgs)
        }
        return rows
    }

    @Throws(IllegalAccessException::class)
    private fun updateGenericTables(
        baseObj: LitePalSupport,
        supportedGenericFields: List<GeneratedGenericFieldMeta>,
        id: Long
    ) {
        for (field in supportedGenericFields) {
            var algorithm: String? = null
            val genericTypeName = field.elementTypeName
            if (!field.encryptAlgorithm.isNullOrBlank() && "java.lang.String" == genericTypeName) {
                algorithm = field.encryptAlgorithm
            }
            val collection = getFieldValue(baseObj, field.propertyName) as? Collection<*>
            if (collection != null) {
                LitePalLog.d(
                    TAG,
                    "updateGenericTables: class name is ${baseObj.getClassName()} , field name is ${field.propertyName}"
                )
                val tableName = DBUtility.getGenericTableName(baseObj.getClassName(), field.propertyName)
                val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(baseObj.getClassName())
                mDatabase.delete(tableName.orEmpty(), "$genericValueIdColumnName = ?", arrayOf(id.toString()))
                for (item in collection) {
                    val values = ContentValues()
                    values.put(genericValueIdColumnName, id)
                    var objectValue: Any? = encryptValue(algorithm, item)
                    if (baseObj.getClassName() == genericTypeName) {
                        val dataSupport = objectValue as? LitePalSupport ?: continue
                        val baseObjId = dataSupport.getBaseObjId()
                        if (baseObjId <= 0) continue
                        values.put(DBUtility.getM2MSelfRefColumnName(field.propertyName), baseObjId)
                    } else {
                        val genericColumnName = org.litepal.util.BaseUtility.changeCase(
                            DBUtility.convertToValidColumnName(field.propertyName)
                        ).orEmpty()
                        putGeneratedContentValue(values, genericColumnName, objectValue)
                    }
                    mDatabase.insert(tableName.orEmpty(), null, values)
                }
            }
        }
    }
}
