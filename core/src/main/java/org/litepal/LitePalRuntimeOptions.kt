package org.litepal

import java.util.concurrent.Executor

data class LitePalRuntimeOptions(
    val allowMainThreadAccess: Boolean = true,
    val mainThreadViolationPolicy: MainThreadViolationPolicy = MainThreadViolationPolicy.LOG,
    val queryExecutor: Executor? = null,
    val transactionExecutor: Executor? = null,
    val generatedMetadataMode: GeneratedMetadataMode = GeneratedMetadataMode.AUTO
)
