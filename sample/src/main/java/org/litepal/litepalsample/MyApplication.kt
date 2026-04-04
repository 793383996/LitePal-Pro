package org.litepal.litepalsample

import android.app.Application
import org.litepal.GeneratedMetadataMode
import org.litepal.LitePal
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.litepalsample.stability.StartupStabilityTestRunner
import java.util.concurrent.Executors

class MyApplication : Application() {
    private val queryExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sample-query").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sample-transaction").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        LitePal.initialize(this)
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = queryExecutor,
                transactionExecutor = transactionExecutor,
                generatedMetadataMode = GeneratedMetadataMode.REQUIRED
            )
        )
        if (!isRunningInstrumentationTest()) {
            StartupStabilityTestRunner.runAsync(this)
        }
    }

    private fun isRunningInstrumentationTest(): Boolean {
        return javaClass.classLoader
            ?.getResource("androidx/test/platform/app/InstrumentationRegistry.class") != null
    }
}
