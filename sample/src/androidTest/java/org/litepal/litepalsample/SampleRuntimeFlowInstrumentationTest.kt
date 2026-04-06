package org.litepal.litepalsample

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.litepalsample.model.Singer
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SampleRuntimeFlowInstrumentationTest {

    private lateinit var testDbName: String

    @Before
    fun setUp() {
        testDbName = "sample_runtime_flow_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB.fromDefault(testDbName))
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePal.unregisterDatabaseListener()
        LitePal.useDefault()
        LitePal.deleteDatabase(testDbName)
    }

    @Test
    fun preloadDatabase_shouldInvokeSuccessCallback() {
        val successPath = AtomicReference<String?>(null)
        val errorRef = AtomicReference<Throwable?>(null)
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

        assertTrue("preload callback timeout", latch.await(10, TimeUnit.SECONDS))
        assertNull("preload should not fail: ${errorRef.get()?.message}", errorRef.get())
        assertTrue("preload path should not be blank", !successPath.get().isNullOrBlank())
    }

    @Test
    fun databaseListener_shouldReceiveCreateForFreshDatabase() {
        val tempDbName = "sample_listener_${System.currentTimeMillis()}"
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
            LitePal.use(LitePalDB.fromDefault(tempDbName))
            LitePal.getDatabase()

            assertTrue("onCreate callback timeout", createLatch.await(10, TimeUnit.SECONDS))
            assertTrue("create callback should be called", createCount.get() >= 1)
            assertEquals("fresh db should not trigger upgrade", 0, upgradeCount.get())
        } finally {
            LitePal.unregisterDatabaseListener()
            LitePal.useDefault()
            LitePal.deleteDatabase(tempDbName)
        }
    }

    @Test
    fun multiDatabaseSwitch_shouldKeepDataIsolated() {
        val sandboxDbName = "sample_sandbox_test_${System.currentTimeMillis()}"
        val markerName = "__sample_multi_db_marker_${System.currentTimeMillis()}"

        try {
            LitePal.use(LitePalDB.fromDefault(testDbName))
            LitePal.deleteAll(Singer::class.java, "name = ?", markerName)

            LitePal.use(LitePalDB.fromDefault(sandboxDbName))
            Singer().apply {
                name = markerName
                age = 22
                isMale = true
            }.save()
            val inSandbox = LitePal.where("name = ?", markerName).count(Singer::class.java)
            assertEquals(1, inSandbox)

            LitePal.use(LitePalDB.fromDefault(testDbName))
            val inPrimary = LitePal.where("name = ?", markerName).count(Singer::class.java)
            assertEquals(0, inPrimary)
        } finally {
            LitePal.use(LitePalDB.fromDefault(testDbName))
            LitePal.deleteAll(Singer::class.java, "name = ?", markerName)
            LitePal.deleteDatabase(sandboxDbName)
        }
    }
}
