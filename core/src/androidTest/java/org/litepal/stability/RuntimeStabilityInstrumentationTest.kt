package org.litepal.stability

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.stability.model.RuntimeUser
import org.litepal.tablemanager.callback.DatabaseListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeStabilityInstrumentationTest {

    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LitePal.initialize(context)
        LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
        LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
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
    fun getDatabase_shouldObserveCreateListenerBeforeReturn() {
        val dbName = "listener_order_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)

        val createLatch = CountDownLatch(1)
        LitePal.registerDatabaseListener(object : DatabaseListener {
            override fun onCreate() {
                createLatch.countDown()
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })

        LitePal.use(newDb(dbName))
        LitePal.getDatabase()
        assertTrue(createLatch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun createListener_canSwitchDatabaseWithoutDeadlock() {
        val dbNameA = "listener_switch_a_${System.currentTimeMillis()}"
        val dbNameB = "listener_switch_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val dbA = newDb(dbNameA)
        val dbB = newDb(dbNameB)
        val createLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)

        LitePal.registerDatabaseListener(object : DatabaseListener {
            override fun onCreate() {
                try {
                    LitePal.use(dbB)
                    createLatch.countDown()
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }

            override fun onUpgrade(oldVersion: Int, newVersion: Int) = Unit
        })

        LitePal.use(dbA)
        Thread {
            try {
                LitePal.getDatabase()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                finishLatch.countDown()
            }
        }.start()

        assertTrue(createLatch.await(5, TimeUnit.SECONDS))
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)
    }

    @Test
    fun concurrentCrudAndUseSwitch_shouldStayStable() {
        val dbNameA = "switch_a_${System.currentTimeMillis()}"
        val dbNameB = "switch_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val dbA = newDb(dbNameA)
        val dbB = newDb(dbNameB)
        LitePal.use(dbA)
        LitePal.getDatabase()
        LitePal.use(dbB)
        LitePal.getDatabase()
        LitePal.use(dbA)

        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(2)

        val crudThread = Thread {
            try {
                for (i in 0 until 80) {
                    val user = RuntimeUser()
                    user.name = "user_$i"
                    user.save()
                    LitePal.where("name = ?", "user_$i").count(RuntimeUser::class.java)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }

        val switchThread = Thread {
            try {
                for (i in 0 until 80) {
                    LitePal.use(if (i % 2 == 0) dbB else dbA)
                    LitePal.getDatabase()
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }

        crudThread.start()
        switchThread.start()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)
    }

    @Test
    fun concurrentGetDatabaseAndUseSwitch_shouldReturnCurrentConfigAtCompletion() {
        val dbNameA = "epoch_switch_a_${System.currentTimeMillis()}"
        val dbNameB = "epoch_switch_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val dbA = newDb(dbNameA)
        val dbB = newDb(dbNameB)
        LitePal.use(dbA)
        LitePal.getDatabase()

        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(2)

        val readerThread = Thread {
            try {
                repeat(1000) {
                    val path = LitePal.getDatabase().path
                    if (!path.endsWith(".db")) {
                        throw IllegalStateException("unexpected database path: $path")
                    }
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }

        val switchThread = Thread {
            try {
                repeat(1000) { index ->
                    LitePal.use(if (index % 2 == 0) dbA else dbB)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }

        readerThread.start()
        switchThread.start()
        assertTrue(done.await(40, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)

        LitePal.use(dbB)
        val finalDbPath = LitePal.getDatabase().path
        assertTrue(finalDbPath.endsWith("${dbNameB}.db"))
    }

    private fun newDb(name: String): LitePalDB {
        val db = LitePalDB(name, 1)
        db.addClassName(RuntimeUser::class.java.name)
        return db
    }

}
