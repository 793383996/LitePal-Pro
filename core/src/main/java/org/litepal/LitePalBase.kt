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

package org.litepal

import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport
import org.litepal.crud.model.AssociationsInfo
import org.litepal.exceptions.DatabaseGenerateException
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.parser.LitePalAttr
import org.litepal.tablemanager.model.AssociationsModel
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.tablemanager.model.GenericModel
import org.litepal.tablemanager.model.TableModel
import org.litepal.tablemanager.typechange.BlobOrm
import org.litepal.tablemanager.typechange.BooleanOrm
import org.litepal.tablemanager.typechange.DateOrm
import org.litepal.tablemanager.typechange.DecimalOrm
import org.litepal.tablemanager.typechange.NumericOrm
import org.litepal.tablemanager.typechange.OrmChange
import org.litepal.tablemanager.typechange.TextOrm
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

abstract class LitePalBase {

    private val typeChangeRules: Array<OrmChange> = arrayOf(
        NumericOrm(), TextOrm(), BooleanOrm(), DecimalOrm(), DateOrm(), BlobOrm()
    )

    private var associationModels: MutableCollection<AssociationsModel> = HashSet()
    private var associationInfos: MutableCollection<AssociationsInfo> = HashSet()
    private var genericModels: MutableCollection<GenericModel> = HashSet()

    protected fun getTableModel(className: String): TableModel {
        val tableName = DBUtility.getTableNameByClassName(className)
        val tableModel = TableModel()
        tableModel.setTableName(tableName)
        tableModel.setClassName(className)
        val supportedFields = getSupportedFields(className)
        for (field in supportedFields) {
            val columnModel = convertFieldToColumnModel(field)
            tableModel.addColumnModel(columnModel)
        }
        return tableModel
    }

    protected fun getAssociations(classNames: List<String>): Collection<AssociationsModel> {
        associationModels.clear()
        genericModels.clear()
        for (className in classNames) {
            analyzeClassFields(className, GET_ASSOCIATIONS_ACTION)
        }
        return associationModels
    }

    protected fun getGenericModels(): Collection<GenericModel> {
        return genericModels
    }

    protected fun getAssociationInfo(className: String): Collection<AssociationsInfo> {
        associationInfos.clear()
        analyzeClassFields(className, GET_ASSOCIATION_INFO_ACTION)
        return associationInfos
    }

    protected fun getSupportedFields(className: String): List<Field> {
        return classFieldsCache.computeIfAbsent(className) { key ->
            val generatedMeta = GeneratedRegistryLocator.findEntityMeta(key)
            if (generatedMeta != null) {
                val resolved = resolveFieldsByNames(key, generatedMeta.supportedFields)
                if (resolved.isNotEmpty()) {
                    LitePalRuntime.recordGeneratedPathHit("meta.supportedFields")
                    return@computeIfAbsent resolved
                }
            }
            LitePalRuntime.recordReflectionFallback("meta.supportedFields")
            val supportedFields = ArrayList<Field>()
            val clazz = try {
                Class.forName(key)
            } catch (e: ClassNotFoundException) {
                throw DatabaseGenerateException(DatabaseGenerateException.CLASS_NOT_FOUND + key)
            }
            recursiveSupportedFields(clazz, supportedFields)
            supportedFields.toList()
        }
    }

    protected fun getSupportedGenericFields(className: String): List<Field> {
        return classGenericFieldsCache.computeIfAbsent(className) { key ->
            val generatedMeta = GeneratedRegistryLocator.findEntityMeta(key)
            if (generatedMeta != null) {
                val resolved = resolveFieldsByNames(key, generatedMeta.supportedGenericFields)
                if (resolved.isNotEmpty()) {
                    LitePalRuntime.recordGeneratedPathHit("meta.supportedGenericFields")
                    return@computeIfAbsent resolved
                }
            }
            LitePalRuntime.recordReflectionFallback("meta.supportedGenericFields")
            val supportedGenericFields = ArrayList<Field>()
            val clazz = try {
                Class.forName(key)
            } catch (e: ClassNotFoundException) {
                throw DatabaseGenerateException(DatabaseGenerateException.CLASS_NOT_FOUND + key)
            }
            recursiveSupportedGenericFields(clazz, supportedGenericFields)
            supportedGenericFields.toList()
        }
    }

    protected fun isCollection(fieldType: Class<*>): Boolean {
        return isList(fieldType) || isSet(fieldType)
    }

    protected fun isList(fieldType: Class<*>): Boolean {
        return List::class.java.isAssignableFrom(fieldType)
    }

    protected fun isSet(fieldType: Class<*>): Boolean {
        return Set::class.java.isAssignableFrom(fieldType)
    }

    protected fun isIdColumn(columnName: String?): Boolean {
        return "_id".equals(columnName, ignoreCase = true) || "id".equals(columnName, ignoreCase = true)
    }

    protected fun getForeignKeyColumnName(associatedTableName: String?): String? {
        return BaseUtility.changeCase("${associatedTableName}_id")
    }

    protected fun getColumnType(fieldType: String?): String? {
        for (ormChange in typeChangeRules) {
            val columnType = ormChange.object2Relation(fieldType)
            if (columnType != null) {
                return columnType
            }
        }
        return null
    }

    protected fun getGenericTypeClass(field: Field): Class<*>? {
        val genericType: Type? = field.genericType
        if (genericType is ParameterizedType) {
            return genericType.actualTypeArguments[0] as? Class<*>
        }
        return null
    }

    private fun recursiveSupportedFields(clazz: Class<*>, supportedFields: MutableList<Field>) {
        if (clazz == LitePalSupport::class.java || clazz == Any::class.java) {
            return
        }
        val fields = clazz.declaredFields
        for (field in fields) {
            val annotation = field.getAnnotation(Column::class.java)
            if (annotation != null && annotation.ignore) {
                continue
            }
            if (!Modifier.isStatic(field.modifiers)) {
                val fieldType = field.type.name
                if (BaseUtility.isFieldTypeSupported(fieldType)) {
                    supportedFields.add(field)
                }
            }
        }
        recursiveSupportedFields(clazz.superclass, supportedFields)
    }

    private fun resolveFieldsByNames(className: String, fieldNames: List<String>): List<Field> {
        if (fieldNames.isEmpty()) {
            return emptyList()
        }
        val clazz = try {
            Class.forName(className)
        } catch (_: ClassNotFoundException) {
            return emptyList()
        }
        val resolved = ArrayList<Field>(fieldNames.size)
        for (fieldName in fieldNames) {
            val field = findFieldRecursively(clazz, fieldName) ?: continue
            if (!Modifier.isStatic(field.modifiers)) {
                resolved.add(field)
            }
        }
        return resolved
    }

    private fun findFieldRecursively(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != LitePalSupport::class.java && current != Any::class.java) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun recursiveSupportedGenericFields(clazz: Class<*>, supportedGenericFields: MutableList<Field>) {
        if (clazz == LitePalSupport::class.java || clazz == Any::class.java) {
            return
        }
        val fields = clazz.declaredFields
        for (field in fields) {
            val annotation = field.getAnnotation(Column::class.java)
            if (annotation != null && annotation.ignore) {
                continue
            }
            if (!Modifier.isStatic(field.modifiers) && isCollection(field.type)) {
                val genericTypeName = getGenericTypeName(field)
                if (BaseUtility.isGenericTypeSupported(genericTypeName) ||
                    clazz.name.equals(genericTypeName, ignoreCase = true)
                ) {
                    supportedGenericFields.add(field)
                }
            }
        }
        recursiveSupportedGenericFields(clazz.superclass, supportedGenericFields)
    }

    private fun analyzeClassFields(className: String, action: Int) {
        try {
            val dynamicClass = Class.forName(className)
            val fields = dynamicClass.declaredFields
            for (field in fields) {
                if (isNonPrimitive(field)) {
                    val annotation = field.getAnnotation(Column::class.java)
                    if (annotation != null && annotation.ignore) {
                        continue
                    }
                    oneToAnyConditions(className, field, action)
                    manyToAnyConditions(className, field, action)
                }
            }
        } catch (ex: ClassNotFoundException) {
            LitePalLog.e(TAG, "Failed to analyze class fields for $className.", ex)
            throw DatabaseGenerateException(DatabaseGenerateException.CLASS_NOT_FOUND + className)
        }
    }

    private fun isNonPrimitive(field: Field): Boolean {
        return !field.type.isPrimitive
    }

    protected fun isPrivate(field: Field): Boolean {
        return Modifier.isPrivate(field.modifiers)
    }

    @Throws(ClassNotFoundException::class)
    private fun oneToAnyConditions(className: String, field: Field, action: Int) {
        val fieldTypeClass = field.type
        if (LitePalAttr.getInstance().getClassNames().contains(fieldTypeClass.name)) {
            val reverseDynamicClass = Class.forName(fieldTypeClass.name)
            val reverseFields = reverseDynamicClass.declaredFields
            var reverseAssociations = false
            for (reverseField in reverseFields) {
                if (!Modifier.isStatic(reverseField.modifiers)) {
                    val reverseFieldTypeClass = reverseField.type
                    if (className == reverseFieldTypeClass.name) {
                        if (action == GET_ASSOCIATIONS_ACTION) {
                            addIntoAssociationModelCollection(
                                className,
                                fieldTypeClass.name,
                                fieldTypeClass.name,
                                Const.Model.ONE_TO_ONE
                            )
                        } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                            addIntoAssociationInfoCollection(
                                className,
                                fieldTypeClass.name,
                                fieldTypeClass.name,
                                field,
                                reverseField,
                                Const.Model.ONE_TO_ONE
                            )
                        }
                        reverseAssociations = true
                    } else if (isCollection(reverseFieldTypeClass)) {
                        val genericTypeName = getGenericTypeName(reverseField)
                        if (className == genericTypeName) {
                            if (action == GET_ASSOCIATIONS_ACTION) {
                                addIntoAssociationModelCollection(
                                    className,
                                    fieldTypeClass.name,
                                    className,
                                    Const.Model.MANY_TO_ONE
                                )
                            } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                                addIntoAssociationInfoCollection(
                                    className,
                                    fieldTypeClass.name,
                                    className,
                                    field,
                                    reverseField,
                                    Const.Model.MANY_TO_ONE
                                )
                            }
                            reverseAssociations = true
                        }
                    }
                }
            }
            if (!reverseAssociations) {
                if (action == GET_ASSOCIATIONS_ACTION) {
                    addIntoAssociationModelCollection(
                        className,
                        fieldTypeClass.name,
                        fieldTypeClass.name,
                        Const.Model.ONE_TO_ONE
                    )
                } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                    addIntoAssociationInfoCollection(
                        className,
                        fieldTypeClass.name,
                        fieldTypeClass.name,
                        field,
                        null,
                        Const.Model.ONE_TO_ONE
                    )
                }
            }
        }
    }

    @Throws(ClassNotFoundException::class)
    private fun manyToAnyConditions(className: String, field: Field, action: Int) {
        if (isCollection(field.type)) {
            val genericTypeName = getGenericTypeName(field)
            if (LitePalAttr.getInstance().getClassNames().contains(genericTypeName)) {
                val reverseDynamicClass = Class.forName(genericTypeName)
                val reverseFields = reverseDynamicClass.declaredFields
                var reverseAssociations = false
                for (reverseField in reverseFields) {
                    if (!Modifier.isStatic(reverseField.modifiers)) {
                        val reverseFieldTypeClass = reverseField.type
                        if (className == reverseFieldTypeClass.name) {
                            if (action == GET_ASSOCIATIONS_ACTION) {
                                addIntoAssociationModelCollection(
                                    className,
                                    genericTypeName!!,
                                    genericTypeName,
                                    Const.Model.MANY_TO_ONE
                                )
                            } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                                addIntoAssociationInfoCollection(
                                    className,
                                    genericTypeName!!,
                                    genericTypeName,
                                    field,
                                    reverseField,
                                    Const.Model.MANY_TO_ONE
                                )
                            }
                            reverseAssociations = true
                        } else if (isCollection(reverseFieldTypeClass)) {
                            val reverseGenericTypeName = getGenericTypeName(reverseField)
                            if (className == reverseGenericTypeName) {
                                if (action == GET_ASSOCIATIONS_ACTION) {
                                    if (className.equals(genericTypeName, ignoreCase = true)) {
                                        val genericModel = GenericModel()
                                        genericModel.setTableName(
                                            DBUtility.getGenericTableName(className, field.name)
                                        )
                                        genericModel.setValueColumnName(DBUtility.getM2MSelfRefColumnName(field))
                                        genericModel.setValueColumnType("integer")
                                        genericModel.setValueIdColumnName(
                                            DBUtility.getGenericValueIdColumnName(className)
                                        )
                                        genericModels.add(genericModel)
                                    } else {
                                        addIntoAssociationModelCollection(
                                            className,
                                            genericTypeName!!,
                                            null,
                                            Const.Model.MANY_TO_MANY
                                        )
                                    }
                                } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                                    if (!className.equals(genericTypeName, ignoreCase = true)) {
                                        addIntoAssociationInfoCollection(
                                            className,
                                            genericTypeName!!,
                                            null,
                                            field,
                                            reverseField,
                                            Const.Model.MANY_TO_MANY
                                        )
                                    }
                                }
                                reverseAssociations = true
                            }
                        }
                    }
                }
                if (!reverseAssociations) {
                    if (action == GET_ASSOCIATIONS_ACTION) {
                        addIntoAssociationModelCollection(
                            className,
                            genericTypeName!!,
                            genericTypeName,
                            Const.Model.MANY_TO_ONE
                        )
                    } else if (action == GET_ASSOCIATION_INFO_ACTION) {
                        addIntoAssociationInfoCollection(
                            className,
                            genericTypeName!!,
                            genericTypeName,
                            field,
                            null,
                            Const.Model.MANY_TO_ONE
                        )
                    }
                }
            } else if (BaseUtility.isGenericTypeSupported(genericTypeName) && action == GET_ASSOCIATIONS_ACTION) {
                val genericModel = GenericModel()
                genericModel.setTableName(DBUtility.getGenericTableName(className, field.name))
                genericModel.setValueColumnName(DBUtility.convertToValidColumnName(field.name))
                genericModel.setValueColumnType(getColumnType(genericTypeName))
                genericModel.setValueIdColumnName(DBUtility.getGenericValueIdColumnName(className))
                genericModels.add(genericModel)
            }
        }
    }

    private fun addIntoAssociationModelCollection(
        className: String,
        associatedClassName: String,
        classHoldsForeignKey: String?,
        associationType: Int
    ) {
        val associationModel = AssociationsModel()
        associationModel.setTableName(DBUtility.getTableNameByClassName(className))
        associationModel.setAssociatedTableName(DBUtility.getTableNameByClassName(associatedClassName))
        associationModel.setTableHoldsForeignKey(DBUtility.getTableNameByClassName(classHoldsForeignKey))
        associationModel.setAssociationType(associationType)
        associationModels.add(associationModel)
    }

    private fun addIntoAssociationInfoCollection(
        selfClassName: String,
        associatedClassName: String,
        classHoldsForeignKey: String?,
        associateOtherModelFromSelf: Field,
        associateSelfFromOtherModel: Field?,
        associationType: Int
    ) {
        val associationInfo = AssociationsInfo()
        associationInfo.setSelfClassName(selfClassName)
        associationInfo.setAssociatedClassName(associatedClassName)
        associationInfo.setClassHoldsForeignKey(classHoldsForeignKey)
        associationInfo.setAssociateOtherModelFromSelf(associateOtherModelFromSelf)
        associationInfo.setAssociateSelfFromOtherModel(associateSelfFromOtherModel)
        associationInfo.setAssociationType(associationType)
        associationInfos.add(associationInfo)
    }

    protected fun getGenericTypeName(field: Field): String? {
        return getGenericTypeClass(field)?.name
    }

    private fun convertFieldToColumnModel(field: Field): ColumnModel {
        val fieldType = field.type.name
        val columnType = getColumnType(fieldType)
        var nullable = true
        var unique = false
        var hasIndex = false
        var defaultValue = ""
        val annotation = field.getAnnotation(Column::class.java)
        if (annotation != null) {
            nullable = annotation.nullable
            unique = annotation.unique
            defaultValue = annotation.defaultValue
            hasIndex = annotation.index
        }
        val columnModel = ColumnModel()
        columnModel.setColumnName(DBUtility.convertToValidColumnName(field.name))
        columnModel.setColumnType(columnType)
        columnModel.setNullable(nullable)
        columnModel.setUnique(unique)
        columnModel.setDefaultValue(defaultValue)
        columnModel.setHasIndex(hasIndex)
        return columnModel
    }

    companion object {
        private val classFieldsCache: MutableMap<String, List<Field>> = ConcurrentHashMap()
        private val classGenericFieldsCache: MutableMap<String, List<Field>> = ConcurrentHashMap()

        @JvmStatic
        internal fun clearReflectionCachesForTesting() {
            classFieldsCache.clear()
            classGenericFieldsCache.clear()
        }

        const val TAG = "LitePalBase"
        private const val GET_ASSOCIATIONS_ACTION = 1
        private const val GET_ASSOCIATION_INFO_ACTION = 2
    }
}
