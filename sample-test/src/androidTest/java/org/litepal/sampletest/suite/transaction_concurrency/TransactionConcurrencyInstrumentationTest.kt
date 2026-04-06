package org.litepal.sampletest.suite.transaction_concurrency

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.extension.runInTransaction
import org.litepal.litepalsample.model.Singer
import org.litepal.sampletest.SampleTestRuntimeBootstrap
import java.util.concurrent.atomic.AtomicInteger

@MediumTest
@RunWith(AndroidJUnit4::class)
class TransactionConcurrencyInstrumentationTest {

    private lateinit var testDbName: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
        testDbName = "sample_tx_concurrency_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(testDbName))
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePal.useDefault()
        LitePal.deleteDatabase(testDbName)
    }

    @Test(timeout = 300_000)
    fun runInTransaction_shouldCommitAndRollbackDeterministically() {
        val prefix = "__suite_tx_${System.currentTimeMillis()}"
        try {
            val commitResult = LitePal.runInTransaction {
                Singer().apply {
                    name = "${prefix}_commit"
                    age = 20
                    isMale = true
                }.save()
            }
            assertTrue(commitResult)
            assertEquals(1, LitePal.where("name = ?", "${prefix}_commit").count(Singer::class.java))

            val rollbackResult = LitePal.runInTransaction {
                Singer().apply {
                    name = "${prefix}_rollback"
                    age = 31
                    isMale = false
                }.save()
                false
            }
            assertTrue(!rollbackResult)
            assertEquals(0, LitePal.where("name = ?", "${prefix}_rollback").count(Singer::class.java))
        } finally {
            LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_%")
        }
    }

    @Test(timeout = 300_000)
    fun concurrentReadWrite_shouldKeepMonotonicCountAndFinalConsistency() = runBlocking {
        val prefix = "__suite_concurrent_${System.currentTimeMillis()}"
        val writerCoroutines = 3
        val readerCoroutines = 2
        val writesPerWriter = 50
        val readLoops = 40
        val expectedWrites = writerCoroutines * writesPerWriter
        val written = AtomicInteger(0)
        val reads = AtomicInteger(0)

        try {
            withTimeout(120_000) {
                coroutineScope {
                    val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
                    repeat(writerCoroutines) { writerIndex ->
                        jobs += async(Dispatchers.IO) {
                            for (i in 0 until writesPerWriter) {
                                Singer().apply {
                                    name = "${prefix}_w${writerIndex}_$i"
                                    age = 18 + (i % 40)
                                    isMale = (writerIndex + i) % 2 == 0
                                }.save()
                                written.incrementAndGet()
                                if (i % 10 == 0) {
                                    delay(1L)
                                }
                            }
                        }
                    }
                    repeat(readerCoroutines) { readerIndex ->
                        jobs += async(Dispatchers.IO) {
                            var previous = 0
                            for (i in 0 until readLoops) {
                                val current = LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
                                assertTrue("reader=$readerIndex observed non-monotonic count", current >= previous)
                                previous = current
                                reads.incrementAndGet()
                                if (i % 8 == 0) {
                                    delay(2L)
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            val actual = LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
            assertEquals(expectedWrites, written.get())
            assertEquals(expectedWrites, actual)
            assertEquals(readerCoroutines * readLoops, reads.get())
        } finally {
            LitePal.deleteAll(Singer::class.java, "name like ?", "${prefix}_w%")
        }
    }
}
