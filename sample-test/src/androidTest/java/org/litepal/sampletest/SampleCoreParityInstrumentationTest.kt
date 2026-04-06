package org.litepal.sampletest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.litepal.sampletest.model.Classroom
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntime
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.util.DBUtility
import org.litepal.tablemanager.callback.DatabaseListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SampleCoreParityInstrumentationTest {

    private val createdDbNames = mutableListOf<String>()
    private lateinit var originalOptions: LitePalRuntimeOptions
    private lateinit var originalErrorPolicy: LitePalErrorPolicy
    private lateinit var originalCryptoPolicy: LitePalCryptoPolicy

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SampleTestRuntimeBootstrap.applySampleDefaults(context)
        originalOptions = LitePal.getRuntimeOptions()
        originalErrorPolicy = LitePalRuntime.getErrorPolicy()
        originalCryptoPolicy = LitePalRuntime.getCryptoPolicy()
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
        LitePal.setRuntimeOptions(originalOptions)
        LitePal.setErrorPolicy(originalErrorPolicy)
        LitePal.setCryptoPolicy(originalCryptoPolicy)
    }

    @Test
    fun mainThreadViolationPolicy_shouldRespectThrowAndLog() {
        LitePal.setRuntimeOptions(
            LitePal.getRuntimeOptions().copy(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW
            )
        )
        val throwRef = AtomicReference<Throwable?>(null)
        runOnMainSync {
            try {
                LitePalRuntime.onDatabaseMainThreadAccess("sample-main-thread-throw")
            } catch (t: Throwable) {
                throwRef.set(t)
            }
        }
        assertTrue(throwRef.get() is IllegalStateException)

        LitePal.setRuntimeOptions(
            LitePal.getRuntimeOptions().copy(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.LOG
            )
        )
        val logRef = AtomicReference<Throwable?>(null)
        runOnMainSync {
            try {
                LitePalRuntime.onDatabaseMainThreadAccess("sample-main-thread-log")
            } catch (t: Throwable) {
                logRef.set(t)
            }
        }
        assertNull(logRef.get())
    }

    @Test
    fun lifecycleBoundListener_shouldAutoUnregisterAfterOwnerDestroyed() {
        val dbNameA = "sample_listener_owner_a_${System.currentTimeMillis()}"
        val dbNameB = "sample_listener_owner_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val owner = TestLifecycleOwner()
        val callbackCount = AtomicInteger(0)
        val firstCreateLatch = CountDownLatch(1)
        LitePal.registerDatabaseListener(owner, object : DatabaseListener {
            override fun onCreate() {
                callbackCount.incrementAndGet()
                firstCreateLatch.countDown()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })

        LitePal.use(LitePalDB.fromDefault(dbNameA))
        LitePal.getDatabase()
        assertTrue(firstCreateLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, callbackCount.get())

        owner.destroy()
        LitePal.use(LitePalDB.fromDefault(dbNameB))
        LitePal.getDatabase()
        Thread.sleep(250)

        assertEquals(1, callbackCount.get())
    }

    @Test
    fun idOnlyMutationPath_shouldClearGenericRowsForClassroom() {
        val dbName = "sample_id_only_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)
        LitePal.use(LitePalDB.fromDefault(dbName))
        LitePal.getDatabase()

        LitePal.deleteAll(Classroom::class.java)
        val prefix = "__sample_id_only_${System.currentTimeMillis()}"
        repeat(12) { index ->
            val classroom = Classroom().apply {
                name = "${prefix}_$index"
                news = mutableListOf("n1", "n2")
                numbers = mutableListOf(1, 2, 3)
            }
            assertTrue(classroom.save())
        }

        val updater = Classroom().apply { name = "${prefix}_renamed" }
        val updatedRows = updater.updateAll("name like ?", "${prefix}_%")
        assertTrue(updatedRows >= 12)
        val renamedCount = LitePal.where("name = ?", "${prefix}_renamed").count(Classroom::class.java)
        assertEquals(12, renamedCount)

        val deletedRows = LitePal.deleteAll(Classroom::class.java, "name = ?", "${prefix}_renamed")
        assertTrue(deletedRows >= 12)

        val newsTable = DBUtility.getGenericTableName(Classroom::class.java.name, "news")
        val numbersTable = DBUtility.getGenericTableName(Classroom::class.java.name, "numbers")
        assertEquals(0, countRows(newsTable))
        assertEquals(0, countRows(numbersTable))
    }

    private fun countRows(tableName: String): Int {
        var cursor: android.database.Cursor? = null
        return try {
            cursor = LitePal.getDatabase().rawQuery("select count(1) from $tableName", null)
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor?.close()
        }
    }

    private fun runOnMainSync(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
            return
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            runOnMainSync {
                registry.currentState = Lifecycle.State.CREATED
                registry.currentState = Lifecycle.State.STARTED
                registry.currentState = Lifecycle.State.RESUMED
            }
        }

        override val lifecycle: Lifecycle
            get() = registry

        fun destroy() {
            runOnMainSync {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }

        private fun runOnMainSync(block: () -> Unit) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                block()
                return
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        }
    }
}





