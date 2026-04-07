package org.litepal.sampletest

import android.content.Context
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.SchemaValidationMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object SampleTestRuntimeBootstrap {
    private val queryThreadCounter = AtomicInteger(0)
    private val transactionThreadCounter = AtomicInteger(0)
    private val queryThreadCount = Runtime.getRuntime()
        .availableProcessors()
        .coerceIn(2, 4)

    private val queryExecutor = Executors.newFixedThreadPool(queryThreadCount) { runnable ->
        Thread(runnable, "litepal-sampletest-query-${queryThreadCounter.incrementAndGet()}").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sampletest-transaction-${transactionThreadCounter.incrementAndGet()}").apply { isDaemon = true }
    }

    fun applySampleDefaults(context: Context) {
        LitePal.initialize(context.applicationContext)
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = queryExecutor,
                transactionExecutor = transactionExecutor,
                schemaValidationMode = SchemaValidationMode.STRICT
            )
        )
        LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
        LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
    }
}
