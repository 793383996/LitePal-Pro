package org.litepal

import java.util.concurrent.Executor

data class LitePalRuntimeOptions(
    val allowMainThreadAccess: Boolean = false,
    val mainThreadViolationPolicy: MainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
    val queryExecutor: Executor? = LitePalExecutors.defaultQueryExecutor(),
    val transactionExecutor: Executor? = LitePalExecutors.defaultTransactionExecutor(),
    val generatedMetadataMode: GeneratedMetadataMode = GeneratedMetadataMode.REQUIRED,
    val schemaValidationMode: SchemaValidationMode = SchemaValidationMode.STRICT
)
