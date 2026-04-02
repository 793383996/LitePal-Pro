package org.litepal.stability.model

import org.litepal.crud.LitePalSupport

class RuntimeAlbum : LitePalSupport() {
    var name: String? = null
    var artist: RuntimeArtist? = null
    var tags: MutableList<String>? = null
}
