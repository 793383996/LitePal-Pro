package org.litepal.generated

import org.litepal.crud.LitePalSupport

interface EntityFactory<out T : LitePalSupport> {
    fun newInstance(): T
}
