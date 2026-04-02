package org.litepal.litepalsample.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object DbTaskExecutor {

    private val dbExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litepal-sample-db").apply {
            isDaemon = true
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun run(task: () -> Unit) {
        dbExecutor.execute(task)
    }

    @JvmStatic
    fun <T> run(
        task: () -> T,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        dbExecutor.execute {
            try {
                val result = task()
                mainHandler.post {
                    onSuccess(result)
                }
            } catch (t: Throwable) {
                if (onError != null) {
                    mainHandler.post {
                        onError(t)
                    }
                }
            }
        }
    }
}
