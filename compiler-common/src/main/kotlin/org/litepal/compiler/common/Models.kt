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
    val declaredProperties: List<PropertyModel>,
    val hasNoArgsConstructor: Boolean,
    val persistedFields: List<PersistentFieldModel>,
    val genericFields: List<GenericFieldModel> = emptyList(),
    val associationFields: List<AssociationFieldModel> = emptyList()
)

data class PropertyModel(
    val propertyName: String,
    val sourceTypeName: String,
    val writable: Boolean,
    val normalizedTypeName: String,
    val collectionType: String? = null,
    val collectionElementTypeName: String? = null,
    val encryptAlgorithm: String? = null
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

data class GenericFieldModel(
    val propertyName: String,
    val elementTypeName: String,
    val collectionType: String,
    val encryptAlgorithm: String? = null
)

data class AssociationFieldModel(
    val associatedClassName: String,
    val associationType: Int,
    val classHoldsForeignKey: String?,
    val selfPropertyName: String,
    val selfCollectionType: String?,
    val reversePropertyName: String?,
    val reverseCollectionType: String?
)
