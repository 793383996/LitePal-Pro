package org.litepal

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object LitePalExecutors {

    private val queryThreadCounter = AtomicInteger(0)
    private val transactionThreadCounter = AtomicInteger(0)

    private val queryExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-query-${queryThreadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    private val transactionExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-transaction-${transactionThreadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    fun defaultQueryExecutor(): Executor = queryExecutor

    fun defaultTransactionExecutor(): Executor = transactionExecutor
}
