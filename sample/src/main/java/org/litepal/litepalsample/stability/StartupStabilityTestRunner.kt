package org.litepal.litepalsample.stability

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.litepal.LitePal
import org.litepal.LitePalRuntime
import org.litepal.extension.runInTransaction
import org.litepal.extension.saveAll
import org.litepal.litepalsample.BuildConfig
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToLong

object StartupStabilityTestRunner {

    private const val TAG = "LitePalStartupStability"
    private val started = AtomicBoolean(false)

    internal enum class StressLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    internal data class StabilityConfig(
        val enabledInDebug: Boolean = true,
        val stressLevel: StressLevel = StressLevel.HIGH,
        val showFailureToast: Boolean = true,
        val maxSlowCaseTop: Int = 5
    )

    internal object DefaultConfig {
        val value = StabilityConfig()
    }

    internal data class CheckpointMetric(
        val name: String,
        val costMs: Long
    )

    internal data class CaseMetric(
        val caseName: String,
        val success: Boolean,
        val costMs: Long,
        val recordsWritten: Int,
        val recordsUpdated: Int,
        val recordsDeleted: Int,
        val assertions: Int,
        val checkpoints: List<CheckpointMetric>,
        val error: String?,
        val errorStackHead: String?
    )

    internal data class FailureDetail(
        val caseName: String,
        val message: String,
        val stackHead: String
    )

    internal data class RunMetric(
        val runId: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val totalMs: Long,
        val buildType: String,
        val stressLevel: StressLevel,
        val caseMetrics: List<CaseMetric>,
        val failures: List<FailureDetail>,
        val minMs: Long,
        val maxMs: Long,
        val avgMs: Long,
        val p50Ms: Long,
        val p95Ms: Long
    ) {
        val totalCases: Int
            get() = caseMetrics.size
        val passedCount: Int
            get() = caseMetrics.count { it.success }
        val failedCount: Int
            get() = totalCases - passedCount
    }

    internal fun runAsync(context: Context, config: StabilityConfig = DefaultConfig.value) {
        if (config.enabledInDebug && !BuildConfig.DEBUG) {
            return
        }
        if (!started.compareAndSet(false, true)) {
            return
        }
        val appContext = context.applicationContext
        Thread(
            {
                val suite = StartupStabilitySuite(config)
                val metric = suite.runAll()
                logRun(metric, config)
                showFailureToastIfNeeded(appContext, metric, config)
            },
            "litepal-startup-stability"
        ).start()
    }

    private fun showFailureToastIfNeeded(context: Context, metric: RunMetric, config: StabilityConfig) {
        if (!config.showFailureToast || metric.failedCount == 0) {
            return
        }
        val firstFailed = metric.failures.firstOrNull()?.caseName ?: "unknown_case"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "LitePal stability failed: ${metric.failedCount}, first=$firstFailed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun logRun(metric: RunMetric, config: StabilityConfig) {
        Log.i(
            TAG,
            "RUN_START|runId=${metric.runId}|timestamp=${metric.startEpochMs}|buildType=${metric.buildType}|thread=${Thread.currentThread().name}|stressLevel=${metric.stressLevel}"
        )

        for (caseMetric in metric.caseMetrics) {
            val checkpoints = caseMetric.checkpoints.joinToString(",") { "${it.name}:${it.costMs}ms" }
            val base = StringBuilder()
                .append("CASE_RESULT")
                .append("|runId=").append(metric.runId)
                .append("|caseName=").append(caseMetric.caseName)
                .append("|success=").append(caseMetric.success)
                .append("|costMs=").append(caseMetric.costMs)
                .append("|recordsWritten=").append(caseMetric.recordsWritten)
                .append("|recordsUpdated=").append(caseMetric.recordsUpdated)
                .append("|recordsDeleted=").append(caseMetric.recordsDeleted)
                .append("|assertions=").append(caseMetric.assertions)
                .append("|checkpoints=").append(sanitize(checkpoints))

            if (caseMetric.error != null) {
                base.append("|error=").append(sanitize(caseMetric.error))
                Log.e(TAG, base.toString())
            } else {
                Log.i(TAG, base.toString())
            }
        }

        Log.i(
            TAG,
            "RUN_SUMMARY|runId=${metric.runId}|totalCases=${metric.totalCases}|passed=${metric.passedCount}|failed=${metric.failedCount}|totalMs=${metric.totalMs}|avgMs=${metric.avgMs}|minMs=${metric.minMs}|maxMs=${metric.maxMs}|p50=${metric.p50Ms}|p95=${metric.p95Ms}"
        )

        val topSlow = metric.caseMetrics
            .sortedByDescending { it.costMs }
            .take(config.maxSlowCaseTop.coerceAtLeast(1))
        for ((index, item) in topSlow.withIndex()) {
            Log.i(TAG, "SLOW_TOP|runId=${metric.runId}|rank=${index + 1}|caseName=${item.caseName}|costMs=${item.costMs}")
        }

        if (metric.failures.isEmpty()) {
            Log.i(TAG, "FAILURES|runId=${metric.runId}|count=0")
        } else {
            Log.e(TAG, "FAILURES|runId=${metric.runId}|count=${metric.failures.size}")
            for (failure in metric.failures) {
                Log.e(
                    TAG,
                    "FAILURE_DETAIL|runId=${metric.runId}|caseName=${failure.caseName}|message=${sanitize(failure.message)}|stackHead=${sanitize(failure.stackHead)}"
                )
            }
        }

        Log.i(TAG, "RUN_END|runId=${metric.runId}|timestamp=${metric.endEpochMs}|totalMs=${metric.totalMs}")
    }

    private fun sanitize(text: String): String {
        return text.replace("|", "/").replace('\n', ' ').replace('\r', ' ')
    }

    private class StartupStabilitySuite(
        private val config: StabilityConfig
    ) {

        private val profile: StressProfile = StressProfile.from(config.stressLevel)
        private val runId: String = UUID.randomUUID().toString()
        private val runPrefix: String = "__startup_stability_${System.currentTimeMillis()}_${runId.substring(0, 8)}"

        private data class StressProfile(
            val bulkInsertCount: Int,
            val bulkPageSize: Int,
            val bulkUpdateCount: Int,
            val bulkDeleteCount: Int,
            val associationSingerCount: Int,
            val associationAlbumPerSinger: Int,
            val associationSongPerAlbum: Int,
            val transactionRepeat: Int,
            val concurrentWriterThreads: Int,
            val concurrentReaderThreads: Int,
            val concurrentWritesPerThread: Int,
            val concurrentReadLoops: Int
        ) {
            companion object {
                fun from(level: StressLevel): StressProfile {
                    return when (level) {
                        StressLevel.LOW -> StressProfile(
                            bulkInsertCount = 200,
                            bulkPageSize = 50,
                            bulkUpdateCount = 200,
                            bulkDeleteCount = 100,
                            associationSingerCount = 30,
                            associationAlbumPerSinger = 3,
                            associationSongPerAlbum = 4,
                            transactionRepeat = 50,
                            concurrentWriterThreads = 2,
                            concurrentReaderThreads = 2,
                            concurrentWritesPerThread = 50,
                            concurrentReadLoops = 40
                        )

                        StressLevel.MEDIUM -> StressProfile(
                            bulkInsertCount = 500,
                            bulkPageSize = 100,
                            bulkUpdateCount = 500,
                            bulkDeleteCount = 300,
                            associationSingerCount = 60,
                            associationAlbumPerSinger = 5,
                            associationSongPerAlbum = 6,
                            transactionRepeat = 100,
                            concurrentWriterThreads = 6,
                            concurrentReaderThreads = 4,
                            concurrentWritesPerThread = 80,
                            concurrentReadLoops = 80
                        )

                        StressLevel.HIGH -> StressProfile(
                            bulkInsertCount = 1200,
                            bulkPageSize = 100,
                            bulkUpdateCount = 1000,
                            bulkDeleteCount = 600,
                            associationSingerCount = 120,
                            associationAlbumPerSinger = 8,
                            associationSongPerAlbum = 10,
                            transactionRepeat = 200,
                            concurrentWriterThreads = 12,
                            concurrentReaderThreads = 8,
                            concurrentWritesPerThread = 100,
                            concurrentReadLoops = 120
                        )
                    }
                }
            }
        }

        private data class RunState(
            val runId: String,
            val runPrefix: String,
            val profile: StressProfile
        ) {
            fun casePrefix(caseName: String): String = "${runPrefix}_${caseName}"
        }

        private interface StabilityCase {
            val name: String
            fun run(state: RunState, metric: MutableCaseMetric)
        }

        private data class LambdaCase(
            override val name: String,
            private val body: (RunState, MutableCaseMetric) -> Unit
        ) : StabilityCase {
            override fun run(state: RunState, metric: MutableCaseMetric) {
                body(state, metric)
            }
        }

        private class MutableCaseMetric {
            var recordsWritten: Int = 0
            var recordsUpdated: Int = 0
            var recordsDeleted: Int = 0
            var assertions: Int = 0
            val checkpoints: MutableList<CheckpointMetric> = mutableListOf()
            fun addCheckpoint(name: String, costMs: Long) {
                checkpoints.add(CheckpointMetric(name, costMs))
            }
        }

        fun runAll(): RunMetric {
            val startMs = System.currentTimeMillis()
            val state = RunState(runId, runPrefix, profile)
            cleanupByPrefix()
            val caseMetrics = mutableListOf<CaseMetric>()
            val failures = mutableListOf<FailureDetail>()
            for (case in buildCases()) {
                val caseMetric = executeCase(state, case)
                caseMetrics.add(caseMetric)
                if (!caseMetric.success) {
                    failures.add(
                        FailureDetail(
                            caseName = case.name,
                            message = caseMetric.error ?: "unknown error",
                            stackHead = caseMetric.errorStackHead ?: "unknown"
                        )
                    )
                }
            }
            cleanupByPrefix()
            val endMs = System.currentTimeMillis()
            val costs = caseMetrics.map { it.costMs }
            return RunMetric(
                runId = runId,
                startEpochMs = startMs,
                endEpochMs = endMs,
                totalMs = endMs - startMs,
                buildType = if (BuildConfig.DEBUG) "debug" else "release",
                stressLevel = config.stressLevel,
                caseMetrics = caseMetrics,
                failures = failures,
                minMs = costs.minOrNull() ?: 0L,
                maxMs = costs.maxOrNull() ?: 0L,
                avgMs = if (costs.isEmpty()) 0L else costs.sum() / costs.size,
                p50Ms = percentile(costs, 50.0),
                p95Ms = percentile(costs, 95.0)
            )
        }

        private fun executeCase(state: RunState, case: StabilityCase): CaseMetric {
            val mutableMetric = MutableCaseMetric()
            val caseStart = System.currentTimeMillis()
            var error: String? = null
            var errorStackHead: String? = null
            var success = false
            try {
                case.run(state, mutableMetric)
                success = true
            } catch (t: Throwable) {
                error = "${t.javaClass.simpleName}: ${t.message ?: "no message"}"
                errorStackHead = t.stackTrace.firstOrNull()?.toString() ?: "n/a"
            }
            return CaseMetric(
                caseName = case.name,
                success = success,
                costMs = System.currentTimeMillis() - caseStart,
                recordsWritten = mutableMetric.recordsWritten,
                recordsUpdated = mutableMetric.recordsUpdated,
                recordsDeleted = mutableMetric.recordsDeleted,
                assertions = mutableMetric.assertions,
                checkpoints = mutableMetric.checkpoints.toList(),
                error = error,
                errorStackHead = errorStackHead
            )
        }

        private fun buildCases(): List<StabilityCase> {
            return listOf(
                LambdaCase("save_association_basic", this::caseSaveAssociationBasic),
                LambdaCase("query_aggregate_basic", this::caseQueryAggregateBasic),
                LambdaCase("update_delete_basic", this::caseUpdateDeleteBasic),
                LambdaCase("transaction_commit_basic", this::caseTransactionCommitBasic),
                LambdaCase("transaction_rollback_basic", this::caseTransactionRollbackBasic),
                LambdaCase("stress_bulk_insert_query", this::caseStressBulkInsertQuery),
                LambdaCase("stress_bulk_update_delete", this::caseStressBulkUpdateDelete),
                LambdaCase("stress_association_high_volume", this::caseStressAssociationHighVolume),
                LambdaCase("stress_transaction_repeat", this::caseStressTransactionRepeat),
                LambdaCase("stress_unique_conflict_rollback", this::caseStressUniqueConflictRollback),
                LambdaCase("stress_concurrent_read_write", this::caseStressConcurrentReadWrite)
            )
        }

        private fun caseSaveAssociationBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("save_association_basic")
            val singer = Singer().apply {
                name = "${prefix}_singer"
                age = 26
                isMale = true
            }
            requireCase(metric, singer.save(), "singer save failed")
            metric.recordsWritten++

            val album = Album().apply {
                name = "${prefix}_album"
                sales = 188
                publisher = "${prefix}_publisher"
                price = 49.9
                serial = "${prefix}_serial"
                release = Date()
                this.singer = singer
            }
            requireCase(metric, album.save(), "album save failed")
            metric.recordsWritten++

            val song1 = Song().apply {
                name = "${prefix}_song_1"
                lyric = "${prefix}_lyric_1"
                duration = "03:18"
                this.album = album
            }
            val song2 = Song().apply {
                name = "${prefix}_song_2"
                lyric = "${prefix}_lyric_2"
                duration = "03:22"
                this.album = album
            }
            requireCase(metric, song1.save(), "song1 save failed")
            requireCase(metric, song2.save(), "song2 save failed")
            metric.recordsWritten += 2

            val loadedAlbum = timedStep(metric, "load_album_eager") {
                LitePal.find(Album::class.java, album.id, true)
            }
            requireCase(metric, loadedAlbum != null, "album query failed")
            requireCase(metric, loadedAlbum?.singer?.id == singer.id, "album-singer association failed")
            val songCount = LitePal.where("album_id = ?", album.id.toString()).count("song")
            requireCase(metric, songCount == 2, "expected 2 songs but got $songCount")
        }

        private fun caseQueryAggregateBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("query_aggregate_basic")
            val singer1 = Singer().apply {
                name = "${prefix}_agg_1"
                age = 20
                isMale = true
            }
            val singer2 = Singer().apply {
                name = "${prefix}_agg_2"
                age = 30
                isMale = false
            }
            requireCase(metric, singer1.save(), "aggregate singer1 save failed")
            requireCase(metric, singer2.save(), "aggregate singer2 save failed")
            metric.recordsWritten += 2

            val condition = "${prefix}_agg_%"
            val count = LitePal.where("name like ?", condition).count(Singer::class.java)
            val maxAge = LitePal.where("name like ?", condition)
                .max(Singer::class.java, "age", Int::class.javaObjectType)
            val minAge = LitePal.where("name like ?", condition)
                .min(Singer::class.java, "age", Int::class.javaObjectType)
            val sumAge = LitePal.where("name like ?", condition)
                .sum(Singer::class.java, "age", Int::class.javaObjectType)
            val averageAge = LitePal.where("name like ?", condition)
                .average(Singer::class.java, "age")

            requireCase(metric, count == 2, "aggregate count expected 2 but got $count")
            requireCase(metric, maxAge == 30, "max age expected 30 but got $maxAge")
            requireCase(metric, minAge == 20, "min age expected 20 but got $minAge")
            requireCase(metric, sumAge == 50, "sum age expected 50 but got $sumAge")
            requireCase(metric, abs(averageAge - 25.0) < 0.00001, "average age expected 25.0 but got $averageAge")
        }

        private fun caseUpdateDeleteBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("update_delete_basic")
            val singer = Singer().apply {
                name = "${prefix}_update_target"
                age = 18
                isMale = true
            }
            requireCase(metric, singer.save(), "update singer save failed")
            metric.recordsWritten++

            val updater = Singer().apply {
                age = 41
            }
            val updateRows = updater.update(singer.id)
            metric.recordsUpdated += updateRows
            requireCase(metric, updateRows == 1, "update affected rows expected 1 but got $updateRows")
            val updatedSinger = LitePal.find(Singer::class.java, singer.id)
            requireCase(metric, updatedSinger?.age == 41, "updated age expected 41 but got ${updatedSinger?.age}")

            val song = Song().apply {
                name = "${prefix}_delete_song"
                lyric = "${prefix}_delete_lyric"
                duration = "02:59"
            }
            requireCase(metric, song.save(), "delete song save failed")
            metric.recordsWritten++
            val deletedRows = LitePal.delete(Song::class.java, song.id)
            metric.recordsDeleted += deletedRows
            requireCase(metric, deletedRows >= 1, "delete affected rows expected >=1 but got $deletedRows")
            val deletedSong = LitePal.find(Song::class.java, song.id)
            requireCase(metric, deletedSong == null, "song should not exist after delete")
        }

        private fun caseTransactionCommitBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("transaction_commit_basic")
            val commitName = "${prefix}_tx_commit"
            val committed = LitePal.runInTransaction {
                val singer = Singer().apply {
                    name = commitName
                    age = 36
                    isMale = true
                }
                singer.save()
            }
            requireCase(metric, committed, "transaction commit should return true")
            val count = LitePal.where("name = ?", commitName).count(Singer::class.java)
            requireCase(metric, count == 1, "commit data should be persisted")
            metric.recordsWritten++
        }

        private fun caseTransactionRollbackBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("transaction_rollback_basic")
            val rollbackName = "${prefix}_tx_rollback"
            val rollbackResult = LitePal.runInTransaction {
                val singer = Singer().apply {
                    name = rollbackName
                    age = 45
                    isMale = false
                }
                requireCase(metric, singer.save(), "rollback singer save failed")
                // In strict mode, throwing from transaction block will be escalated by runtime policy.
                // Use explicit `false` to verify rollback path deterministically across policies.
                false
            }
            requireCase(metric, !rollbackResult, "transaction rollback should return false")
            val count = LitePal.where("name = ?", rollbackName).count(Singer::class.java)
            requireCase(metric, count == 0, "rollback data should not be persisted")
            metric.recordsWritten++
        }

        private fun caseStressBulkInsertQuery(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_bulk_insert_query")
            val count = state.profile.bulkInsertCount
            val singers = (0 until count).map { index ->
                Singer().apply {
                    name = "${prefix}_bulk_$index"
                    age = index % 80
                    isMale = index % 2 == 0
                }
            }
            val saveAllResult = timedStep(metric, "bulk_save") { singers.saveAll() }
            requireCase(metric, saveAllResult, "bulk saveAll failed")
            metric.recordsWritten += count

            val actualCount = timedStep(metric, "bulk_count") {
                LitePal.where("name like ?", "${prefix}_bulk_%").count(Singer::class.java)
            }
            requireCase(metric, actualCount == count, "bulk count expected $count but got $actualCount")

            var offset = 0
            var pagedTotal = 0
            while (true) {
                val page = LitePal.where("name like ?", "${prefix}_bulk_%")
                    .order("id asc")
                    .limit(state.profile.bulkPageSize)
                    .offset(offset)
                    .find(Singer::class.java)
                if (page.isEmpty()) {
                    break
                }
                pagedTotal += page.size
                offset += page.size
            }
            requireCase(metric, pagedTotal == count, "paged query expected $count but got $pagedTotal")

            val maxAge = LitePal.where("name like ?", "${prefix}_bulk_%")
                .max(Singer::class.java, "age", Int::class.javaObjectType)
            val minAge = LitePal.where("name like ?", "${prefix}_bulk_%")
                .min(Singer::class.java, "age", Int::class.javaObjectType)
            val sumAge = LitePal.where("name like ?", "${prefix}_bulk_%")
                .sum(Singer::class.java, "age", Int::class.javaObjectType)
            val averageAge = LitePal.where("name like ?", "${prefix}_bulk_%")
                .average(Singer::class.java, "age")

            val cycleSum = 3160
            val fullCycles = count / 80
            val remain = count % 80
            val expectedSum = fullCycles * cycleSum + remain * (remain - 1) / 2
            requireCase(metric, maxAge == 79, "bulk max age expected 79 but got $maxAge")
            requireCase(metric, minAge == 0, "bulk min age expected 0 but got $minAge")
            requireCase(metric, sumAge == expectedSum, "bulk sum age expected $expectedSum but got $sumAge")
            val expectedAverage = expectedSum.toDouble() / count
            requireCase(metric, abs(averageAge - expectedAverage) < 0.00001, "bulk average age mismatch")
        }

        private fun caseStressBulkUpdateDelete(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_bulk_update_delete")
            val toDelete = state.profile.bulkDeleteCount
            val toUpdate = state.profile.bulkUpdateCount
            val untouched = 200
            val total = toUpdate + untouched
            val groupB = toUpdate - toDelete

            val singers = mutableListOf<Singer>()
            for (i in 0 until toDelete) {
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_$i"
                        age = 10
                        isMale = true
                    }
                )
            }
            for (i in 0 until groupB) {
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_${i + toDelete}"
                        age = 10
                        isMale = false
                    }
                )
            }
            for (i in 0 until untouched) {
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_${i + toUpdate}"
                        age = 30
                        isMale = false
                    }
                )
            }
            requireCase(metric, singers.saveAll(), "stress update/delete saveAll failed")
            metric.recordsWritten += total

            val values = ContentValues().apply {
                put("age", 66)
            }
            // Use ContentValues update to avoid overriding unrelated fields (name/isMale) to defaults.
            val rowsUpdated = LitePal.updateAll(
                Singer::class.java,
                values,
                "name like ? and age = ?",
                "${prefix}_u_%",
                "10"
            )
            metric.recordsUpdated += rowsUpdated
            requireCase(metric, rowsUpdated == toUpdate, "expected update rows $toUpdate but got $rowsUpdated")

            val rowsDeleted = LitePal.deleteAll(
                Singer::class.java,
                "name like ? and age = ? and ismale = ?",
                "${prefix}_u_%",
                "66",
                "1"
            )
            metric.recordsDeleted += rowsDeleted
            requireCase(metric, rowsDeleted == toDelete, "expected delete rows $toDelete but got $rowsDeleted")

            val remaining = LitePal.where("name like ?", "${prefix}_u_%").count(Singer::class.java)
            val updatedRemaining = LitePal.where("name like ? and age = ?", "${prefix}_u_%", "66").count(Singer::class.java)
            val untouchedRemaining = LitePal.where("name like ? and age = ?", "${prefix}_u_%", "30").count(Singer::class.java)

            requireCase(metric, remaining == total - toDelete, "remaining count mismatch")
            requireCase(metric, updatedRemaining == groupB, "updated remaining count mismatch")
            requireCase(metric, untouchedRemaining == untouched, "untouched remaining count mismatch")
        }

        private fun caseStressAssociationHighVolume(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_association_high_volume")
            val singerCount = state.profile.associationSingerCount
            val albumPerSinger = state.profile.associationAlbumPerSinger
            val songPerAlbum = state.profile.associationSongPerAlbum

            val singers = (0 until singerCount).map { index ->
                Singer().apply {
                    name = "${prefix}_singer_$index"
                    age = 20 + index % 30
                    isMale = index % 2 == 0
                }
            }
            requireCase(metric, timedStep(metric, "save_singers") { singers.saveAll() }, "save singers failed")
            metric.recordsWritten += singers.size

            val albums = mutableListOf<Album>()
            for (singerIndex in singers.indices) {
                val singer = singers[singerIndex]
                for (albumIndex in 0 until albumPerSinger) {
                    albums.add(
                        Album().apply {
                            name = "${prefix}_album_${singerIndex}_$albumIndex"
                            sales = 100 + albumIndex
                            publisher = "${prefix}_pub_$albumIndex"
                            price = 19.9 + albumIndex
                            serial = "${prefix}_serial_${singerIndex}_$albumIndex"
                            release = Date()
                            this.singer = singer
                        }
                    )
                }
            }
            requireCase(metric, timedStep(metric, "save_albums") { albums.saveAll() }, "save albums failed")
            metric.recordsWritten += albums.size

            val songs = mutableListOf<Song>()
            for (albumIndex in albums.indices) {
                val album = albums[albumIndex]
                for (songIndex in 0 until songPerAlbum) {
                    songs.add(
                        Song().apply {
                            name = "${prefix}_song_${albumIndex}_$songIndex"
                            lyric = "${prefix}_lyric_${albumIndex}_$songIndex"
                            duration = "03:${(10 + songIndex).toString().padStart(2, '0')}"
                            this.album = album
                        }
                    )
                }
            }
            requireCase(metric, timedStep(metric, "save_songs") { songs.saveAll() }, "save songs failed")
            metric.recordsWritten += songs.size

            val expectedAlbumCount = singerCount * albumPerSinger
            val expectedSongCount = expectedAlbumCount * songPerAlbum
            val realSingerCount = LitePal.where("name like ?", "${prefix}_singer_%").count(Singer::class.java)
            val realAlbumCount = LitePal.where("name like ?", "${prefix}_album_%").count(Album::class.java)
            val realSongCount = LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
            requireCase(metric, realSingerCount == singerCount, "singer count mismatch")
            requireCase(metric, realAlbumCount == expectedAlbumCount, "album count mismatch")
            requireCase(metric, realSongCount == expectedSongCount, "song count mismatch")

            val eagerAlbum = LitePal.where("name like ?", "${prefix}_album_%")
                .order("id asc")
                .findFirst(Album::class.java, true)
            requireCase(metric, eagerAlbum != null, "eager album query failed")
            requireCase(metric, eagerAlbum?.singer != null, "eager singer relation missing")

            val firstSinger = singers.first()
            val firstSingerAlbumCount = LitePal.where("singer_id = ?", firstSinger.id.toString()).count("album")
            requireCase(metric, firstSingerAlbumCount == albumPerSinger, "first singer album count mismatch")
        }

        private fun caseStressTransactionRepeat(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_transaction_repeat")
            val repeat = state.profile.transactionRepeat
            var expectedCommitted = 0
            for (i in 0 until repeat) {
                val shouldCommit = i % 2 == 0
                if (shouldCommit) {
                    expectedCommitted++
                }
                val result = LitePal.runInTransaction {
                    val singer = Singer().apply {
                        name = "${prefix}_tx_$i"
                        age = 20 + i % 60
                        isMale = i % 2 == 0
                    }
                    requireCase(metric, singer.save(), "transaction save failed on $i")
                    shouldCommit
                }
                metric.recordsWritten++
                requireCase(metric, result == shouldCommit, "transaction result mismatch at $i")
            }
            val actualCommitted = LitePal.where("name like ?", "${prefix}_tx_%").count(Singer::class.java)
            requireCase(metric, actualCommitted == expectedCommitted, "transaction committed count mismatch")
        }

        private fun caseStressUniqueConflictRollback(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_unique_conflict_rollback")
            val songs = (0 until 40).map { index ->
                Song().apply {
                    name = "${prefix}_song_$index"
                    lyric = "${prefix}_lyric_${index % 10}"
                    duration = "03:33"
                }
            }
            val saveResult = timedStep(metric, "save_conflict_batch") {
                LitePalRuntime.withSilentErrorLog {
                    runCatching { songs.saveAll() }.getOrDefault(false)
                }
            }
            metric.recordsWritten += songs.size
            requireCase(metric, !saveResult, "unique conflict should fail saveAll")
            val songsCount = LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
            val lyricsCount = LitePal.where("lyric like ?", "${prefix}_lyric_%").count(Song::class.java)
            requireCase(metric, songsCount == 0, "song rows should rollback to 0")
            requireCase(metric, lyricsCount == 0, "lyric rows should rollback to 0")
        }

        private fun caseStressConcurrentReadWrite(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_concurrent_read_write")
            val writerThreads = state.profile.concurrentWriterThreads
            val readerThreads = state.profile.concurrentReaderThreads
            val writesPerThread = state.profile.concurrentWritesPerThread
            val readLoops = state.profile.concurrentReadLoops
            val pool = Executors.newFixedThreadPool(writerThreads + readerThreads)
            val startGate = CountDownLatch(1)
            val doneGate = CountDownLatch(writerThreads + readerThreads)
            val errors = ConcurrentLinkedQueue<Throwable>()
            val written = AtomicInteger(0)

            repeat(writerThreads) { writerIndex ->
                pool.execute {
                    try {
                        startGate.await()
                        for (i in 0 until writesPerThread) {
                            val singer = Singer().apply {
                                name = "${prefix}_w${writerIndex}_$i"
                                age = 18 + (i % 40)
                                isMale = (writerIndex + i) % 2 == 0
                            }
                            if (!singer.save()) {
                                throw IllegalStateException("concurrent writer save failed: writer=$writerIndex, i=$i")
                            }
                            written.incrementAndGet()
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    } finally {
                        doneGate.countDown()
                    }
                }
            }

            repeat(readerThreads) { readerIndex ->
                pool.execute {
                    try {
                        startGate.await()
                        var previous = 0
                        for (i in 0 until readLoops) {
                            val count = LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
                            if (count < previous) {
                                throw IllegalStateException("reader observed decreasing count: reader=$readerIndex, previous=$previous, current=$count")
                            }
                            previous = count
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    } finally {
                        doneGate.countDown()
                    }
                }
            }

            timedStep(metric, "concurrent_run") {
                startGate.countDown()
                val completed = doneGate.await(180, TimeUnit.SECONDS)
                requireCase(metric, completed, "concurrent test timeout")
            }
            pool.shutdownNow()

            requireCase(metric, errors.isEmpty(), "concurrent errors: ${errors.joinToString("; ") { it.message ?: "unknown" }}")
            val expected = writerThreads * writesPerThread
            val actual = LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
            requireCase(metric, written.get() == expected, "writer counter mismatch")
            requireCase(metric, actual == expected, "concurrent final count expected $expected but got $actual")

            val names = LitePal.where("name like ?", "${prefix}_w%").find(Singer::class.java).mapNotNull { it.name }
            requireCase(metric, names.size == expected, "concurrent query result size mismatch")
            requireCase(metric, names.toSet().size == expected, "concurrent names should be unique")
            metric.recordsWritten += expected
        }

        private fun cleanupByPrefix() {
            LitePal.deleteAll(Song::class.java, "name like ?", "${runPrefix}%")
            LitePal.deleteAll(Song::class.java, "lyric like ?", "${runPrefix}%")
            LitePal.deleteAll(Album::class.java, "name like ?", "${runPrefix}%")
            LitePal.deleteAll(Album::class.java, "serial like ?", "${runPrefix}%")
            LitePal.deleteAll(Singer::class.java, "name like ?", "${runPrefix}%")
        }

        private fun percentile(values: List<Long>, p: Double): Long {
            if (values.isEmpty()) {
                return 0L
            }
            val sorted = values.sorted()
            val rank = ((p / 100.0) * (sorted.size - 1)).roundToLong().toInt().coerceIn(0, sorted.lastIndex)
            return sorted[rank]
        }

        private inline fun <T> timedStep(metric: MutableCaseMetric, checkpoint: String, block: () -> T): T {
            val start = System.currentTimeMillis()
            return try {
                block()
            } finally {
                metric.addCheckpoint(checkpoint, System.currentTimeMillis() - start)
            }
        }

        private fun requireCase(metric: MutableCaseMetric, condition: Boolean, message: String) {
            metric.assertions++
            if (!condition) {
                throw IllegalStateException(message)
            }
        }
    }
}
