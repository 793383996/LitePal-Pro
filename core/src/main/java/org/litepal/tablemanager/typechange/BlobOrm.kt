package org.litepal.tablemanager.typechange

/**
 * This class deals with byte type.
 */
class BlobOrm : OrmChange() {
    override fun object2Relation(fieldType: String?): String? {
        if (fieldType == null) return null
        if (fieldType == "[B") return "blob"
        return null
    }
}
