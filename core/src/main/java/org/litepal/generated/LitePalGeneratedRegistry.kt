package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface LitePalGeneratedRegistry {
    val schemaVersion: Int
    val schemaJson: String
    val schemaHash: String
    val anchorClassName: String
    val anchorEntities: List<String>
    fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>>
}
