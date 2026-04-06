package org.litepal.sampletest

import android.content.Context
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.SchemaValidationMode
import java.util.concurrent.Executors

internal object SampleTestRuntimeBootstrap {
    private val queryExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sampletest-query").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sampletest-transaction").apply { isDaemon = true }
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
