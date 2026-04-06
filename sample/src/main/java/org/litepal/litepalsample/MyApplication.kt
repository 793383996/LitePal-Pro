package org.litepal.litepalsample

import android.app.Application
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.SchemaValidationMode
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
        // 核心启动流程 1/4（Core boot flow 1/4）：使用 Application Context 初始化 LitePal。
        LitePal.initialize(this)

        // 核心启动流程 2/4（Core boot flow 2/4）：配置运行时调度器与严格安全默认值。
        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = queryExecutor,
                transactionExecutor = transactionExecutor,
                schemaValidationMode = SchemaValidationMode.STRICT
            )
        )

        // 核心启动流程 3/4（Core boot flow 3/4）：启用严格错误策略与现代加密策略。
        LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
        LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)

        // 核心启动流程 4/4（Core boot flow 4/4）：可选异步稳定性套件（Instrumentation 期间跳过）。
        if (!isRunningInstrumentationTest()) {
            StartupStabilityTestRunner.runAsync(this)
        }
    }

    private fun isRunningInstrumentationTest(): Boolean {
        return javaClass.classLoader
            ?.getResource("androidx/test/platform/app/InstrumentationRegistry.class") != null
    }
}
