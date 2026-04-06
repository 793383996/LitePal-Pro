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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.litepal.LitePalBase
import org.litepal.LitePalRuntime
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.LitePalSupportException
import org.litepal.generated.GeneratedFieldMeta
import org.litepal.generated.GeneratedGenericFieldMeta
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.generated.PropertyAccessor
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import org.litepal.util.cipher.CipherUtil
import java.util.Date
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

abstract class DataHandler : LitePalBase() {

    protected lateinit var mDatabase: SQLiteDatabase

    private var tempEmptyModel: LitePalSupport? = null
    private var fkInCurrentModel: MutableList<AssociationsInfo>? = null
    private var fkInOtherModel: MutableList<AssociationsInfo>? = null

    @Suppress("UNCHECKED_CAST")
    protected fun <T> query(
        modelClass: Class<T>,
        columns: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        limit: String?,
        foreignKeyAssociations: List<AssociationsInfo>?
    ): List<T> {
        val dataList = ArrayList<T>()
        var cursor: Cursor? = null
        try {
            val supportedGenericFields = getSupportedGenericFields(modelClass.name).toMutableList()
            val customizedColumns = getCustomizedColumns(columns, supportedGenericFields, foreignKeyAssociations)
            val validColumns = DBUtility.convertSelectClauseToValidNames(customizedColumns as Array<String>?)
            cursor = mDatabase.query(
                getTableName(modelClass),
                validColumns,
                selection,
                selectionArgs,
                groupBy,
                having,
                orderBy,
                limit
            )
            if (cursor.moveToFirst()) {
                val hasCurrentModelForeignKeys = !foreignKeyAssociations.isNullOrEmpty()
                @Suppress("UNCHECKED_CAST")
                val generatedCursorMapper = GeneratedRegistryLocator
                    .findEntityMeta(modelClass.name)
                    ?.cursorMapper as? org.litepal.generated.CursorMapper<LitePalSupport>
                val eagerForeignKeyColumnIndexMap = HashMap<AssociationsInfo, Int>()
                val eagerForeignKeyIdMapByBaseObj = HashMap<Long, MutableMap<AssociationsInfo, Long>>()
                val baseObjs = ArrayList<LitePalSupport>()
                do {
                    val modelInstance = createInstanceFromClass(modelClass) as T
                    val baseObj = modelInstance as LitePalSupport
                    val baseObjId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    giveBaseObjIdValue(
                        baseObj,
                        baseObjId
                    )
                    if (generatedCursorMapper != null) {
                        LitePalRuntime.recordGeneratedPathHit("query.cursorMapper")
                        generatedCursorMapper.mapFromCursor(baseObj, cursor)
                    } else {
                        LitePalRuntime.recordReflectionFallback("query.cursorMapper.missing")
                        throw IllegalStateException(
                            "Generated cursor mapper is REQUIRED but missing for ${modelClass.name}."
                        )
                    }
                    if (hasCurrentModelForeignKeys) {
                        collectEagerForeignKeyIds(
                            baseObjId,
                            cursor,
                            foreignKeyAssociations,
                            eagerForeignKeyColumnIndexMap,
                            eagerForeignKeyIdMapByBaseObj
                        )
                    }
                    baseObjs.add(baseObj)
                    dataList.add(modelInstance)
                } while (cursor.moveToNext())
                if (baseObjs.isNotEmpty()) {
                    if (hasCurrentModelForeignKeys) {
                        attachCurrentModelForeignKeyAssociations(
                            baseObjs,
                            foreignKeyAssociations.orEmpty(),
                            eagerForeignKeyIdMapByBaseObj
                        )
                    }
                    if (!fkInOtherModel.isNullOrEmpty()) {
                        setAssociatedModelsBatch(baseObjs)
                    }
                }
                setGenericValuesToModelsBatch(baseObjs, supportedGenericFields)
            }
            return dataList
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        } finally {
            cursor?.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> mathQuery(
        tableName: String?,
        columns: Array<String>,
        conditions: Array<String?>?,
        type: Class<T>
    ): T {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        var cursor: Cursor? = null
        var result: T? = null
        try {
            cursor = mDatabase.query(
                tableName.orEmpty(),
                columns,
                getWhereClause(*conditions.orEmpty()),
                getWhereArgs(*conditions.orEmpty()),
                null,
                null,
                null
            )
            if (cursor.moveToFirst()) {
                result = readMathQueryValue(cursor, type)
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        } finally {
            cursor?.close()
        }
        return result as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readMathQueryValue(cursor: Cursor, type: Class<T>): T {
        return when {
            type == Int::class.java ||
                type == Integer.TYPE ||
                type == java.lang.Integer::class.java -> cursor.getInt(0) as T
            type == Long::class.java ||
                type == java.lang.Long.TYPE ||
                type == java.lang.Long::class.java -> cursor.getLong(0) as T
            type == Short::class.java ||
                type == java.lang.Short.TYPE ||
                type == java.lang.Short::class.java -> cursor.getShort(0) as T
            type == Float::class.java ||
                type == java.lang.Float.TYPE ||
                type == java.lang.Float::class.java -> cursor.getFloat(0) as T
            type == Double::class.java ||
                type == java.lang.Double.TYPE ||
                type == java.lang.Double::class.java -> cursor.getDouble(0) as T
            type == Boolean::class.java ||
                type == java.lang.Boolean.TYPE ||
                type == java.lang.Boolean::class.java -> (cursor.getInt(0) == 1) as T
            type == Char::class.java ||
                type == java.lang.Character.TYPE ||
                type == java.lang.Character::class.java -> {
                val value = cursor.getString(0)
                (value?.firstOrNull() ?: '\u0000') as T
            }
            type == Date::class.java -> {
                val dateValue = cursor.getLong(0)
                Date(dateValue) as T
            }
            else -> cursor.getString(0) as T
        }
    }

    protected fun giveBaseObjIdValue(baseObj: LitePalSupport, id: Long) {
        if (id > 0) {
            baseObj.assignBaseObjId(id)
        }
    }

    protected fun putFieldsValue(
        baseObj: LitePalSupport,
        supportedFields: List<GeneratedFieldMeta>,
        values: ContentValues
    ) {
        @Suppress("UNCHECKED_CAST")
        val generatedBinder = GeneratedRegistryLocator
            .findEntityMeta(baseObj.getClassName())
            ?.fieldBinder as? org.litepal.generated.FieldBinder<LitePalSupport>
        if (generatedBinder != null) {
            if (isSaving()) {
                LitePalRuntime.recordGeneratedPathHit("write.fieldBinder.save")
                generatedBinder.bindForSave(baseObj) { column, value ->
                    putGeneratedContentValue(values, column, value)
                }
                return
            }
            if (isUpdating()) {
                LitePalRuntime.recordGeneratedPathHit("write.fieldBinder.update")
                generatedBinder.bindForUpdate(baseObj) { column, value ->
                    putGeneratedContentValue(values, column, value)
                }
                return
            }
        }
        LitePalRuntime.recordReflectionFallback("write.fieldBinder.missing")
        throw IllegalStateException(
            "Generated field binder is REQUIRED but missing for ${baseObj.getClassName()}."
        )
    }

    protected fun encryptValue(algorithm: String?, fieldValue: Any?): Any? {
        var encryptedValue = fieldValue
        if (algorithm != null && encryptedValue != null) {
            if (LitePalSupport.AES.equals(algorithm, ignoreCase = true)) {
                encryptedValue = CipherUtil.aesEncrypt(encryptedValue as String)
            } else if (LitePalSupport.MD5.equals(algorithm, ignoreCase = true)) {
                encryptedValue = CipherUtil.md5Encrypt(encryptedValue as String)
            }
        }
        return encryptedValue
    }

    protected fun getFieldValue(dataSupport: LitePalSupport, propertyName: String?): Any? {
        if (shouldGetOrSet(dataSupport, propertyName)) {
            val accessor = getRequiredPropertyAccessor(dataSupport.getClassName())
            LitePalRuntime.recordGeneratedPathHit("propertyAccessor.get")
            return accessor.get(dataSupport, propertyName.orEmpty())
        }
        return null
    }

    protected fun setFieldValue(dataSupport: LitePalSupport, propertyName: String?, parameter: Any?) {
        if (shouldGetOrSet(dataSupport, propertyName)) {
            val accessor = getRequiredPropertyAccessor(dataSupport.getClassName())
            LitePalRuntime.recordGeneratedPathHit("propertyAccessor.set")
            accessor.set(dataSupport, propertyName.orEmpty(), parameter)
        }
    }

    protected fun analyzeAssociatedModels(
        baseObj: LitePalSupport,
        associationInfos: Collection<AssociationsInfo>
    ) {
        try {
            for (associationInfo in associationInfos) {
                when (associationInfo.getAssociationType()) {
                    Const.Model.MANY_TO_ONE -> Many2OneAnalyzer().analyze(baseObj, associationInfo)
                    Const.Model.ONE_TO_ONE -> One2OneAnalyzer().analyze(baseObj, associationInfo)
                    Const.Model.MANY_TO_MANY -> Many2ManyAnalyzer().analyze(baseObj, associationInfo)
                }
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    protected fun getAssociatedModel(
        baseObj: LitePalSupport,
        associationInfo: AssociationsInfo
    ): LitePalSupport? {
        return getFieldValue(baseObj, associationInfo.getAssociateOtherModelFromSelf()) as LitePalSupport?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun getAssociatedModels(
        baseObj: LitePalSupport,
        associationInfo: AssociationsInfo
    ): Collection<LitePalSupport>? {
        return getFieldValue(
            baseObj,
            associationInfo.getAssociateOtherModelFromSelf()
        ) as Collection<LitePalSupport>?
    }

    protected fun getEmptyModel(baseObj: LitePalSupport): LitePalSupport {
        if (tempEmptyModel != null) {
            return tempEmptyModel!!
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val modelClass = baseObj.javaClass as Class<out LitePalSupport>
            tempEmptyModel = createInstanceFromClass(modelClass) as LitePalSupport
            return tempEmptyModel!!
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    protected fun getWhereClause(vararg conditions: String?): String? {
        if (conditions.isEmpty()) {
            return null
        }
        return conditions[0]
    }

    protected fun getWhereArgs(vararg conditions: String?): Array<String>? {
        if (conditions.size > 1) {
            val whereArgs = ArrayList<String>(conditions.size - 1)
            for (i in 1 until conditions.size) {
                whereArgs.add(conditions[i].orEmpty())
            }
            return whereArgs.toTypedArray()
        }
        return null
    }

    protected fun isAffectAllLines(vararg conditions: Any?): Boolean {
        if (conditions.isEmpty()) {
            return true
        }
        if (conditions.size == 1) {
            return when (val first = conditions[0]) {
                null -> true
                is Array<*> -> first.isEmpty()
                is LongArray -> first.isEmpty()
                is IntArray -> first.isEmpty()
                is ShortArray -> first.isEmpty()
                is ByteArray -> first.isEmpty()
                is BooleanArray -> first.isEmpty()
                is FloatArray -> first.isEmpty()
                is DoubleArray -> first.isEmpty()
                is CharArray -> first.isEmpty()
                else -> false
            }
        }
        return false
    }

    protected fun queryIds(
        modelClass: Class<*>,
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String? = "id"
    ): LongArray {
        var cursor: Cursor? = null
        val ids = ArrayList<Long>()
        try {
            cursor = mDatabase.query(
                getTableName(modelClass),
                arrayOf(BaseUtility.changeCase("id")),
                selection,
                selectionArgs,
                null,
                null,
                orderBy,
                null
            )
            if (cursor.moveToFirst()) {
                val idColumnIndex = cursor.getColumnIndexOrThrow("id")
                do {
                    ids.add(cursor.getLong(idColumnIndex))
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        } finally {
            cursor?.close()
        }
        return LongArray(ids.size) { index -> ids[index] }
    }

    protected fun getWhereOfIdsWithOr(ids: Collection<Long>): String {
        if (ids.isEmpty()) {
            return BaseUtility.changeCase("1 = 0").orEmpty()
        }
        return BaseUtility.changeCase("id in (${ids.joinToString(",")})").orEmpty()
    }

    protected fun getWhereOfIdsWithOr(vararg ids: Long): String {
        if (ids.isEmpty()) {
            return BaseUtility.changeCase("1 = 0").orEmpty()
        }
        return BaseUtility.changeCase("id in (${ids.joinToString(",")})").orEmpty()
    }

    protected fun shouldGetOrSet(dataSupport: LitePalSupport?, propertyName: String?): Boolean {
        return dataSupport != null && !propertyName.isNullOrBlank()
    }

    protected fun getIntermediateTableName(
        baseObj: LitePalSupport,
        associatedTableName: String
    ): String {
        return BaseUtility.changeCase(
            DBUtility.getIntermediateTableName(baseObj.getTableName(), associatedTableName)
        ).orEmpty()
    }

    protected fun getTableName(modelClass: Class<*>): String {
        return BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)).orEmpty()
    }

    protected fun createInstanceFromClass(modelClass: Class<*>): Any {
        @Suppress("UNCHECKED_CAST")
        val generatedFactory = GeneratedRegistryLocator
            .findEntityMeta(modelClass.name)
            ?.entityFactory as? org.litepal.generated.EntityFactory<LitePalSupport>
        if (generatedFactory != null) {
            LitePalRuntime.recordGeneratedPathHit("entityFactory.newInstance")
            return generatedFactory.newInstance()
        }
        LitePalRuntime.recordReflectionFallback("entityFactory.missing")
        throw IllegalStateException(
            "Generated entity factory is REQUIRED but missing for ${modelClass.name}."
        )
    }

    protected fun getForeignKeyAssociations(className: String, isEager: Boolean): List<AssociationsInfo>? {
        if (isEager) {
            analyzeAssociations(className)
            return fkInCurrentModel
        }
        return null
    }

    protected fun putGeneratedContentValue(values: ContentValues, column: String, value: Any?) {
        val validColumn = BaseUtility.changeCase(DBUtility.convertToValidColumnName(column)).orEmpty()
        when (value) {
            null -> values.putNull(validColumn)
            is Byte -> values.put(validColumn, value)
            is ByteArray -> values.put(validColumn, value)
            is Boolean -> values.put(validColumn, value)
            is String -> values.put(validColumn, value)
            is Float -> values.put(validColumn, value)
            is Long -> values.put(validColumn, value)
            is Int -> values.put(validColumn, value)
            is Short -> values.put(validColumn, value)
            is Double -> values.put(validColumn, value)
            else -> values.put(validColumn, value.toString())
        }
    }

    private fun isUpdating(): Boolean {
        return UpdateHandler::class.java.name == javaClass.name
    }

    private fun isSaving(): Boolean {
        return SaveHandler::class.java.name == javaClass.name
    }

    private fun getCustomizedColumns(
        columns: Array<String?>?,
        supportedGenericFields: MutableList<GeneratedGenericFieldMeta>,
        foreignKeyAssociations: List<AssociationsInfo>?
    ): Array<String?>? {
        if (columns != null && columns.isNotEmpty()) {
            var columnsContainsId = false
            val columnList = columns.toMutableList()
            val supportedGenericFieldNames = ArrayList<String>()
            val columnToRemove = ArrayList<Int>()
            val genericColumnsForQuery = ArrayList<String>()
            val tempSupportedGenericFields = ArrayList<GeneratedGenericFieldMeta>()

            for (supportedGenericField in supportedGenericFields) {
                supportedGenericFieldNames.add(supportedGenericField.propertyName)
            }

            for (i in columnList.indices) {
                val columnName = columnList[i] ?: continue
                if (BaseUtility.containsIgnoreCases(supportedGenericFieldNames, columnName)) {
                    columnToRemove.add(i)
                } else if (isIdColumn(columnName)) {
                    columnsContainsId = true
                    if ("_id".equals(columnName, ignoreCase = true)) {
                        columnList[i] = BaseUtility.changeCase("id")
                    }
                }
            }

            for (i in columnToRemove.size - 1 downTo 0) {
                val index = columnToRemove[i]
                val genericColumn = columnList.removeAt(index)
                if (genericColumn != null) {
                    genericColumnsForQuery.add(genericColumn)
                }
            }

            for (supportedGenericField in supportedGenericFields) {
                val fieldName = supportedGenericField.propertyName
                if (BaseUtility.containsIgnoreCases(genericColumnsForQuery, fieldName)) {
                    tempSupportedGenericFields.add(supportedGenericField)
                }
            }

            supportedGenericFields.clear()
            supportedGenericFields.addAll(tempSupportedGenericFields)

            if (!foreignKeyAssociations.isNullOrEmpty()) {
                for (associationInfo in foreignKeyAssociations) {
                    val associatedTable = DBUtility.getTableNameByClassName(
                        associationInfo.getAssociatedClassName()
                    )
                    columnList.add(getForeignKeyColumnName(associatedTable))
                }
            }
            if (!columnsContainsId) {
                columnList.add(BaseUtility.changeCase("id"))
            }
            return columnList.toTypedArray()
        }
        return null
    }

    private fun analyzeAssociations(className: String) {
        val associationInfos = getAssociationInfo(className)
        if (fkInCurrentModel == null) {
            fkInCurrentModel = ArrayList()
        } else {
            fkInCurrentModel?.clear()
        }
        if (fkInOtherModel == null) {
            fkInOtherModel = ArrayList()
        } else {
            fkInOtherModel?.clear()
        }

        for (associationInfo in associationInfos) {
            if (associationInfo.getAssociationType() == Const.Model.MANY_TO_ONE ||
                associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE
            ) {
                if (associationInfo.getClassHoldsForeignKey() == className) {
                    fkInCurrentModel?.add(associationInfo)
                } else {
                    fkInOtherModel?.add(associationInfo)
                }
            } else if (associationInfo.getAssociationType() == Const.Model.MANY_TO_MANY) {
                fkInOtherModel?.add(associationInfo)
            }
        }
    }

    private fun collectEagerForeignKeyIds(
        baseObjId: Long,
        cursor: Cursor,
        foreignKeyAssociations: List<AssociationsInfo>?,
        foreignKeyColumnIndexMap: MutableMap<AssociationsInfo, Int>,
        foreignKeyIdMapByBaseObj: MutableMap<Long, MutableMap<AssociationsInfo, Long>>
    ) {
        if (foreignKeyAssociations.isNullOrEmpty()) {
            return
        }
        val associationToIdMap = foreignKeyIdMapByBaseObj.getOrPut(baseObjId) { HashMap() }
        for (associationInfo in foreignKeyAssociations) {
            val foreignKeyColumn = getForeignKeyColumnName(
                DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName())
            )
            val columnIndex = foreignKeyColumnIndexMap.getOrPut(associationInfo) {
                cursor.getColumnIndex(foreignKeyColumn)
            }
            if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
                val associatedId = cursor.getLong(columnIndex)
                if (associatedId > 0) {
                    associationToIdMap[associationInfo] = associatedId
                }
            }
        }
    }

    private fun attachCurrentModelForeignKeyAssociations(
        baseObjs: List<LitePalSupport>,
        foreignKeyAssociations: List<AssociationsInfo>,
        foreignKeyIdMapByBaseObj: Map<Long, Map<AssociationsInfo, Long>>
    ) {
        if (baseObjs.isEmpty() || foreignKeyAssociations.isEmpty() || foreignKeyIdMapByBaseObj.isEmpty()) {
            return
        }
        val baseObjById = HashMap<Long, LitePalSupport>(baseObjs.size)
        for (baseObj in baseObjs) {
            baseObjById[baseObj.getBaseObjId()] = baseObj
        }
        for (associationInfo in foreignKeyAssociations) {
            val associatedClassName = associationInfo.getAssociatedClassName().orEmpty()
            if (associatedClassName.isEmpty()) {
                continue
            }
            val associatedIds = ArrayList<Long>()
            val associatedIdSeen = HashSet<Long>()
            for (idMapping in foreignKeyIdMapByBaseObj.values) {
                idMapping[associationInfo]?.let { associatedId ->
                    if (associatedIdSeen.add(associatedId)) {
                        associatedIds.add(associatedId)
                    }
                }
            }
            if (associatedIds.isEmpty()) {
                continue
            }
            val associatedClass = resolveAssociationModelClass(associatedClassName) ?: continue
            val associatedObjById = HashMap<Long, LitePalSupport>(associatedIds.size)
            for (idChunk in associatedIds.chunked(QUERY_CHUNK_SIZE)) {
                val associatedModels = query(
                    associatedClass,
                    null,
                    getWhereOfIdsWithOr(idChunk),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                for (associatedModel in associatedModels) {
                    associatedObjById[associatedModel.getBaseObjId()] = associatedModel
                }
            }
            for ((baseObjId, idMapping) in foreignKeyIdMapByBaseObj) {
                val associatedId = idMapping[associationInfo] ?: continue
                val baseObj = baseObjById[baseObjId] ?: continue
                val associatedObj = associatedObjById[associatedId] ?: continue
                setFieldValue(baseObj, associationInfo.getAssociateOtherModelFromSelf(), associatedObj)
            }
        }
    }

    private fun setAssociatedModelsBatch(baseObjs: List<LitePalSupport>) {
        val associations = fkInOtherModel ?: return
        if (baseObjs.isEmpty()) {
            return
        }
        val baseObjById = HashMap<Long, LitePalSupport>(baseObjs.size)
        val orderedBaseIds = ArrayList<Long>(baseObjs.size)
        val baseIdSeen = HashSet<Long>(baseObjs.size)
        for (baseObj in baseObjs) {
            val id = baseObj.getBaseObjId()
            if (id <= 0) {
                continue
            }
            if (baseIdSeen.add(id)) {
                baseObjById[id] = baseObj
                orderedBaseIds.add(id)
            }
        }
        if (orderedBaseIds.isEmpty()) {
            return
        }
        val baseTableName = baseObjs[0].getTableName()
        for (associationInfo in associations) {
            val associatedClassName = associationInfo.getAssociatedClassName().orEmpty()
            if (associatedClassName.isEmpty()) {
                continue
            }
            val supportedGenericFields = getSupportedGenericFields(associatedClassName)
            if (associationInfo.getAssociationType() == Const.Model.MANY_TO_MANY) {
                loadManyToManyAssociationsBatch(
                    baseTableName,
                    orderedBaseIds,
                    baseObjById,
                    associationInfo,
                    associatedClassName,
                    supportedGenericFields
                )
            } else {
                loadManyToOneOrOneToOneAssociationsBatch(
                    orderedBaseIds,
                    baseObjById,
                    associationInfo,
                    associatedClassName,
                    supportedGenericFields
                )
            }
        }
    }

    private fun loadManyToOneOrOneToOneAssociationsBatch(
        baseIds: List<Long>,
        baseObjById: Map<Long, LitePalSupport>,
        associationInfo: AssociationsInfo,
        associatedClassName: String,
        supportedGenericFields: List<GeneratedGenericFieldMeta>
    ) {
        val foreignKeyColumn = getForeignKeyColumnName(
            DBUtility.getTableNameByClassName(associationInfo.getSelfClassName())
        ) ?: return
        val associatedClass = resolveAssociationModelClass(associatedClassName) ?: return
        val associatedTableName = BaseUtility.changeCase(
            DBUtility.getTableNameByClassName(associatedClassName)
        ).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val generatedCursorMapper = GeneratedRegistryLocator
            .findEntityMeta(associatedClassName)
            ?.cursorMapper as? org.litepal.generated.CursorMapper<LitePalSupport>
        val oneToOneAssignedOwnerIds = if (associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE) {
            HashSet<Long>()
        } else {
            null
        }
        for (idChunk in baseIds.chunked(QUERY_CHUNK_SIZE)) {
            if (idChunk.isEmpty()) {
                continue
            }
            val placeholders = Array(idChunk.size) { "?" }.joinToString(",")
            val whereClause = "$foreignKeyColumn IN ($placeholders)"
            val whereArgs = Array(idChunk.size) { index -> idChunk[index].toString() }
            val pendingAssignments = ArrayList<Pair<Long, LitePalSupport>>()
            val loadedAssociatedModels = ArrayList<LitePalSupport>()
            var cursor: Cursor? = null
            try {
                cursor = mDatabase.query(
                    associatedTableName,
                    null,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "$foreignKeyColumn asc, id asc",
                    null
                )
                if (cursor.moveToFirst()) {
                    val ownerIdColumnIndex = cursor.getColumnIndex(foreignKeyColumn)
                    if (ownerIdColumnIndex == -1) {
                        continue
                    }
                    do {
                        val ownerId = cursor.getLong(ownerIdColumnIndex)
                        if (!baseObjById.containsKey(ownerId)) {
                            continue
                        }
                        if (oneToOneAssignedOwnerIds != null && !oneToOneAssignedOwnerIds.add(ownerId)) {
                            if (shouldSkipOneToOneDuplicateOwner(ownerId, associationInfo)) {
                                continue
                            }
                        }
                        val modelInstance = createInstanceFromClass(associatedClass) as LitePalSupport
                        giveBaseObjIdValue(
                            modelInstance,
                            cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                        )
                        if (generatedCursorMapper != null) {
                            LitePalRuntime.recordGeneratedPathHit("assoc.cursorMapper")
                            generatedCursorMapper.mapFromCursor(modelInstance, cursor)
                        } else {
                            throw IllegalStateException(
                                "Generated cursor mapper is REQUIRED for association entity $associatedClassName."
                            )
                        }
                        loadedAssociatedModels.add(modelInstance)
                        pendingAssignments.add(ownerId to modelInstance)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
            setGenericValuesToModelsBatch(loadedAssociatedModels, supportedGenericFields)
            for ((ownerId, associatedModel) in pendingAssignments) {
                val baseObj = baseObjById[ownerId] ?: continue
                if (associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE) {
                    setFieldValue(baseObj, associationInfo.getAssociateOtherModelFromSelf(), associatedModel)
                } else {
                    addAssociatedModelIntoCollection(
                        baseObj,
                        associationInfo.getAssociateOtherModelFromSelf(),
                        associationInfo.getAssociateOtherModelCollectionType(),
                        associatedModel
                    )
                }
            }
        }
    }

    private fun loadManyToManyAssociationsBatch(
        baseTableName: String,
        baseIds: List<Long>,
        baseObjById: Map<Long, LitePalSupport>,
        associationInfo: AssociationsInfo,
        associatedClassName: String,
        supportedGenericFields: List<GeneratedGenericFieldMeta>
    ) {
        val associatedClass = resolveAssociationModelClass(associatedClassName) ?: return
        val associatedTableName = DBUtility.getTableNameByClassName(associatedClassName)
        val intermediateTableName = DBUtility.getIntermediateTableName(baseTableName, associatedTableName)
        val baseIdAlias = "_lp_base_id"
        @Suppress("UNCHECKED_CAST")
        val generatedCursorMapper = GeneratedRegistryLocator
            .findEntityMeta(associatedClassName)
            ?.cursorMapper as? org.litepal.generated.CursorMapper<LitePalSupport>
        for (idChunk in baseIds.chunked(QUERY_CHUNK_SIZE)) {
            if (idChunk.isEmpty()) {
                continue
            }
            val placeholders = Array(idChunk.size) { "?" }.joinToString(",")
            val sql = StringBuilder()
                .append("select a.*, b.")
                .append(baseTableName).append("_id as ").append(baseIdAlias)
                .append(" from ").append(associatedTableName).append(" a")
                .append(" inner join ").append(intermediateTableName).append(" b")
                .append(" on a.id = b.").append(associatedTableName).append("_id")
                .append(" where b.").append(baseTableName).append("_id in (")
                .append(placeholders).append(")")
                .append(" order by b.").append(baseTableName).append("_id asc, a.id asc")
                .toString()
            val args = Array(idChunk.size) { index -> idChunk[index].toString() }
            val pendingAssignments = ArrayList<Pair<Long, LitePalSupport>>()
            val loadedAssociatedModels = ArrayList<LitePalSupport>()
            var cursor: Cursor? = null
            try {
                cursor = mDatabase.rawQuery(BaseUtility.changeCase(sql).orEmpty(), args)
                if (cursor.moveToFirst()) {
                    val ownerIdColumnIndex = cursor.getColumnIndex(baseIdAlias)
                    if (ownerIdColumnIndex == -1) {
                        continue
                    }
                    do {
                        val ownerId = cursor.getLong(ownerIdColumnIndex)
                        if (!baseObjById.containsKey(ownerId)) {
                            continue
                        }
                        val modelInstance = createInstanceFromClass(associatedClass) as LitePalSupport
                        giveBaseObjIdValue(
                            modelInstance,
                            cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                        )
                        if (generatedCursorMapper != null) {
                            LitePalRuntime.recordGeneratedPathHit("m2m.cursorMapper")
                            generatedCursorMapper.mapFromCursor(modelInstance, cursor)
                        } else {
                            throw IllegalStateException(
                                "Generated cursor mapper is REQUIRED for many-to-many entity $associatedClassName."
                            )
                        }
                        loadedAssociatedModels.add(modelInstance)
                        pendingAssignments.add(ownerId to modelInstance)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
            setGenericValuesToModelsBatch(loadedAssociatedModels, supportedGenericFields)
            for ((ownerId, associatedModel) in pendingAssignments) {
                val baseObj = baseObjById[ownerId] ?: continue
                addAssociatedModelIntoCollection(
                    baseObj,
                    associationInfo.getAssociateOtherModelFromSelf(),
                    associationInfo.getAssociateOtherModelCollectionType(),
                    associatedModel
                )
            }
        }
    }

    private fun setGenericValuesToModelsBatch(
        baseObjs: List<LitePalSupport>,
        supportedGenericFields: List<GeneratedGenericFieldMeta>
    ) {
        if (baseObjs.isEmpty() || supportedGenericFields.isEmpty()) {
            return
        }
        val baseClassName = baseObjs[0].getClassName()
        @Suppress("UNCHECKED_CAST")
        val baseModelClass = baseObjs[0].javaClass as Class<out LitePalSupport>
        val baseObjById = HashMap<Long, LitePalSupport>(baseObjs.size)
        val stableBaseIds = ArrayList<Long>(baseObjs.size)
        val baseIdSeen = HashSet<Long>(baseObjs.size)
        for (baseObj in baseObjs) {
            val id = baseObj.getBaseObjId()
            if (id <= 0) {
                continue
            }
            baseObjById[id] = baseObj
            if (baseIdSeen.add(id)) {
                stableBaseIds.add(id)
            }
        }
        if (stableBaseIds.isEmpty()) {
            return
        }
        for (field in supportedGenericFields) {
            val genericTypeName = field.elementTypeName
            val tableName = DBUtility.getGenericTableName(baseClassName, field.propertyName)
            val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(baseClassName)
            if (baseClassName == genericTypeName) {
                val genericValueColumnName = DBUtility.getM2MSelfRefColumnName(field.propertyName)
                setSelfReferenceGenericValuesBatch(
                    baseModelClass,
                    field,
                    tableName,
                    genericValueColumnName,
                    genericValueIdColumnName,
                    baseObjById,
                    stableBaseIds
                )
            } else {
                val genericValueColumnName = DBUtility.convertToValidColumnName(field.propertyName)
                setCollectionGenericValuesBatch(
                    field,
                    tableName,
                    genericValueColumnName,
                    genericValueIdColumnName,
                    baseObjById,
                    stableBaseIds
                )
            }
        }
    }

    private fun setCollectionGenericValuesBatch(
        field: GeneratedGenericFieldMeta,
        tableName: String,
        genericValueColumnName: String?,
        genericValueIdColumnName: String,
        baseObjById: Map<Long, LitePalSupport>,
        baseIds: List<Long>
    ) {
        val normalizedValueColumnName = BaseUtility.changeCase(genericValueColumnName).orEmpty()
        if (normalizedValueColumnName.isEmpty()) {
            return
        }
        val projection = arrayOf(genericValueIdColumnName, normalizedValueColumnName)
        for (idChunk in baseIds.chunked(QUERY_CHUNK_SIZE)) {
            if (idChunk.isEmpty()) {
                continue
            }
            val placeholders = Array(idChunk.size) { "?" }.joinToString(",")
            val whereClause = "$genericValueIdColumnName IN ($placeholders)"
            val whereArgs = Array(idChunk.size) { index -> idChunk[index].toString() }
            var cursor: Cursor? = null
            try {
                cursor = mDatabase.query(
                    tableName,
                    projection,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "$genericValueIdColumnName asc, rowid asc"
                )
                if (cursor.moveToFirst()) {
                    val idColumnIndex = cursor.getColumnIndex(genericValueIdColumnName)
                    val valueColumnIndex = cursor.getColumnIndex(normalizedValueColumnName)
                    if (idColumnIndex == -1 || valueColumnIndex == -1) {
                        continue
                    }
                    do {
                        val ownerId = cursor.getLong(idColumnIndex)
                        val baseObj = baseObjById[ownerId] ?: continue
                        setToModelFromCursor(baseObj, field, valueColumnIndex, cursor)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }
    }

    private fun setSelfReferenceGenericValuesBatch(
        baseModelClass: Class<out LitePalSupport>,
        field: GeneratedGenericFieldMeta,
        tableName: String,
        genericValueColumnName: String,
        genericValueIdColumnName: String,
        baseObjById: Map<Long, LitePalSupport>,
        baseIds: List<Long>
    ) {
        val normalizedValueColumnName = BaseUtility.changeCase(genericValueColumnName).orEmpty()
        if (normalizedValueColumnName.isEmpty()) {
            return
        }
        val projection = arrayOf(genericValueIdColumnName, normalizedValueColumnName)
        val refIdsByOwner = HashMap<Long, MutableList<Long>>()
        val allReferencedIds = LinkedHashSet<Long>()
        for (idChunk in baseIds.chunked(QUERY_CHUNK_SIZE)) {
            if (idChunk.isEmpty()) {
                continue
            }
            val placeholders = Array(idChunk.size) { "?" }.joinToString(",")
            val whereClause = "$genericValueIdColumnName IN ($placeholders)"
            val whereArgs = Array(idChunk.size) { index -> idChunk[index].toString() }
            var cursor: Cursor? = null
            try {
                cursor = mDatabase.query(
                    tableName,
                    projection,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "$genericValueIdColumnName asc, rowid asc"
                )
                if (cursor.moveToFirst()) {
                    val ownerIdColumnIndex = cursor.getColumnIndex(genericValueIdColumnName)
                    val valueIdColumnIndex = cursor.getColumnIndex(normalizedValueColumnName)
                    if (ownerIdColumnIndex == -1 || valueIdColumnIndex == -1) {
                        continue
                    }
                    do {
                        val ownerId = cursor.getLong(ownerIdColumnIndex)
                        val valueId = cursor.getLong(valueIdColumnIndex)
                        if (ownerId <= 0 || valueId <= 0) {
                            continue
                        }
                        refIdsByOwner.getOrPut(ownerId) { ArrayList() }.add(valueId)
                        allReferencedIds.add(valueId)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }
        if (allReferencedIds.isEmpty()) {
            return
        }
        val referencedModelById = queryModelsByIdsWithoutGeneric(baseModelClass, allReferencedIds)
        for ((ownerId, refIds) in refIdsByOwner) {
            val ownerObj = baseObjById[ownerId] ?: continue
            val collection = getOrCreateCollection(ownerObj, field.propertyName, field.collectionType)
            for (refId in refIds) {
                val referencedModel = referencedModelById[refId] ?: continue
                collection.add(referencedModel)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun queryModelsByIdsWithoutGeneric(
        modelClass: Class<out LitePalSupport>,
        ids: Collection<Long>
    ): Map<Long, LitePalSupport> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        val generatedCursorMapper = GeneratedRegistryLocator
            .findEntityMeta(modelClass.name)
            ?.cursorMapper as? org.litepal.generated.CursorMapper<LitePalSupport>
        val modelsById = HashMap<Long, LitePalSupport>(ids.size)
        for (idChunk in ids.chunked(QUERY_CHUNK_SIZE)) {
            if (idChunk.isEmpty()) {
                continue
            }
            var cursor: Cursor? = null
            try {
                cursor = mDatabase.query(
                    getTableName(modelClass),
                    null,
                    getWhereOfIdsWithOr(idChunk),
                    null,
                    null,
                    null,
                    "id asc",
                    null
                )
                if (cursor.moveToFirst()) {
                    do {
                        val modelInstance = createInstanceFromClass(modelClass) as LitePalSupport
                        giveBaseObjIdValue(modelInstance, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                        if (generatedCursorMapper != null) {
                            LitePalRuntime.recordGeneratedPathHit("generic.cursorMapper")
                            generatedCursorMapper.mapFromCursor(modelInstance, cursor)
                        } else {
                            throw IllegalStateException(
                                "Generated cursor mapper is REQUIRED for generic entity ${modelClass.name}."
                            )
                        }
                        modelsById[modelInstance.getBaseObjId()] = modelInstance
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }
        return modelsById
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAssociationModelClass(className: String): Class<out LitePalSupport>? {
        val cached = ASSOCIATION_MODEL_CLASS_CACHE[className]
        if (cached != null) {
            return cached
        }
        val instance = try {
            createInstanceFromClassName(className)
        } catch (e: Exception) {
            LitePalLog.e(TAG, "Failed to resolve association class $className.", e)
            null
        }
        val resolved = instance?.javaClass as? Class<out LitePalSupport> ?: return null
        ASSOCIATION_MODEL_CLASS_CACHE[className] = resolved
        return resolved
    }

    @Suppress("UNCHECKED_CAST")
    private fun createInstanceFromClassName(className: String): LitePalSupport? {
        val generatedFactory = GeneratedRegistryLocator
            .findEntityMeta(className)
            ?.entityFactory as? org.litepal.generated.EntityFactory<LitePalSupport>
            ?: return null
        LitePalRuntime.recordGeneratedPathHit("entityFactory.newInstance")
        return generatedFactory.newInstance()
    }

    private fun shouldSkipOneToOneDuplicateOwner(
        ownerId: Long,
        associationInfo: AssociationsInfo
    ): Boolean {
        val message = "Duplicate one-to-one eager row detected for ownerId=$ownerId, " +
            "self=${associationInfo.getSelfClassName()}, associated=${associationInfo.getAssociatedClassName()}."
        if (LitePalRuntime.shouldThrowOnError()) {
            throw LitePalSupportException(message)
        }
        LitePalLog.w(TAG, "$message Keep the first row in COMPAT mode.")
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateCollection(
        modelInstance: Any,
        propertyName: String,
        collectionType: String?
    ): MutableCollection<Any?> {
        val baseObj = modelInstance as LitePalSupport
        var collection = getFieldValue(baseObj, propertyName) as MutableCollection<Any?>?
        if (collection == null) {
            collection = if (isList(collectionType)) {
                ArrayList()
            } else {
                HashSet()
            }
            setFieldValue(baseObj, propertyName, collection)
        }
        return collection
    }

    @Suppress("UNCHECKED_CAST")
    private fun addAssociatedModelIntoCollection(
        baseObj: LitePalSupport,
        propertyName: String?,
        collectionType: String?,
        associatedModel: LitePalSupport
    ) {
        val targetProperty = propertyName ?: return
        var collection = getFieldValue(baseObj, targetProperty) as MutableCollection<LitePalSupport>?
        if (collection == null) {
            collection = if (isList(collectionType)) {
                ArrayList()
            } else {
                HashSet()
            }
            setFieldValue(baseObj, targetProperty, collection)
        }
        collection.add(associatedModel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setToModelFromCursor(
        modelInstance: Any,
        field: GeneratedGenericFieldMeta,
        columnIndex: Int,
        cursor: Cursor
    ) {
        if (cursor.isNull(columnIndex)) {
            return
        }
        val baseObj = modelInstance as LitePalSupport
        var value: Any? = readCursorValueForField(cursor, columnIndex, field)
        if (field.elementTypeName == "java.lang.Boolean") {
            value = when (value.toString()) {
                "0" -> false
                "1" -> true
                else -> value
            }
        } else if (field.elementTypeName == "java.lang.Character") {
            value = when (value) {
                is Char -> value
                else -> value.toString().firstOrNull() ?: return
            }
        } else if (field.elementTypeName == "java.util.Date") {
            val date = value as Long
            value = if (date == Long.MAX_VALUE) {
                null
            } else {
                Date(date)
            }
        }
        if (field.elementTypeName == "java.lang.String" && !field.encryptAlgorithm.isNullOrBlank()) {
            value = decryptValue(field.encryptAlgorithm, value)
        }
        var collection = getFieldValue(baseObj, field.propertyName) as MutableCollection<Any?>?
        if (collection == null) {
            collection = if (isList(field.collectionType)) {
                ArrayList()
            } else {
                HashSet()
            }
            setFieldValue(baseObj, field.propertyName, collection)
        }
        collection.add(value)
    }

    private fun readCursorValueForField(
        cursor: Cursor,
        columnIndex: Int,
        field: GeneratedGenericFieldMeta
    ): Any? {
        return when (field.elementTypeName) {
            "java.lang.Boolean" -> cursor.getInt(columnIndex)
            "java.lang.Character" -> cursor.getString(columnIndex)
            "java.util.Date" -> cursor.getLong(columnIndex)
            "java.lang.Byte" -> cursor.getShort(columnIndex).toByte()
            "java.lang.Short" -> cursor.getShort(columnIndex)
            "java.lang.Integer" -> cursor.getInt(columnIndex)
            "java.lang.Long" -> cursor.getLong(columnIndex)
            "java.lang.Float" -> cursor.getFloat(columnIndex)
            "java.lang.Double" -> cursor.getDouble(columnIndex)
            else -> cursor.getString(columnIndex)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRequiredPropertyAccessor(className: String): PropertyAccessor<LitePalSupport> {
        val accessor = GeneratedRegistryLocator
            .findEntityMeta(className)
            ?.propertyAccessor as? PropertyAccessor<LitePalSupport>
        if (accessor != null) {
            return accessor
        }
        LitePalRuntime.recordReflectionFallback("propertyAccessor.missing")
        throw IllegalStateException(
            "Generated property accessor is REQUIRED but missing for $className."
        )
    }

    protected fun decryptValue(algorithm: String?, fieldValue: Any?): Any? {
        var decryptedValue = fieldValue
        if (algorithm != null && decryptedValue != null) {
            if (LitePalSupport.AES.equals(algorithm, ignoreCase = true)) {
                decryptedValue = CipherUtil.aesDecrypt(decryptedValue as String)
            }
        }
        return decryptedValue
    }

    companion object {
        const val TAG = "DataHandler"
        private const val QUERY_CHUNK_SIZE = 500
        private val ASSOCIATION_MODEL_CLASS_CACHE = ConcurrentHashMap<String, Class<out LitePalSupport>>()
    }
}
