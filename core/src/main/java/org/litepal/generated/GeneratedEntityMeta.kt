package org.litepal.generated

import org.litepal.crud.LitePalSupport

data class GeneratedEntityMeta(
    override val className: String,
    override val tableName: String,
    override val supportedFields: List<String>,
    override val supportedGenericFields: List<String>,
    override val persistedFields: List<GeneratedFieldMeta> = emptyList(),
    override val entityFactory: EntityFactory<LitePalSupport>? = null,
    override val fieldBinder: FieldBinder<LitePalSupport>? = null,
    override val cursorMapper: CursorMapper<LitePalSupport>? = null,
    override val associationMeta: AssociationMeta? = null
) : EntityMeta<LitePalSupport>
