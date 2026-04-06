package org.litepal.litepalsample

import org.litepal.LitePal
import org.litepal.extension.findAll
import org.litepal.extension.runInTransaction
import org.litepal.litepalsample.model.Song

/**
 * Kotlin 快速入门片段（Kotlin quickstart snippet），用于 4.0 迁移阶段的 Sample 参考。
 */
object KotlinQuickstart {
    fun loadSongsInTransaction(): List<Song> {
        var songs: List<Song> = emptyList()
        // 事务核心流程（Transaction core flow）：仅当 lambda 返回 true 时提交事务。
        LitePal.runInTransaction {
            songs = LitePal.findAll<Song>()
            true
        }
        return songs
    }
}
