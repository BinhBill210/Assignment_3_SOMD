package com.example.assignment_3.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.DuplicateUnitCodeException
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.testing.TestAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Repository + DAO + Room together, with the reminder scheduler recorded. These verify the
 * business operations the repositories own: reminder bookkeeping and error translation.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private val now = Instant.parse("2025-11-04T23:00:00Z")
    private val hour = 60 * 60 * 1000L
    private val container = TestAppContainer(
        ApplicationProvider.getApplicationContext(),
        Clock.fixed(now, ZoneId.of("Australia/Melbourne"))
    )
    private val units = container.studyUnitRepository
    private val assessments = container.assessmentRepository
    private val sessions = container.studySessionRepository
    private val scheduler = container.scheduler

    @After
    fun tearDown() = container.database.close()

    private suspend fun newUnit(code: String = "COS30017") =
        units.saveUnit(StudyUnit(code = code, name = "Unit", colorIndex = 0, targetMinutesPerWeek = 600, createdAt = 0))

    private fun assessment(unitId: Long, dueAt: Long, reminder: Int? = 60, id: Long = 0, status: AssessmentStatus = AssessmentStatus.NOT_STARTED) =
        Assessment(id, unitId, "Report", AssessmentType.ASSIGNMENT, 30, dueAt, status, reminder, "", 0, 0)

    @Test
    fun savingAssessment_schedulesReminderAtDueMinusOffset() = runTest {
        val unitId = newUnit()
        val id = assessments.saveAssessment(assessment(unitId, dueAt = now.toEpochMilli() + 5 * hour, reminder = 60))

        val s = scheduler.scheduled.single()
        assertEquals(id, s.assessmentId)
        assertEquals(4 * hour, s.delayMillis)
        assertEquals(now.toEpochMilli(), assessments.getAssessment(id)!!.updatedAt)
    }

    @Test
    fun reminderOff_orTriggerPassed_cancelsInsteadOfScheduling() = runTest {
        val unitId = newUnit()
        val off = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 5 * hour, reminder = null))
        val passed = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 30 * 60_000, reminder = 60))

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(off, passed), scheduler.cancelled)
    }

    @Test
    fun submitting_cancelsReminder_andReopeningReschedules() = runTest {
        val unitId = newUnit()
        val id = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 5 * hour))
        scheduler.scheduled.clear()

        assessments.updateStatus(id, AssessmentStatus.SUBMITTED)
        assertEquals(listOf(id), scheduler.cancelled)

        assessments.updateStatus(id, AssessmentStatus.IN_PROGRESS)
        assertEquals(id, scheduler.scheduled.single().assessmentId)
    }

    @Test
    fun deletingUnit_cancelsEveryChildReminder() = runTest {
        val unitId = newUnit()
        val other = newUnit("ABC1234")
        val a = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 5 * hour))
        val b = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 9 * hour))
        val kept = assessments.saveAssessment(assessment(other, now.toEpochMilli() + 9 * hour))
        scheduler.cancelled.clear()

        units.deleteUnit(units.getUnit(unitId)!!)

        assertEquals(setOf(a, b), scheduler.cancelled.toSet())
        assertTrue(kept !in scheduler.cancelled)
        assertEquals(listOf(kept), assessments.observeOutstanding().first().map { it.assessment.id })
    }

    @Test
    fun duplicateUnitCode_isTranslatedToDomainException() = runTest {
        newUnit("COS30017")
        try {
            newUnit("COS30017")
            fail("expected DuplicateUnitCodeException")
        } catch (e: DuplicateUnitCodeException) {
            assertEquals("COS30017", e.code)
        }
    }

    @Test
    fun editingUnitToAnotherUnitsCode_isRejected() = runTest {
        newUnit("COS30017")
        val id = newUnit("ABC1234")
        try {
            units.saveUnit(units.getUnit(id)!!.copy(code = "COS30017"))
            fail("expected DuplicateUnitCodeException")
        } catch (expected: DuplicateUnitCodeException) {
        }
    }

    @Test
    fun deletedSession_canBeRestoredWithTheSameId() = runTest {
        val unitId = newUnit()
        val aId = assessments.saveAssessment(assessment(unitId, now.toEpochMilli() + 5 * hour))
        val sId = sessions.saveSession(StudySession(assessmentId = aId, startedAt = 1, durationMinutes = 25, note = "n", source = SessionSource.TIMER))
        val saved = sessions.observeSessions(aId).first().single()

        sessions.deleteSession(saved)
        assertTrue(sessions.observeSessions(aId).first().isEmpty())
        sessions.saveSession(saved)

        assertEquals(listOf(saved), sessions.observeSessions(aId).first())
        assertEquals(sId, saved.id)
    }
}
