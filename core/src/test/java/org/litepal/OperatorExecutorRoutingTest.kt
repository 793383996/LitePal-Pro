package org.litepal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.crud.LitePalSupport
import org.litepal.extension.runInTransaction
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class OperatorExecutorRoutingTest {

    private lateinit var testDbName: String

    @Before
    fun setUp() {
        LitePal.initialize(RuntimeEnvironment.getApplication())
        testDbName = "operator_exec_${System.currentTimeMillis()}"
        val db = LitePalDB(testDbName, 1)
        db.addClassName(DispatchModel::class.java.name)
        LitePal.use(db)
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
        LitePal.useDefault()
        LitePal.deleteDatabase(testDbName)
    }

    @Test
    fun operatorQueryAndWrite_shouldRouteThroughConfiguredExecutors() {
        val queryDispatch = AtomicInteger(0)
        val writeDispatch = AtomicInteger(0)
        val queryExecutor = Executor { runnable ->
            queryDispatch.incrementAndGet()
            runnable.run()
        }
        val writeExecutor = Executor { runnable ->
            writeDispatch.incrementAndGet()
            runnable.run()
        }
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                queryExecutor = queryExecutor,
                transactionExecutor = writeExecutor
            )
        )

        LitePal.count(DispatchModel::class.java)
        LitePal.deleteAll(DispatchModel::class.java)

        assertTrue(queryDispatch.get() >= 1)
        assertTrue(writeDispatch.get() >= 1)
    }

    @Test
    fun transactionContext_shouldBypassExecutorDispatch() {
        val queryDispatch = AtomicInteger(0)
        val queryExecutor = Executor { runnable ->
            queryDispatch.incrementAndGet()
            runnable.run()
        }
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                queryExecutor = queryExecutor
            )
        )

        val committed = LitePal.runInTransaction {
            LitePal.count(DispatchModel::class.java)
            true
        }

        assertTrue(committed)
        assertEquals(0, queryDispatch.get())
    }

    class DispatchModel : LitePalSupport() {
        var name: String? = null
    }
}

