package org.litepal

import android.os.Handler
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
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
}
