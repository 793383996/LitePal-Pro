package org.litepal.generated

import android.database.Cursor
import org.litepal.crud.LitePalSupport

data class AssociationMeta(
    val relations: List<AssociationFieldMeta> = emptyList(),
    val genericFields: List<GeneratedGenericFieldMeta> = emptyList()
)

data class AssociationFieldMeta(
    val associatedClassName: String,
    val associationType: Int,
    val classHoldsForeignKey: String?,
    val selfPropertyName: String,
    val selfCollectionType: String?,
    val reversePropertyName: String?,
    val reverseCollectionType: String?
)

data class GeneratedGenericFieldMeta(
    val propertyName: String,
    val elementTypeName: String,
    val collectionType: String,
    val encryptAlgorithm: String?
)

interface EntityFactory<out T : LitePalSupport> {
    fun newInstance(): T
}

interface FieldBinder<in T : LitePalSupport> {
    fun bindForSave(model: T, put: (column: String, value: Any?) -> Unit)
    fun bindForUpdate(model: T, put: (column: String, value: Any?) -> Unit)
}

interface CursorMapper<in T : LitePalSupport> {
    fun mapFromCursor(model: T, cursor: Cursor)
}

interface IdAccessor<in T : LitePalSupport> {
    fun setId(model: T, id: Long)
}

interface PropertyAccessor<in T : LitePalSupport> {
    fun get(model: T, propertyName: String): Any?
    fun set(model: T, propertyName: String, value: Any?)
}

data class GeneratedFieldMeta(
    val propertyName: String,
    val columnName: String,
    val typeName: String,
    val columnType: String,
    val nullable: Boolean,
    val unique: Boolean,
    val indexed: Boolean,
    val defaultValue: String,
    val encryptAlgorithm: String?
)

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

data class GeneratedEntityMeta(
    override val className: String,
    override val tableName: String,
    override val supportedFields: List<String>,
    override val supportedGenericFields: List<String>,
    override val persistedFields: List<GeneratedFieldMeta> = emptyList(),
    override val entityFactory: EntityFactory<LitePalSupport>? = null,
    override val fieldBinder: FieldBinder<LitePalSupport>? = null,
    override val cursorMapper: CursorMapper<LitePalSupport>? = null,
    override val idAccessor: IdAccessor<LitePalSupport>? = null,
    override val associationMeta: AssociationMeta? = null,
    override val propertyAccessor: PropertyAccessor<LitePalSupport>? = null
) : EntityMeta<LitePalSupport>

interface LitePalGeneratedRegistry {
    val schemaVersion: Int
    val schemaJson: String
    val schemaHash: String
    val anchorClassName: String
    val anchorEntities: List<String>
    fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>>
}
