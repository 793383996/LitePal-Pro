package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface PropertyAccessor<in T : LitePalSupport> {
    fun get(model: T, propertyName: String): Any?
    fun set(model: T, propertyName: String, value: Any?)
}
