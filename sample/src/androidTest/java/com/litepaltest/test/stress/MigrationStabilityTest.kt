package com.litepaltest.test.stress

import androidx.test.filters.MediumTest
import com.litepaltest.model.Cellphone
import com.litepaltest.model.Headset
import com.litepaltest.model.Product
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalRuntime
import org.litepal.extension.saveAll
import org.litepal.util.DBUtility
import java.util.UUID

@MediumTest
class MigrationStabilityTest {

    @Test
    fun testDbMigrateStress() {
        val seed = 2026040231L
        val batchId = newBatchId("db_migrate_stress")
        StressTestReporter.runCase(SUITE, "db_migrate_stress", seed, batchId) {
            val prefix = "__migrate_${batchId}"
            val dbName = "db_migrate_stress_${System.currentTimeMillis()}"
            LitePal.deleteDatabase(dbName)
            try {
                val v1 = LitePalDB(dbName, 1).apply {
                    addClassName(Product::class.java.name)
                    addClassName(Cellphone::class.java.name)
                }
                LitePal.use(v1)
                LitePal.getDatabase()

                val products = (0 until 60).map { index ->
                    Product().apply {
                        brand = "${prefix}_product_$index"
                        price = 99.0 + index
                    }
                }
                assertTrue(products.saveAll())
                val serial = "${prefix}_serial_unique"
                val baselineCell = Cellphone().apply {
                    brand = "${prefix}_brand"
                    inStock = 'Y'
                    price = 1024.0
                    this.serial = serial
                }
                assertTrue(baselineCell.save())

                assertEquals(
                    60,
                    LitePal.where("brand like ?", "${prefix}_product_%").count(Product::class.java)
                )
                assertEquals(
                    1,
                    LitePal.where("serial = ?", serial).count(Cellphone::class.java)
                )

                val v2 = LitePalDB(dbName, 2).apply {
                    addClassName(Product::class.java.name)
                    addClassName(Cellphone::class.java.name)
                    addClassName(Headset::class.java.name)
                }
                LitePal.use(v2)
                val db = LitePal.getDatabase()
                assertTrue(DBUtility.isTableExists(DBUtility.getTableNameByClassName(Headset::class.java.name), db))

                assertEquals(
                    60,
                    LitePal.where("brand like ?", "${prefix}_product_%").count(Product::class.java)
                )
                assertEquals(
                    1,
                    LitePal.where("serial = ?", serial).count(Cellphone::class.java)
                )

                val tableName = DBUtility.getTableNameByClassName(Cellphone::class.java.name)
                val indexPair = DBUtility.findIndexedColumns(tableName, db)
                assertTrue(indexPair.second.contains("serial"))

                val conflictCell = Cellphone().apply {
                    brand = "${prefix}_brand_conflict"
                    inStock = 'N'
                    price = 2048.0
                    this.serial = serial
                }
                LitePalRuntime.withSilentErrorLog {
                    assertFalse(conflictCell.save())
                }

                val newCell = Cellphone().apply {
                    brand = "${prefix}_brand_new"
                    inStock = 'Y'
                    price = 4096.0
                    this.serial = "${prefix}_serial_new"
                }
                assertTrue(newCell.save())
                assertEquals(2, LitePal.where("serial like ?", "${prefix}_serial_%").count(Cellphone::class.java))
            } finally {
                LitePal.useDefault()
                LitePal.deleteDatabase(dbName)
            }
        }
    }

    private fun newBatchId(caseName: String): String {
        return "${caseName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val SUITE = "MigrationStabilityTest"

        @JvmStatic
        @AfterClass
        fun afterClassSummary() {
            StressTestReporter.logSuiteSummary(SUITE)
            StressTestReporter.logGlobalSummary()
        }
    }
}
