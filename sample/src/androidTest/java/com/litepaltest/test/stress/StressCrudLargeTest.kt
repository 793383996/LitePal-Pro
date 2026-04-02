package com.litepaltest.test.stress

import androidx.test.filters.LargeTest
import com.litepaltest.model.Cellphone
import com.litepaltest.test.LitePalTestCase
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.extension.saveAll
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@LargeTest
class StressCrudLargeTest : LitePalTestCase() {

    @Test(timeout = 360_000)
    fun testBulkInsertAndPagedQuery() {
        val seed = 2026040201L
        val batchId = newBatchId("bulk_insert_paged_query")
        StressTestReporter.runCase(SUITE, "bulk_insert_paged_query", seed, batchId) {
            val prefix = "__stress_crud_${batchId}"
            try {
                val random = Random(seed)
                val total = 2000
                val cellphones = (0 until total).map { index ->
                    Cellphone().apply {
                        brand = "BRAND_${random.nextInt(5)}"
                        inStock = if (index % 2 == 0) 'Y' else 'N'
                        price = random.nextDouble() * 10000
                        serial = "${prefix}_s_$index"
                    }
                }
                val saveBatchSize = 250
                var start = 0
                var batchIndex = 0
                val totalBatches = (cellphones.size + saveBatchSize - 1) / saveBatchSize
                while (start < cellphones.size) {
                    val end = minOf(start + saveBatchSize, cellphones.size)
                    batchIndex++
                    StressTestReporter.logProgress(
                        SUITE,
                        "bulk_insert_paged_query",
                        "save_batch",
                        "batch=$batchIndex/$totalBatches,size=${end - start}"
                    )
                    assertTrue(cellphones.subList(start, end).saveAll())
                    start = end
                }
                val count = LitePal.where("serial like ?", "${prefix}_s_%").count(Cellphone::class.java)
                assertEquals(total, count)

                var offset = 0
                var queried = 0
                while (true) {
                    val page = LitePal.where("serial like ?", "${prefix}_s_%")
                        .order("id asc")
                        .limit(100)
                        .offset(offset)
                        .find(Cellphone::class.java)
                    if (page.isEmpty()) {
                        break
                    }
                    queried += page.size
                    offset += page.size
                }
                assertEquals(total, queried)
            } finally {
                LitePal.deleteAll(Cellphone::class.java, "serial like ?", "${prefix}_s_%")
            }
        }
    }

    @Test(timeout = 360_000)
    fun testBulkUpdateAndDelete() {
        val seed = 2026040202L
        val batchId = newBatchId("bulk_update_delete")
        StressTestReporter.runCase(SUITE, "bulk_update_delete", seed, batchId) {
            val prefix = "__stress_crud_${batchId}"
            try {
                val totalA = 1000
                val totalB = 600
                val cellphones = mutableListOf<Cellphone>()
                for (i in 0 until totalA) {
                    cellphones.add(
                        Cellphone().apply {
                            brand = "A"
                            inStock = 'Y'
                            price = 1000.0 + i
                            serial = "${prefix}_a_$i"
                        }
                    )
                }
                for (i in 0 until totalB) {
                    cellphones.add(
                        Cellphone().apply {
                            brand = "B"
                            inStock = 'N'
                            price = 2000.0 + i
                            serial = "${prefix}_b_$i"
                        }
                    )
                }
                val saveBatchSize = 250
                var start = 0
                var batchIndex = 0
                val totalBatches = (cellphones.size + saveBatchSize - 1) / saveBatchSize
                while (start < cellphones.size) {
                    val end = minOf(start + saveBatchSize, cellphones.size)
                    batchIndex++
                    StressTestReporter.logProgress(
                        SUITE,
                        "bulk_update_delete",
                        "save_batch",
                        "batch=$batchIndex/$totalBatches,size=${end - start}"
                    )
                    assertTrue(cellphones.subList(start, end).saveAll())
                    start = end
                }

                val updater = Cellphone().apply { brand = "UPDATED" }
                val rowsUpdated = updater.updateAll("serial like ? and brand = ?", "${prefix}_%", "A")
                assertEquals(totalA, rowsUpdated)

                val rowsDeleted = LitePal.deleteAll(
                    Cellphone::class.java,
                    "serial like ? and brand = ?",
                    "${prefix}_%",
                    "B"
                )
                assertEquals(totalB, rowsDeleted)

                val updatedCount = LitePal.where("serial like ? and brand = ?", "${prefix}_%", "UPDATED")
                    .count(Cellphone::class.java)
                val allCount = LitePal.where("serial like ?", "${prefix}_%").count(Cellphone::class.java)
                assertEquals(totalA, updatedCount)
                assertEquals(totalA, allCount)
            } finally {
                LitePal.deleteAll(Cellphone::class.java, "serial like ?", "${prefix}_%")
            }
        }
    }

    @Test(timeout = 360_000)
    fun testConcurrentReadWrite() {
        val seed = 2026040203L
        val batchId = newBatchId("concurrent_read_write")
        StressTestReporter.runCase(SUITE, "concurrent_read_write", seed, batchId) {
            val prefix = "__stress_crud_${batchId}"
            try {
                val writerThreads = 4
                val readerThreads = 3
                val writesPerThread = 80
                val readLoops = 60
                val expectedWrites = writerThreads * writesPerThread

                val pool = Executors.newFixedThreadPool(writerThreads + readerThreads)
                val startGate = CountDownLatch(1)
                val doneGate = CountDownLatch(writerThreads + readerThreads)
                val errors = ConcurrentLinkedQueue<Throwable>()
                val written = AtomicInteger(0)
                val reads = AtomicInteger(0)

                repeat(writerThreads) { writerIndex ->
                    pool.execute {
                        try {
                            startGate.await()
                            for (i in 0 until writesPerThread) {
                                val cellphone = Cellphone().apply {
                                    brand = "CONCURRENT"
                                    inStock = 'Y'
                                    price = 1.0 + i
                                    serial = "${prefix}_w${writerIndex}_$i"
                                }
                                if (!cellphone.save()) {
                                    throw IllegalStateException("writer save failed: writer=$writerIndex, i=$i")
                                }
                                val done = written.incrementAndGet()
                                if (done % 40 == 0 || done == expectedWrites) {
                                    StressTestReporter.logProgress(
                                        SUITE,
                                        "concurrent_read_write",
                                        "writer_progress",
                                        "saved=$done/$expectedWrites"
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            errors.add(t)
                        } finally {
                            doneGate.countDown()
                        }
                    }
                }

                repeat(readerThreads) { readerIndex ->
                    pool.execute {
                        try {
                            startGate.await()
                            var previous = 0
                            for (i in 0 until readLoops) {
                                val current = LitePal.where("serial like ?", "${prefix}_w%").count(Cellphone::class.java)
                                if (current < previous) {
                                    throw IllegalStateException(
                                        "reader saw decreasing count: reader=$readerIndex, previous=$previous, current=$current"
                                    )
                                }
                                previous = current
                                val doneReads = reads.incrementAndGet()
                                if (doneReads % 30 == 0) {
                                    StressTestReporter.logProgress(
                                        SUITE,
                                        "concurrent_read_write",
                                        "reader_progress",
                                        "reads=$doneReads,currentRows=$current"
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            errors.add(t)
                        } finally {
                            doneGate.countDown()
                        }
                    }
                }

                startGate.countDown()
                val completed = doneGate.await(120, TimeUnit.SECONDS)
                pool.shutdownNow()
                StressTestReporter.logProgress(
                    SUITE,
                    "concurrent_read_write",
                    "await_done",
                    "completed=$completed,written=${written.get()},reads=${reads.get()}"
                )
                assertTrue(completed)
                assertTrue(errors.isEmpty())

                val actual = LitePal.where("serial like ?", "${prefix}_w%").count(Cellphone::class.java)
                assertEquals(expectedWrites, written.get())
                assertEquals(expectedWrites, actual)
            } finally {
                LitePal.deleteAll(Cellphone::class.java, "serial like ?", "${prefix}_%")
            }
        }
    }

    private fun newBatchId(caseName: String): String {
        return "${caseName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val SUITE = "StressCrudLargeTest"

        @JvmStatic
        @AfterClass
        fun afterClassSummary() {
            StressTestReporter.logSuiteSummary(SUITE)
            StressTestReporter.logGlobalSummary()
        }
    }
}
