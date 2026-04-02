package com.litepaltest.test.stress

import androidx.test.filters.MediumTest
import com.litepaltest.model.Book
import com.litepaltest.model.Product
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.litepalsample.model.Singer
import org.litepal.util.DBUtility
import java.util.UUID

@MediumTest
class MultiDatabaseStressTest {

    @Test
    fun testMultiDatabaseIsolationStress() {
        val seed = 2026040241L
        val batchId = newBatchId("multi_database_stress")
        StressTestReporter.runCase(SUITE, "multi_database_stress", seed, batchId) {
            val defaultPrefix = "__mdb_default_${batchId}_"
            val db2Prefix = "__mdb_db2_${batchId}_"
            val db3Prefix = "__mdb_db3_${batchId}_"
            val db2Name = "db2_stress_${System.currentTimeMillis()}"
            val db3Name = "db3_stress_${System.currentTimeMillis()}"
            val rounds = 200
            val db2Config = LitePalDB(db2Name, 1).apply {
                addClassName(Product::class.java.name)
            }
            val db3Config = LitePalDB(db3Name, 1).apply {
                addClassName(Book::class.java.name)
            }

            LitePal.deleteDatabase(db2Name)
            LitePal.deleteDatabase(db3Name)
            try {
                repeat(rounds) { index ->
                    LitePal.useDefault()
                    val singer = Singer().apply {
                        name = "${defaultPrefix}${index}"
                        age = 18 + index % 50
                        isMale = index % 2 == 0
                    }
                    assertTrue(singer.save())
                    assertEquals(index + 1, LitePal.where("name like ?", "${defaultPrefix}%").count(Singer::class.java))

                    LitePal.use(db2Config)
                    LitePal.getDatabase()
                    val product = Product().apply {
                        brand = "${db2Prefix}${index}"
                        price = 100.0 + index
                    }
                    assertTrue(product.save())
                    assertEquals(index + 1, LitePal.where("brand like ?", "${db2Prefix}%").count(Product::class.java))
                    assertFalse(
                        DBUtility.isTableExists(
                            DBUtility.getTableNameByClassName(Book::class.java.name),
                            LitePal.getDatabase()
                        )
                    )
                    assertEquals(0, countSingerPrefixIfTableExists(defaultPrefix))

                    LitePal.use(db3Config)
                    LitePal.getDatabase()
                    val book = Book().apply {
                        bookName = "${db3Prefix}${index}"
                        pages = 100 + index
                        price = 10.5 + index
                    }
                    assertTrue(book.save())
                    assertEquals(index + 1, LitePal.where("bookname like ?", "${db3Prefix}%").count(Book::class.java))
                    assertFalse(
                        DBUtility.isTableExists(
                            DBUtility.getTableNameByClassName(Product::class.java.name),
                            LitePal.getDatabase()
                        )
                    )
                    assertEquals(0, countSingerPrefixIfTableExists(defaultPrefix))
                }

                LitePal.useDefault()
                assertEquals(rounds, LitePal.where("name like ?", "${defaultPrefix}%").count(Singer::class.java))

                LitePal.use(db2Config)
                assertEquals(rounds, LitePal.where("brand like ?", "${db2Prefix}%").count(Product::class.java))

                LitePal.use(db3Config)
                assertEquals(rounds, LitePal.where("bookname like ?", "${db3Prefix}%").count(Book::class.java))
            } finally {
                LitePal.useDefault()
                LitePal.deleteAll(Singer::class.java, "name like ?", "${defaultPrefix}%")
                LitePal.deleteDatabase(db2Name)
                LitePal.deleteDatabase(db3Name)
                LitePal.useDefault()
            }
        }
    }

    private fun countSingerPrefixIfTableExists(prefix: String): Int {
        val db = LitePal.getDatabase()
        val singerTable = DBUtility.getTableNameByClassName(Singer::class.java.name)
        if (!DBUtility.isTableExists(singerTable, db)) {
            return 0
        }
        return LitePal.where("name like ?", "${prefix}%").count(Singer::class.java)
    }

    private fun newBatchId(caseName: String): String {
        return "${caseName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val SUITE = "MultiDatabaseStressTest"

        @JvmStatic
        @AfterClass
        fun afterClassSummary() {
            StressTestReporter.logSuiteSummary(SUITE)
            StressTestReporter.logGlobalSummary()
        }
    }
}
