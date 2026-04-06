package com.litepaltest.test.stress

import androidx.test.filters.LargeTest
import org.junit.AfterClass
import org.junit.Ignore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.extension.saveAll
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song
import java.util.Date
import java.util.Random
import java.util.UUID

@LargeTest
@Ignore("Disabled heavy stress suite for interactive sample/device runs.")
class StressAssociationLargeTest {

    @Test(timeout = 600_000)
    fun testAssociationHighVolume() {
        val seed = 2026040211L
        val batchId = newBatchId("association_high_volume")
        StressTestReporter.runCase(SUITE, "association_high_volume", seed, batchId) {
            val prefix = "__stress_assoc_${batchId}"
            try {
                val singerCount = 30
                val albumPerSinger = 4
                val songPerAlbum = 5

                StressTestReporter.logProgress(
                    SUITE,
                    "association_high_volume",
                    "seeded_plan",
                    "singers=$singerCount,albumPerSinger=$albumPerSinger,songPerAlbum=$songPerAlbum"
                )

                val singers = (0 until singerCount).map { index ->
                    Singer().apply {
                        name = "${prefix}_singer_$index"
                        age = 18 + index % 35
                        isMale = index % 2 == 0
                    }
                }
                assertTrue(singers.saveAll())
                StressTestReporter.logProgress(SUITE, "association_high_volume", "singers_saved", "count=${singers.size}")

                val albums = mutableListOf<Album>()
                for (singerIndex in singers.indices) {
                    val singer = singers[singerIndex]
                    for (albumIndex in 0 until albumPerSinger) {
                        albums.add(
                            Album().apply {
                                name = "${prefix}_album_${singerIndex}_$albumIndex"
                                sales = 100 + albumIndex
                                publisher = "${prefix}_publisher"
                                price = 29.9 + albumIndex
                                serial = "${prefix}_serial_${singerIndex}_$albumIndex"
                                release = Date()
                                this.singer = singer
                            }
                        )
                    }
                }
                assertTrue(albums.saveAll())
                StressTestReporter.logProgress(SUITE, "association_high_volume", "albums_saved", "count=${albums.size}")

                val songs = mutableListOf<Song>()
                for (albumIndex in albums.indices) {
                    val album = albums[albumIndex]
                    for (songIndex in 0 until songPerAlbum) {
                        songs.add(
                            Song().apply {
                                name = "${prefix}_song_${albumIndex}_$songIndex"
                                lyric = "${prefix}_lyric_${albumIndex}_$songIndex"
                                duration = "03:${(10 + songIndex).toString().padStart(2, '0')}"
                                this.album = album
                            }
                        )
                    }
                }
                val songBatchSize = 200
                var start = 0
                var batchIndex = 0
                val totalBatches = (songs.size + songBatchSize - 1) / songBatchSize
                while (start < songs.size) {
                    val end = minOf(start + songBatchSize, songs.size)
                    batchIndex++
                    StressTestReporter.logProgress(
                        SUITE,
                        "association_high_volume",
                        "songs_save_batch",
                        "batch=$batchIndex/$totalBatches,size=${end - start}"
                    )
                    assertTrue(songs.subList(start, end).saveAll())
                    start = end
                }
                StressTestReporter.logProgress(SUITE, "association_high_volume", "songs_saved", "count=${songs.size}")

                val expectedAlbumCount = singerCount * albumPerSinger
                val expectedSongCount = expectedAlbumCount * songPerAlbum

                assertEquals(
                    singerCount,
                    LitePal.where("name like ?", "${prefix}_singer_%").count(Singer::class.java)
                )
                assertEquals(
                    expectedAlbumCount,
                    LitePal.where("name like ?", "${prefix}_album_%").count(Album::class.java)
                )
                assertEquals(
                    expectedSongCount,
                    LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
                )

                val eagerAlbum = LitePal.where("name like ?", "${prefix}_album_%")
                    .order("id asc")
                    .findFirst(Album::class.java, true)
                assertNotNull(eagerAlbum)
                assertNotNull(eagerAlbum?.singer)
            } finally {
                cleanup(prefix)
            }
        }
    }

    @Test
    fun testAssociationEagerLookupWithSeed() {
        val seed = 2026040212L
        val batchId = newBatchId("association_eager_lookup")
        StressTestReporter.runCase(SUITE, "association_eager_lookup", seed, batchId) {
            val prefix = "__stress_assoc_${batchId}"
            try {
                val random = Random(seed)
                val singerCount = 20
                val albumPerSinger = 3
                val songPerAlbum = 4

                val singers = (0 until singerCount).map { index ->
                    Singer().apply {
                        name = "${prefix}_singer_$index"
                        age = 20 + index
                        isMale = index % 2 == 0
                    }
                }
                assertTrue(singers.saveAll())

                val albums = mutableListOf<Album>()
                for (singerIndex in singers.indices) {
                    val singer = singers[singerIndex]
                    for (albumIndex in 0 until albumPerSinger) {
                        albums.add(
                            Album().apply {
                                name = "${prefix}_album_${singerIndex}_$albumIndex"
                                sales = 50 + albumIndex
                                publisher = "${prefix}_pub"
                                price = 15.0 + albumIndex
                                serial = "${prefix}_serial_${singerIndex}_$albumIndex"
                                release = Date()
                                this.singer = singer
                            }
                        )
                    }
                }
                assertTrue(albums.saveAll())

                val songs = mutableListOf<Song>()
                for (albumIndex in albums.indices) {
                    val album = albums[albumIndex]
                    for (songIndex in 0 until songPerAlbum) {
                        songs.add(
                            Song().apply {
                                name = "${prefix}_song_${albumIndex}_$songIndex"
                                lyric = "${prefix}_lyric_${albumIndex}_$songIndex"
                                duration = "02:${(20 + songIndex).toString().padStart(2, '0')}"
                                this.album = album
                            }
                        )
                    }
                }
                assertTrue(songs.saveAll())

                repeat(10) {
                    val albumPick = albums[random.nextInt(albums.size)]
                    val eagerAlbum = LitePal.find(Album::class.java, albumPick.id, true)
                    assertNotNull(eagerAlbum)
                    assertNotNull(eagerAlbum?.singer)
                }

                val firstSinger = singers.first()
                val count = LitePal.where("singer_id = ?", firstSinger.id.toString()).count("album")
                assertEquals(albumPerSinger, count)
            } finally {
                cleanup(prefix)
            }
        }
    }

    private fun cleanup(prefix: String) {
        LitePal.deleteAll(Song::class.java, "name like ?", "${prefix}_song_%")
        LitePal.deleteAll(Song::class.java, "lyric like ?", "${prefix}_lyric_%")
        LitePal.deleteAll(Album::class.java, "name like ?", "${prefix}_album_%")
        LitePal.deleteAll(Album::class.java, "serial like ?", "${prefix}_serial_%")
        LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_singer_%")
    }

    private fun newBatchId(caseName: String): String {
        return "${caseName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val SUITE = "StressAssociationLargeTest"

        @JvmStatic
        @AfterClass
        fun afterClassSummary() {
            StressTestReporter.logSuiteSummary(SUITE)
            StressTestReporter.logGlobalSummary()
        }
    }
}
