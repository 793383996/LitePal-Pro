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
import android.util.SparseArray
import org.litepal.LitePalBase
import org.litepal.LitePalRuntime
import org.litepal.annotation.Column
import org.litepal.annotation.Encrypt
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.exceptions.LitePalSupportException
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import org.litepal.util.cipher.CipherUtil
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
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
            val supportedFields = getSupportedFields(modelClass.name)
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
                val isEagerQuery = !foreignKeyAssociations.isNullOrEmpty()
                val queryInfoCacheSparseArray = SparseArray<QueryInfoCache>()
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
                        LitePalRuntime.recordReflectionFallback("query.setValueToModel")
                        setValueToModel(
                            modelInstance,
                            supportedFields,
                            cursor,
                            queryInfoCacheSparseArray
                        )
                    }
                    if (isEagerQuery) {
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
                queryInfoCacheSparseArray.clear()
                if (isEagerQuery && baseObjs.isNotEmpty()) {
                    attachCurrentModelForeignKeyAssociations(
                        baseObjs,
                        foreignKeyAssociations,
                        eagerForeignKeyIdMapByBaseObj
                    )
                    setAssociatedModelsBatch(baseObjs)
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
                val method = getCursorGetter(cursor.javaClass, genGetColumnMethod(type))
                result = method.invoke(cursor, 0) as T
            }
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        } finally {
            cursor?.close()
        }
        return result as T
    }

    protected fun giveBaseObjIdValue(baseObj: LitePalSupport, id: Long) {
        if (id > 0) {
            DynamicExecutor.set(baseObj, "baseObjId", id, LitePalSupport::class.java)
        }
    }

    protected fun putFieldsValue(
        baseObj: LitePalSupport,
        supportedFields: List<Field>,
        values: ContentValues
    ) {
        @Suppress("UNCHECKED_CAST")
        val generatedBinder = GeneratedRegistryLocator
            .findEntityMeta(baseObj.getClassName())
            ?.fieldBinder as? org.litepal.generated.FieldBinder<LitePalSupport>
        if (generatedBinder != null) {
            LitePalRuntime.recordGeneratedPathHit("write.fieldBinder")
            if (isUpdating()) {
                generatedBinder.bindForUpdate(baseObj) { column, value ->
                    putGeneratedContentValue(values, column, value)
                }
            } else if (isSaving()) {
                generatedBinder.bindForSave(baseObj) { column, value ->
                    putGeneratedContentValue(values, column, value)
                }
            }
            return
        }
        LitePalRuntime.recordReflectionFallback("write.putFieldsValue")
        for (field in supportedFields) {
            if (!isIdColumn(field.name)) {
                putFieldsValueDependsOnSaveOrUpdate(baseObj, field, values)
            }
        }
    }

    protected fun putContentValuesForSave(
        baseObj: LitePalSupport,
        field: Field,
        values: ContentValues
    ) {
        var fieldValue = getFieldValue(baseObj, field)
        if ("java.util.Date" == field.type.name) {
            if (fieldValue != null) {
                fieldValue = (fieldValue as Date).time
            } else {
                val annotation = field.getAnnotation(Column::class.java)
                if (annotation != null) {
                    val defaultValue = annotation.defaultValue
                    if (defaultValue.isNotEmpty()) {
                        try {
                            fieldValue = defaultValue.toLong()
                        } catch (_: NumberFormatException) {
                            LitePalLog.w(
                                TAG,
                                "$field in ${baseObj.javaClass} with invalid defaultValue. So we use null instead"
                            )
                        }
                    }
                }
                if (fieldValue == null) {
                    fieldValue = Long.MAX_VALUE
                }
            }
        }
        if (fieldValue != null) {
            val annotation = field.getAnnotation(Encrypt::class.java)
            if (annotation != null && "java.lang.String" == field.type.name) {
                fieldValue = encryptValue(annotation.algorithm, fieldValue)
            }
            val parameters = arrayOf(
                BaseUtility.changeCase(DBUtility.convertToValidColumnName(field.name)),
                fieldValue
            )
            val parameterTypes = getParameterTypes(field, fieldValue, parameters)
            DynamicExecutor.send(values, "put", parameters, values.javaClass, parameterTypes)
        }
    }

    protected fun putContentValuesForUpdate(
        baseObj: LitePalSupport,
        field: Field,
        values: ContentValues
    ) {
        var fieldValue = getFieldValue(baseObj, field)
        if ("java.util.Date" == field.type.name) {
            fieldValue = if (fieldValue != null) {
                (fieldValue as Date).time
            } else {
                Long.MAX_VALUE
            }
        }
        val annotation = field.getAnnotation(Encrypt::class.java)
        if (annotation != null && "java.lang.String" == field.type.name) {
            fieldValue = encryptValue(annotation.algorithm, fieldValue)
        }
        val parameters = arrayOf(
            BaseUtility.changeCase(DBUtility.convertToValidColumnName(field.name)),
            fieldValue
        )
        val parameterTypes = getParameterTypes(field, fieldValue, parameters)
        DynamicExecutor.send(values, "put", parameters, values.javaClass, parameterTypes)
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

    protected fun getFieldValue(dataSupport: LitePalSupport, field: Field?): Any? {
        if (shouldGetOrSet(dataSupport, field)) {
            return DynamicExecutor.getField(dataSupport, field!!.name, dataSupport.javaClass)
        }
        return null
    }

    protected fun setFieldValue(dataSupport: LitePalSupport, field: Field?, parameter: Any?) {
        if (shouldGetOrSet(dataSupport, field)) {
            DynamicExecutor.setField(dataSupport, field!!.name, parameter, dataSupport.javaClass)
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
        var className: String? = null
        try {
            className = baseObj.getClassName()
            val modelClass = Class.forName(className)
            tempEmptyModel = modelClass.newInstance() as LitePalSupport
            return tempEmptyModel!!
        } catch (_: ClassNotFoundException) {
            throw DatabaseGenerateException(DatabaseGenerateException.CLASS_NOT_FOUND + className)
        } catch (e: InstantiationException) {
            throw LitePalSupportException(
                className + LitePalSupportException.INSTANTIATION_EXCEPTION,
                e
            )
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

    protected fun shouldGetOrSet(dataSupport: LitePalSupport?, field: Field?): Boolean {
        return dataSupport != null && field != null
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
        LitePalRuntime.recordReflectionFallback("constructor.newInstance")
        try {
            val constructor = findBestSuitConstructor(modelClass)
            return constructor.newInstance(*getConstructorParams(modelClass, constructor))
        } catch (e: Exception) {
            throw LitePalSupportException(e.message, e)
        }
    }

    protected fun findBestSuitConstructor(modelClass: Class<*>): Constructor<*> {
        val cachedConstructor = CONSTRUCTOR_CACHE[modelClass.name]
        if (cachedConstructor != null) {
            return cachedConstructor
        }
        val constructors = modelClass.declaredConstructors
        if (constructors.isEmpty()) {
            throw LitePalSupportException("${modelClass.name} has no constructor. LitePal could not handle it")
        }
        var bestSuitConstructor: Constructor<*>? = null
        var minConstructorParamLength = Int.MAX_VALUE
        for (constructor in constructors) {
            val types = constructor.parameterTypes
            var canUseThisConstructor = true
            for (parameterType in types) {
                val parameterTypeName = parameterType.name
                if (
                    parameterType == modelClass ||
                    parameterTypeName.startsWith("com.android") &&
                    parameterTypeName.endsWith("InstantReloadException")
                ) {
                    canUseThisConstructor = false
                    break
                }
            }
            if (canUseThisConstructor && types.size < minConstructorParamLength) {
                bestSuitConstructor = constructor
                minConstructorParamLength = types.size
            }
        }
        if (bestSuitConstructor != null) {
            bestSuitConstructor.isAccessible = true
            CONSTRUCTOR_CACHE[modelClass.name] = bestSuitConstructor
            return bestSuitConstructor
        }
        val builder = StringBuilder(modelClass.name)
            .append(" has no suited constructor to new instance. Constructors defined in class:")
        for (constructor in constructors) {
            builder.append("\n").append(constructor.toString())
        }
        throw LitePalSupportException(builder.toString())
    }

    protected fun getConstructorParams(
        modelClass: Class<*>,
        constructor: Constructor<*>
    ): Array<Any?> {
        val paramTypes = constructor.parameterTypes
        val params = arrayOfNulls<Any>(paramTypes.size)
        for (i in paramTypes.indices) {
            params[i] = getInitParamValue(modelClass, paramTypes[i])
        }
        return params
    }

    protected fun setValueToModel(
        modelInstance: Any,
        supportedFields: List<Field>,
        cursor: Cursor,
        sparseArray: SparseArray<QueryInfoCache>
    ) {
        val cacheSize = sparseArray.size()
        if (cacheSize > 0) {
            for (i in 0 until cacheSize) {
                val columnIndex = sparseArray.keyAt(i)
                val cache = sparseArray[columnIndex]
                if (cache != null) {
                    setToModelByReflection(
                        modelInstance,
                        cache.field,
                        columnIndex,
                        cache.getMethodName,
                        cursor
                    )
                }
            }
        } else {
            for (field in supportedFields) {
                val getMethodName = genGetColumnMethod(field)
                val columnName = if (isIdColumn(field.name)) {
                    "id"
                } else {
                    DBUtility.convertToValidColumnName(field.name)
                }
                val columnIndex = cursor.getColumnIndex(BaseUtility.changeCase(columnName))
                if (columnIndex != -1) {
                    setToModelByReflection(modelInstance, field, columnIndex, getMethodName, cursor)
                    val cache = QueryInfoCache()
                    cache.getMethodName = getMethodName
                    cache.field = field
                    sparseArray.put(columnIndex, cache)
                }
            }
        }
    }

    protected fun getForeignKeyAssociations(className: String, isEager: Boolean): List<AssociationsInfo>? {
        if (isEager) {
            analyzeAssociations(className)
            return fkInCurrentModel
        }
        return null
    }

    protected fun getParameterTypes(
        field: Field,
        fieldValue: Any?,
        parameters: Array<Any?>
    ): Array<Class<*>> {
        return if (isCharType(field)) {
            parameters[1] = fieldValue.toString()
            arrayOf(String::class.java, String::class.java)
        } else {
            when {
                field.type.isPrimitive -> {
                    arrayOf(String::class.java, getObjectType(field.type)!!)
                }
                "java.util.Date" == field.type.name -> {
                    arrayOf(String::class.java, java.lang.Long::class.java)
                }
                else -> {
                    arrayOf(String::class.java, field.type)
                }
            }
        }
    }

    private fun getObjectType(primitiveType: Class<*>?): Class<*>? {
        if (primitiveType != null && primitiveType.isPrimitive) {
            return when (primitiveType.name) {
                "int" -> java.lang.Integer::class.java
                "short" -> java.lang.Short::class.java
                "long" -> java.lang.Long::class.java
                "float" -> java.lang.Float::class.java
                "double" -> java.lang.Double::class.java
                "boolean" -> java.lang.Boolean::class.java
                "char" -> java.lang.Character::class.java
                else -> null
            }
        }
        return null
    }

    private fun getInitParamValue(modelClass: Class<*>, paramType: Class<*>): Any? {
        return when (paramType.name) {
            "boolean", "java.lang.Boolean" -> false
            "float", "java.lang.Float" -> 0f
            "double", "java.lang.Double" -> 0.0
            "int", "java.lang.Integer" -> 0
            "long", "java.lang.Long" -> 0L
            "short", "java.lang.Short" -> 0
            "char", "java.lang.Character" -> ' '
            "[B" -> ByteArray(0)
            "[Ljava.lang.Byte;" -> emptyArray<Byte>()
            "java.lang.String" -> ""
            else -> {
                if (modelClass == paramType) {
                    null
                } else {
                    createInstanceFromClass(paramType)
                }
            }
        }
    }

    private fun isCharType(field: Field): Boolean {
        val type = field.type.name
        return type == "char" || type.endsWith("Character")
    }

    private fun isPrimitiveBooleanType(field: Field): Boolean {
        return "boolean" == field.type.name
    }

    private fun putFieldsValueDependsOnSaveOrUpdate(
        baseObj: LitePalSupport,
        field: Field,
        values: ContentValues
    ) {
        if (isUpdating()) {
            if (!isFieldWithDefaultValue(baseObj, field)) {
                putContentValuesForUpdate(baseObj, field, values)
            }
        } else if (isSaving()) {
            putContentValuesForSave(baseObj, field, values)
        }
    }

    private fun putGeneratedContentValue(values: ContentValues, column: String, value: Any?) {
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

    private fun isFieldWithDefaultValue(baseObj: LitePalSupport, field: Field): Boolean {
        val emptyModel = getEmptyModel(baseObj)
        val realReturn = getFieldValue(baseObj, field)
        val defaultReturn = getFieldValue(emptyModel, field)
        if (realReturn != null && defaultReturn != null) {
            return realReturn.toString() == defaultReturn.toString()
        }
        return realReturn == defaultReturn
    }

    protected fun makeGetterMethodName(field: Field): String {
        var fieldName = field.name
        val getterMethodPrefix = if (isPrimitiveBooleanType(field)) {
            if (Regex("^is[A-Z]{1}.*$").matches(fieldName)) {
                fieldName = fieldName.substring(2)
            }
            "is"
        } else {
            "get"
        }
        return if (Regex("^[a-z]{1}[A-Z]{1}.*").matches(fieldName)) {
            getterMethodPrefix + fieldName
        } else {
            getterMethodPrefix + BaseUtility.capitalize(fieldName)
        }
    }

    protected fun makeSetterMethodName(field: Field): String {
        val setterMethodPrefix = "set"
        return if (isPrimitiveBooleanType(field) && Regex("^is[A-Z]{1}.*$").matches(field.name)) {
            setterMethodPrefix + field.name.substring(2)
        } else if (Regex("^[a-z]{1}[A-Z]{1}.*").matches(field.name)) {
            setterMethodPrefix + field.name
        } else {
            setterMethodPrefix + BaseUtility.capitalize(field.name)
        }
    }

    private fun genGetColumnMethod(field: Field): String {
        val fieldType = if (isCollection(field.type)) {
            getGenericTypeClass(field)
        } else {
            field.type
        }
        return genGetColumnMethod(
            fieldType ?: throw LitePalSupportException("Generic type on ${field.name} is not supported.")
        )
    }

    private fun genGetColumnMethod(fieldType: Class<*>): String {
        val typeName = if (fieldType.isPrimitive) {
            BaseUtility.capitalize(fieldType.name)
        } else {
            fieldType.simpleName
        }
        var methodName = "get$typeName"
        methodName = when (methodName) {
            "getBoolean", "getInteger" -> "getInt"
            "getChar", "getCharacter" -> "getString"
            "getDate" -> "getLong"
            else -> methodName
        }
        return methodName
    }

    private fun getCustomizedColumns(
        columns: Array<String?>?,
        supportedGenericFields: MutableList<Field>,
        foreignKeyAssociations: List<AssociationsInfo>?
    ): Array<String?>? {
        if (columns != null && columns.isNotEmpty()) {
            var columnsContainsId = false
            val columnList = columns.toMutableList()
            val supportedGenericFieldNames = ArrayList<String>()
            val columnToRemove = ArrayList<Int>()
            val genericColumnsForQuery = ArrayList<String>()
            val tempSupportedGenericFields = ArrayList<Field>()

            for (supportedGenericField in supportedGenericFields) {
                supportedGenericFieldNames.add(supportedGenericField.name)
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
                val fieldName = supportedGenericField.name
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
            val supportedFields = getSupportedFields(associatedClassName)
            val supportedGenericFields = getSupportedGenericFields(associatedClassName)
            if (associationInfo.getAssociationType() == Const.Model.MANY_TO_MANY) {
                loadManyToManyAssociationsBatch(
                    baseTableName,
                    orderedBaseIds,
                    baseObjById,
                    associationInfo,
                    associatedClassName,
                    supportedFields,
                    supportedGenericFields
                )
            } else {
                loadManyToOneOrOneToOneAssociationsBatch(
                    orderedBaseIds,
                    baseObjById,
                    associationInfo,
                    associatedClassName,
                    supportedFields,
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
        supportedFields: List<Field>,
        supportedGenericFields: List<Field>
    ) {
        val foreignKeyColumn = getForeignKeyColumnName(
            DBUtility.getTableNameByClassName(associationInfo.getSelfClassName())
        ) ?: return
        val associatedClass = resolveAssociationModelClass(associatedClassName) ?: return
        val associatedTableName = BaseUtility.changeCase(
            DBUtility.getTableNameByClassName(associatedClassName)
        ).orEmpty()
        val queryInfoCacheSparseArray = SparseArray<QueryInfoCache>()
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
                            LitePalRuntime.recordReflectionFallback("assoc.setValueToModel")
                            setValueToModel(
                                modelInstance,
                                supportedFields,
                                cursor,
                                queryInfoCacheSparseArray
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
                        associatedModel
                    )
                }
            }
        }
        queryInfoCacheSparseArray.clear()
    }

    private fun loadManyToManyAssociationsBatch(
        baseTableName: String,
        baseIds: List<Long>,
        baseObjById: Map<Long, LitePalSupport>,
        associationInfo: AssociationsInfo,
        associatedClassName: String,
        supportedFields: List<Field>,
        supportedGenericFields: List<Field>
    ) {
        val associatedClass = resolveAssociationModelClass(associatedClassName) ?: return
        val associatedTableName = DBUtility.getTableNameByClassName(associatedClassName)
        val intermediateTableName = DBUtility.getIntermediateTableName(baseTableName, associatedTableName)
        val baseIdAlias = "_lp_base_id"
        val queryInfoCacheSparseArray = SparseArray<QueryInfoCache>()
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
                            LitePalRuntime.recordReflectionFallback("m2m.setValueToModel")
                            setValueToModel(
                                modelInstance,
                                supportedFields,
                                cursor,
                                queryInfoCacheSparseArray
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
                    associatedModel
                )
            }
        }
        queryInfoCacheSparseArray.clear()
    }

    private fun setGenericValuesToModelsBatch(
        baseObjs: List<LitePalSupport>,
        supportedGenericFields: List<Field>
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
            val genericTypeName = getGenericTypeName(field)
            val tableName = DBUtility.getGenericTableName(baseClassName, field.name)
            val genericValueIdColumnName = DBUtility.getGenericValueIdColumnName(baseClassName)
            if (baseClassName == genericTypeName) {
                val genericValueColumnName = DBUtility.getM2MSelfRefColumnName(field)
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
                val genericValueColumnName = DBUtility.convertToValidColumnName(field.name)
                setCollectionGenericValuesBatch(
                    field,
                    tableName,
                    genericValueColumnName,
                    genericValueIdColumnName,
                    genGetColumnMethod(field),
                    baseObjById,
                    stableBaseIds
                )
            }
        }
    }

    private fun setCollectionGenericValuesBatch(
        field: Field,
        tableName: String,
        genericValueColumnName: String?,
        genericValueIdColumnName: String,
        getMethodName: String,
        baseObjById: Map<Long, LitePalSupport>,
        baseIds: List<Long>
    ) {
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
                    null,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "$genericValueIdColumnName asc, rowid asc"
                )
                if (cursor.moveToFirst()) {
                    val idColumnIndex = cursor.getColumnIndex(genericValueIdColumnName)
                    val valueColumnIndex = cursor.getColumnIndex(
                        BaseUtility.changeCase(genericValueColumnName)
                    )
                    if (idColumnIndex == -1 || valueColumnIndex == -1) {
                        continue
                    }
                    do {
                        val ownerId = cursor.getLong(idColumnIndex)
                        val baseObj = baseObjById[ownerId] ?: continue
                        setToModelByReflection(
                            baseObj,
                            field,
                            valueColumnIndex,
                            getMethodName,
                            cursor
                        )
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }
    }

    private fun setSelfReferenceGenericValuesBatch(
        baseModelClass: Class<out LitePalSupport>,
        field: Field,
        tableName: String,
        genericValueColumnName: String,
        genericValueIdColumnName: String,
        baseObjById: Map<Long, LitePalSupport>,
        baseIds: List<Long>
    ) {
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
                    null,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "$genericValueIdColumnName asc, rowid asc"
                )
                if (cursor.moveToFirst()) {
                    val ownerIdColumnIndex = cursor.getColumnIndex(genericValueIdColumnName)
                    val valueIdColumnIndex = cursor.getColumnIndex(
                        BaseUtility.changeCase(genericValueColumnName)
                    )
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
            val collection = getOrCreateCollection(ownerObj, field)
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
        val supportedFields = getSupportedFields(modelClass.name)
        val queryInfoCacheSparseArray = SparseArray<QueryInfoCache>()
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
                            LitePalRuntime.recordReflectionFallback("generic.setValueToModel")
                            setValueToModel(modelInstance, supportedFields, cursor, queryInfoCacheSparseArray)
                        }
                        modelsById[modelInstance.getBaseObjId()] = modelInstance
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }
        queryInfoCacheSparseArray.clear()
        return modelsById
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAssociationModelClass(className: String): Class<out LitePalSupport>? {
        val cached = ASSOCIATION_MODEL_CLASS_CACHE[className]
        if (cached != null) {
            return cached
        }
        return try {
            val resolved = Class.forName(className) as Class<out LitePalSupport>
            ASSOCIATION_MODEL_CLASS_CACHE[className] = resolved
            resolved
        } catch (e: ClassNotFoundException) {
            LitePalLog.e(TAG, "Failed to resolve association class $className.", e)
            null
        }
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
    private fun getOrCreateCollection(modelInstance: Any, field: Field): MutableCollection<Any?> {
        var collection = DynamicExecutor.getField(
            modelInstance,
            field.name,
            modelInstance.javaClass
        ) as MutableCollection<Any?>?
        if (collection == null) {
            collection = if (isList(field.type)) {
                ArrayList()
            } else {
                HashSet()
            }
            DynamicExecutor.setField(modelInstance, field.name, collection, modelInstance.javaClass)
        }
        return collection
    }

    @Suppress("UNCHECKED_CAST")
    private fun addAssociatedModelIntoCollection(
        baseObj: LitePalSupport,
        field: Field?,
        associatedModel: LitePalSupport
    ) {
        val collectionField = field ?: return
        var collection = getFieldValue(baseObj, collectionField) as MutableCollection<LitePalSupport>?
        if (collection == null) {
            collection = if (isList(collectionField.type)) {
                ArrayList()
            } else {
                HashSet()
            }
            DynamicExecutor.setField(
                baseObj,
                collectionField.name,
                collection,
                baseObj.javaClass
            )
        }
        collection.add(associatedModel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setToModelByReflection(
        modelInstance: Any,
        field: Field,
        columnIndex: Int,
        getMethodName: String,
        cursor: Cursor
    ) {
        if (cursor.isNull(columnIndex)) {
            return
        }
        val method: Method = getCursorGetter(cursor.javaClass, getMethodName)
        var value: Any? = method.invoke(cursor, columnIndex)

        if (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.javaObjectType) {
            value = when (value.toString()) {
                "0" -> false
                "1" -> true
                else -> value
            }
        } else if (field.type == Char::class.javaPrimitiveType || field.type == Char::class.javaObjectType) {
            value = when (value) {
                is Char -> value
                else -> value.toString().firstOrNull() ?: return
            }
        } else if (field.type == Date::class.java) {
            val date = value as Long
            value = if (date == Long.MAX_VALUE) {
                null
            } else {
                Date(date)
            }
        }

        if (isCollection(field.type)) {
            var collection = DynamicExecutor.getField(
                modelInstance,
                field.name,
                modelInstance.javaClass
            ) as MutableCollection<Any?>?
            if (collection == null) {
                collection = if (isList(field.type)) {
                    ArrayList()
                } else {
                    HashSet()
                }
                DynamicExecutor.setField(modelInstance, field.name, collection, modelInstance.javaClass)
            }
            val genericTypeName = getGenericTypeName(field)
            if ("java.lang.String" == genericTypeName) {
                val annotation = field.getAnnotation(Encrypt::class.java)
                if (annotation != null) {
                    value = decryptValue(annotation.algorithm, value)
                }
            }
            collection.add(value)
        } else {
            val annotation = field.getAnnotation(Encrypt::class.java)
            if (annotation != null && "java.lang.String" == field.type.name) {
                value = decryptValue(annotation.algorithm, value)
            }
            DynamicExecutor.setField(modelInstance, field.name, value, modelInstance.javaClass)
        }
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

    class QueryInfoCache {
        lateinit var getMethodName: String
        lateinit var field: Field
    }

    private fun getCursorGetter(cursorClass: Class<*>, methodName: String): Method {
        val cacheKey = "${cursorClass.name}#$methodName"
        val cachedMethod = CURSOR_GETTER_METHOD_CACHE[cacheKey]
        if (cachedMethod != null) {
            return cachedMethod
        }
        val method = cursorClass.getMethod(methodName, Int::class.javaPrimitiveType)
        CURSOR_GETTER_METHOD_CACHE[cacheKey] = method
        return method
    }

    companion object {
        const val TAG = "DataHandler"
        private const val QUERY_CHUNK_SIZE = 500
        private val CONSTRUCTOR_CACHE = ConcurrentHashMap<String, Constructor<*>>()
        private val CURSOR_GETTER_METHOD_CACHE = ConcurrentHashMap<String, Method>()
        private val ASSOCIATION_MODEL_CLASS_CACHE = ConcurrentHashMap<String, Class<out LitePalSupport>>()
    }
}
