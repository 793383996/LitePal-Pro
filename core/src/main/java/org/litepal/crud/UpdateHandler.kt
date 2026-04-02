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
import android.os.Build
import org.litepal.annotation.Encrypt
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.LitePalSupportException
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException

class UpdateHandler(db: SQLiteDatabase) : DataHandler() {

    init {
        mDatabase = db
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    fun onUpdate(baseObj: LitePalSupport, id: Long): Int {
        val supportedFields = getSupportedFields(baseObj.getClassName())
        val supportedGenericFields = getSupportedGenericFields(baseObj.getClassName())
        updateGenericTables(baseObj, supportedGenericFields, id)
        val values = ContentValues()
        putFieldsValue(baseObj, supportedFields, values)
        putFieldsToDefaultValue(baseObj, values, id)
        return if (values.size() > 0) {
            mDatabase.update(baseObj.getTableName(), values, "id = ?", arrayOf(id.toString()))
        } else {
            0
        }
    }

    fun onUpdate(modelClass: Class<*>, id: Long, values: ContentValues): Int {
        if (values.size() > 0) {
            convertContentValues(values)
            return mDatabase.update(getTableName(modelClass), values, "id = ?", arrayOf(id.toString()))
        }
        return 0
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    fun onUpdateAll(baseObj: LitePalSupport, vararg conditions: String): Int {
        val conditionArray = arrayOf(*conditions)
        BaseUtility.checkConditionsCorrect(*conditionArray)
        if (conditionArray.isNotEmpty()) {
            conditionArray[0] = DBUtility.convertWhereClauseToColumnName(conditionArray[0]).orEmpty()
        }
        val supportedFields = getSupportedFields(baseObj.getClassName())
        val supportedGenericFields = getSupportedGenericFields(baseObj.getClassName())
        var ids: LongArray? = null
        if (supportedGenericFields.isNotEmpty()) {
            val matchedIds = queryIds(
                baseObj.javaClass,
                getWhereClause(*conditionArray),
                getWhereArgs(*conditionArray),
                "id"
            )
            if (matchedIds.isNotEmpty()) {
                ids = matchedIds
                updateGenericTables(baseObj, supportedGenericFields, *ids)
            }
        }
        val values = ContentValues()
        putFieldsValue(baseObj, supportedFields, values)
        putFieldsToDefaultValue(baseObj, values, *(ids ?: longArrayOf()))
        return doUpdateAllAction(baseObj.getTableName(), values, *conditionArray)
    }

    fun onUpdateAll(tableName: String?, values: ContentValues, vararg conditions: String): Int {
        val conditionArray = arrayOf(*conditions)
        BaseUtility.checkConditionsCorrect(*conditionArray)
        if (conditionArray.isNotEmpty()) {
            conditionArray[0] = DBUtility.convertWhereClauseToColumnName(conditionArray[0]).orEmpty()
        }
        convertContentValues(values)
        return doUpdateAllAction(tableName, values, *conditionArray)
    }

    private fun doUpdateAllAction(tableName: String?, values: ContentValues, vararg conditions: String): Int {
        BaseUtility.checkConditionsCorrect(*conditions)
        return if (values.size() > 0) {
            mDatabase.update(tableName.orEmpty(), values, getWhereClause(*conditions), getWhereArgs(*conditions))
        } else {
            0
        }
    }

    private fun putFieldsToDefaultValue(baseObj: LitePalSupport, values: ContentValues, vararg ids: Long) {
        var fieldName: String? = null
        try {
            val emptyModel = getEmptyModel(baseObj)
            val emptyModelClass = emptyModel.javaClass
            for (name in baseObj.getFieldsToSetToDefault()) {
                if (!isIdColumn(name)) {
                    fieldName = name
                    val field = emptyModelClass.getDeclaredField(fieldName)
                    if (isCollection(field.type)) {
                        if (ids.isNotEmpty()) {
                            val genericTypeName = getGenericTypeName(field)
                            if (BaseUtility.isGenericTypeSupported(genericTypeName)) {
                                val tableName = DBUtility.getGenericTableName(baseObj.getClassName(), field.name)
                                val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(baseObj.getClassName())
                                deleteByIds(
                                    tableName.orEmpty(),
                                    genericValueIdColumnName.orEmpty(),
                                    ids.toList()
                                )
                            }
                        }
                    } else {
                        putContentValuesForUpdate(emptyModel, field, values)
                    }
                }
            }
        } catch (e: NoSuchFieldException) {
            throw LitePalSupportException(
                LitePalSupportException.noSuchFieldExceptioin(baseObj.getClassName(), fieldName.orEmpty()),
                e
            )
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    @Suppress("unused")
    private fun doUpdateAssociations(baseObj: LitePalSupport, id: Long, values: ContentValues): Int {
        var rowsAffected = 0
        analyzeAssociations(baseObj)
        updateSelfTableForeignKey(baseObj, values)
        rowsAffected += updateAssociatedTableForeignKey(baseObj, id)
        return rowsAffected
    }

    private fun analyzeAssociations(baseObj: LitePalSupport) {
        try {
            val associationInfos = getAssociationInfo(baseObj.getClassName())
            analyzeAssociatedModels(baseObj, associationInfos)
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    private fun updateSelfTableForeignKey(baseObj: LitePalSupport, values: ContentValues) {
        val associatedModelMap = baseObj.getAssociatedModelsMapWithoutFK()
        for (associatedTable in associatedModelMap.keys) {
            val fkName = getForeignKeyColumnName(associatedTable)
            values.put(fkName, associatedModelMap[associatedTable])
        }
    }

    private fun updateAssociatedTableForeignKey(baseObj: LitePalSupport, id: Long): Int {
        val associatedModelMap = baseObj.getAssociatedModelsMapWithFK()
        val values = ContentValues()
        var rowsAffected = 0
        for (associatedTable in associatedModelMap.keys) {
            values.clear()
            val fkName = getForeignKeyColumnName(baseObj.getTableName())
            values.put(fkName, id)
            val ids = associatedModelMap[associatedTable]
            if (!ids.isNullOrEmpty()) {
                rowsAffected += updateByIds(associatedTable, values, ids)
            }
        }
        return rowsAffected
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class)
    private fun updateGenericTables(
        baseObj: LitePalSupport,
        supportedGenericFields: List<Field>,
        vararg ids: Long
    ) {
        if (ids.isNotEmpty()) {
            for (field in supportedGenericFields) {
                val annotation = field.getAnnotation(Encrypt::class.java)
                var algorithm: String? = null
                val genericTypeName = getGenericTypeName(field)
                if (annotation != null && "java.lang.String" == genericTypeName) {
                    algorithm = annotation.algorithm
                }
                field.isAccessible = true
                val collection = field[baseObj] as? Collection<*>
                if (!collection.isNullOrEmpty()) {
                    val tableName = DBUtility.getGenericTableName(baseObj.getClassName(), field.name)
                    val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(baseObj.getClassName())
                    deleteByIds(
                        tableName.orEmpty(),
                        genericValueIdColumnName.orEmpty(),
                        ids.toList()
                    )
                    for (id in ids) {
                        for (item in collection) {
                            val values = ContentValues()
                            values.put(genericValueIdColumnName, id)
                            var objectValue: Any? = encryptValue(algorithm, item)
                            if (baseObj.getClassName() == genericTypeName) {
                                val dataSupport = objectValue as? LitePalSupport ?: continue
                                val baseObjId = dataSupport.getBaseObjId()
                                if (baseObjId <= 0) continue
                                values.put(DBUtility.getM2MSelfRefColumnName(field), baseObjId)
                            } else {
                                val parameters = arrayOf(
                                    DBUtility.convertToValidColumnName(org.litepal.util.BaseUtility.changeCase(field.name)),
                                    objectValue
                                )
                                val parameterTypes = arrayOf<Class<*>>(String::class.java, getGenericTypeClass(field)!!)
                                DynamicExecutor.send(values, "put", parameters, values.javaClass, parameterTypes)
                            }
                            mDatabase.insert(tableName.orEmpty(), null, values)
                        }
                    }
                }
            }
        }
    }

    private fun convertContentValues(values: ContentValues) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            val valuesToConvert = HashMap<String, Any?>()
            for (key in values.keySet()) {
                if (DBUtility.isFieldNameConflictWithSQLiteKeywords(key)) {
                    valuesToConvert[key] = values.get(key)
                }
            }
            for (key in valuesToConvert.keys) {
                val convertedKey = DBUtility.convertToValidColumnName(key)
                val obj = values.get(key)
                values.remove(key)
                when (obj) {
                    null -> values.putNull(convertedKey)
                    is Byte -> values.put(convertedKey, obj)
                    is ByteArray -> values.put(convertedKey, obj)
                    is Boolean -> values.put(convertedKey, obj)
                    is String -> values.put(convertedKey, obj)
                    is Float -> values.put(convertedKey, obj)
                    is Long -> values.put(convertedKey, obj)
                    is Int -> values.put(convertedKey, obj)
                    is Short -> values.put(convertedKey, obj)
                    is Double -> values.put(convertedKey, obj)
                }
            }
        }
    }

    private fun deleteByIds(tableName: String, columnName: String, ids: List<Long>) {
        val chunkSize = 500
        if (ids.isEmpty()) {
            return
        }
        val loopCount = (ids.size - 1) / chunkSize
        for (i in 0..loopCount) {
            val begin = chunkSize * i
            val end = minOf(chunkSize * (i + 1), ids.size)
            if (begin >= end) {
                continue
            }
            val placeholders = Array(end - begin) { "?" }.joinToString(",")
            val whereClause = "$columnName IN ($placeholders)"
            val whereArgs = Array(end - begin) { index -> ids[begin + index].toString() }
            mDatabase.delete(tableName, whereClause, whereArgs)
        }
    }

    private fun updateByIds(tableName: String, values: ContentValues, ids: Collection<Long>): Int {
        val targetIds = ids.toList()
        if (targetIds.isEmpty()) {
            return 0
        }
        val chunkSize = 500
        var rowsAffected = 0
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
            rowsAffected += mDatabase.update(tableName, values, whereClause, whereArgs)
        }
        return rowsAffected
    }
}
