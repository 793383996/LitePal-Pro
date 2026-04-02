package org.litepal.generated

import android.database.Cursor
import org.litepal.crud.LitePalSupport

interface CursorMapper<T : LitePalSupport> {
    fun mapFromCursor(model: T, cursor: Cursor)
}
