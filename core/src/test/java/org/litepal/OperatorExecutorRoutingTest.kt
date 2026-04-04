package org.litepal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Handler
import android.os.Looper
import org.litepal.crud.LitePalSupport
import org.litepal.extension.runInTransaction
import org.litepal.generated.EntityMeta
import org.litepal.generated.GeneratedEntityMeta
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.generated.LitePalGeneratedRegistry
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class OperatorExecutorRoutingTest {

    private lateinit var testDbName: String

    @Before
    fun setUp() {
        LitePal.initialize(RuntimeEnvironment.getApplication())
        GeneratedRegistryLocator.installRegistryForTesting(RoutingTestRegistry())
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = true,
                schemaValidationMode = SchemaValidationMode.LOG
            )
        )
        testDbName = "operator_exec_${System.currentTimeMillis()}"
        val db = LitePalDB(testDbName, 1)
        LitePal.use(db)
        LitePal.getDatabase()
    }

    @After
    fun tearDown() {
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
        LitePal.useDefault()
        LitePal.deleteDatabase(testDbName)
        GeneratedRegistryLocator.resetForTesting()
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
                allowMainThreadAccess = true,
                schemaValidationMode = SchemaValidationMode.LOG,
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
                allowMainThreadAccess = true,
                schemaValidationMode = SchemaValidationMode.LOG,
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

    @Test
    fun getDatabase_onMainThreadWithAllowAccess_shouldBypassTransactionExecutorDispatch() {
        val transactionDispatch = AtomicInteger(0)
        val transactionExecutor = Executor { runnable ->
            transactionDispatch.incrementAndGet()
            runnable.run()
        }
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = true,
                schemaValidationMode = SchemaValidationMode.LOG,
                transactionExecutor = transactionExecutor
            )
        )
        val dbName = "operator_exec_main_allow_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB(dbName, 1))
        val failure = AtomicReference<Throwable?>()
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                LitePal.getDatabase()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)
        assertEquals(0, transactionDispatch.get())
        LitePal.deleteDatabase(dbName)
    }

    @Test
    fun getDatabase_onMainThreadWithoutAllowAccess_shouldDispatchTransactionExecutor() {
        val transactionDispatch = AtomicInteger(0)
        val transactionExecutor = Executor { runnable ->
            transactionDispatch.incrementAndGet()
            runnable.run()
        }
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.LOG,
                schemaValidationMode = SchemaValidationMode.LOG,
                transactionExecutor = transactionExecutor
            )
        )
        val dbName = "operator_exec_main_block_${System.currentTimeMillis()}"
        LitePal.use(LitePalDB(dbName, 1))
        val failure = AtomicReference<Throwable?>()
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                LitePal.getDatabase()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                done.countDown()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertTrue("unexpected failure: ${failure.get()}", failure.get() == null)
        assertTrue(transactionDispatch.get() >= 1)
        LitePal.deleteDatabase(dbName)
    }

    class DispatchModel : LitePalSupport() {
        var name: String? = null
    }

    class RoutingTestRegistry : LitePalGeneratedRegistry {
        override val schemaVersion: Int = 1
        override val schemaJson: String = "{}"
        override val schemaHash: String = "operator-executor-routing-test"
        override val anchorClassName: String = "org.litepal.OperatorExecutorRoutingAnchor"
        override val anchorEntities: List<String> = listOf(DispatchModel::class.java.name)

        override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> {
            return mapOf(
                DispatchModel::class.java.name to GeneratedEntityMeta(
                    className = DispatchModel::class.java.name,
                    tableName = DispatchModel::class.java.name.substringAfterLast('.'),
                    supportedFields = listOf("name"),
                    supportedGenericFields = emptyList()
                )
            )
        }
    }
}

