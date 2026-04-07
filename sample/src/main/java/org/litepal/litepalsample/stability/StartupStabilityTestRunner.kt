package org.litepal.litepalsample.stability

import android.content.Context
import android.content.ContentValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToLong

object StartupStabilityTestRunner {

    private val started = AtomicBoolean(false)
    private val cancellationSignals = ConcurrentHashMap<String, AtomicBoolean>()

    enum class StressLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    data class StabilityConfig(
        val enabledInDebug: Boolean = true,
        val stressLevel: StressLevel = StressLevel.HIGH,
        val showFailureToast: Boolean = true,
        val maxSlowCaseTop: Int = 5
    )

    object DefaultConfig {
        val value = StabilityConfig()
    }

    data class CheckpointMetric(
        val name: String,
        val costMs: Long
    )

    enum class CaseStatus {
        PASSED,
        FAILED,
        CANCELLED
    }

    data class CaseMetric(
        val caseId: String,
        val caseName: String,
        val status: CaseStatus,
        val success: Boolean,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val costMs: Long,
        val recordsWritten: Int,
        val recordsUpdated: Int,
        val recordsDeleted: Int,
        val assertions: Int,
        val checkpoints: List<CheckpointMetric>,
        val threadName: String,
        val errorType: String?,
        val error: String?,
        val errorStackHead: String?,
        val errorStackTrace: String?,
        val errorRootCauseChain: String?
    )

    data class FailureDetail(
        val caseName: String,
        val message: String,
        val stackHead: String
    )

    data class RunMetric(
        val runId: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val totalMs: Long,
        val buildType: String,
        val stressLevel: StressLevel,
        val cancelled: Boolean,
        val scheduledCases: Int,
        val caseMetrics: List<CaseMetric>,
        val pendingCaseNames: List<String>,
        val failures: List<FailureDetail>,
        val minMs: Long,
        val maxMs: Long,
        val avgMs: Long,
        val p50Ms: Long,
        val p95Ms: Long
    ) {
        val totalCases: Int
            get() = scheduledCases
        val passedCount: Int
            get() = caseMetrics.count { it.status == CaseStatus.PASSED }
        val failedCount: Int
            get() = caseMetrics.count { it.status == CaseStatus.FAILED }
    }

    data class TestErrorDetail(
        val type: String,
        val message: String,
        val summary: String,
        val stackTrace: String,
        val rootCauseChain: String
    )

    data class TestCaseResult(
        val caseId: String,
        val caseName: String,
        val status: CaseStatus,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val costMs: Long,
        val recordsWritten: Int,
        val recordsUpdated: Int,
        val recordsDeleted: Int,
        val assertions: Int,
        val checkpoints: List<CheckpointMetric>,
        val threadName: String,
        val error: TestErrorDetail?
    )

    data class TestRunReport(
        val runId: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val totalMs: Long,
        val stressLevel: StressLevel,
        val buildType: String,
        val cancelled: Boolean,
        val totalCases: Int,
        val passedCount: Int,
        val failedCount: Int,
        val pendingCaseNames: List<String>,
        val caseResults: List<TestCaseResult>
    )

    data class EventTrace(
        val runId: String,
        val caseId: String? = null,
        val timestampEpochMs: Long = System.currentTimeMillis(),
        val threadName: String = Thread.currentThread().name
    )

    sealed interface TestRunEvent {
        data class RunStarted(
            val trace: EventTrace,
            val stressLevel: StressLevel,
            val totalCases: Int,
            val pendingCaseNames: List<String>
        ) : TestRunEvent

        data class CaseStarted(
            val trace: EventTrace,
            val caseName: String
        ) : TestRunEvent

        data class Checkpoint(
            val trace: EventTrace,
            val caseName: String,
            val checkpointName: String,
            val costMs: Long
        ) : TestRunEvent

        data class CasePassed(
            val trace: EventTrace,
            val result: TestCaseResult
        ) : TestRunEvent

        data class CaseFailed(
            val trace: EventTrace,
            val result: TestCaseResult
        ) : TestRunEvent

        data class RunCancelled(
            val trace: EventTrace,
            val report: TestRunReport
        ) : TestRunEvent

        data class RunFinished(
            val trace: EventTrace,
            val report: TestRunReport
        ) : TestRunEvent

        data class RunCrashed(
            val trace: EventTrace,
            val error: TestErrorDetail
        ) : TestRunEvent
    }

    fun runAsync(context: Context, config: StabilityConfig = DefaultConfig.value) {
        if (config.enabledInDebug && !BuildConfig.DEBUG) {
            return
        }
        if (!started.compareAndSet(false, true)) {
            return
        }
        val appContext = context.applicationContext
        val runId = UUID.randomUUID().toString()
        val cancellationSignal = AtomicBoolean(false)
        val logger = StartupStabilityEventLogger(config)
        Thread(
            {
                val metric = StartupStabilityRunnerCore(
                    config = config,
                    runId = runId,
                    cancellationSignal = cancellationSignal
                ).runAll()
                logger.logRun(metric)
                logger.showFailureToastIfNeeded(appContext, metric)
            },
            "litepal-startup-stability"
        ).start()
    }

    suspend fun runSuite(
        config: StabilityConfig = DefaultConfig.value,
        observer: (TestRunEvent) -> Unit = {}
    ): TestRunReport {
        val runId = UUID.randomUUID().toString()
        val cancellationSignal = AtomicBoolean(false)
        val logger = StartupStabilityEventLogger(config)
        cancellationSignals[runId] = cancellationSignal
        return try {
            withContext(Dispatchers.IO) {
                val metric = StartupStabilityRunnerCore(
                    config = config,
                    runId = runId,
                    cancellationSignal = cancellationSignal,
                    observer = observer
                ).runAll()
                logger.logRun(metric)
                val report = metric.toReport()
                if (metric.cancelled) {
                    observer(
                        TestRunEvent.RunCancelled(
                            trace = EventTrace(runId = runId),
                            report = report
                        )
                    )
                } else {
                    observer(
                        TestRunEvent.RunFinished(
                            trace = EventTrace(runId = runId),
                            report = report
                        )
                    )
                }
                report
            }
        } catch (t: Throwable) {
            val error = toErrorDetail(t)
            observer(
                TestRunEvent.RunCrashed(
                    trace = EventTrace(runId = runId),
                    error = error
                )
            )
            throw t
        } finally {
            cancellationSignals.remove(runId)
        }
    }

    fun cancelCurrentRun(runId: String) {
        cancellationSignals[runId]?.set(true)
    }

    private fun toErrorDetail(throwable: Throwable): TestErrorDetail {
        val message = throwable.message ?: "no message"
        val summary = "${throwable.javaClass.simpleName}: $message"
        val stackTrace = throwable.stackTraceToString()
        val chain = buildString {
            var current: Throwable? = throwable
            while (current != null) {
                if (isNotEmpty()) {
                    append(" <- ")
                }
                append(current.javaClass.simpleName)
                append(":")
                append(current.message ?: "no message")
                current = current.cause
            }
        }
        return TestErrorDetail(
            type = throwable.javaClass.name,
            message = message,
            summary = summary,
            stackTrace = stackTrace,
            rootCauseChain = chain
        )
    }

    private fun RunMetric.toReport(): TestRunReport {
        return TestRunReport(
            runId = runId,
            startEpochMs = startEpochMs,
            endEpochMs = endEpochMs,
            totalMs = totalMs,
            stressLevel = stressLevel,
            buildType = buildType,
            cancelled = cancelled,
            totalCases = totalCases,
            passedCount = passedCount,
            failedCount = failedCount,
            pendingCaseNames = pendingCaseNames,
            caseResults = caseMetrics.map { metric ->
                TestCaseResult(
                    caseId = metric.caseId,
                    caseName = metric.caseName,
                    status = metric.status,
                    startEpochMs = metric.startEpochMs,
                    endEpochMs = metric.endEpochMs,
                    costMs = metric.costMs,
                    recordsWritten = metric.recordsWritten,
                    recordsUpdated = metric.recordsUpdated,
                    recordsDeleted = metric.recordsDeleted,
                    assertions = metric.assertions,
                    checkpoints = metric.checkpoints,
                    threadName = metric.threadName,
                    error = if (metric.errorType == null || metric.error == null) {
                        null
                    } else {
                        TestErrorDetail(
                            type = metric.errorType,
                            message = metric.error,
                            summary = metric.error,
                            stackTrace = metric.errorStackTrace.orEmpty(),
                            rootCauseChain = metric.errorRootCauseChain.orEmpty()
                        )
                    }
                )
            }
        )
    }

    internal class StartupStabilitySuite(
        private val config: StabilityConfig,
        private val runId: String,
        private val cancellationSignal: AtomicBoolean,
        private val observer: (TestRunEvent) -> Unit = {}
    ) {

        private val profile: StressProfile = StressProfile.from(config.stressLevel)
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
            val profile: StressProfile,
            val cancellationSignal: AtomicBoolean,
            val observer: (TestRunEvent) -> Unit,
            val currentCaseIdRef: AtomicReference<String?>
        ) {
            fun casePrefix(caseName: String): String = "${runPrefix}_${caseName}"
            fun trace(caseId: String? = null): EventTrace = EventTrace(runId = runId, caseId = caseId)
            fun throwIfCancelled(point: String): Unit {
                if (cancellationSignal.get()) {
                    throw SuiteCancellationException(runId, point)
                }
            }

            fun emitProgressCheckpoint(checkpointName: String) {
                val caseId = currentCaseIdRef.get() ?: return
                observer(
                    TestRunEvent.Checkpoint(
                        trace = trace(caseId),
                        caseName = caseId,
                        checkpointName = checkpointName,
                        costMs = 0L
                    )
                )
            }
        }

        private class SuiteCancellationException(
            val runId: String,
            val point: String
        ) : RuntimeException("Run $runId cancelled at $point")

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
            val state = RunState(
                runId = runId,
                runPrefix = runPrefix,
                profile = profile,
                cancellationSignal = cancellationSignal,
                observer = observer,
                currentCaseIdRef = AtomicReference(null)
            )
            cleanupByPrefix()
            val caseMetrics = mutableListOf<CaseMetric>()
            val failures = mutableListOf<FailureDetail>()
            val allCases = buildCases()
            state.observer(
                TestRunEvent.RunStarted(
                    trace = state.trace(),
                    stressLevel = config.stressLevel,
                    totalCases = allCases.size,
                    pendingCaseNames = allCases.map { it.name }
                )
            )
            var cancelled = false
            var pendingCaseNames = allCases.map { it.name }
            for ((index, case) in allCases.withIndex()) {
                if (state.cancellationSignal.get()) {
                    cancelled = true
                    pendingCaseNames = allCases.drop(index).map { it.name }
                    break
                }
                pendingCaseNames = allCases.drop(index + 1).map { it.name }
                val caseMetric = executeCase(state, case)
                caseMetrics.add(caseMetric)
                if (caseMetric.status == CaseStatus.CANCELLED) {
                    cancelled = true
                    break
                }
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
                cancelled = cancelled,
                scheduledCases = allCases.size,
                caseMetrics = caseMetrics,
                pendingCaseNames = if (cancelled) pendingCaseNames else emptyList(),
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
            val trace = state.trace(case.name)
            state.currentCaseIdRef.set(case.name)
            state.observer(
                TestRunEvent.CaseStarted(
                    trace = trace,
                    caseName = case.name
                )
            )
            var errorDetail: TestErrorDetail? = null
            var status = CaseStatus.PASSED
            try {
                case.run(state, mutableMetric)
                state.throwIfCancelled("after_case_${case.name}")
            } catch (cancelled: SuiteCancellationException) {
                status = CaseStatus.CANCELLED
                errorDetail = toErrorDetail(cancelled)
            } catch (t: Throwable) {
                status = CaseStatus.FAILED
                errorDetail = toErrorDetail(t)
            } finally {
                state.currentCaseIdRef.set(null)
            }
            val caseEnd = System.currentTimeMillis()
            val result = TestCaseResult(
                caseId = case.name,
                caseName = case.name,
                status = status,
                startEpochMs = caseStart,
                endEpochMs = caseEnd,
                costMs = caseEnd - caseStart,
                recordsWritten = mutableMetric.recordsWritten,
                recordsUpdated = mutableMetric.recordsUpdated,
                recordsDeleted = mutableMetric.recordsDeleted,
                assertions = mutableMetric.assertions,
                checkpoints = mutableMetric.checkpoints.toList(),
                threadName = Thread.currentThread().name,
                error = errorDetail
            )
            when (status) {
                CaseStatus.PASSED -> {
                    state.observer(TestRunEvent.CasePassed(trace = trace, result = result))
                }

                CaseStatus.FAILED,
                CaseStatus.CANCELLED -> {
                    state.observer(TestRunEvent.CaseFailed(trace = trace, result = result))
                }
            }
            return CaseMetric(
                caseId = case.name,
                caseName = case.name,
                status = status,
                success = status == CaseStatus.PASSED,
                startEpochMs = caseStart,
                endEpochMs = caseEnd,
                costMs = caseEnd - caseStart,
                recordsWritten = mutableMetric.recordsWritten,
                recordsUpdated = mutableMetric.recordsUpdated,
                recordsDeleted = mutableMetric.recordsDeleted,
                assertions = mutableMetric.assertions,
                checkpoints = mutableMetric.checkpoints.toList(),
                threadName = Thread.currentThread().name,
                errorType = errorDetail?.type,
                error = errorDetail?.summary,
                errorStackHead = errorDetail?.stackTrace?.lineSequence()?.firstOrNull(),
                errorStackTrace = errorDetail?.stackTrace,
                errorRootCauseChain = errorDetail?.rootCauseChain
            )
        }

        private fun buildCases(): List<StabilityCase> {
            return listOf(
                LambdaCase(StartupStabilityCaseCatalog.SAVE_ASSOCIATION_BASIC, this::caseSaveAssociationBasic),
                LambdaCase(StartupStabilityCaseCatalog.QUERY_AGGREGATE_BASIC, this::caseQueryAggregateBasic),
                LambdaCase(StartupStabilityCaseCatalog.UPDATE_DELETE_BASIC, this::caseUpdateDeleteBasic),
                LambdaCase(StartupStabilityCaseCatalog.TRANSACTION_COMMIT_BASIC, this::caseTransactionCommitBasic),
                LambdaCase(StartupStabilityCaseCatalog.TRANSACTION_ROLLBACK_BASIC, this::caseTransactionRollbackBasic),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_BULK_INSERT_QUERY, this::caseStressBulkInsertQuery),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_BULK_UPDATE_DELETE, this::caseStressBulkUpdateDelete),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_ASSOCIATION_HIGH_VOLUME, this::caseStressAssociationHighVolume),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_TRANSACTION_REPEAT, this::caseStressTransactionRepeat),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_UNIQUE_CONFLICT_ROLLBACK, this::caseStressUniqueConflictRollback),
                LambdaCase(StartupStabilityCaseCatalog.STRESS_CONCURRENT_READ_WRITE, this::caseStressConcurrentReadWrite)
            )
        }

        private fun caseSaveAssociationBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("save_association_basic")
            val singer = Singer().apply {
                name = "${prefix}_singer"
                age = 26
                isMale = true
            }
            requireCase(metric, timedStep(state, metric, "lp_save__basic_singer") { singer.save() }, "singer save failed")
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
            requireCase(metric, timedStep(state, metric, "lp_save__basic_album") { album.save() }, "album save failed")
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
            val songsSaved = timedStep(state, metric, "lp_save__basic_songs") {
                song1.save() && song2.save()
            }
            requireCase(metric, songsSaved, "song save failed")
            metric.recordsWritten += 2

            val loadedAlbum = timedStep(state, metric, "lp_find__album_eager") {
                LitePal.find(Album::class.java, album.id, true)
            }
            requireCase(metric, loadedAlbum != null, "album query failed")
            requireCase(metric, loadedAlbum?.singer?.id == singer.id, "album-singer association failed")
            val songCount = timedStep(state, metric, "lp_count__songs_by_album") {
                LitePal.where("album_id = ?", album.id.toString()).count("song")
            }
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
            requireCase(metric, timedStep(state, metric, "lp_save__agg_singer_1") { singer1.save() }, "aggregate singer1 save failed")
            requireCase(metric, timedStep(state, metric, "lp_save__agg_singer_2") { singer2.save() }, "aggregate singer2 save failed")
            metric.recordsWritten += 2

            val condition = "${prefix}_agg_%"
            val count = timedStep(state, metric, "lp_count__agg_singers") {
                LitePal.where("name like ?", condition).count(Singer::class.java)
            }
            val maxAge = timedStep(state, metric, "lp_max__agg_age") {
                LitePal.where("name like ?", condition)
                    .max(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val minAge = timedStep(state, metric, "lp_min__agg_age") {
                LitePal.where("name like ?", condition)
                    .min(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val sumAge = timedStep(state, metric, "lp_sum__agg_age") {
                LitePal.where("name like ?", condition)
                    .sum(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val averageAge = timedStep(state, metric, "lp_average__agg_age") {
                LitePal.where("name like ?", condition)
                    .average(Singer::class.java, "age")
            }

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
            requireCase(metric, timedStep(state, metric, "lp_save__update_target") { singer.save() }, "update singer save failed")
            metric.recordsWritten++

            val updater = Singer().apply {
                age = 41
            }
            val updateRows = timedStep(state, metric, "lp_update__update_target") {
                updater.update(singer.id)
            }
            metric.recordsUpdated += updateRows
            requireCase(metric, updateRows == 1, "update affected rows expected 1 but got $updateRows")
            val updatedSinger = timedStep(state, metric, "lp_find__updated_singer") {
                LitePal.find(Singer::class.java, singer.id)
            }
            requireCase(metric, updatedSinger?.age == 41, "updated age expected 41 but got ${updatedSinger?.age}")

            val song = Song().apply {
                name = "${prefix}_delete_song"
                lyric = "${prefix}_delete_lyric"
                duration = "02:59"
            }
            requireCase(metric, timedStep(state, metric, "lp_save__delete_song") { song.save() }, "delete song save failed")
            metric.recordsWritten++
            val deletedRows = timedStep(state, metric, "lp_delete__delete_song") {
                LitePal.delete(Song::class.java, song.id)
            }
            metric.recordsDeleted += deletedRows
            requireCase(metric, deletedRows >= 1, "delete affected rows expected >=1 but got $deletedRows")
            val deletedSong = timedStep(state, metric, "lp_find__deleted_song") {
                LitePal.find(Song::class.java, song.id)
            }
            requireCase(metric, deletedSong == null, "song should not exist after delete")
        }

        private fun caseTransactionCommitBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("transaction_commit_basic")
            val commitName = "${prefix}_tx_commit"
            val committed = timedStep(state, metric, "lp_run_in_tx__commit") {
                LitePal.runInTransaction {
                    val singer = Singer().apply {
                        name = commitName
                        age = 36
                        isMale = true
                    }
                    singer.save()
                }
            }
            requireCase(metric, committed, "transaction commit should return true")
            val count = timedStep(state, metric, "lp_count__commit_verify") {
                LitePal.where("name = ?", commitName).count(Singer::class.java)
            }
            requireCase(metric, count == 1, "commit data should be persisted")
            metric.recordsWritten++
        }

        private fun caseTransactionRollbackBasic(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("transaction_rollback_basic")
            val rollbackName = "${prefix}_tx_rollback"
            val rollbackResult = timedStep(state, metric, "lp_run_in_tx__rollback") {
                LitePal.runInTransaction {
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
            }
            requireCase(metric, !rollbackResult, "transaction rollback should return false")
            val count = timedStep(state, metric, "lp_count__rollback_verify") {
                LitePal.where("name = ?", rollbackName).count(Singer::class.java)
            }
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
            val saveAllResult = timedStep(state, metric, "lp_save_all__bulk_singers") { singers.saveAll() }
            requireCase(metric, saveAllResult, "bulk saveAll failed")
            metric.recordsWritten += count

            val actualCount = timedStep(state, metric, "lp_count__bulk_singers") {
                LitePal.where("name like ?", "${prefix}_bulk_%").count(Singer::class.java)
            }
            requireCase(metric, actualCount == count, "bulk count expected $count but got $actualCount")

            var offset = 0
            var pagedTotal = 0
            timedStep(state, metric, "lp_find__bulk_paged_query") {
                while (true) {
                    state.throwIfCancelled("bulk_paged_query_offset_$offset")
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
            }
            requireCase(metric, pagedTotal == count, "paged query expected $count but got $pagedTotal")

            val maxAge = timedStep(state, metric, "lp_max__bulk_age") {
                LitePal.where("name like ?", "${prefix}_bulk_%")
                    .max(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val minAge = timedStep(state, metric, "lp_min__bulk_age") {
                LitePal.where("name like ?", "${prefix}_bulk_%")
                    .min(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val sumAge = timedStep(state, metric, "lp_sum__bulk_age") {
                LitePal.where("name like ?", "${prefix}_bulk_%")
                    .sum(Singer::class.java, "age", Int::class.javaObjectType)
            }
            val averageAge = timedStep(state, metric, "lp_average__bulk_age") {
                LitePal.where("name like ?", "${prefix}_bulk_%")
                    .average(Singer::class.java, "age")
            }

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
                state.throwIfCancelled("stress_update_delete_seed_delete_group_$i")
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_$i"
                        age = 10
                        isMale = true
                    }
                )
            }
            for (i in 0 until groupB) {
                state.throwIfCancelled("stress_update_delete_seed_update_group_$i")
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_${i + toDelete}"
                        age = 10
                        isMale = false
                    }
                )
            }
            for (i in 0 until untouched) {
                state.throwIfCancelled("stress_update_delete_seed_untouched_group_$i")
                singers.add(
                    Singer().apply {
                        name = "${prefix}_u_${i + toUpdate}"
                        age = 30
                        isMale = false
                    }
                )
            }
            requireCase(
                metric,
                timedStep(state, metric, "lp_save_all__update_delete_seed") { singers.saveAll() },
                "stress update/delete saveAll failed"
            )
            metric.recordsWritten += total

            val values = ContentValues().apply {
                put("age", 66)
            }
            // Use ContentValues update to avoid overriding unrelated fields (name/isMale) to defaults.
            val rowsUpdated = timedStep(state, metric, "lp_update_all__update_delete_age") {
                LitePal.updateAll(
                    Singer::class.java,
                    values,
                    "name like ? and age = ?",
                    "${prefix}_u_%",
                    "10"
                )
            }
            metric.recordsUpdated += rowsUpdated
            requireCase(metric, rowsUpdated == toUpdate, "expected update rows $toUpdate but got $rowsUpdated")

            val rowsDeleted = timedStep(state, metric, "lp_delete_all__update_delete_group_a") {
                LitePal.deleteAll(
                    Singer::class.java,
                    "name like ? and age = ? and ismale = ?",
                    "${prefix}_u_%",
                    "66",
                    "1"
                )
            }
            metric.recordsDeleted += rowsDeleted
            requireCase(metric, rowsDeleted == toDelete, "expected delete rows $toDelete but got $rowsDeleted")

            val remaining = timedStep(state, metric, "lp_count__update_delete_remaining") {
                LitePal.where("name like ?", "${prefix}_u_%").count(Singer::class.java)
            }
            val updatedRemaining = timedStep(state, metric, "lp_count__update_delete_updated_remaining") {
                LitePal.where("name like ? and age = ?", "${prefix}_u_%", "66").count(Singer::class.java)
            }
            val untouchedRemaining = timedStep(state, metric, "lp_count__update_delete_untouched_remaining") {
                LitePal.where("name like ? and age = ?", "${prefix}_u_%", "30").count(Singer::class.java)
            }

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
            requireCase(metric, timedStep(state, metric, "lp_save_all__assoc_singers") { singers.saveAll() }, "save singers failed")
            metric.recordsWritten += singers.size

            val albums = mutableListOf<Album>()
            for (singerIndex in singers.indices) {
                state.throwIfCancelled("stress_association_seed_albums_singer_$singerIndex")
                val singer = singers[singerIndex]
                for (albumIndex in 0 until albumPerSinger) {
                    state.throwIfCancelled("stress_association_seed_albums_singer_${singerIndex}_album_$albumIndex")
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
            requireCase(metric, timedStep(state, metric, "lp_save_all__assoc_albums") { albums.saveAll() }, "save albums failed")
            metric.recordsWritten += albums.size

            val songs = mutableListOf<Song>()
            for (albumIndex in albums.indices) {
                state.throwIfCancelled("stress_association_seed_songs_album_$albumIndex")
                val album = albums[albumIndex]
                for (songIndex in 0 until songPerAlbum) {
                    state.throwIfCancelled("stress_association_seed_songs_album_${albumIndex}_song_$songIndex")
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
            requireCase(metric, timedStep(state, metric, "lp_save_all__assoc_songs") { songs.saveAll() }, "save songs failed")
            metric.recordsWritten += songs.size

            val expectedAlbumCount = singerCount * albumPerSinger
            val expectedSongCount = expectedAlbumCount * songPerAlbum
            val realSingerCount = timedStep(state, metric, "lp_count__assoc_singers") {
                LitePal.where("name like ?", "${prefix}_singer_%").count(Singer::class.java)
            }
            val realAlbumCount = timedStep(state, metric, "lp_count__assoc_albums") {
                LitePal.where("name like ?", "${prefix}_album_%").count(Album::class.java)
            }
            val realSongCount = timedStep(state, metric, "lp_count__assoc_songs") {
                LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
            }
            requireCase(metric, realSingerCount == singerCount, "singer count mismatch")
            requireCase(metric, realAlbumCount == expectedAlbumCount, "album count mismatch")
            requireCase(metric, realSongCount == expectedSongCount, "song count mismatch")

            val eagerAlbum = timedStep(state, metric, "lp_find_first__assoc_album_eager") {
                LitePal.where("name like ?", "${prefix}_album_%")
                    .order("id asc")
                    .findFirst(Album::class.java, true)
            }
            requireCase(metric, eagerAlbum != null, "eager album query failed")
            requireCase(metric, eagerAlbum?.singer != null, "eager singer relation missing")

            val firstSinger = singers.first()
            val firstSingerAlbumCount = timedStep(state, metric, "lp_count__assoc_first_singer_album_count") {
                LitePal.where("singer_id = ?", firstSinger.id.toString()).count("album")
            }
            requireCase(metric, firstSingerAlbumCount == albumPerSinger, "first singer album count mismatch")
        }

        private fun caseStressTransactionRepeat(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_transaction_repeat")
            val repeat = state.profile.transactionRepeat
            var expectedCommitted = 0
            timedStep(state, metric, "lp_run_in_tx__repeat_loop") {
                for (i in 0 until repeat) {
                    state.throwIfCancelled("stress_transaction_repeat_$i")
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
            }
            val actualCommitted = timedStep(state, metric, "lp_count__tx_repeat_verify") {
                LitePal.where("name like ?", "${prefix}_tx_%").count(Singer::class.java)
            }
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
            val saveResult = timedStep(state, metric, "lp_save_all__conflict_batch") {
                LitePalRuntime.withSilentErrorLog {
                    runCatching { songs.saveAll() }.getOrDefault(false)
                }
            }
            metric.recordsWritten += songs.size
            requireCase(metric, !saveResult, "unique conflict should fail saveAll")
            val songsCount = timedStep(state, metric, "lp_count__conflict_song_rows") {
                LitePal.where("name like ?", "${prefix}_song_%").count(Song::class.java)
            }
            val lyricsCount = timedStep(state, metric, "lp_count__conflict_lyric_rows") {
                LitePal.where("lyric like ?", "${prefix}_lyric_%").count(Song::class.java)
            }
            requireCase(metric, songsCount == 0, "song rows should rollback to 0")
            requireCase(metric, lyricsCount == 0, "lyric rows should rollback to 0")
        }

        private fun caseStressConcurrentReadWrite(state: RunState, metric: MutableCaseMetric) {
            val prefix = state.casePrefix("stress_concurrent_read_write")
            val runtimeOptions = LitePalRuntime.getRuntimeOptions()
            val queryParallelism = resolveExecutorParallelism(runtimeOptions.queryExecutor)
            val transactionParallelism = resolveExecutorParallelism(runtimeOptions.transactionExecutor)
            val writeConstrainedMode = transactionParallelism <= 1
            val readConstrainedMode = queryParallelism <= 2
            val constrainedMode = writeConstrainedMode || readConstrainedMode

            // 低并发执行器下自适应缩放压力参数，避免固定 30s 超时误判；高并发执行器保持原始强度。
            val writerCoroutines = when {
                writeConstrainedMode -> state.profile.concurrentWriterThreads.coerceAtMost(4).coerceAtLeast(2)
                constrainedMode -> state.profile.concurrentWriterThreads.coerceAtMost(8).coerceAtLeast(4)
                else -> state.profile.concurrentWriterThreads
            }
            val readerCoroutines = when {
                readConstrainedMode -> state.profile.concurrentReaderThreads.coerceAtMost(4).coerceAtLeast(2)
                constrainedMode -> state.profile.concurrentReaderThreads.coerceAtMost(6).coerceAtLeast(3)
                else -> state.profile.concurrentReaderThreads
            }
            val writesPerCoroutine = when {
                writeConstrainedMode -> state.profile.concurrentWritesPerThread.coerceAtMost(40).coerceAtLeast(24)
                constrainedMode -> state.profile.concurrentWritesPerThread.coerceAtMost(70).coerceAtLeast(40)
                else -> state.profile.concurrentWritesPerThread
            }
            val readLoops = when {
                readConstrainedMode -> state.profile.concurrentReadLoops.coerceAtMost(60).coerceAtLeast(30)
                constrainedMode -> state.profile.concurrentReadLoops.coerceAtMost(90).coerceAtLeast(45)
                else -> state.profile.concurrentReadLoops
            }
            val writerBatchSize = when {
                writesPerCoroutine >= 100 -> 20
                writesPerCoroutine >= 60 -> 15
                else -> 10
            }.coerceAtLeast(1).coerceAtMost(writesPerCoroutine)
            val writerProgressStep = (writesPerCoroutine / 5).coerceAtLeast(writerBatchSize)
            val readerProgressStep = (readLoops / 5).coerceAtLeast(1)
            val expectedReads = readerCoroutines * readLoops
            val totalTimeoutMs = 30_000L
            val stallTimeoutMs = when {
                writeConstrainedMode -> 18_000L
                constrainedMode -> 14_000L
                else -> 10_000L
            }
            val errors = ConcurrentLinkedQueue<Throwable>()
            val written = AtomicInteger(0)
            val reads = AtomicInteger(0)
            val lastProgressEpoch = AtomicLong(System.currentTimeMillis())
            val activeWriters = AtomicInteger(writerCoroutines)

            try {
                timedStep(state, metric, "lp_concurrency__read_write_run") {
                    runBlocking {
                        withTimeout(totalTimeoutMs) {
                            coroutineScope {
                                val workerContext = Dispatchers.IO
                                val startGate = kotlinx.coroutines.CompletableDeferred<Unit>()
                                val firstWriteGate = kotlinx.coroutines.CompletableDeferred<Unit>()
                                val workers = mutableListOf<kotlinx.coroutines.Deferred<Throwable?>>()
                                state.emitProgressCheckpoint(
                                    "concurrency_plan:writers=$writerCoroutines,readers=$readerCoroutines,writesPerWriter=$writesPerCoroutine,readsPerReader=$readLoops,batch=$writerBatchSize,queryParallelism=$queryParallelism,transactionParallelism=$transactionParallelism,constrained=$constrainedMode,timeoutMs=$totalTimeoutMs"
                                )

                                repeat(writerCoroutines) { writerIndex ->
                                    workers += async(workerContext) {
                                        startGate.await()
                                        runCatching {
                                            var offset = 0
                                            while (offset < writesPerCoroutine) {
                                                state.throwIfCancelled("writer_${writerIndex}_$offset")
                                                val endExclusive = (offset + writerBatchSize).coerceAtMost(writesPerCoroutine)
                                                val batch = ArrayList<Singer>(endExclusive - offset)
                                                for (i in offset until endExclusive) {
                                                    batch.add(
                                                        Singer().apply {
                                                            name = "${prefix}_w${writerIndex}_$i"
                                                            age = 18 + (i % 40)
                                                            isMale = (writerIndex + i) % 2 == 0
                                                        }
                                                    )
                                                }
                                                if (!batch.saveAll()) {
                                                    throw IllegalStateException(
                                                        "concurrent writer batch save failed: writer=$writerIndex, from=$offset, to=${endExclusive - 1}, queryParallelism=$queryParallelism, transactionParallelism=$transactionParallelism"
                                                    )
                                                }
                                                val totalWritten = written.addAndGet(batch.size)
                                                lastProgressEpoch.set(System.currentTimeMillis())
                                                if (!firstWriteGate.isCompleted) {
                                                    firstWriteGate.complete(Unit)
                                                }
                                                if (endExclusive == writesPerCoroutine || endExclusive % writerProgressStep == 0 || offset == 0) {
                                                    state.emitProgressCheckpoint(
                                                        "writer_$writerIndex/$endExclusive,total=$totalWritten,batch=${batch.size}"
                                                    )
                                                }
                                                offset = endExclusive
                                                if (writeConstrainedMode) {
                                                    delay(1L)
                                                }
                                            }
                                        }.exceptionOrNull().also {
                                            if (activeWriters.decrementAndGet() == 0 && !firstWriteGate.isCompleted) {
                                                // 避免 reader 永久等待（所有 writer 未产出即退出）。
                                                firstWriteGate.complete(Unit)
                                            }
                                        }
                                    }
                                }

                                repeat(readerCoroutines) { readerIndex ->
                                    workers += async(workerContext) {
                                        startGate.await()
                                        firstWriteGate.await()
                                        runCatching {
                                            var previous = 0
                                            for (i in 0 until readLoops) {
                                                state.throwIfCancelled("reader_${readerIndex}_$i")
                                                val count = LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
                                                if (count < previous) {
                                                    throw IllegalStateException(
                                                        "reader observed decreasing count: reader=$readerIndex, previous=$previous, current=$count"
                                                    )
                                                }
                                                previous = count
                                                reads.incrementAndGet()
                                                lastProgressEpoch.set(System.currentTimeMillis())
                                                if (i == 0 || (i + 1) % readerProgressStep == 0 || i == readLoops - 1) {
                                                    state.emitProgressCheckpoint(
                                                        "reader_$readerIndex/${i + 1},count=$count,reads=${reads.get()}"
                                                    )
                                                }
                                                if (readConstrainedMode) {
                                                    delay(4L)
                                                }
                                            }
                                        }.exceptionOrNull()
                                    }
                                }

                                val watchdog = async(workerContext) {
                                    startGate.await()
                                    while (true) {
                                        delay(5_000L)
                                        val idleMs = System.currentTimeMillis() - lastProgressEpoch.get()
                                        if (idleMs > stallTimeoutMs) {
                                            throw IllegalStateException(
                                                "concurrent run stalled: idle=${idleMs}ms, written=${written.get()}, reads=${reads.get()}, activeWriters=${activeWriters.get()}, writers=$writerCoroutines, readers=$readerCoroutines, batch=$writerBatchSize, queryParallelism=$queryParallelism, transactionParallelism=$transactionParallelism, constrained=$constrainedMode"
                                            )
                                        }
                                        state.emitProgressCheckpoint(
                                            "watchdog_idle=${idleMs}ms,written=${written.get()},reads=${reads.get()},activeWriters=${activeWriters.get()}"
                                        )
                                    }
                                }

                                startGate.complete(Unit)
                                workers.awaitAll().filterNotNull().forEach { errors.add(it) }
                                state.emitProgressCheckpoint(
                                    "concurrency_workers_done,written=${written.get()},reads=${reads.get()},activeWriters=${activeWriters.get()}"
                                )
                                watchdog.cancel()
                                runCatching { watchdog.await() }.onFailure { watcherEndError ->
                                    if (watcherEndError !is CancellationException) {
                                        throw watcherEndError
                                    }
                                }
                                state.emitProgressCheckpoint("concurrency_watchdog_stopped")
                            }
                        }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException(
                    "concurrent run timeout after ${totalTimeoutMs}ms: written=${written.get()}, reads=${reads.get()}, activeWriters=${activeWriters.get()}, writers=$writerCoroutines, readers=$readerCoroutines, writesPerWriter=$writesPerCoroutine, readLoops=$readLoops, queryParallelism=$queryParallelism, transactionParallelism=$transactionParallelism, constrained=$constrainedMode",
                    timeout
                )
            }

            requireCase(metric, errors.isEmpty(), "concurrent errors: ${errors.joinToString("; ") { it.message ?: "unknown" }}")
            val expected = writerCoroutines * writesPerCoroutine
            val actual = timedStep(state, metric, "lp_count__concurrency_verify") {
                LitePal.where("name like ?", "${prefix}_w%").count(Singer::class.java)
            }
            requireCase(metric, written.get() == expected, "writer counter mismatch")
            requireCase(metric, reads.get() == expectedReads, "reader loop count mismatch")
            requireCase(metric, actual == expected, "concurrent final count expected $expected but got $actual")

            val names = timedStep(state, metric, "lp_find__concurrency_names") {
                LitePal.where("name like ?", "${prefix}_w%").find(Singer::class.java).mapNotNull { it.name }
            }
            requireCase(metric, names.size == expected, "concurrent query result size mismatch")
            requireCase(metric, names.toSet().size == expected, "concurrent names should be unique")
            metric.recordsWritten += expected
        }

        private fun isLikelySingleThreadExecutor(executor: Executor?): Boolean {
            if (executor == null) {
                return false
            }
            if (executor is ThreadPoolExecutor) {
                return executor.maximumPoolSize <= 1
            }
            val className = executor.javaClass.name
            return className.contains("FinalizableDelegatedExecutorService", ignoreCase = false) ||
                className.contains("DelegatedExecutorService", ignoreCase = false) ||
                className.contains("SingleThread", ignoreCase = true)
        }

        private fun resolveExecutorParallelism(executor: Executor?): Int {
            if (executor == null) {
                return 2
            }
            if (executor is ThreadPoolExecutor) {
                return executor.maximumPoolSize.coerceAtLeast(1)
            }
            return if (isLikelySingleThreadExecutor(executor)) 1 else 2
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

        private inline fun <T> timedStep(state: RunState, metric: MutableCaseMetric, checkpoint: String, block: () -> T): T {
            state.throwIfCancelled("checkpoint_${checkpoint}_start")
            val start = System.currentTimeMillis()
            return try {
                block()
            } finally {
                val cost = System.currentTimeMillis() - start
                metric.addCheckpoint(checkpoint, cost)
                val caseId = state.currentCaseIdRef.get()
                state.observer(
                    TestRunEvent.Checkpoint(
                        trace = state.trace(caseId),
                        caseName = caseId ?: "unknown_case",
                        checkpointName = checkpoint,
                        costMs = cost
                    )
                )
                state.throwIfCancelled("checkpoint_${checkpoint}_end")
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
