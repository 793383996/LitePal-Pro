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
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song
import org.litepal.util.DBUtility
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SampleCrudIntegrationInstrumentationTest {

    private lateinit var testDbName: String

    @Before
    fun setUp() {
        testDbName = "sample_crud_it_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(testDbName))
        LitePal.getDatabase()
        LitePal.resetRuntimeMetrics()
    }

    @After
    fun tearDown() {
        LitePal.useDefault()
        LitePal.deleteDatabase(testDbName)
        LitePal.resetRuntimeMetrics()
    }

    @Test
    fun generatedRequiredMode_shouldOpenDatabaseAndExposeSampleTables() {
        val options = LitePal.getRuntimeOptions()
        assertNotNull(options)
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

    @Test
    fun aggregateApi_shouldSupportBoxedNumberTypes() {
        val prefix = "__sample_boxed_aggregate_${System.currentTimeMillis()}"
        try {
            Singer().apply {
                name = "${prefix}_1"
                age = 20
                isMale = true
            }.save()
            Singer().apply {
                name = "${prefix}_2"
                age = 30
                isMale = false
            }.save()

            val where = LitePal.where("name like ?", "${prefix}_%")
            val max = where.max(Singer::class.java, "age", Int::class.javaObjectType)
            val min = where.min(Singer::class.java, "age", Int::class.javaObjectType)
            val sum = where.sum(Singer::class.java, "age", Int::class.javaObjectType)
            val avg = where.average(Singer::class.java, "age")

            assertEquals(30, max.toInt())
            assertEquals(20, min.toInt())
            assertEquals(50, sum.toInt())
            assertTrue(avg in 24.99..25.01)
        } finally {
            LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_%")
        }
    }
}
