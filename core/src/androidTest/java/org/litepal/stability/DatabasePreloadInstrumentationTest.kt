package org.litepal.stability

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.stability.model.RuntimeUser
import org.litepal.tablemanager.callback.DatabasePreloadListener
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DatabasePreloadInstrumentationTest {

    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LitePal.initialize(context)
        LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
    }

    @After
    fun tearDown() {
        LitePal.unregisterDatabaseListener()
        LitePal.useDefault()
        for (dbName in createdDbNames) {
            LitePal.deleteDatabase(dbName)
        }
        createdDbNames.clear()
    }

    @Test
    fun preloadDatabase_shouldCallbackSuccess() {
        val dbName = "preload_success_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)

        LitePal.use(newDb(dbName))
        val successPath = AtomicReference<String?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        LitePal.preloadDatabase(object : DatabasePreloadListener {
            override fun onSuccess(path: String) {
                successPath.set(path)
                latch.countDown()
            }

            override fun onError(throwable: Throwable) {
                failure.set(throwable)
                latch.countDown()
            }
        })

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertTrue("preload should not fail: ${failure.get()}", failure.get() == null)
        assertTrue("path should not be blank", !successPath.get().isNullOrBlank())
    }

    @Test
    fun preloadDatabase_shouldMakeSubsequentOpenFastEnough() {
        val coldDbName = "preload_cold_${System.currentTimeMillis()}"
        val preloadedDbName = "preload_hot_${System.currentTimeMillis()}"
        createdDbNames.add(coldDbName)
        createdDbNames.add(preloadedDbName)

        LitePal.use(newDb(coldDbName))
        val coldCostMs = elapsedMs {
            LitePal.getDatabase()
        }

        LitePal.use(newDb(preloadedDbName))
        val preloadDone = CountDownLatch(1)
        LitePal.preloadDatabase(object : DatabasePreloadListener {
            override fun onSuccess(path: String) {
                preloadDone.countDown()
            }

            override fun onError(throwable: Throwable) {
                preloadDone.countDown()
            }
        })
        assertTrue(preloadDone.await(10, TimeUnit.SECONDS))

        val hotCostMs = elapsedMs {
            LitePal.getDatabase()
        }
        assertTrue(
            "preloaded open should not regress significantly. cold=$coldCostMs ms, hot=$hotCostMs ms",
            hotCostMs <= coldCostMs + 50
        )
    }

    @Test
    fun preloadDatabase_shouldCancelWhenDatabaseConfigChanges() {
        val dbNameA = "preload_cancel_a_${System.currentTimeMillis()}"
        val dbNameB = "preload_cancel_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val releaseMainLatch = CountDownLatch(1)
        val mainBlockedLatch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            mainBlockedLatch.countDown()
            try {
                releaseMainLatch.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        assertTrue(mainBlockedLatch.await(3, TimeUnit.SECONDS))

        LitePal.use(newDb(dbNameA))
        val successPath = AtomicReference<String?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val callbackLatch = CountDownLatch(1)
        LitePal.preloadDatabase(object : DatabasePreloadListener {
            override fun onSuccess(path: String) {
                successPath.set(path)
                callbackLatch.countDown()
            }

            override fun onError(throwable: Throwable) {
                failure.set(throwable)
                callbackLatch.countDown()
            }
        })

        LitePal.use(newDb(dbNameB))
        releaseMainLatch.countDown()

        assertTrue(callbackLatch.await(10, TimeUnit.SECONDS))
        assertNull("preload should not report success when config switches", successPath.get())
        assertTrue(
            "preload should be canceled on config switch. actual=${failure.get()}",
            failure.get() is CancellationException
        )
    }

    private fun newDb(name: String): LitePalDB {
        val db = LitePalDB(name, 1)
        db.addClassName(RuntimeUser::class.java.name)
        return db
    }

    private fun elapsedMs(block: () -> Unit): Long {
        val startNs = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
    }
}
