package com.example.assignment_3.domain

import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.model.AssessmentWithSessions
import com.example.assignment_3.data.local.model.UnitWithAssessments
import com.example.assignment_3.data.local.model.toUnitProgress
import com.example.assignment_3.testing.DAY
import com.example.assignment_3.testing.HOUR
import com.example.assignment_3.testing.TEST_NOW
import com.example.assignment_3.testing.TEST_ZONE
import com.example.assignment_3.testing.assessment
import com.example.assignment_3.testing.unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderPolicyTest {
    private val now = 1_000_000_000_000L
    private val due = now + 2 * DAY

    @Test
    fun delay_isDueMinusOffsetMinusNow() {
        assertEquals(DAY, ReminderPolicy.delayUntilReminder(now, due, 24 * 60, AssessmentStatus.NOT_STARTED))
    }

    @Test
    fun noReminder_whenOffOrSubmitted() {
        assertNull(ReminderPolicy.delayUntilReminder(now, due, null, AssessmentStatus.IN_PROGRESS))
        assertNull(ReminderPolicy.delayUntilReminder(now, due, 60, AssessmentStatus.SUBMITTED))
    }

    @Test
    fun triggerExactlyNow_isNotScheduled() {
        assertNull(ReminderPolicy.delayUntilReminder(now, now + HOUR, 60, AssessmentStatus.NOT_STARTED))
        assertEquals(1L, ReminderPolicy.delayUntilReminder(now, now + HOUR + 1, 60, AssessmentStatus.NOT_STARTED))
    }

    @Test
    fun shouldNotify_onlyWhenStillPendingAndUnchanged() {
        assertTrue(ReminderPolicy.shouldNotify(AssessmentStatus.IN_PROGRESS, due, 60, due))
        assertFalse("deleted", ReminderPolicy.shouldNotify(null, null, null, due))
        assertFalse("submitted", ReminderPolicy.shouldNotify(AssessmentStatus.SUBMITTED, due, 60, due))
        assertFalse("reminder switched off", ReminderPolicy.shouldNotify(AssessmentStatus.NOT_STARTED, due, null, due))
        assertFalse("deadline moved", ReminderPolicy.shouldNotify(AssessmentStatus.NOT_STARTED, due + HOUR, 60, due))
    }
}

class WeekRangeTest {

    @Test
    fun week_startsMondayMidnightLocal() {
        val week = WeekRange.containing(TEST_NOW.toEpochMilli(), TEST_ZONE)
        val expectedStart = LocalDateTime.of(2025, 11, 3, 0, 0).atZone(TEST_ZONE).toInstant().toEpochMilli()
        assertEquals(expectedStart, week.startMillis)
        assertEquals(expectedStart + 7 * DAY, week.endMillis)
    }

    @Test
    fun boundaries_areHalfOpen() {
        val week = WeekRange.containing(TEST_NOW.toEpochMilli(), TEST_ZONE)
        assertTrue(week.startMillis in week)
        assertFalse(week.endMillis in week)
        assertTrue(week.endMillis - 1 in week)
    }

    @Test
    fun weekContainingDstChange_isStillMondayToMonday() {
        // Melbourne daylight saving ends 6 Apr 2025, so this week is 169 hours long.
        val millis = LocalDateTime.of(2025, 4, 6, 12, 0).atZone(TEST_ZONE).toInstant().toEpochMilli()
        val week = WeekRange.containing(millis, TEST_ZONE)
        assertEquals(169 * HOUR, week.endMillis - week.startMillis)
    }
}

/** Strategy B of the investigation must agree with strategy A's SQL; this pins its rules. */
class KotlinAggregationTest {

    @Test
    fun aggregatesCountsAndOnlySessionsInsideTheWeek() {
        val week = WeekRange.containing(TEST_NOW.toEpochMilli(), TEST_ZONE)
        fun session(id: Long, a: Assessment, at: Long, min: Int) = StudySession(id, a.id, at, min, "", SessionSource.MANUAL)
        val a1 = assessment(id = 1, status = AssessmentStatus.SUBMITTED)
        val a2 = assessment(id = 2)
        val graph = listOf(
            UnitWithAssessments(
                unit(id = 1, code = "COS30017"),
                listOf(
                    AssessmentWithSessions(a1, listOf(session(1, a1, week.startMillis, 30), session(2, a1, week.startMillis - 1, 999))),
                    AssessmentWithSessions(a2, listOf(session(3, a2, week.endMillis - 1, 45), session(4, a2, week.endMillis, 999)))
                )
            ),
            UnitWithAssessments(unit(id = 2, code = "ABC1234"), emptyList())
        )

        val result = graph.toUnitProgress(week.startMillis, week.endMillis)

        assertEquals(listOf("ABC1234", "COS30017"), result.map { it.code })
        val cos = result[1]
        assertEquals(2, cos.assessmentCount)
        assertEquals(1, cos.submittedCount)
        assertEquals(75, cos.minutesThisWeek)
        assertEquals(0, result[0].minutesThisWeek)
    }
}
