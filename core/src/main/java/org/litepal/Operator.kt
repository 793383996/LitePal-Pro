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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.litepal.generated.SchemaValidationGate
import org.litepal.crud.DeleteHandler
import org.litepal.crud.LitePalSupport
import org.litepal.crud.QueryHandler
import org.litepal.crud.SaveHandler
import org.litepal.crud.UpdateHandler
import org.litepal.parser.LitePalAttr
import org.litepal.parser.LitePalConfig
import org.litepal.parser.LitePalParser
import org.litepal.tablemanager.Connector
import org.litepal.tablemanager.DatabaseRuntimeLock
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.DBUtility
import org.litepal.util.LitePalLog
import org.litepal.util.SharedUtil
import org.litepal.util.cipher.CipherUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong

object Operator {

    private const val TAG = "Operator"
    private val handler = Handler(Looper.getMainLooper())
    private val pendingDatabaseEvents = ConcurrentLinkedQueue<DatabaseLifecycleEvent>()
    private val dbConfigEpoch = AtomicLong(0L)
    private val listenerRegistrationId = AtomicLong(0L)
    private val listenerRegistrationLock = Any()
    private val preloadCallbacksByEpoch = ConcurrentHashMap<Long, CopyOnWriteArrayList<DatabasePreloadListener>>()
    private val preloadEpochInFlight = ConcurrentHashMap.newKeySet<Long>()
    private val preloadFuturesByEpoch = ConcurrentHashMap<Long, Future<*>>()
    private val schemaValidatedEpoch = AtomicLong(Long.MIN_VALUE)
    private val preloadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LitePal-DB-Preload").apply {
            isDaemon = true
        }
    }

    private data class TransactionContext(
        val database: SQLiteDatabase,
        var depth: Int
    )

    private sealed class DatabaseLifecycleEvent {
        data object Created : DatabaseLifecycleEvent()
        data class Upgraded(val oldVersion: Int, val newVersion: Int) : DatabaseLifecycleEvent()
    }

    private data class ListenerRegistration(
        val id: Long,
        val listener: DatabaseListener,
        val owner: LifecycleOwner? = null,
        val ownerObserver: DefaultLifecycleObserver? = null
    ) {
        fun detachObserver() {
            val lifecycleOwner = owner ?: return
            val observer = ownerObserver ?: return
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
            } catch (t: Throwable) {
                LitePalLog.w(TAG, "Failed to detach lifecycle observer from database listener: ${t.message}")
            }
        }
    }

    private val transactionContextHolder = ThreadLocal<TransactionContext>()

    @Volatile
    private var listenerRegistration: ListenerRegistration? = null

    @JvmStatic
    fun getHandler(): Handler = handler

    private fun <T> executeOnQueryPath(block: () -> T): T {
        if (transactionContextHolder.get() != null) {
            return block()
        }
        return LitePalRuntime.executeOnQueryExecutor(block)
    }

    private fun <T> executeOnWritePath(block: () -> T): T {
        if (transactionContextHolder.get() != null) {
            return block()
        }
        return LitePalRuntime.executeOnTransactionExecutor(block)
    }

    @JvmStatic
    fun initialize(context: Context) {
        LitePalApplication.sContext = context.applicationContext
    }

    @JvmStatic
    fun getDatabase(): SQLiteDatabase {
        LitePalRuntime.onDatabaseMainThreadAccess("getDatabase")
        val contextualDb = transactionContextHolder.get()?.database
        if (contextualDb != null) {
            return contextualDb
        }
        val shouldTrackMainThreadBlock = Looper.myLooper() == Looper.getMainLooper()
        val startedAt = if (shouldTrackMainThreadBlock) System.nanoTime() else 0L
        return DatabaseRuntimeLock.withReadLock {
            try {
                Connector.getDatabase()
            } finally {
                if (shouldTrackMainThreadBlock) {
                    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    LitePalRuntime.onMainThreadDatabaseBlock(elapsedMs)
                }
            }
        }
    }

    @JvmStatic
    fun beginTransaction() {
        DatabaseRuntimeLock.acquireReadLock()
        try {
            val existing = transactionContextHolder.get()
            if (existing != null) {
                existing.depth += 1
                existing.database.beginTransaction()
                return
            }
            val db = Connector.getDatabase()
            db.beginTransaction()
            transactionContextHolder.set(TransactionContext(db, 1))
        } catch (t: Throwable) {
            DatabaseRuntimeLock.releaseReadLock()
            throw t
        }
    }

    @JvmStatic
    fun endTransaction() {
        val context = transactionContextHolder.get()
        val shouldReleaseReadLock = DatabaseRuntimeLock.currentReadHoldCount() > 0
        try {
            (context?.database ?: Connector.getDatabase()).endTransaction()
        } finally {
            if (context != null) {
                context.depth -= 1
                if (context.depth <= 0) {
                    transactionContextHolder.remove()
                }
            }
            if (shouldReleaseReadLock) {
                DatabaseRuntimeLock.releaseReadLock()
            }
        }
    }

    @JvmStatic
    fun setTransactionSuccessful() {
        getDatabase().setTransactionSuccessful()
    }

    @JvmStatic
    fun use(litePalDB: LitePalDB) {
        DatabaseRuntimeLock.withWriteLock {
            val litePalAttr = LitePalAttr.getInstance()
            litePalAttr.dbName = litePalDB.dbName
            litePalAttr.version = litePalDB.version
            litePalAttr.storage = litePalDB.storage
            litePalAttr.setClassNames(litePalDB.getClassNames())
            if (!isDefaultDatabase(litePalDB.dbName)) {
                litePalAttr.extraKeyName = litePalDB.dbName
                litePalAttr.cases = "lower"
            }
            Connector.clearLitePalOpenHelperInstance()
            val newEpoch = dbConfigEpoch.incrementAndGet()
            cancelOutdatedPreloadsLocked(newEpoch)
        }
    }

    @JvmStatic
    fun useDefault() {
        DatabaseRuntimeLock.withWriteLock {
            LitePalAttr.clearInstance()
            Connector.clearLitePalOpenHelperInstance()
            val newEpoch = dbConfigEpoch.incrementAndGet()
            cancelOutdatedPreloadsLocked(newEpoch)
        }
    }

    @JvmStatic
    fun deleteDatabase(dbName: String?): Boolean {
        return DatabaseRuntimeLock.withWriteLock {
            if (TextUtils.isEmpty(dbName)) {
                return@withWriteLock false
            }
            val name = Connector.normalizeDatabaseName(dbName!!)
            val candidates = Connector.resolveDeleteCandidates(name)
            var deleted = false
            for (dbFile in candidates) {
                if (dbFile.exists() && dbFile.delete()) {
                    deleted = true
                }
            }
            if (deleted) {
                removeVersionInSharedPreferences(name)
                if (name.equals(LitePalAttr.getInstance().dbName, ignoreCase = true)) {
                    Connector.clearLitePalOpenHelperInstance()
                }
                val newEpoch = dbConfigEpoch.incrementAndGet()
                cancelOutdatedPreloadsLocked(newEpoch)
            }
            return@withWriteLock deleted
        }
    }

    @JvmStatic
    internal fun currentDbConfigEpoch(): Long = dbConfigEpoch.get()

    @JvmStatic
    fun setRuntimeOptions(options: LitePalRuntimeOptions) {
        LitePalRuntime.setRuntimeOptions(options)
    }

    @JvmStatic
    fun getRuntimeOptions(): LitePalRuntimeOptions = LitePalRuntime.getRuntimeOptions()

    @JvmStatic
    fun getGeneratedPathHitCount(): Long = LitePalRuntime.getGeneratedPathHitCount()

    @JvmStatic
    fun getReflectionFallbackCount(): Long = LitePalRuntime.getReflectionFallbackCount()

    @JvmStatic
    fun getMainThreadDbBlockTotalMs(): Long = LitePalRuntime.getMainThreadDbBlockTotalMs()

    @JvmStatic
    fun resetRuntimeMetrics() {
        LitePalRuntime.resetMetrics()
    }

    @JvmStatic
    fun aesKey(key: String) {
        CipherUtil.aesKey = key
    }

    @JvmStatic
    fun setErrorPolicy(policy: LitePalErrorPolicy) {
        LitePalRuntime.setErrorPolicy(policy)
    }

    @JvmStatic
    fun setCryptoPolicy(policy: LitePalCryptoPolicy) {
        LitePalRuntime.setCryptoPolicy(policy)
    }

    @JvmStatic
    fun getErrorPolicy(): LitePalErrorPolicy = LitePalRuntime.getErrorPolicy()

    @JvmStatic
    fun getCryptoPolicy(): LitePalCryptoPolicy = LitePalRuntime.getCryptoPolicy()

    @JvmStatic
    fun notifyDatabaseCreated() {
        pendingDatabaseEvents.offer(DatabaseLifecycleEvent.Created)
    }

    @JvmStatic
    fun notifyDatabaseUpgraded(oldVersion: Int, newVersion: Int) {
        pendingDatabaseEvents.offer(DatabaseLifecycleEvent.Upgraded(oldVersion, newVersion))
    }

    @JvmStatic
    fun preloadDatabase(listener: DatabasePreloadListener? = null) {
        val epoch = currentDbConfigEpoch()
        if (listener != null) {
            preloadCallbacksByEpoch.computeIfAbsent(epoch) { CopyOnWriteArrayList() }.add(listener)
        }
        if (!preloadEpochInFlight.add(epoch)) {
            return
        }
        val task = submitPreloadTask {
            var dbPath: String? = null
            var failure: Throwable? = null
            try {
                dbPath = Connector.getDatabase().path
            } catch (t: Throwable) {
                failure = t
                LitePalRuntime.onError(TAG, "preloadDatabase", t)
            } finally {
                preloadEpochInFlight.remove(epoch)
                preloadFuturesByEpoch.remove(epoch)
                val callbacks = preloadCallbacksByEpoch.remove(epoch)
                if (callbacks.isNullOrEmpty()) {
                    return@submitPreloadTask
                }
                val isStale = currentDbConfigEpoch() != epoch
                if (isStale && failure == null) {
                    failure = CancellationException("Database preload canceled due to database configuration change.")
                }
                handler.post {
                    for (callback in callbacks) {
                        try {
                            if (failure == null) {
                                callback.onSuccess(dbPath.orEmpty())
                            } else {
                                callback.onError(failure)
                            }
                        } catch (callbackError: Throwable) {
                            LitePalLog.e(TAG, "Database preload callback failed.", callbackError)
                        }
                    }
                }
            }
        }
        preloadFuturesByEpoch[epoch] = task
    }

    private fun submitPreloadTask(task: () -> Unit): Future<*> {
        val transactionExecutor = LitePalRuntime.getRuntimeOptions().transactionExecutor
        if (transactionExecutor != null) {
            val futureTask = FutureTask(task)
            transactionExecutor.execute(futureTask)
            return futureTask
        }
        return preloadExecutor.submit(task)
    }

    private fun cancelOutdatedPreloadsLocked(currentEpoch: Long) {
        val cancelledEpochs = ArrayList<Long>()
        for ((epoch, future) in preloadFuturesByEpoch) {
            if (epoch == currentEpoch) {
                continue
            }
            if (preloadFuturesByEpoch.remove(epoch, future)) {
                future.cancel(true)
                preloadEpochInFlight.remove(epoch)
                cancelledEpochs.add(epoch)
            }
        }
        if (cancelledEpochs.isEmpty()) {
            return
        }
        val cancellation = CancellationException("Database preload canceled due to database configuration change.")
        for (epoch in cancelledEpochs) {
            val callbacks = preloadCallbacksByEpoch.remove(epoch)
            if (callbacks.isNullOrEmpty()) {
                continue
            }
            handler.post {
                for (callback in callbacks) {
                    try {
                        callback.onError(cancellation)
                    } catch (callbackError: Throwable) {
                        LitePalLog.e(TAG, "Database preload callback failed.", callbackError)
                    }
                }
            }
        }
    }

    @JvmStatic
    internal fun flushPendingDatabaseEvents() {
        if (pendingDatabaseEvents.isEmpty()) {
            return
        }
        val readHoldCount = DatabaseRuntimeLock.currentReadHoldCount()
        if (readHoldCount > 0) {
            repeat(readHoldCount) {
                DatabaseRuntimeLock.releaseReadLock()
            }
        }
        try {
            flushPendingDatabaseEventsInternal()
        } finally {
            if (readHoldCount > 0) {
                repeat(readHoldCount) {
                    DatabaseRuntimeLock.acquireReadLock()
                }
            }
        }
    }

    private fun flushPendingDatabaseEventsInternal() {
        if (listenerRegistration?.listener == null) {
            pendingDatabaseEvents.clear()
            return
        }
        while (true) {
            val event = pendingDatabaseEvents.poll() ?: break
            val registrationSnapshot = listenerRegistration ?: continue
            val expectedRegistrationId = registrationSnapshot.id
            when (event) {
                is DatabaseLifecycleEvent.Created -> {
                    dispatchMainThreadListener("onCreate") {
                        dispatchDatabaseEventIfCurrent(expectedRegistrationId, event)
                    }
                }

                is DatabaseLifecycleEvent.Upgraded -> {
                    dispatchMainThreadListener("onUpgrade") {
                        dispatchDatabaseEventIfCurrent(expectedRegistrationId, event)
                    }
                }
            }
        }
    }

    private fun dispatchDatabaseEventIfCurrent(expectedRegistrationId: Long, event: DatabaseLifecycleEvent) {
        val activeRegistration = listenerRegistration ?: return
        if (activeRegistration.id != expectedRegistrationId) {
            LitePalLog.d(
                TAG,
                "Skip stale database event dispatch for listener registrationId=$expectedRegistrationId."
            )
            return
        }
        when (event) {
            is DatabaseLifecycleEvent.Created -> activeRegistration.listener.onCreate()
            is DatabaseLifecycleEvent.Upgraded -> activeRegistration.listener.onUpgrade(event.oldVersion, event.newVersion)
        }
    }

    private fun removeVersionInSharedPreferences(dbName: String) {
        if (isDefaultDatabase(dbName)) {
            SharedUtil.removeVersion(null)
        } else {
            SharedUtil.removeVersion(dbName)
        }
    }

    private fun isDefaultDatabase(dbName: String?): Boolean {
        if (!BaseUtility.isLitePalXMLExists()) {
            return false
        }
        if (dbName.isNullOrBlank()) {
            return false
        }
        var name = dbName
        if (!name.endsWith(Const.Config.DB_NAME_SUFFIX)) {
            name += Const.Config.DB_NAME_SUFFIX
        }
        val config: LitePalConfig = LitePalParser.parseLitePalConfiguration()
        var defaultDbName = config.dbName ?: return false
        if (!defaultDbName.endsWith(Const.Config.DB_NAME_SUFFIX)) {
            defaultDbName += Const.Config.DB_NAME_SUFFIX
        }
        return name.equals(defaultDbName, ignoreCase = true)
    }

    private fun dispatchMainThreadListener(operation: String, callback: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
            return
        }
        val latch = CountDownLatch(1)
        handler.post {
            try {
                callback()
            } finally {
                latch.countDown()
            }
        }
        val completed = try {
            latch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LitePalLog.e(TAG, "Interrupted while waiting for main-thread database listener callback.", e)
            false
        }
        if (!completed) {
            LitePalLog.w(TAG, "Database listener $operation timed out while waiting on main thread.")
        }
    }

    @JvmStatic
    fun select(vararg columns: String?): FluentQuery {
        val query = FluentQuery()
        query.mColumns = columns.map { it }.toTypedArray()
        return query
    }

    @JvmStatic
    fun where(vararg conditions: String?): FluentQuery {
        val query = FluentQuery()
        query.mConditions = conditions.map { it }.toTypedArray()
        return query
    }

    @JvmStatic
    fun order(column: String?): FluentQuery {
        val query = FluentQuery()
        query.mOrderBy = column
        return query
    }

    @JvmStatic
    fun limit(value: Int): FluentQuery {
        val query = FluentQuery()
        query.mLimit = value.toString()
        return query
    }

    @JvmStatic
    fun offset(value: Int): FluentQuery {
        val query = FluentQuery()
        query.mOffset = value.toString()
        return query
    }

    @JvmStatic
    fun count(modelClass: Class<*>): Int {
        return count(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)))
    }


    @JvmStatic
    fun count(tableName: String?): Int {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val query = FluentQuery()
                query.count(tableName)
            }
        }
    }


    @JvmStatic
    fun average(modelClass: Class<*>, column: String): Double {
        return average(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)), column)
    }


    @JvmStatic
    fun average(tableName: String?, column: String): Double {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val query = FluentQuery()
                query.average(tableName, column)
            }
        }
    }


    @JvmStatic
    fun <T> max(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return max(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)), columnName, columnType)
    }


    @JvmStatic
    fun <T> max(tableName: String?, columnName: String, columnType: Class<T>): T {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val query = FluentQuery()
                query.max(tableName, columnName, columnType)
            }
        }
    }


    @JvmStatic
    fun <T> min(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return min(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)), columnName, columnType)
    }


    @JvmStatic
    fun <T> min(tableName: String?, columnName: String, columnType: Class<T>): T {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val query = FluentQuery()
                query.min(tableName, columnName, columnType)
            }
        }
    }


    @JvmStatic
    fun <T> sum(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return sum(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)), columnName, columnType)
    }


    @JvmStatic
    fun <T> sum(tableName: String?, columnName: String, columnType: Class<T>): T {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val query = FluentQuery()
                query.sum(tableName, columnName, columnType)
            }
        }
    }


    @JvmStatic
    fun <T> find(modelClass: Class<T>, id: Long): T? = find(modelClass, id, false)


    @JvmStatic
    fun <T> find(modelClass: Class<T>, id: Long, isEager: Boolean): T? {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onFind(modelClass, id, isEager)
            }
        }
    }


    @JvmStatic
    fun <T> findFirst(modelClass: Class<T>): T? = findFirst(modelClass, false)


    @JvmStatic
    fun <T> findFirst(modelClass: Class<T>, isEager: Boolean): T? {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onFindFirst(modelClass, isEager)
            }
        }
    }


    @JvmStatic
    fun <T> findLast(modelClass: Class<T>): T? = findLast(modelClass, false)


    @JvmStatic
    fun <T> findLast(modelClass: Class<T>, isEager: Boolean): T? {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onFindLast(modelClass, isEager)
            }
        }
    }


    @JvmStatic
    fun <T> findAll(modelClass: Class<T>, vararg ids: Long): List<T> = findAll(modelClass, false, *ids)


    @JvmStatic
    fun <T> findAll(modelClass: Class<T>, isEager: Boolean, vararg ids: Long): List<T> {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onFindAll(modelClass, isEager, *ids)
            }
        }
    }


    @JvmStatic
    fun findBySQL(vararg sql: String): Cursor? {
        return executeOnQueryPath {
            DatabaseRuntimeLock.withReadLock {
                BaseUtility.checkConditionsCorrect(*sql)
                if (sql.isEmpty()) {
                    return@withReadLock null
                }
                val selectionArgs = if (sql.size == 1) {
                    null
                } else {
                    Array(sql.size - 1) { index -> sql[index + 1] }
                }
                Connector.getDatabase().rawQuery(sql[0], selectionArgs)
            }
        }
    }

    @JvmStatic
    fun delete(modelClass: Class<*>, id: Long): Int {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val deleteHandler = DeleteHandler(db)
                    val rowsAffected = deleteHandler.onDelete(modelClass, id)
                    db.setTransactionSuccessful()
                    rowsAffected
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    @JvmStatic
    fun deleteAll(modelClass: Class<*>, vararg conditions: String?): Int {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val deleteHandler = DeleteHandler(db)
                    val rowsAffected = deleteHandler.onDeleteAll(modelClass, *conditions.map { it.orEmpty() }.toTypedArray())
                    db.setTransactionSuccessful()
                    rowsAffected
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    @JvmStatic
    fun deleteAll(tableName: String, vararg conditions: String?): Int {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val deleteHandler = DeleteHandler(Connector.getDatabase())
                deleteHandler.onDeleteAll(tableName, *conditions.map { it.orEmpty() }.toTypedArray())
            }
        }
    }


    @JvmStatic
    fun update(modelClass: Class<*>, values: ContentValues, id: Long): Int {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val updateHandler = UpdateHandler(Connector.getDatabase())
                updateHandler.onUpdate(modelClass, id, values)
            }
        }
    }


    @JvmStatic
    fun updateAll(modelClass: Class<*>, values: ContentValues, vararg conditions: String?): Int {
        return updateAll(BaseUtility.changeCase(DBUtility.getTableNameByClassName(modelClass.name)), values, *conditions)
    }


    @JvmStatic
    fun updateAll(tableName: String?, values: ContentValues, vararg conditions: String?): Int {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val updateHandler = UpdateHandler(Connector.getDatabase())
                updateHandler.onUpdateAll(tableName, values, *conditions.map { it.orEmpty() }.toTypedArray())
            }
        }
    }


    @JvmStatic
    fun <T : LitePalSupport> saveAll(collection: Collection<T>): Boolean {
        return executeOnWritePath {
            DatabaseRuntimeLock.withReadLock {
                val db = Connector.getDatabase()
                db.beginTransaction()
                try {
                    val saveHandler = SaveHandler(db)
                    saveHandler.onSaveAll(collection)
                    db.setTransactionSuccessful()
                    true
                } catch (e: Exception) {
                    LitePalRuntime.onError(TAG, "saveAll", e)
                    false
                } finally {
                    db.endTransaction()
                }
            }
        }
    }


    @JvmStatic
    fun <T : LitePalSupport> markAsDeleted(collection: Collection<T>) {
        for (item in collection) {
            item.clearSavedState()
        }
    }

    @JvmStatic
    fun <T> isExist(modelClass: Class<T>, vararg conditions: String?): Boolean {
        return executeOnQueryPath {
            conditions.isNotEmpty() && where(*conditions).count(modelClass) > 0
        }
    }

    @JvmStatic
    fun registerDatabaseListener(listener: DatabaseListener) {
        val nextId = listenerRegistrationId.incrementAndGet()
        var previous: ListenerRegistration? = null
        synchronized(listenerRegistrationLock) {
            previous = listenerRegistration
            listenerRegistration = ListenerRegistration(nextId, listener)
        }
        previous?.detachObserver()
    }

    @JvmStatic
    fun registerDatabaseListener(owner: LifecycleOwner, listener: DatabaseListener) {
        val nextId = listenerRegistrationId.incrementAndGet()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterDatabaseListenerInternal(nextId, clearPendingEvents = true)
            }
        }
        val registration = ListenerRegistration(nextId, listener, owner, observer)
        var previous: ListenerRegistration? = null
        synchronized(listenerRegistrationLock) {
            previous = listenerRegistration
            listenerRegistration = registration
        }
        previous?.detachObserver()
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            unregisterDatabaseListenerInternal(nextId, clearPendingEvents = true)
        }
    }

    @JvmStatic
    fun unregisterDatabaseListener() {
        unregisterDatabaseListenerInternal(null, clearPendingEvents = true)
    }

    private fun unregisterDatabaseListenerInternal(expectedRegistrationId: Long?, clearPendingEvents: Boolean) {
        var removed: ListenerRegistration? = null
        synchronized(listenerRegistrationLock) {
            val current = listenerRegistration ?: return@synchronized
            if (expectedRegistrationId != null && current.id != expectedRegistrationId) {
                return@synchronized
            }
            listenerRegistration = null
            removed = current
        }
        removed?.detachObserver()
        if (clearPendingEvents) {
            pendingDatabaseEvents.clear()
        }
    }

    @JvmStatic
    fun getDBListener(): DatabaseListener? = listenerRegistration?.listener

    @JvmStatic
    internal fun validateSchemaIfNeeded(database: SQLiteDatabase, epoch: Long) {
        val currentValidatedEpoch = schemaValidatedEpoch.get()
        if (currentValidatedEpoch == epoch) {
            return
        }
        if (!schemaValidatedEpoch.compareAndSet(currentValidatedEpoch, epoch)) {
            if (schemaValidatedEpoch.get() == epoch) {
                return
            }
        }
        SchemaValidationGate.validate(database)
    }
}

