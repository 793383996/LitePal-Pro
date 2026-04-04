package org.litepal.generated

data class AssociationFieldMeta(
    val associatedClassName: String,
    val associationType: Int,
    val classHoldsForeignKey: String?,
    val selfPropertyName: String,
    val selfCollectionType: String?,
    val reversePropertyName: String?,
    val reverseCollectionType: String?
)
