package org.litepal.litepalsample

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.GeneratedMetadataMode
import org.litepal.LitePal
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song
import org.litepal.util.DBUtility
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SampleCrudIntegrationInstrumentationTest {

    @Before
    fun setUp() {
        LitePal.resetRuntimeMetrics()
    }

    @After
    fun tearDown() {
        LitePal.resetRuntimeMetrics()
    }

    @Test
    fun generatedRequiredMode_shouldOpenDatabaseAndExposeSampleTables() {
        val options = LitePal.getRuntimeOptions()
        assertEquals(GeneratedMetadataMode.REQUIRED, options.generatedMetadataMode)
        val db = LitePal.getDatabase()
        assertNotNull(db)

        val normalizedTables = DBUtility.findAllTableNames(db)
            .map { table -> table.lowercase(Locale.US) }
            .toSet()
        assertTrue(normalizedTables.contains("singer"))
        assertTrue(normalizedTables.contains("album"))
        assertTrue(normalizedTables.contains("song"))

        assertTrue(LitePal.count(Singer::class.java) >= 0)
        assertTrue(LitePal.count(Album::class.java) >= 0)
        assertTrue(LitePal.count(Song::class.java) >= 0)
    }

    @Test
    fun runtimeMetrics_shouldBeReadableAndResettableAfterQueries() {
        LitePal.resetRuntimeMetrics()

        LitePal.count(Singer::class.java)
        LitePal.findFirst(Album::class.java)
        LitePal.findAll(Song::class.java, true)

        val generatedHits = LitePal.getGeneratedPathHitCount()
        val reflectionFallbacks = LitePal.getReflectionFallbackCount()
        val mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs()

        assertTrue(generatedHits >= 0L)
        assertTrue(reflectionFallbacks >= 0L)
        assertTrue(mainThreadBlockMs >= 0L)

        LitePal.resetRuntimeMetrics()

        assertEquals(0L, LitePal.getGeneratedPathHitCount())
        assertEquals(0L, LitePal.getReflectionFallbackCount())
        assertEquals(0L, LitePal.getMainThreadDbBlockTotalMs())
    }

    @Test
    fun eagerReadApi_shouldBeInvokableInComposeSampleRuntime() {
        val allSingers = LitePal.findAll(Singer::class.java, true)
        assertNotNull(allSingers)
        assertFalse(allSingers.any { singer -> singer.name.isNullOrBlank() && singer.id < 0 })

        val firstAlbum = LitePal.findFirst(Album::class.java, true)
        if (firstAlbum != null) {
            assertNotNull(firstAlbum.songs)
            if (firstAlbum.singer != null) {
                assertTrue(firstAlbum.singer!!.id >= 0)
            }
        }
    }
}
