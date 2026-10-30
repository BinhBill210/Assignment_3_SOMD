package com.example.assignment_3.benchmark

import android.util.Log
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.assignment_3.data.local.StudyHiveDatabase
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.data.local.model.toUnitProgress
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.domain.WeekRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Investigation (docs/INVESTIGATION.md): SQL aggregation (A) vs @Relation + Kotlin aggregation (B)
 * for the dashboard unit-progress query. Raw timings are written as CSV for analysis.
 *
 * Run on its own:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.assignment_3.benchmark.DashboardAggregationBenchmark \
 *     -Pandroid.testInstrumentationRunnerArguments.benchLabel=run1
 */
@RunWith(AndroidJUnit4::class)
class DashboardAggregationBenchmark {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val label = InstrumentationRegistry.getArguments().getString("benchLabel") ?: "adhoc"
    private val zone = ZoneId.of("Australia/Melbourne")
    private val now = Instant.parse("2025-11-04T23:00:00Z").toEpochMilli()
    private val week = WeekRange.containing(now, zone)

    private val sizes = listOf(500, 5_000, 50_000)
    private val units = 8
    private val assessmentsPerUnit = 6
    private val warmups = 5
    private val runs = 30
    private val flowRepetitions = 20

    private data class Sample(val size: Int, val strategy: String, val metric: String, val iteration: Int, val nanos: Long)

    @Test
    fun compareStrategies() = runBlocking {
        val samples = mutableListOf<Sample>()
        val rows = mutableListOf<String>()

        for (size in sizes) {
            val db = StudyHiveDatabase.inMemory(context)
            try {
                seed(db, size)
                val dao = db.studyUnitDao()
                val queryA: suspend () -> List<UnitProgress> = { dao.getProgress(week.startMillis, week.endMillis) }
                val queryB: suspend () -> List<UnitProgress> = {
                    dao.getUnitsWithAssessments().toUnitProgress(week.startMillis, week.endMillis)
                }

                // Correctness gate: no timing is meaningful unless both return the same answer.
                val a = queryA()
                assertEquals("strategies disagree at size $size", a, queryB())

                val graph = dao.getUnitsWithAssessments()
                val materialisedB = graph.size + graph.sumOf { it.assessments.size } +
                    graph.sumOf { u -> u.assessments.sumOf { it.sessions.size } }
                rows += "$size,A,${a.size}"
                rows += "$size,B,$materialisedB"

                repeat(warmups) { queryA(); queryB() }
                repeat(runs) { i ->
                    // Alternate the order each iteration so neither strategy always runs "warm".
                    val order = if (i % 2 == 0) listOf("A" to queryA, "B" to queryB) else listOf("B" to queryB, "A" to queryA)
                    for ((name, query) in order) {
                        val start = System.nanoTime()
                        query()
                        samples += Sample(size, name, "query", i, System.nanoTime() - start)
                    }
                }

                if (size == sizes.last()) {
                    samples += measureReemission(db, "A", dao.observeProgress(week.startMillis, week.endMillis))
                    samples += measureReemission(
                        db, "B",
                        dao.observeUnitsWithAssessments().map { it.toUnitProgress(week.startMillis, week.endMillis) }
                    )
                }
            } finally {
                db.close()
            }
        }

        write(samples, rows)
    }

    /** Time from inserting one session to the observing Flow delivering the recomputed value. */
    private suspend fun measureReemission(db: StudyHiveDatabase, strategy: String, flow: Flow<List<UnitProgress>>): List<Sample> =
        kotlinx.coroutines.coroutineScope {
            val emissions = Channel<Long>(Channel.UNLIMITED)
            val job = launch(Dispatchers.Default) { flow.collect { emissions.send(System.nanoTime()) } }
            emissions.receive()
            val firstUnitId = db.studyUnitDao().observeAll().first().first().id
            val firstAssessmentId = db.assessmentDao().getIdsForUnit(firstUnitId).first()
            val result = (0 until flowRepetitions).map { i ->
                val start = System.nanoTime()
                db.studySessionDao().insert(
                    StudySession(assessmentId = firstAssessmentId, startedAt = week.startMillis + 1, durationMinutes = 1, note = "", source = SessionSource.MANUAL)
                )
                val emittedAt = emissions.receive()
                Sample(sizes.last(), strategy, "reemit", i, emittedAt - start)
            }
            job.cancel()
            result
        }

    private suspend fun seed(db: StudyHiveDatabase, sessionCount: Int) {
        val random = Random(42)
        val spanMillis = 16L * 7 * 24 * 60 * 60 * 1000
        db.withTransaction {
            val assessmentIds = mutableListOf<Long>()
            repeat(units) { u ->
                val unitId = db.studyUnitDao().insert(
                    StudyUnit(code = "UNI${10000 + u}", name = "Unit $u", colorIndex = u, targetMinutesPerWeek = 600, createdAt = 0)
                )
                repeat(assessmentsPerUnit) { k ->
                    assessmentIds += db.assessmentDao().insert(
                        Assessment(
                            unitId = unitId, title = "A$k", type = AssessmentType.ASSIGNMENT, weighting = 15,
                            dueAt = now + k * 86_400_000L, status = if (k % 3 == 0) AssessmentStatus.SUBMITTED else AssessmentStatus.NOT_STARTED,
                            reminderOffsetMinutes = null, notes = "", createdAt = 0, updatedAt = 0
                        )
                    )
                }
            }
            db.studySessionDao().insertAll(
                List(sessionCount) {
                    StudySession(
                        assessmentId = assessmentIds[random.nextInt(assessmentIds.size)],
                        startedAt = week.endMillis - 1 - random.nextLong(spanMillis),
                        durationMinutes = 15 + random.nextInt(106),
                        note = "",
                        source = SessionSource.MANUAL
                    )
                }
            )
        }
    }

    private fun write(samples: List<Sample>, materialised: List<String>) {
        val dir = File(context.getExternalFilesDir(null), "benchmark").apply { mkdirs() }
        File(dir, "raw_$label.csv").writeText(
            "label,size,strategy,metric,iteration,nanos\n" +
                samples.joinToString("\n") { "$label,${it.size},${it.strategy},${it.metric},${it.iteration},${it.nanos}" } + "\n"
        )
        File(dir, "materialised_$label.csv").writeText("size,strategy,objects\n" + materialised.joinToString("\n") + "\n")

        samples.groupBy { Triple(it.metric, it.size, it.strategy) }.toSortedMap(compareBy({ it.first }, { it.second }, { it.third }))
            .forEach { (key, list) ->
                val ms = list.map { it.nanos / 1e6 }.sorted()
                Log.i(TAG, "$label ${key.first} size=${key.second} ${key.third}: median=${"%.2f".format(ms[ms.size / 2])} ms " +
                    "p90=${"%.2f".format(ms[(ms.size * 9) / 10])} ms min=${"%.2f".format(ms.first())} max=${"%.2f".format(ms.last())}")
            }
    }

    private companion object {
        const val TAG = "StudyHiveBench"
    }
}
