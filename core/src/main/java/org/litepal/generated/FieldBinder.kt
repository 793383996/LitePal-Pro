package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface FieldBinder<in T : LitePalSupport> {
    fun bindForSave(model: T, put: (column: String, value: Any?) -> Unit)
    fun bindForUpdate(model: T, put: (column: String, value: Any?) -> Unit)
}
