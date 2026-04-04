package org.litepal.compiler.common

object RelationshipModeling {

    private const val ONE_TO_ONE = 1
    private const val MANY_TO_ONE = 2
    private const val MANY_TO_MANY = 3

    private val supportedGenericTypes = setOf(
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Long",
        "java.lang.Short",
        "java.lang.Boolean",
        "java.lang.Character"
    )

    fun enrichEntities(entities: List<EntityModel>): List<EntityModel> {
        if (entities.isEmpty()) {
            return emptyList()
        }
        val withGeneric = entities.map { entity ->
            val genericFields = entity.declaredProperties
                .filter { property ->
                    val elementType = property.collectionElementTypeName
                    val collectionType = property.collectionType
                    elementType != null &&
                        collectionType != null &&
                        (elementType in supportedGenericTypes || elementType == entity.className)
                }
                .map { property ->
                    GenericFieldModel(
                        propertyName = property.propertyName,
                        elementTypeName = property.collectionElementTypeName.orEmpty(),
                        collectionType = property.collectionType.orEmpty(),
                        encryptAlgorithm = property.encryptAlgorithm
                    )
                }
                .sortedBy { it.propertyName }
            entity.copy(
                supportedGenericFields = genericFields.map { it.propertyName },
                genericFields = genericFields
            )
        }
        val entityByClass = withGeneric.associateBy { it.className }
        return withGeneric.map { entity ->
            val relations = buildAssociations(entity, entityByClass)
                .sortedWith(
                    compareBy<AssociationFieldModel>(
                        { it.associatedClassName },
                        { it.selfPropertyName },
                        { it.associationType },
                        { it.classHoldsForeignKey.orEmpty() },
                        { it.reversePropertyName.orEmpty() }
                    )
                )
            entity.copy(associationFields = relations)
        }
    }

    private fun buildAssociations(
        entity: EntityModel,
        entityByClass: Map<String, EntityModel>
    ): List<AssociationFieldModel> {
        val relations = LinkedHashMap<String, AssociationFieldModel>()
        for (property in entity.declaredProperties.sortedBy { it.propertyName }) {
            val propertyCollectionType = property.collectionType
            val propertyElementType = property.collectionElementTypeName
            val directType = property.normalizedTypeName
            if (propertyCollectionType != null && propertyElementType != null) {
                val target = propertyElementType
                if (!entityByClass.containsKey(target)) {
                    continue
                }
                if (target == entity.className) {
                    // self collection is backed by generic table metadata, not association rows.
                    continue
                }
                val reverse = findReverseProperty(entity.className, entityByClass[target])
                val (associationType, classHoldsForeignKey) = when {
                    reverse?.collectionType != null -> MANY_TO_MANY to null
                    reverse != null -> MANY_TO_ONE to target
                    else -> MANY_TO_ONE to target
                }
                val relation = AssociationFieldModel(
                    associatedClassName = target,
                    associationType = associationType,
                    classHoldsForeignKey = classHoldsForeignKey,
                    selfPropertyName = property.propertyName,
                    selfCollectionType = propertyCollectionType,
                    reversePropertyName = reverse?.propertyName,
                    reverseCollectionType = reverse?.collectionType
                )
                relations["${relation.associatedClassName}#${relation.selfPropertyName}"] = relation
                continue
            }
            if (!entityByClass.containsKey(directType)) {
                continue
            }
            if (directType == entity.className) {
                continue
            }
            val reverse = findReverseProperty(entity.className, entityByClass[directType])
            val (associationType, classHoldsForeignKey) = when {
                reverse?.collectionType != null -> MANY_TO_ONE to entity.className
                else -> ONE_TO_ONE to directType
            }
            val relation = AssociationFieldModel(
                associatedClassName = directType,
                associationType = associationType,
                classHoldsForeignKey = classHoldsForeignKey,
                selfPropertyName = property.propertyName,
                selfCollectionType = null,
                reversePropertyName = reverse?.propertyName,
                reverseCollectionType = reverse?.collectionType
            )
            relations["${relation.associatedClassName}#${relation.selfPropertyName}"] = relation
        }
        return relations.values.toList()
    }

    private fun findReverseProperty(
        sourceClassName: String,
        targetEntity: EntityModel?
    ): PropertyModel? {
        if (targetEntity == null) {
            return null
        }
        val properties = targetEntity.declaredProperties.sortedBy { it.propertyName }
        val collectionMatch = properties.firstOrNull { property ->
            property.collectionType != null && property.collectionElementTypeName == sourceClassName
        }
        if (collectionMatch != null) {
            return collectionMatch
        }
        return properties.firstOrNull { property ->
            property.collectionType == null && property.normalizedTypeName == sourceClassName
        }
    }
}
