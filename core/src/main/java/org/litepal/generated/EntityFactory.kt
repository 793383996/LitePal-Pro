package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface EntityFactory<T : LitePalSupport> {
    fun newInstance(): T
}
