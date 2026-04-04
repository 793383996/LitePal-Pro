package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface IdAccessor<in T : LitePalSupport> {
    fun setId(model: T, id: Long)
}
