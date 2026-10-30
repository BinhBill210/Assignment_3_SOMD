package com.example.assignment_3.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignment_3.data.local.StudyHiveDatabase
import com.example.assignment_3.data.local.dao.AssessmentDao
import com.example.assignment_3.data.local.dao.StudySessionDao
import com.example.assignment_3.data.local.dao.StudyUnitDao
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.SessionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real generated SQL against an in-memory SQLite database on the device. */
@RunWith(AndroidJUnit4::class)
class StudyHiveDaoTest {
    private lateinit var db: StudyHiveDatabase
    private lateinit var units: StudyUnitDao
    private lateinit var assessments: AssessmentDao
    private lateinit var sessions: StudySessionDao

    private val day = 24 * 60 * 60 * 1000L
    private val weekStart = 1_700_000_000_000L
    private val weekEnd = weekStart + 7 * day

    @Before
    fun setUp() {
        db = StudyHiveDatabase.inMemory(ApplicationProvider.getApplicationContext())
        units = db.studyUnitDao()
        assessments = db.assessmentDao()
        sessions = db.studySessionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun unit(code: String, color: Int = 0) = StudyUnit(code = code, name = "Unit $code", colorIndex = color, targetMinutesPerWeek = 600, createdAt = 0)

    private fun assessment(unitId: Long, dueAt: Long, weighting: Int = 20, status: AssessmentStatus = AssessmentStatus.NOT_STARTED) =
        Assessment(
            unitId = unitId, title = "A", type = AssessmentType.ASSIGNMENT, weighting = weighting, dueAt = dueAt,
            status = status, reminderOffsetMinutes = 60, notes = "", createdAt = 0, updatedAt = 0
        )

    private fun session(assessmentId: Long, startedAt: Long, minutes: Int) =
        StudySession(assessmentId = assessmentId, startedAt = startedAt, durationMinutes = minutes, note = "", source = SessionSource.MANUAL)

    @Test
    fun emptyDatabase_returnsEmptyResults() = runTest {
        assertTrue(units.observeAll().first().isEmpty())
        assertTrue(units.getProgress(weekStart, weekEnd).isEmpty())
        assertTrue(assessments.observeOutstanding().first().isEmpty())
        assertNull(units.getById(1))
        assertEquals(0, assessments.weightingTotal(1, 0))
    }

    @Test
    fun insertAndUpdateUnit_roundTrips() = runTest {
        val id = units.insert(unit("COS30017"))
        assertEquals("COS30017", units.getById(id)!!.code)

        units.update(units.getById(id)!!.copy(name = "Renamed"))
        assertEquals("Renamed", units.observeById(id).first()!!.name)
    }

    @Test
    fun duplicateUnitCode_isRejectedByUniqueIndex() = runTest {
        units.insert(unit("COS30017"))
        try {
            units.insert(unit("COS30017"))
            fail("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
        }
        assertEquals(1, units.observeAll().first().size)
    }

    @Test
    fun assessmentForMissingUnit_violatesForeignKey() = runTest {
        try {
            assessments.insert(assessment(unitId = 999, dueAt = weekStart))
            fail("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
        }
    }

    @Test
    fun deletingUnit_cascadesToAssessmentsAndSessions() = runTest {
        val unitId = units.insert(unit("COS30017"))
        val keepId = units.insert(unit("ABC1234"))
        val a1 = assessments.insert(assessment(unitId, weekStart))
        val a2 = assessments.insert(assessment(unitId, weekStart + day))
        val kept = assessments.insert(assessment(keepId, weekStart))
        sessions.insert(session(a1, weekStart, 30))
        sessions.insert(session(a2, weekStart, 30))
        sessions.insert(session(a2, weekStart, 30))
        sessions.insert(session(kept, weekStart, 30))

        assertEquals(CascadeCounts(assessments = 2, sessions = 3), units.cascadeCounts(unitId))
        units.delete(units.getById(unitId)!!)

        assertNull(assessments.getById(a1))
        assertTrue(sessions.observeForAssessment(a2).first().isEmpty())
        assertEquals("other units are untouched", 1, sessions.observeForAssessment(kept).first().size)
    }

    @Test
    fun outstanding_excludesSubmitted_andOrdersByDueDate() = runTest {
        val unitId = units.insert(unit("COS30017", color = 3))
        assessments.insert(assessment(unitId, weekStart + 3 * day))
        assessments.insert(assessment(unitId, weekStart + day))
        assessments.insert(assessment(unitId, weekStart + 2 * day, status = AssessmentStatus.SUBMITTED))

        val result = assessments.observeOutstanding().first()
        assertEquals(listOf(weekStart + day, weekStart + 3 * day), result.map { it.assessment.dueAt })
        assertEquals("COS30017", result.first().unitCode)
        assertEquals(3, result.first().unitColorIndex)
    }

    @Test
    fun weightingTotal_excludesTheAssessmentBeingEdited() = runTest {
        val unitId = units.insert(unit("COS30017"))
        val a = assessments.insert(assessment(unitId, weekStart, weighting = 30))
        assessments.insert(assessment(unitId, weekStart, weighting = 50))
        assertEquals(80, assessments.weightingTotal(unitId, excludingId = 0))
        assertEquals(50, assessments.weightingTotal(unitId, excludingId = a))
    }

    @Test
    fun updateStatus_changesOnlyThatRow() = runTest {
        val unitId = units.insert(unit("COS30017"))
        val a = assessments.insert(assessment(unitId, weekStart))
        val b = assessments.insert(assessment(unitId, weekStart))
        assessments.updateStatus(a, AssessmentStatus.SUBMITTED, updatedAt = 42)

        assertEquals(AssessmentStatus.SUBMITTED, assessments.getById(a)!!.status)
        assertEquals(42, assessments.getById(a)!!.updatedAt)
        assertEquals(AssessmentStatus.NOT_STARTED, assessments.getById(b)!!.status)
    }

    @Test
    fun progressQuery_countsAndFiltersSessionsToTheWeek() = runTest {
        val cos = units.insert(unit("COS30017"))
        val empty = units.insert(unit("ABC1234"))
        val a1 = assessments.insert(assessment(cos, weekStart, status = AssessmentStatus.SUBMITTED))
        val a2 = assessments.insert(assessment(cos, weekStart))
        sessions.insert(session(a1, weekStart, 30))            // first instant of the week: counted
        sessions.insert(session(a2, weekEnd - 1, 45))          // last instant of the week: counted
        sessions.insert(session(a2, weekStart - 1, 999))       // previous week: excluded
        sessions.insert(session(a2, weekEnd, 999))             // next week: excluded

        val progress = units.getProgress(weekStart, weekEnd)
        assertEquals(listOf("ABC1234", "COS30017"), progress.map { it.code })
        val p = progress.single { it.unitId == cos }
        assertEquals(2, p.assessmentCount)
        assertEquals(1, p.submittedCount)
        assertEquals(75, p.minutesThisWeek)
        assertEquals(0, progress.single { it.unitId == empty }.minutesThisWeek)
    }

    @Test
    fun progressFlow_reEmitsWhenASessionIsInserted() = runTest {
        val cos = units.insert(unit("COS30017"))
        val a = assessments.insert(assessment(cos, weekStart))
        val flow = units.observeProgress(weekStart, weekEnd)
        assertEquals(0, flow.first().single().minutesThisWeek)

        sessions.insert(session(a, weekStart + day, 50))

        assertEquals(50, flow.first().single().minutesThisWeek)
    }
}
