package org.litepal.litepalsample

import org.litepal.LitePal
import org.litepal.extension.findAll
import org.litepal.extension.runInTransaction
import org.litepal.litepalsample.model.Song

/**
 * Kotlin-only quickstart snippet kept in sample for 4.0 migration.
 */
object KotlinQuickstart {
    fun loadSongsInTransaction(): List<Song> {
        var songs: List<Song> = emptyList()
        LitePal.runInTransaction {
            songs = LitePal.findAll<Song>()
            true
        }
        return songs
    }
}
