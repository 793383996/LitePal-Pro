package org.litepal.tablemanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseRuntimeLockTest {

    @Test
    fun writeLock_shouldWaitUntilReadLockReleased() {
        val writerEntered = AtomicBoolean(false)
        val writerDone = CountDownLatch(1)

        DatabaseRuntimeLock.acquireReadLock()
        try {
            val writer = Thread {
                DatabaseRuntimeLock.withWriteLock {
                    writerEntered.set(true)
                }
                writerDone.countDown()
            }
            writer.start()

            Thread.sleep(100)
            assertFalse(writerEntered.get())
        } finally {
            DatabaseRuntimeLock.releaseReadLock()
        }

        assertTrue(writerDone.await(5, TimeUnit.SECONDS))
        assertTrue(writerEntered.get())
    }
}
