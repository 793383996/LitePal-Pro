package org.litepal.stability

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.stability.model.RuntimeAlbum
import org.litepal.stability.model.RuntimeArtist
import org.litepal.stability.model.RuntimeUser
import org.litepal.util.Const
import org.litepal.util.DBUtility
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class CorePerformanceBaselineInstrumentationTest {

    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LitePal.initialize(context)
        LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
        LitePal.resetRuntimeMetrics()
    }

    @After
    fun tearDown() {
        LitePal.unregisterDatabaseListener()
        LitePal.useDefault()
        for (dbName in createdDbNames) {
            LitePal.deleteDatabase(dbName)
        }
        createdDbNames.clear()
    }

    @Test
    fun baseline_coldAndWarmDatabaseOpen() {
        val coldSamples = ArrayList<Long>()
        val warmSamples = ArrayList<Long>()
        var coldFailureCount = 0
        var warmFailureCount = 0

        repeat(5) { index ->
            val dbName = "perf_cold_${System.currentTimeMillis()}_$index"
            createdDbNames.add(dbName)
            LitePal.use(newDb(dbName, RuntimeUser::class.java.name))
            try {
                coldSamples.add(elapsedMs { LitePal.getDatabase() })
            } catch (_: Throwable) {
                coldFailureCount++
            }
        }

        val warmDbName = "perf_warm_${System.currentTimeMillis()}"
        createdDbNames.add(warmDbName)
        LitePal.use(newDb(warmDbName, RuntimeUser::class.java.name))
        LitePal.getDatabase()
        repeat(20) {
            try {
                warmSamples.add(elapsedMs { LitePal.getDatabase() })
            } catch (_: Throwable) {
                warmFailureCount++
            }
        }

        val coldMetrics = metricsFrom(coldSamples, coldFailureCount, sqlCount = -1L)
        val warmMetrics = metricsFrom(warmSamples, warmFailureCount, sqlCount = -1L)
        logMetrics("cold_open", coldMetrics)
        logMetrics("warm_open", warmMetrics)

        assertTrue(coldMetrics.failureRate == 0.0)
        assertTrue(warmMetrics.failureRate == 0.0)
    }

    @Test
    fun baseline_eagerQueryWithSqlTrace() {
        val dbName = "perf_eager_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)
        LitePal.use(newDb(dbName, RuntimeArtist::class.java.name, RuntimeAlbum::class.java.name))
        val db = LitePal.getDatabase()
        seedArtistAlbumData()

        val samples = ArrayList<Long>()
        var failureCount = 0
        val sqlCounter = AtomicLong(0)
        val clearTrace = attachSqlTrace(db, sqlCounter)
        try {
            repeat(10) {
                try {
                    val elapsed = elapsedMs {
                        val result = LitePal.findAll(RuntimeArtist::class.java, true)
                        if (result.isEmpty()) {
                            error("eager query returned empty result")
                        }
                    }
                    samples.add(elapsed)
                } catch (_: Throwable) {
                    failureCount++
                }
            }
        } finally {
            clearTrace?.invoke()
        }

        val metrics = metricsFrom(samples, failureCount, sqlCounter.get())
        logMetrics("eager_query", metrics)
        assertTrue(metrics.failureRate == 0.0)
    }

    @Test
    fun baseline_schemaSnapshotOnLargeSchema() {
        val dbName = "perf_schema_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)
        LitePal.use(newDb(dbName, RuntimeUser::class.java.name))
        val db = LitePal.getDatabase()
        createLargeSchema(db, tableCount = 80)

        val samples = ArrayList<Long>()
        var failureCount = 0
        val sqlCounter = AtomicLong(0)
        val clearTrace = attachSqlTrace(db, sqlCounter)
        try {
            repeat(20) {
                try {
                    samples.add(
                        elapsedMs {
                            DBUtility.beginTableSnapshotSession(db)
                            DBUtility.endTableSnapshotSession(db)
                        }
                    )
                } catch (_: Throwable) {
                    failureCount++
                }
            }
        } finally {
            clearTrace?.invoke()
        }

        val metrics = metricsFrom(samples, failureCount, sqlCounter.get())
        logMetrics("schema_snapshot", metrics)
        assertTrue(metrics.failureRate == 0.0)
    }

    @Test
    fun baseline_concurrentSwitchGetDatabase() {
        val dbNameA = "perf_switch_a_${System.currentTimeMillis()}"
        val dbNameB = "perf_switch_b_${System.currentTimeMillis()}"
        createdDbNames.add(dbNameA)
        createdDbNames.add(dbNameB)

        val dbA = newDb(dbNameA, RuntimeUser::class.java.name)
        val dbB = newDb(dbNameB, RuntimeUser::class.java.name)
        LitePal.use(dbA)
        LitePal.getDatabase()
        LitePal.use(dbB)
        LitePal.getDatabase()
        LitePal.use(dbA)

        val samples = Collections.synchronizedList(mutableListOf<Long>())
        val failureCount = AtomicLong(0L)
        val doneLatch = CountDownLatch(2)

        Thread {
            try {
                repeat(500) {
                    val elapsed = elapsedMs {
                        LitePal.getDatabase()
                    }
                    samples.add(elapsed)
                }
            } catch (_: Throwable) {
                failureCount.incrementAndGet()
            } finally {
                doneLatch.countDown()
            }
        }.start()

        Thread {
            try {
                repeat(500) { index ->
                    LitePal.use(if (index % 2 == 0) dbA else dbB)
                }
            } catch (_: Throwable) {
                failureCount.incrementAndGet()
            } finally {
                doneLatch.countDown()
            }
        }.start()

        assertTrue(doneLatch.await(40, TimeUnit.SECONDS))
        val metrics = metricsFrom(samples.toList(), failureCount.get().toInt(), sqlCount = -1L)
        logMetrics("concurrent_switch_getdb", metrics)
        assertTrue(metrics.failureRate == 0.0)
    }

    private fun seedArtistAlbumData() {
        LitePal.deleteAll(RuntimeAlbum::class.java)
        LitePal.deleteAll(RuntimeArtist::class.java)
        for (i in 0 until 40) {
            val artist = RuntimeArtist()
            artist.name = "artist_$i"
            artist.save()
            for (j in 0 until 8) {
                val album = RuntimeAlbum()
                album.name = "album_${i}_$j"
                album.artist = artist
                album.tags = mutableListOf("tagA", "tagB", "tagC")
                album.save()
            }
        }
    }

    private fun createLargeSchema(db: SQLiteDatabase, tableCount: Int) {
        db.execSQL(
            "create table if not exists ${Const.TableSchema.TABLE_NAME} (" +
                "id integer primary key autoincrement, " +
                "${Const.TableSchema.COLUMN_NAME} text, " +
                "${Const.TableSchema.COLUMN_TYPE} integer)"
        )
        for (i in 0 until tableCount) {
            val tableName = "perf_table_$i"
            db.execSQL("create table if not exists $tableName (id integer primary key, c$i text)")
            db.execSQL(
                "insert or replace into ${Const.TableSchema.TABLE_NAME} " +
                    "(${Const.TableSchema.COLUMN_NAME}, ${Const.TableSchema.COLUMN_TYPE}) values (?, ?)",
                arrayOf<Any>(tableName, Const.TableSchema.NORMAL_TABLE)
            )
        }
    }

    private fun newDb(name: String, vararg classNames: String): LitePalDB {
        val db = LitePalDB(name, 1)
        for (className in classNames) {
            db.addClassName(className)
        }
        return db
    }

    private fun attachSqlTrace(db: SQLiteDatabase, counter: AtomicLong): (() -> Unit)? {
        val traceMethod = db.javaClass.methods.firstOrNull { method ->
            method.name == "setTraceCallback" && method.parameterTypes.size == 1
        } ?: return null
        val callbackType = traceMethod.parameterTypes[0]
        val callback = Proxy.newProxyInstance(
            callbackType.classLoader,
            arrayOf(callbackType)
        ) { _, _, _ ->
            counter.incrementAndGet()
            null
        }
        return try {
            traceMethod.invoke(db, callback)
            val clearAction: () -> Unit = {
                try {
                    traceMethod.invoke(db, null)
                } catch (_: Throwable) {
                    // no-op
                }
            }
            clearAction
        } catch (_: Throwable) {
            null
        }
    }

    private fun elapsedMs(block: () -> Unit): Long {
        val startNs = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
    }

    private fun metricsFrom(samples: List<Long>, failureCount: Int, sqlCount: Long): Metrics {
        val total = samples.size + failureCount
        val sorted = samples.sorted()
        val avgMs = if (sorted.isEmpty()) 0.0 else sorted.average()
        val p95Ms = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 95) / 100].toDouble()
        val failureRate = if (total == 0) 0.0 else failureCount.toDouble() / total.toDouble()
        return Metrics(
            avgMs = avgMs,
            p95Ms = p95Ms,
            sqlCount = sqlCount,
            failureRate = failureRate,
            generatedHitCount = LitePal.getGeneratedPathHitCount(),
            reflectionFallbackCount = LitePal.getReflectionFallbackCount(),
            mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs()
        )
    }

    private fun logMetrics(name: String, metrics: Metrics) {
        Log.i(
            "CorePerfBaseline",
            "$name avg=${"%.2f".format(metrics.avgMs)}ms, " +
                "p95=${"%.2f".format(metrics.p95Ms)}ms, " +
                "sqlCount=${metrics.sqlCount}, " +
                "failureRate=${"%.4f".format(metrics.failureRate)}, " +
                "generatedHitCount=${metrics.generatedHitCount}, " +
                "reflectionFallbackCount=${metrics.reflectionFallbackCount}, " +
                "mainThreadBlockMs=${metrics.mainThreadBlockMs}"
        )
    }

    private data class Metrics(
        val avgMs: Double,
        val p95Ms: Double,
        val sqlCount: Long,
        val failureRate: Double,
        val generatedHitCount: Long,
        val reflectionFallbackCount: Long,
        val mainThreadBlockMs: Long
    )
}
