package org.litepal.sampletest.test.stress

import androidx.test.filters.LargeTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.LitePalRuntime
import org.litepal.extension.runInTransaction
import org.litepal.extension.saveAll
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song
import java.util.UUID

@LargeTest
class StressTransactionLargeTest {

    @Test(timeout = 360_000)
    fun testTransactionRepeatCommitRollback() {
        val seed = 2026040221L
        val batchId = newBatchId("tx_repeat_commit_rollback")
        StressTestReporter.runCase(SUITE, "tx_repeat_commit_rollback", seed, batchId) {
            val prefix = "__stress_tx_${batchId}"
            try {
                val repeat = 400
                var committedExpected = 0
                for (i in 0 until repeat) {
                    val shouldCommit = i % 2 == 0
                    if (shouldCommit) {
                        committedExpected++
                    }
                    val result = LitePal.runInTransaction {
                        val singer = Singer().apply {
                            name = "${prefix}_singer_$i"
                            age = 18 + i % 40
                            isMale = i % 2 == 0
                        }
                        singer.save()
                        shouldCommit
                    }
                    assertEquals(shouldCommit, result)
                }
                val actual = LitePal.where("name like ?", "${prefix}_singer_%").count(Singer::class.java)
                assertEquals(committedExpected, actual)
            } finally {
                LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_singer_%")
            }
        }
    }

    @Test(timeout = 360_000)
    fun testTransactionUniqueConflictRollback() {
        val seed = 2026040222L
        val batchId = newBatchId("tx_unique_conflict_rollback")
        StressTestReporter.runCase(SUITE, "tx_unique_conflict_rollback", seed, batchId) {
            val prefix = "__stress_tx_${batchId}"
            try {
                val songs = (0 until 40).map { index ->
                    Song().apply {
                        name = "${prefix}_song_$index"
                        lyric = "${prefix}_lyric_${index % 10}"
                        duration = "03:20"
                    }
                }
                val result = LitePalRuntime.withSilentErrorLog {
                    LitePal.runInTransaction {
                        songs.saveAll()
                    }
                }
                assertFalse(result)
                val songRows = LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
                val lyricRows = LitePal.where("lyric like ?", "${prefix}_lyric_%").count(Song::class.java)
                assertEquals(0, songRows)
                assertEquals(0, lyricRows)
            } finally {
                LitePal.deleteAll(Song::class.java, "name like ?", "${prefix}_song_%")
                LitePal.deleteAll(Song::class.java, "lyric like ?", "${prefix}_lyric_%")
            }
        }
    }

    @Test(timeout = 360_000)
    fun testTransactionMixedCrudCommit() {
        val seed = 2026040223L
        val batchId = newBatchId("tx_mixed_crud_commit")
        StressTestReporter.runCase(SUITE, "tx_mixed_crud_commit", seed, batchId) {
            val prefix = "__stress_tx_${batchId}"
            try {
                val baseline = mutableListOf<Singer>()
                for (i in 0 until 200) {
                    baseline.add(
                        Singer().apply {
                            name = "${prefix}_base_$i"
                            age = 10
                            isMale = true
                        }
                    )
                }
                for (i in 0 until 100) {
                    baseline.add(
                        Singer().apply {
                            name = "${prefix}_base_${i + 200}"
                            age = 20
                            isMale = false
                        }
                    )
                }
                assertTrue(baseline.saveAll())

                val transactionResult = LitePal.runInTransaction {
                    val updater = Singer().apply { age = 77 }
                    val updated = updater.updateAll("name like ? and age = ?", "${prefix}_base_%", "10")
                    val deleted = LitePal.deleteAll(
                        Singer::class.java,
                        "name like ? and age = ?",
                        "${prefix}_base_%",
                        "20"
                    )
                    val newRows = (0 until 80).map { i ->
                        Singer().apply {
                            name = "${prefix}_new_$i"
                            age = 31
                            isMale = i % 2 == 0
                        }
                    }
                    val inserted = newRows.saveAll()
                    updated == 200 && deleted == 100 && inserted
                }
                assertTrue(transactionResult)

                val updatedCount = LitePal.where("name like ? and age = ?", "${prefix}_base_%", "77")
                    .count(Singer::class.java)
                val baseCount = LitePal.where("name like ?", "${prefix}_base_%").count(Singer::class.java)
                val newCount = LitePal.where("name like ?", "${prefix}_new_%").count(Singer::class.java)
                val total = LitePal.where("name like ?", "${prefix}_%").count(Singer::class.java)

                assertEquals(200, updatedCount)
                assertEquals(200, baseCount)
                assertEquals(80, newCount)
                assertEquals(280, total)
            } finally {
                LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_%")
            }
        }
    }

    private fun newBatchId(caseName: String): String {
        return "${caseName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val SUITE = "StressTransactionLargeTest"

        @JvmStatic
        @AfterClass
        fun afterClassSummary() {
            StressTestReporter.logSuiteSummary(SUITE)
            StressTestReporter.logGlobalSummary()
        }
    }
}




