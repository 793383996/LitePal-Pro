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

import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import org.litepal.LitePalApplication
import org.litepal.LitePalRuntime
import org.litepal.Operator
import org.litepal.parser.LitePalConfig
import org.litepal.parser.LitePalAttr
import org.litepal.parser.LitePalParser
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.LitePalLog
import java.io.File

/**
 * Connector that builds and caches a [LitePalOpenHelper].
 */
object Connector {

    private const val TAG = "Connector"
    private const val MAX_CONFIG_EPOCH_RETRY = 5

    @Volatile
    private var litePalHelper: LitePalOpenHelper? = null
    private val helperInitLock = Any()
    private val coldOpenDispatchLock = Any()

    @JvmStatic
    fun getWritableDatabase(): SQLiteDatabase {
        val allowMainThreadDirectOpen = LitePalRuntime.getRuntimeOptions().allowMainThreadAccess &&
            Looper.myLooper() == Looper.getMainLooper()
        if (allowMainThreadDirectOpen) {
            // Avoid a self-inflicted wait cycle:
            // main thread blocks on FutureTask#get() while DB listener callbacks are waiting to run on main.
            return openWritableDatabaseWithEpochValidation()
        }
        return LitePalRuntime.executeOnTransactionExecutor {
            openWritableDatabaseWithEpochValidation()
        }
    }

    @JvmStatic
    fun getDatabase(): SQLiteDatabase = getWritableDatabase()

    private fun openWritableDatabaseWithEpochValidation(): SQLiteDatabase {
        repeat(MAX_CONFIG_EPOCH_RETRY) { attempt ->
            val epochBefore = Operator.currentDbConfigEpoch()
            val database = openWritableDatabaseWithScheduling()
            Operator.flushPendingDatabaseEvents()
            val epochAfter = Operator.currentDbConfigEpoch()
            if (epochAfter == epochBefore) {
                Operator.validateSchemaIfNeeded(database, epochAfter)
                return database
            }
            LitePalLog.w(
                TAG,
                "Database config changed during open/flush (epoch $epochBefore -> $epochAfter), retrying (${attempt + 1}/$MAX_CONFIG_EPOCH_RETRY)."
            )
        }
        LitePalLog.w(
            TAG,
            "Database config keeps changing after $MAX_CONFIG_EPOCH_RETRY retries; returning latest reopened database."
        )
        var fallbackEpoch = Operator.currentDbConfigEpoch()
        var fallbackDatabase = openWritableDatabaseWithScheduling()
        Operator.flushPendingDatabaseEvents()
        var epochAfterFallback = Operator.currentDbConfigEpoch()
        if (epochAfterFallback != fallbackEpoch) {
            LitePalLog.w(
                TAG,
                "Database config changed again in fallback open/flush (epoch $fallbackEpoch -> $epochAfterFallback)."
            )
            fallbackEpoch = epochAfterFallback
            fallbackDatabase = openWritableDatabaseWithScheduling()
            Operator.flushPendingDatabaseEvents()
            val epochAfterFinalReopen = Operator.currentDbConfigEpoch()
            if (epochAfterFinalReopen != fallbackEpoch) {
                LitePalLog.w(
                    TAG,
                    "Database config changed again after final fallback reopen (epoch $fallbackEpoch -> $epochAfterFinalReopen)."
                )
            }
            Operator.validateSchemaIfNeeded(fallbackDatabase, epochAfterFinalReopen)
            return fallbackDatabase
        }
        Operator.validateSchemaIfNeeded(fallbackDatabase, epochAfterFallback)
        return fallbackDatabase
    }

    private fun openWritableDatabaseWithScheduling(): SQLiteDatabase {
        LitePalRuntime.onDatabaseMainThreadAccess("openWritableDatabase")
        if (litePalHelper == null && Looper.myLooper() == Looper.getMainLooper()) {
            LitePalLog.w(
                TAG,
                "Cold database open is running on main thread. Consider calling LitePal.preloadDatabase() earlier to reduce blocking."
            )
        }
        if (litePalHelper != null) {
            return DatabaseRuntimeLock.withReadLock {
                buildConnection().writableDatabase
            }
        }
        synchronized(coldOpenDispatchLock) {
            return DatabaseRuntimeLock.withReadLock {
                buildConnection().writableDatabase
            }
        }
    }

    private fun buildConnection(): LitePalOpenHelper {
        val litePalAttr = LitePalAttr.getInstance()
        litePalAttr.checkSelfValid()
        val existing = litePalHelper
        if (existing != null) {
            return existing
        }
        synchronized(helperInitLock) {
            val recheck = litePalHelper
            if (recheck != null) {
                return recheck
            }
            val dbPath = resolveDatabasePath(
                dbName = litePalAttr.dbName.orEmpty(),
                storage = litePalAttr.storage,
                createDir = true
            )
            ensureDatabaseParentDir(dbPath)
            return LitePalOpenHelper(dbPath, litePalAttr.version).also { helper ->
                litePalHelper = helper
            }
        }
    }

    @JvmStatic
    fun resolveDatabaseFile(dbName: String, storage: String?): File {
        val normalizedDbName = normalizeDatabaseName(dbName)
        if (storage.isNullOrBlank() || storage.equals("internal", ignoreCase = true)) {
            return LitePalApplication.getContext().getDatabasePath(normalizedDbName)
        }
        return File(resolveDatabasePath(normalizedDbName, storage, createDir = false))
    }

    @JvmStatic
    fun resolveDeleteCandidates(dbName: String): List<File> {
        val normalizedDbName = normalizeDatabaseName(dbName)
        val candidates = LinkedHashSet<String>()
        val context = LitePalApplication.getContext()
        candidates.add(context.getDatabasePath(normalizedDbName).absolutePath)

        val candidateStorages = LinkedHashSet<String?>()
        val attrStorage = LitePalAttr.getInstance().storage
        candidateStorages.add(attrStorage)
        candidateStorages.add("external")

        if (BaseUtility.isLitePalXMLExists()) {
            val config: LitePalConfig = LitePalParser.parseLitePalConfiguration()
            candidateStorages.add(config.storage)
        }

        for (storage in candidateStorages) {
            if (storage.isNullOrBlank() || storage.equals("internal", ignoreCase = true)) {
                continue
            }
            val path = resolveDatabasePath(normalizedDbName, storage, createDir = false)
            candidates.add(path)
        }
        return candidates.map(::File)
    }

    @JvmStatic
    fun normalizeDatabaseName(dbName: String): String {
        return if (dbName.endsWith(Const.Config.DB_NAME_SUFFIX)) {
            dbName
        } else {
            "$dbName${Const.Config.DB_NAME_SUFFIX}"
        }
    }

    private fun resolveDatabasePath(dbName: String, storage: String?, createDir: Boolean): String {
        if (storage.isNullOrBlank() || storage.equals("internal", ignoreCase = true)) {
            return dbName
        }
        val externalRoot = LitePalApplication.getContext().getExternalFilesDir(null) ?: run {
            LitePalLog.d(TAG, "External files dir is null, fallback to internal database path.")
            return dbName
        }
        val baseDir = if (storage.equals("external", ignoreCase = true)) {
            File(externalRoot, "databases")
        } else {
            // Legacy custom storage paths are normalized into app-scoped external storage.
            val normalizedStorage = storage
                .replace('\\', '/')
                .trim()
                .removePrefix("/")
                .replace("../", "")
            LitePalLog.d(
                TAG,
                "Legacy storage value '$storage' normalized to app-scoped path '$normalizedStorage'."
            )
            File(externalRoot, normalizedStorage)
        }
        if (createDir && !baseDir.exists()) {
            baseDir.mkdirs()
        }
        return File(baseDir, dbName).absolutePath
    }

    private fun ensureDatabaseParentDir(dbPath: String) {
        val dbFile = if (File(dbPath).isAbsolute) {
            File(dbPath)
        } else {
            LitePalApplication.getContext().getDatabasePath(dbPath)
        }
        val parentDir = dbFile.parentFile ?: return
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
    }

    @JvmStatic
    fun clearLitePalOpenHelperInstance() {
        DatabaseRuntimeLock.withWriteLock {
            val helper = litePalHelper
            litePalHelper = null
            if (helper != null) {
                try {
                    helper.close()
                } catch (t: Throwable) {
                    LitePalLog.e(TAG, "Failed to close LitePalOpenHelper cleanly.", t)
                }
            }
        }
    }
}
