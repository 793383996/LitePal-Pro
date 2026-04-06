package org.litepal

import android.os.Handler
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class LitePalRuntimeOptionsTest {

    @After
    fun tearDown() {
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
    }

    @Test
    fun mainThreadViolationThrow_shouldThrowWhenAccessIsDisallowed() {
        LitePalRuntime.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW
            )
        )

        val errorRef = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                LitePalRuntime.onDatabaseMainThreadAccess("unit-test")
            } catch (t: Throwable) {
                errorRef.set(t)
            } finally {
                latch.countDown()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(errorRef.get() is IllegalStateException)
    }

    @Test
    fun mainThreadViolationLog_shouldNotThrowWhenAccessIsDisallowed() {
        LitePalRuntime.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.LOG
            )
        )
        val errorRef = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                LitePalRuntime.onDatabaseMainThreadAccess("unit-test")
            } catch (t: Throwable) {
                errorRef.set(t)
            } finally {
                latch.countDown()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertNull(errorRef.get())
    }

    @Test
    fun sharedSingleThreadExecutor_shouldNotDeadlockOnNestedCrossDispatch() {
        val sharedExecutor = Executors.newSingleThreadExecutor()
        LitePalRuntime.setRuntimeOptions(
            LitePalRuntimeOptions(
                queryExecutor = sharedExecutor,
                transactionExecutor = sharedExecutor
            )
        )

        val resultRef = AtomicReference<String?>()
        val errorRef = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        val caller = Thread {
            try {
                resultRef.set(
                    LitePalRuntime.executeOnTransactionExecutor {
                        LitePalRuntime.executeOnQueryExecutor { "ok" }
                    }
                )
            } catch (t: Throwable) {
                errorRef.set(t)
            } finally {
                latch.countDown()
            }
        }
        caller.start()

        val completed = latch.await(3, TimeUnit.SECONDS)
        if (!completed) {
            caller.interrupt()
        }
        sharedExecutor.shutdownNow()

        assertTrue("nested cross-dispatch timed out (possible deadlock)", completed)
        assertNull("unexpected throwable=${errorRef.get()}", errorRef.get())
        assertEquals("ok", resultRef.get())
    }
}
