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

package org.litepal.tablemanager

import java.util.concurrent.locks.ReentrantReadWriteLock

object DatabaseRuntimeLock {

    @PublishedApi
    internal val lock = ReentrantReadWriteLock(true)

    @JvmStatic
    inline fun <T> withReadLock(block: () -> T): T {
        lock.readLock().lock()
        return try {
            block()
        } finally {
            lock.readLock().unlock()
        }
    }

    @JvmStatic
    inline fun <T> withWriteLock(block: () -> T): T {
        lock.writeLock().lock()
        return try {
            block()
        } finally {
            lock.writeLock().unlock()
        }
    }

    @JvmStatic
    fun acquireReadLock() {
        lock.readLock().lock()
    }

    @JvmStatic
    fun releaseReadLock() {
        lock.readLock().unlock()
    }

    @JvmStatic
    fun currentReadHoldCount(): Int = lock.readHoldCount
}
