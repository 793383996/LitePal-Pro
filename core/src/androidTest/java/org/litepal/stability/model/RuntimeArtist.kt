package org.litepal.stability.model

import org.litepal.crud.LitePalSupport

class RuntimeArtist : LitePalSupport() {
    var name: String? = null
    var albums: MutableList<RuntimeAlbum>? = null
}
