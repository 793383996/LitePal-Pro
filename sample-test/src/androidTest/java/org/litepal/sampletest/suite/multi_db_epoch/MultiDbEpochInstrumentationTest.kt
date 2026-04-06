package org.litepal.sampletest.suite.multi_db_epoch

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.litepalsample.model.Singer
import org.litepal.sampletest.SampleTestRuntimeBootstrap

@MediumTest
@RunWith(AndroidJUnit4::class)
class MultiDbEpochInstrumentationTest {

    private lateinit var defaultDbName: String
    private lateinit var epochDbName: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
        defaultDbName = "sample_multi_db_default_${System.currentTimeMillis()}"
        epochDbName = "sample_multi_db_epoch_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(defaultDbName))
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePal.useDefault()
        LitePal.deleteDatabase(defaultDbName)
        LitePal.deleteDatabase(epochDbName)
    }

    @Test(timeout = 180_000)
    fun switchDatabase_shouldIsolateDataBetweenDefaultAndEpoch() {
        val marker = "__multi_db_marker_${System.currentTimeMillis()}"

        LitePal.use(LitePalDB.fromDefault(defaultDbName))
        Singer().apply {
            name = "${marker}_default"
            age = 22
            isMale = true
        }.save()
        assertEquals(1, LitePal.where("name = ?", "${marker}_default").count(Singer::class.java))
        assertEquals(0, LitePal.where("name = ?", "${marker}_epoch").count(Singer::class.java))

        LitePal.use(LitePalDB.fromDefault(epochDbName))
        LitePal.getDatabase()
        Singer().apply {
            name = "${marker}_epoch"
            age = 30
            isMale = false
        }.save()
        assertEquals(1, LitePal.where("name = ?", "${marker}_epoch").count(Singer::class.java))
        assertEquals(0, LitePal.where("name = ?", "${marker}_default").count(Singer::class.java))

        LitePal.use(LitePalDB.fromDefault(defaultDbName))
        assertEquals(1, LitePal.where("name = ?", "${marker}_default").count(Singer::class.java))
        assertEquals(0, LitePal.where("name = ?", "${marker}_epoch").count(Singer::class.java))
    }

    @Test(timeout = 180_000)
    fun deleteEpochDatabase_shouldNotAffectDefaultData() {
        val marker = "__multi_db_delete_${System.currentTimeMillis()}"

        LitePal.use(LitePalDB.fromDefault(defaultDbName))
        Singer().apply {
            name = marker
            age = 25
            isMale = true
        }.save()
        assertEquals(1, LitePal.where("name = ?", marker).count(Singer::class.java))

        LitePal.use(LitePalDB.fromDefault(epochDbName))
        LitePal.getDatabase()
        Singer().apply {
            name = marker
            age = 26
            isMale = false
        }.save()
        assertEquals(1, LitePal.where("name = ?", marker).count(Singer::class.java))

        LitePal.useDefault()
        LitePal.deleteDatabase(epochDbName)

        LitePal.use(LitePalDB.fromDefault(defaultDbName))
        assertEquals(1, LitePal.where("name = ?", marker).count(Singer::class.java))
        LitePal.deleteAll(Singer::class.java, "name = ?", marker)
    }
}
