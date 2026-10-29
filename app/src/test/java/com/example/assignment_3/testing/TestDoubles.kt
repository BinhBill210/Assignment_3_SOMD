package com.example.assignment_3.testing

import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudySessionRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.DuplicateUnitCodeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

val TEST_ZONE: ZoneId = ZoneId.of("Australia/Melbourne")

/** Wednesday 5 Nov 2025, 10:00 in Melbourne. */
val TEST_NOW: Instant = Instant.parse("2025-11-04T23:00:00Z")

const val HOUR = 60 * 60 * 1000L
const val DAY = 24 * HOUR

class MutableClock(var instant: Instant = TEST_NOW, private val zone: ZoneId = TEST_ZONE) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
    fun advanceBy(millis: Long) {
        instant = instant.plusMillis(millis)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun unit(id: Long = 1, code: String = "COS30017", name: String = "Mobile Dev", colorIndex: Int = 0, targetHours: Int = 10) =
    StudyUnit(id, code, name, colorIndex, targetHours * 60, createdAt = 0)

fun assessment(
    id: Long = 1,
    unitId: Long = 1,
    title: String = "Assignment $id",
    weighting: Int = 20,
    dueAt: Long = TEST_NOW.toEpochMilli() + 3 * DAY,
    status: AssessmentStatus = AssessmentStatus.NOT_STARTED,
    reminderOffsetMinutes: Int? = 24 * 60
) = Assessment(id, unitId, title, AssessmentType.ASSIGNMENT, weighting, dueAt, status, reminderOffsetMinutes, "", 0, 0)

class FakeStudyUnitRepository : StudyUnitRepository {
    val units = MutableStateFlow<List<StudyUnit>>(emptyList())
    val progress = MutableStateFlow<List<UnitProgress>>(emptyList())
    var failWrites = false
    var cascade = CascadeCounts(0, 0)
    val deleted = mutableListOf<StudyUnit>()
    private var nextId = 100L

    override fun observeUnits(): Flow<List<StudyUnit>> = units
    override fun observeUnit(id: Long): Flow<StudyUnit?> = units.map { list -> list.firstOrNull { it.id == id } }
    override fun observeProgress(weekStart: Long, weekEnd: Long): Flow<List<UnitProgress>> = progress
    override suspend fun getUnit(id: Long): StudyUnit? = units.value.firstOrNull { it.id == id }

    override suspend fun saveUnit(unit: StudyUnit): Long {
        if (failWrites) throw IllegalStateException("disk full")
        if (units.value.any { it.code == unit.code && it.id != unit.id }) throw DuplicateUnitCodeException(unit.code)
        val id = if (unit.id == 0L) nextId++ else unit.id
        units.update { list -> list.filterNot { it.id == id } + unit.copy(id = id) }
        return id
    }

    override suspend fun cascadeCounts(unitId: Long): CascadeCounts = cascade

    override suspend fun deleteUnit(unit: StudyUnit) {
        if (failWrites) throw IllegalStateException("disk full")
        deleted += unit
        units.update { list -> list.filterNot { it.id == unit.id } }
    }
}

class FakeAssessmentRepository(private val unitRepository: FakeStudyUnitRepository) : AssessmentRepository {
    val assessments = MutableStateFlow<List<Assessment>>(emptyList())
    var failWrites = false
    private var nextId = 100L

    private fun withUnit(list: List<Assessment>, units: List<StudyUnit>) = list.mapNotNull { a ->
        units.firstOrNull { it.id == a.unitId }?.let { AssessmentWithUnit(a, it.code, it.name, it.colorIndex) }
    }

    override fun observeOutstanding(): Flow<List<AssessmentWithUnit>> =
        combine(assessments, unitRepository.units) { list, units ->
            withUnit(list.filter { it.status != AssessmentStatus.SUBMITTED }.sortedBy { it.dueAt }, units)
        }

    override fun observeForUnit(unitId: Long): Flow<List<Assessment>> =
        assessments.map { list -> list.filter { it.unitId == unitId } }

    override fun observeAssessment(id: Long): Flow<AssessmentWithUnit?> =
        combine(assessments, unitRepository.units) { list, units -> withUnit(list.filter { it.id == id }, units).firstOrNull() }

    override suspend fun getAssessment(id: Long): Assessment? = assessments.value.firstOrNull { it.id == id }

    override suspend fun weightingTotal(unitId: Long, excludingId: Long): Int =
        assessments.value.filter { it.unitId == unitId && it.id != excludingId }.sumOf { it.weighting }

    override suspend fun saveAssessment(assessment: Assessment): Long {
        if (failWrites) throw IllegalStateException("disk full")
        val id = if (assessment.id == 0L) nextId++ else assessment.id
        assessments.update { list -> list.filterNot { it.id == id } + assessment.copy(id = id) }
        return id
    }

    override suspend fun updateStatus(id: Long, status: AssessmentStatus) {
        if (failWrites) throw IllegalStateException("disk full")
        assessments.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    override suspend fun deleteAssessment(assessment: Assessment) {
        if (failWrites) throw IllegalStateException("disk full")
        assessments.update { list -> list.filterNot { it.id == assessment.id } }
    }
}

class FakeStudySessionRepository : StudySessionRepository {
    val sessions = MutableStateFlow<List<StudySession>>(emptyList())
    var failWrites = false
    private var nextId = 100L

    override fun observeSessions(assessmentId: Long): Flow<List<StudySession>> =
        sessions.map { list -> list.filter { it.assessmentId == assessmentId }.sortedByDescending { it.startedAt } }

    override suspend fun saveSession(session: StudySession): Long {
        if (failWrites) throw IllegalStateException("disk full")
        val id = if (session.id == 0L) nextId++ else session.id
        sessions.update { list -> list.filterNot { it.id == id } + session.copy(id = id) }
        return id
    }

    override suspend fun deleteSession(session: StudySession) {
        if (failWrites) throw IllegalStateException("disk full")
        sessions.update { list -> list.filterNot { it.id == session.id } }
    }
}
