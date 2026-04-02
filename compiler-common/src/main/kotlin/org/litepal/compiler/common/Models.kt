package org.litepal.compiler.common

data class AnchorModel(
    val version: Int,
    val anchorClassName: String,
    val entities: List<EntityModel>
)

data class EntityModel(
    val className: String,
    val tableName: String,
    val supportedFields: List<String>,
    val supportedGenericFields: List<String>,
    val persistedFields: List<PersistentFieldModel>
)

data class PersistentFieldModel(
    val propertyName: String,
    val columnName: String,
    val typeName: String,
    val columnType: String,
    val nullable: Boolean,
    val unique: Boolean,
    val hasIndex: Boolean,
    val defaultValue: String,
    val encryptAlgorithm: String? = null
)
