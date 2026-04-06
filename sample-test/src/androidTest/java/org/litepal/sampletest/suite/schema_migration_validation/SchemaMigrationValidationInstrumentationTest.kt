package org.litepal.sampletest.suite.schema_migration_validation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.litepalsample.model.Singer
import org.litepal.sampletest.SampleTestRuntimeBootstrap
import org.litepal.util.DBUtility
import java.util.Locale

@MediumTest
@RunWith(AndroidJUnit4::class)
class SchemaMigrationValidationInstrumentationTest {

    private lateinit var dbName: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
        dbName = "sample_schema_validation_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(dbName))
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePal.useDefault()
        LitePal.deleteDatabase(dbName)
    }

    @Test(timeout = 180_000)
    fun generatedSchema_shouldCreateCoreTablesAndColumns() {
        val db = LitePal.getDatabase()
        val tables = DBUtility.findAllTableNames(db)
            .map { table -> table.lowercase(Locale.US) }
            .toSet()
        assertTrue(tables.contains("singer"))
        assertTrue(tables.contains("album"))
        assertTrue(tables.contains("song"))

        val songTable = DBUtility.findPragmaTableInfo("song", db)
        val columnNames = songTable.getColumnModels()
            .map { model -> model.getColumnName().orEmpty().lowercase(Locale.US) }
            .toSet()
        assertTrue(columnNames.contains("id"))
        assertTrue(columnNames.contains("name"))
        assertTrue(columnNames.contains("lyric"))
        assertTrue(columnNames.contains("duration"))
    }

    @Test(timeout = 180_000)
    fun generatedPath_shouldSupportCrudAndAggregateWithoutFallbackFailures() {
        val prefix = "__schema_validation_${System.currentTimeMillis()}"
        try {
            repeat(3) { index ->
                Singer().apply {
                    name = "${prefix}_$index"
                    age = 20 + index
                    isMale = index % 2 == 0
                }.save()
            }

            val count = LitePal.where("name like ?", "${prefix}_%").count(Singer::class.java)
            val max = LitePal.where("name like ?", "${prefix}_%").max(Singer::class.java, "age", Int::class.javaObjectType)
            val min = LitePal.where("name like ?", "${prefix}_%").min(Singer::class.java, "age", Int::class.javaObjectType)
            val avg = LitePal.where("name like ?", "${prefix}_%").average(Singer::class.java, "age")

            assertEquals(3, count)
            assertEquals(22, max)
            assertEquals(20, min)
            assertTrue(avg in 20.99..21.01)
        } finally {
            LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_%")
        }
    }
}
