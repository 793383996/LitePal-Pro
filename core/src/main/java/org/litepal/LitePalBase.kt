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

import org.litepal.crud.LitePalSupport
import org.litepal.crud.model.AssociationsInfo
import org.litepal.generated.GeneratedFieldMeta
import org.litepal.generated.GeneratedGenericFieldMeta
import org.litepal.generated.GeneratedRegistryLocator
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
import org.litepal.util.DBUtility
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
            tableModel.addColumnModel(convertFieldToColumnModel(field))
        }
        return tableModel
    }

    protected fun getAssociations(classNames: List<String>): Collection<AssociationsModel> {
        associationModels.clear()
        genericModels.clear()
        for (className in classNames) {
            if (className == TABLE_SCHEMA_CLASS_NAME) {
                continue
            }
            val entityMeta = requireEntityMeta(className)
            entityMeta.associationMeta?.relations.orEmpty().forEach { relation ->
                addIntoAssociationModelCollection(
                    className = className,
                    associatedClassName = relation.associatedClassName,
                    classHoldsForeignKey = relation.classHoldsForeignKey,
                    associationType = relation.associationType
                )
            }
            entityMeta.associationMeta?.genericFields.orEmpty().forEach { generic ->
                addIntoGenericModelCollection(className, generic)
            }
        }
        return associationModels
    }

    protected fun getGenericModels(): Collection<GenericModel> = genericModels

    protected fun getAssociationInfo(className: String): Collection<AssociationsInfo> {
        associationInfos.clear()
        if (className == TABLE_SCHEMA_CLASS_NAME) {
            return associationInfos
        }
        val entityMeta = requireEntityMeta(className)
        entityMeta.associationMeta?.relations.orEmpty().forEach { relation ->
            val associationInfo = AssociationsInfo()
            associationInfo.setSelfClassName(className)
            associationInfo.setAssociatedClassName(relation.associatedClassName)
            associationInfo.setClassHoldsForeignKey(relation.classHoldsForeignKey)
            associationInfo.setAssociateOtherModelFromSelf(relation.selfPropertyName)
            associationInfo.setAssociateOtherModelCollectionType(relation.selfCollectionType)
            associationInfo.setAssociateSelfFromOtherModel(relation.reversePropertyName)
            associationInfo.setAssociateSelfCollectionType(relation.reverseCollectionType)
            associationInfo.setAssociationType(relation.associationType)
            associationInfos.add(associationInfo)
        }
        return associationInfos
    }

    protected fun getSupportedFields(className: String): List<GeneratedFieldMeta> {
        return classFieldsCache.computeIfAbsent(className) { key ->
            if (key == TABLE_SCHEMA_CLASS_NAME) {
                return@computeIfAbsent tableSchemaSupportedFields()
            }
            val generatedMeta = requireEntityMeta(key)
            LitePalRuntime.recordGeneratedPathHit("meta.supportedFields")
            generatedMeta.persistedFields
        }
    }

    protected fun getSupportedGenericFields(className: String): List<GeneratedGenericFieldMeta> {
        return classGenericFieldsCache.computeIfAbsent(className) { key ->
            if (key == TABLE_SCHEMA_CLASS_NAME) {
                return@computeIfAbsent emptyList()
            }
            val generatedMeta = requireEntityMeta(key)
            LitePalRuntime.recordGeneratedPathHit("meta.supportedGenericFields")
            generatedMeta.associationMeta?.genericFields.orEmpty()
        }
    }

    protected fun isCollection(collectionType: String?): Boolean {
        return isList(collectionType) || isSet(collectionType)
    }

    protected fun isList(collectionType: String?): Boolean {
        return collectionType.equals("LIST", ignoreCase = true)
    }

    protected fun isSet(collectionType: String?): Boolean {
        return collectionType.equals("SET", ignoreCase = true)
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

    private fun requireEntityMeta(className: String) = GeneratedRegistryLocator.findEntityMeta(className)
        ?: run {
            LitePalRuntime.recordGeneratedContractViolation("entity.meta.missing")
            throw IllegalStateException(
                "Generated metadata is REQUIRED but entity meta is missing for $className."
            )
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

    private fun addIntoGenericModelCollection(className: String, genericField: GeneratedGenericFieldMeta) {
        val elementTypeName = genericField.elementTypeName
        val genericModel = GenericModel()
        genericModel.setTableName(DBUtility.getGenericTableName(className, genericField.propertyName))
        if (elementTypeName == className) {
            genericModel.setValueColumnName(DBUtility.getM2MSelfRefColumnName(genericField.propertyName))
            genericModel.setValueColumnType("integer")
        } else if (BaseUtility.isGenericTypeSupported(elementTypeName)) {
            genericModel.setValueColumnName(DBUtility.convertToValidColumnName(genericField.propertyName))
            genericModel.setValueColumnType(getColumnType(elementTypeName))
        } else {
            return
        }
        genericModel.setValueIdColumnName(DBUtility.getGenericValueIdColumnName(className))
        genericModels.add(genericModel)
    }

    private fun tableSchemaSupportedFields(): List<GeneratedFieldMeta> {
        return listOf(
            GeneratedFieldMeta(
                propertyName = "name",
                columnName = "name",
                typeName = "java.lang.String",
                columnType = "text",
                nullable = true,
                unique = false,
                indexed = false,
                defaultValue = "",
                encryptAlgorithm = null
            ),
            GeneratedFieldMeta(
                propertyName = "type",
                columnName = "type",
                typeName = "java.lang.Integer",
                columnType = "integer",
                nullable = true,
                unique = false,
                indexed = false,
                defaultValue = "",
                encryptAlgorithm = null
            )
        )
    }

    private fun convertFieldToColumnModel(field: GeneratedFieldMeta): ColumnModel {
        val columnModel = ColumnModel()
        columnModel.setColumnName(DBUtility.convertToValidColumnName(field.columnName))
        columnModel.setColumnType(getColumnType(field.typeName))
        columnModel.setNullable(field.nullable)
        columnModel.setUnique(field.unique)
        columnModel.setDefaultValue(field.defaultValue)
        columnModel.setHasIndex(field.indexed)
        return columnModel
    }

    companion object {
        private val classFieldsCache: MutableMap<String, List<GeneratedFieldMeta>> = ConcurrentHashMap()
        private val classGenericFieldsCache: MutableMap<String, List<GeneratedGenericFieldMeta>> = ConcurrentHashMap()

        @JvmStatic
        internal fun clearReflectionCachesForTesting() {
            classFieldsCache.clear()
            classGenericFieldsCache.clear()
        }

        const val TAG = "LitePalBase"
        private const val TABLE_SCHEMA_CLASS_NAME = "org.litepal.model.Table_Schema"
    }
}
