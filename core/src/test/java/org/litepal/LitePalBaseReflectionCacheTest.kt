package org.litepal

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.crud.LitePalSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LitePalBaseReflectionCacheTest {

    @Test
    fun reflectionCaches_shouldBeSharedAcrossInstances() {
        LitePalBase.clearReflectionCachesForTesting()

        val baseA = TestLitePalBase()
        val baseB = TestLitePalBase()
        val fieldsA = baseA.supportedFields(CacheModel::class.java.name)
        val fieldsB = baseB.supportedFields(CacheModel::class.java.name)
        val genericA = baseA.supportedGenericFields(CacheModel::class.java.name)
        val genericB = baseB.supportedGenericFields(CacheModel::class.java.name)

        assertSame(fieldsA, fieldsB)
        assertSame(genericA, genericB)
    }

    @Test
    fun reflectionCaches_shouldRemainStableUnderConcurrency() {
        LitePalBase.clearReflectionCachesForTesting()

        val workerCount = 12
        val iterationsPerWorker = 100
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(workerCount)
        val failure = AtomicReference<Throwable?>(null)

        repeat(workerCount) {
            Thread {
                try {
                    val base = TestLitePalBase()
                    startLatch.await()
                    repeat(iterationsPerWorker) {
                        base.supportedFields(CacheModel::class.java.name)
                        base.supportedGenericFields(CacheModel::class.java.name)
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    finishLatch.countDown()
                }
            }.start()
        }

        startLatch.countDown()
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)

        val baseA = TestLitePalBase()
        val baseB = TestLitePalBase()
        assertSame(
            baseA.supportedFields(CacheModel::class.java.name),
            baseB.supportedFields(CacheModel::class.java.name)
        )
        assertSame(
            baseA.supportedGenericFields(CacheModel::class.java.name),
            baseB.supportedGenericFields(CacheModel::class.java.name)
        )
    }

    private class TestLitePalBase : LitePalBase() {
        fun supportedFields(className: String) = getSupportedFields(className)
        fun supportedGenericFields(className: String) = getSupportedGenericFields(className)
    }

    private class CacheModel : LitePalSupport() {
        var name: String? = null
        var score: Int = 0
        var tags: MutableList<String>? = null
    }
}
