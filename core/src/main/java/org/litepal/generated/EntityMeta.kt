package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface EntityMeta<T : LitePalSupport> {
    val className: String
    val tableName: String
    val supportedFields: List<String>
    val supportedGenericFields: List<String>
    val persistedFields: List<GeneratedFieldMeta>
    val entityFactory: EntityFactory<T>?
    val fieldBinder: FieldBinder<T>?
    val cursorMapper: CursorMapper<T>?
    val idAccessor: IdAccessor<T>?
    val associationMeta: AssociationMeta?
    val propertyAccessor: PropertyAccessor<T>?
}
