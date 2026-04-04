package org.litepal.stability

import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.stability.model.RuntimeAlbum
import org.litepal.stability.model.RuntimeArtist
import org.litepal.stability.model.RuntimeUser

@LitePalSchemaAnchor(
    version = 1,
    entities = [
        RuntimeUser::class,
        RuntimeArtist::class,
        RuntimeAlbum::class
    ]
)
class CoreAndroidTestSchemaAnchor
