package org.litepal.stability

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.stability.model.RuntimeUser
import org.litepal.tablemanager.callback.DatabaseListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DatabaseListenerLifecycleInstrumentationTest {

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
    fun lifecycleBoundListener_shouldAutoUnregisterAfterOwnerDestroyed() {
        val dbNameA = "lifecycle_listener_a_${System.currentTimeMillis()}"
        val dbNameB = "lifecycle_listener_b_${System.currentTimeMillis()}"
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

        LitePal.use(newDb(dbNameA))
        LitePal.getDatabase()
        assertTrue(firstCreateLatch.await(3, TimeUnit.SECONDS))
        assertEquals(1, callbackCount.get())

        owner.destroy()
        LitePal.use(newDb(dbNameB))
        LitePal.getDatabase()
        Thread.sleep(200)

        assertEquals(1, callbackCount.get())
    }

    @Test
    fun destroyingOldOwner_shouldNotUnregisterNewListener() {
        val dbName = "listener_registration_guard_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)

        val ownerA = TestLifecycleOwner()
        val ownerB = TestLifecycleOwner()
        val listenerBLatch = CountDownLatch(1)

        LitePal.registerDatabaseListener(ownerA, object : DatabaseListener {
            override fun onCreate() = Unit
            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })
        LitePal.registerDatabaseListener(ownerB, object : DatabaseListener {
            override fun onCreate() {
                listenerBLatch.countDown()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })

        ownerA.destroy()
        LitePal.use(newDb(dbName))
        LitePal.getDatabase()

        assertTrue(listenerBLatch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun unregisterDatabaseListener_shouldStopFurtherCallbacks() {
        val dbName = "listener_unregister_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)

        val callbackCount = AtomicInteger(0)
        LitePal.registerDatabaseListener(object : DatabaseListener {
            override fun onCreate() {
                callbackCount.incrementAndGet()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })
        LitePal.unregisterDatabaseListener()

        LitePal.use(newDb(dbName))
        LitePal.getDatabase()
        Thread.sleep(200)

        assertEquals(0, callbackCount.get())
    }

    @Test
    fun replacingListener_beforeMainDispatch_shouldSkipOldRegistrationCallback() {
        val dbNameA = "listener_replace_a_${System.currentTimeMillis()}"
        val dbNameB = "listener_replace_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val oldListenerCount = AtomicInteger(0)
        val newListenerCount = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)
        val releaseMainLatch = CountDownLatch(1)
        val mainBlockedLatch = CountDownLatch(1)
        val workerDoneLatch = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            mainBlockedLatch.countDown()
            try {
                releaseMainLatch.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        assertTrue(mainBlockedLatch.await(3, TimeUnit.SECONDS))

        LitePal.registerDatabaseListener(object : DatabaseListener {
            override fun onCreate() {
                oldListenerCount.incrementAndGet()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })
        LitePal.use(newDb(dbNameA))
        Thread {
            try {
                LitePal.getDatabase()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                workerDoneLatch.countDown()
            }
        }.start()

        LitePal.registerDatabaseListener(object : DatabaseListener {
            override fun onCreate() {
                newListenerCount.incrementAndGet()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })

        releaseMainLatch.countDown()
        assertTrue(workerDoneLatch.await(10, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)
        assertEquals(0, oldListenerCount.get())

        LitePal.use(newDb(dbNameB))
        LitePal.getDatabase()
        Thread.sleep(200)
        assertEquals(1, newListenerCount.get())
    }

    private fun newDb(name: String): LitePalDB {
        val db = LitePalDB(name, 1)
        db.addClassName(RuntimeUser::class.java.name)
        return db
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
