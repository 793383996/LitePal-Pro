package org.litepal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.crud.LitePalSupport
import org.litepal.extension.runInTransaction
import org.litepal.generated.EntityMeta
import org.litepal.generated.GeneratedEntityMeta
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.generated.LitePalGeneratedRegistry
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
        System.setProperty("litepal.generated.registry", RoutingTestRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
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
        System.clearProperty("litepal.generated.registry")
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

