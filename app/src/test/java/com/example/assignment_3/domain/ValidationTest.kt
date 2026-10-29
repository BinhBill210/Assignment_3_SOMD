package com.example.assignment_3.domain

import com.example.assignment_3.testing.DAY
import com.example.assignment_3.testing.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitValidatorTest {

    @Test
    fun validUnit_hasNoErrors() {
        assertTrue(UnitValidator.validate("COS30017", "Mobile Development", "10").isValid)
    }

    @Test
    fun code_isNormalisedBeforeValidation() {
        assertEquals("COS30017", UnitValidator.normaliseCode("  cos30017 "))
        assertTrue(UnitValidator.validate(" cos30017 ", "Mobile", "10").isValid)
    }

    @Test
    fun blankFields_reportEveryError() {
        val errors = UnitValidator.validate("", " ", "")
        assertNotNull(errors.code)
        assertNotNull(errors.name)
        assertNotNull(errors.targetHours)
    }

    @Test
    fun malformedCode_isRejected() {
        listOf("CO30017", "COSXX017", "COS301", "COSTA300177", "30017COS").forEach {
            assertNotNull("expected $it to be rejected", UnitValidator.validate(it, "Name", "10").code)
        }
    }

    @Test
    fun targetHours_boundaries() {
        assertNotNull(UnitValidator.validate("COS30017", "N", "0").targetHours)
        assertNull(UnitValidator.validate("COS30017", "N", "1").targetHours)
        assertNull(UnitValidator.validate("COS30017", "N", "40").targetHours)
        assertNotNull(UnitValidator.validate("COS30017", "N", "41").targetHours)
        assertNotNull(UnitValidator.validate("COS30017", "N", "ten").targetHours)
    }

    @Test
    fun name_lengthBoundary() {
        assertNull(UnitValidator.validate("COS30017", "a".repeat(60), "10").name)
        assertNotNull(UnitValidator.validate("COS30017", "a".repeat(61), "10").name)
    }
}

class AssessmentValidatorTest {
    private val now = 1_000_000_000_000L

    private fun validate(
        title: String = "Assignment 1",
        weighting: String = "30",
        dueAt: Long? = now + 3 * DAY,
        reminder: Int? = 24 * 60,
        otherTotal: Int = 0,
        isNew: Boolean = true
    ) = AssessmentValidator.validate(title, weighting, dueAt, reminder, otherTotal, now, isNew)

    @Test
    fun validAssessment_hasNoErrors() {
        assertTrue(validate().isValid)
    }

    @Test
    fun weighting_boundaries() {
        assertNotNull(validate(weighting = "0").weighting)
        assertNull(validate(weighting = "1").weighting)
        assertNull(validate(weighting = "100").weighting)
        assertNotNull(validate(weighting = "101").weighting)
        assertNotNull(validate(weighting = "").weighting)
    }

    @Test
    fun weighting_cannotPushUnitTotalOver100() {
        assertNull(validate(weighting = "40", otherTotal = 60).weighting)
        val error = validate(weighting = "41", otherTotal = 60).weighting
        assertNotNull(error)
        assertTrue(error!!.contains("40%"))
    }

    @Test
    fun weighting_fullyAllocatedUnit_rejectsAnyWeighting() {
        assertNotNull(validate(weighting = "1", otherTotal = 100).weighting)
    }

    @Test
    fun dueDate_requiredAndMustBeFutureForNewAssessments() {
        assertNotNull(validate(dueAt = null).dueAt)
        assertNotNull(validate(dueAt = now).dueAt)
        assertNull(validate(dueAt = now + 1, reminder = null).dueAt)
    }

    @Test
    fun existingAssessment_mayAlreadyBeOverdue() {
        val errors = validate(dueAt = now - DAY, isNew = false)
        assertNull(errors.dueAt)
        assertNull("an overdue reminder should be ignored, not block edits", errors.reminder)
    }

    @Test
    fun reminder_inThePast_isRejected() {
        // Due in 2 hours with a 3-hour reminder: the trigger time has already passed.
        assertNotNull(validate(dueAt = now + 2 * HOUR, reminder = 180).reminder)
        assertNull(validate(dueAt = now + 4 * HOUR, reminder = 180).reminder)
        assertNull(validate(dueAt = now + 2 * HOUR, reminder = null).reminder)
    }

    @Test
    fun title_blankAndTooLong() {
        assertNotNull(validate(title = "   ").title)
        assertNull(validate(title = "a".repeat(80)).title)
        assertNotNull(validate(title = "a".repeat(81)).title)
    }
}

class SessionValidatorTest {
    private val now = 1_000_000_000_000L

    @Test
    fun duration_boundaries() {
        assertFalse(SessionValidator.validate("0", now, now).isValid)
        assertTrue(SessionValidator.validate("1", now, now).isValid)
        assertTrue(SessionValidator.validate("720", now, now).isValid)
        assertFalse(SessionValidator.validate("721", now, now).isValid)
        assertFalse(SessionValidator.validate("", now, now).isValid)
    }

    @Test
    fun futureSession_isRejected() {
        assertNotNull(SessionValidator.validate("30", now + 1, now).date)
        assertNull(SessionValidator.validate("30", now, now).date)
    }
}
