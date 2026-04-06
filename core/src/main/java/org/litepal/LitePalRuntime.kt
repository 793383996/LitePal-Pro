/*
 * Copyright (C)  Tony Green, LitePal Framework Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litepal

import android.os.Looper
import org.litepal.exceptions.LitePalSupportException
import org.litepal.util.LitePalLog
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

object LitePalRuntime {

    private const val TAG = "LitePalRuntime"

    @Volatile
    private var errorPolicy: LitePalErrorPolicy = LitePalErrorPolicy.COMPAT

    @Volatile
    private var cryptoPolicy: LitePalCryptoPolicy = LitePalCryptoPolicy.V2_WRITE_DUAL_READ

    @Volatile
    private var runtimeOptions: LitePalRuntimeOptions = LitePalRuntimeOptions()

    private val silentErrorLogDepth = ThreadLocal<Int>()
    private val queryDispatchDepth = ThreadLocal<Int>()
    private val transactionDispatchDepth = ThreadLocal<Int>()
    private val activeExecutorStack = ThreadLocal<ArrayDeque<Executor>>()
    private val generatedPathHitCount = AtomicLong(0L)
    private val reflectionFallbackCount = AtomicLong(0L)
    private val mainThreadDbBlockTotalMs = AtomicLong(0L)

    @JvmStatic
    fun setErrorPolicy(policy: LitePalErrorPolicy) {
        errorPolicy = policy
    }

    @JvmStatic
    fun getErrorPolicy(): LitePalErrorPolicy = errorPolicy

    @JvmStatic
    fun setCryptoPolicy(policy: LitePalCryptoPolicy) {
        cryptoPolicy = policy
    }

    @JvmStatic
    fun getCryptoPolicy(): LitePalCryptoPolicy = cryptoPolicy

    @JvmStatic
    fun setRuntimeOptions(options: LitePalRuntimeOptions) {
        runtimeOptions = options
    }

    @JvmStatic
    fun getRuntimeOptions(): LitePalRuntimeOptions = runtimeOptions

    @JvmStatic
    fun shouldThrowOnError(): Boolean = errorPolicy == LitePalErrorPolicy.STRICT

    @JvmStatic
    fun onDatabaseMainThreadAccess(operation: String) {
        val options = runtimeOptions
        if (options.allowMainThreadAccess) {
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return
        }
        val message = "Database main-thread access blocked for operation=$operation. " +
            "Set allowMainThreadAccess=true or dispatch to background executor."
        if (options.mainThreadViolationPolicy == MainThreadViolationPolicy.THROW) {
            throw IllegalStateException(message)
        }
        LitePalLog.w(TAG, message)
    }

    @JvmStatic
    fun onMainThreadDatabaseBlock(costMs: Long) {
        if (costMs > 0) {
            mainThreadDbBlockTotalMs.addAndGet(costMs)
        }
    }

    @JvmStatic
    fun <T> executeOnQueryExecutor(block: () -> T): T {
        val options = runtimeOptions
        return executeBlocking(options.queryExecutor, queryDispatchDepth, block)
    }

    @JvmStatic
    fun <T> executeOnTransactionExecutor(block: () -> T): T {
        val options = runtimeOptions
        return executeBlocking(options.transactionExecutor, transactionDispatchDepth, block)
    }

    @JvmStatic
    fun recordGeneratedPathHit(tag: String) {
        generatedPathHitCount.incrementAndGet()
    }

    @JvmStatic
    fun recordReflectionFallback(tag: String) {
        reflectionFallbackCount.incrementAndGet()
    }

    @JvmStatic
    fun getGeneratedPathHitCount(): Long = generatedPathHitCount.get()

    @JvmStatic
    fun getReflectionFallbackCount(): Long = reflectionFallbackCount.get()

    @JvmStatic
    fun getMainThreadDbBlockTotalMs(): Long = mainThreadDbBlockTotalMs.get()

    @JvmStatic
    fun resetMetrics() {
        generatedPathHitCount.set(0L)
        reflectionFallbackCount.set(0L)
        mainThreadDbBlockTotalMs.set(0L)
    }

    @JvmStatic
    fun <T> withSilentErrorLog(block: () -> T): T {
        val currentDepth = silentErrorLogDepth.get() ?: 0
        silentErrorLogDepth.set(currentDepth + 1)
        return try {
            block()
        } finally {
            val nextDepth = (silentErrorLogDepth.get() ?: 1) - 1
            if (nextDepth <= 0) {
                silentErrorLogDepth.remove()
            } else {
                silentErrorLogDepth.set(nextDepth)
            }
        }
    }

    @JvmStatic
    fun isErrorLogSilenced(): Boolean = (silentErrorLogDepth.get() ?: 0) > 0

    @JvmStatic
    fun onError(tag: String, operation: String, throwable: Throwable) {
        if (!isErrorLogSilenced()) {
            LitePalLog.e(tag, "$operation failed (policy=$errorPolicy).", throwable)
        }
        if (shouldThrowOnError()) {
            if (throwable is RuntimeException) {
                throw throwable
            }
            throw LitePalSupportException(throwable.message, throwable)
        }
    }

    @JvmStatic
    fun failFast(message: String) {
        if (!isErrorLogSilenced()) {
            LitePalLog.e(TAG, message)
        }
        if (shouldThrowOnError()) {
            throw LitePalSupportException(message)
        }
    }

    private fun <T> executeBlocking(
        executor: Executor?,
        dispatchDepth: ThreadLocal<Int>,
        block: () -> T
    ): T {
        if (executor == null) {
            return block()
        }
        if (isExecutorActive(executor)) {
            // Prevent self-deadlock when query/transaction executors are configured to the same
            // single-thread executor and an operation nests cross-executor dispatches.
            return block()
        }
        val currentDepth = dispatchDepth.get() ?: 0
        if (currentDepth > 0) {
            return block()
        }
        val task = FutureTask {
            withActiveExecutor(executor) {
                val nestedDepth = dispatchDepth.get() ?: 0
                dispatchDepth.set(nestedDepth + 1)
                try {
                    block()
                } finally {
                    val nextDepth = (dispatchDepth.get() ?: 1) - 1
                    if (nextDepth <= 0) {
                        dispatchDepth.remove()
                    } else {
                        dispatchDepth.set(nextDepth)
                    }
                }
            }
        }
        executor.execute(task)
        return try {
            task.get()
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            if (cause is RuntimeException) {
                throw cause
            }
            throw LitePalSupportException(cause.message, cause)
        }
    }

    private fun isExecutorActive(executor: Executor): Boolean {
        val stack = activeExecutorStack.get() ?: return false
        return stack.any { it === executor }
    }

    private inline fun <T> withActiveExecutor(executor: Executor, block: () -> T): T {
        val stack = activeExecutorStack.get() ?: ArrayDeque<Executor>().also {
            activeExecutorStack.set(it)
        }
        stack.addLast(executor)
        return try {
            block()
        } finally {
            if (stack.isNotEmpty() && stack.last() === executor) {
                stack.removeLast()
            } else {
                val descending = stack.descendingIterator()
                while (descending.hasNext()) {
                    if (descending.next() === executor) {
                        descending.remove()
                        break
                    }
                }
            }
            if (stack.isEmpty()) {
                activeExecutorStack.remove()
            }
        }
    }
}
