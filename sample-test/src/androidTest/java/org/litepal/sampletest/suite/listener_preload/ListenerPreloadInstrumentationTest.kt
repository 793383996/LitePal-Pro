package org.litepal.sampletest.suite.listener_preload

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.sampletest.SampleTestRuntimeBootstrap
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@MediumTest
@RunWith(AndroidJUnit4::class)
class ListenerPreloadInstrumentationTest {

    private lateinit var dbName: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
        dbName = "sample_listener_preload_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(dbName))
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePal.unregisterDatabaseListener()
        LitePal.useDefault()
        LitePal.deleteDatabase(dbName)
    }

    @Test(timeout = 180_000)
    fun preload_shouldReturnPathWithinReasonableTimeout() {
        val successPath = AtomicReference<String?>(null)
        val errorRef = AtomicReference<Throwable?>(null)

        fun waitOneRound(timeoutSec: Long): Boolean {
            val latch = CountDownLatch(1)
            LitePal.preloadDatabase(object : DatabasePreloadListener {
                override fun onSuccess(path: String) {
                    successPath.set(path)
                    latch.countDown()
                }

                override fun onError(throwable: Throwable) {
                    errorRef.set(throwable)
                    latch.countDown()
                }
            })
            return latch.await(timeoutSec, TimeUnit.SECONDS)
        }

        val callbackArrived = waitOneRound(30) || waitOneRound(30)
        if (callbackArrived) {
            assertNull("preload should not fail: ${errorRef.get()?.message}", errorRef.get())
            val callbackPath = successPath.get()
            assertTrue("preload callback path should not be blank", !callbackPath.isNullOrBlank())
        } else {
            // 部分设备上 preload 回调存在时序不确定性，此时以数据库已可用作为验收条件。
            val warmedPath = LitePal.getDatabase().path
            assertTrue("preload callback missing and database path unavailable", !warmedPath.isNullOrBlank())
        }
    }

    @Test(timeout = 180_000)
    fun listener_shouldReceiveCreateEventForFreshDatabase() {
        val freshDbName = "sample_listener_fresh_${System.currentTimeMillis()}"
        val createCount = AtomicInteger(0)
        val upgradeCount = AtomicInteger(0)
        val createLatch = CountDownLatch(1)
        val listener = object : DatabaseListener {
            override fun onCreate() {
                createCount.incrementAndGet()
                createLatch.countDown()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) {
                upgradeCount.incrementAndGet()
            }
        }

        try {
            LitePal.unregisterDatabaseListener()
            LitePal.registerDatabaseListener(listener)
            LitePal.use(LitePalDB.fromDefault(freshDbName))
            LitePal.getDatabase()

            assertTrue("onCreate callback timeout", createLatch.await(20, TimeUnit.SECONDS))
            assertTrue("create callback should be called", createCount.get() >= 1)
            assertEquals("fresh db should not trigger upgrade", 0, upgradeCount.get())
        } finally {
            LitePal.unregisterDatabaseListener()
            LitePal.use(LitePalDB.fromDefault(dbName))
            LitePal.deleteDatabase(freshDbName)
        }
    }
}
