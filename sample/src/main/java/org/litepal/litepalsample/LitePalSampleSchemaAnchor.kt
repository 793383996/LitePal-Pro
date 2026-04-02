package org.litepal.litepalsample

import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song

@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class LitePalSampleSchemaAnchor
