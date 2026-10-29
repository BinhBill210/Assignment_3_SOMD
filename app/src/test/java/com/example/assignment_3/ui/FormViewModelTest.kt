package com.example.assignment_3.ui

import androidx.lifecycle.SavedStateHandle
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.ReminderOption
import com.example.assignment_3.testing.FakeAssessmentRepository
import com.example.assignment_3.testing.FakeStudyUnitRepository
import com.example.assignment_3.testing.MainDispatcherRule
import com.example.assignment_3.testing.MutableClock
import com.example.assignment_3.testing.TEST_NOW
import com.example.assignment_3.testing.TEST_ZONE
import com.example.assignment_3.testing.assessment
import com.example.assignment_3.testing.unit
import com.example.assignment_3.ui.assessment.AssessmentFormViewModel
import com.example.assignment_3.ui.navigation.NEW
import com.example.assignment_3.ui.units.UnitFormViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class UnitFormViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository()
    private val clock = MutableClock()

    private fun newVm(id: Long = NEW, handle: SavedStateHandle = SavedStateHandle()) =
        UnitFormViewModel(id, handle, units, clock)

    private fun UnitFormViewModel.fill(code: String = "cos30017", name: String = "Mobile Dev", hours: String = "10") {
        onCodeChange(code); onNameChange(name); onTargetHoursChange(hours)
    }

    @Test
    fun errorsHidden_untilFirstSaveAttempt_thenLive() = runTest {
        val vm = newVm()
        vm.onCodeChange("x")
        assertTrue(vm.uiState.value.errors.isValid)

        vm.save()
        assertNotNull(vm.uiState.value.errors.code)
        assertNotNull(vm.uiState.value.errors.name)

        vm.onNameChange("Mobile Dev")
        assertNull("errors update live after the first attempt", vm.uiState.value.errors.name)
        assertTrue(units.units.value.isEmpty())
    }

    @Test
    fun validSave_normalisesCodeAndConvertsHoursToMinutes() = runTest {
        val vm = newVm()
        vm.fill()
        vm.save()

        val saved = units.units.value.single()
        assertEquals("COS30017", saved.code)
        assertEquals(600, saved.targetMinutesPerWeek)
        assertEquals(TEST_NOW.toEpochMilli(), saved.createdAt)
        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun duplicateCode_showsFieldError_andDoesNotNavigate() = runTest {
        units.units.value = listOf(unit(id = 1, code = "COS30017"))
        val vm = newVm()
        vm.fill(code = "COS30017")
        vm.save()

        assertTrue(vm.uiState.value.errors.code!!.contains("already"))
        assertFalse(vm.uiState.value.isSaved)
        assertEquals(1, units.units.value.size)
    }

    @Test
    fun repositoryFailure_keepsInputAndShowsMessage() = runTest {
        units.failWrites = true
        val vm = newVm()
        vm.fill()
        vm.save()

        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("Mobile Dev", vm.uiState.value.draft.name)
    }

    @Test
    fun editMode_loadsExistingUnit_andUpdatesInPlace() = runTest {
        units.units.value = listOf(unit(id = 7, code = "COS30017", targetHours = 12))
        val vm = newVm(id = 7)
        assertEquals("12", vm.uiState.value.draft.targetHours)
        assertFalse(vm.uiState.value.isDirty)

        vm.onNameChange("Renamed")
        assertTrue(vm.uiState.value.isDirty)
        vm.save()

        assertEquals("Renamed", units.units.value.single { it.id == 7L }.name)
        assertEquals(1, units.units.value.size)
    }

    @Test
    fun editMode_missingUnit_reportsNotFound() = runTest {
        assertTrue(newVm(id = 42).uiState.value.notFound)
    }

    @Test
    fun draft_survivesProcessDeath_viaSavedStateHandle() = runTest {
        val handle = SavedStateHandle()
        newVm(handle = handle).onNameChange("Half typed")

        // A new ViewModel over the same handle is what Android does after process death.
        val restored = newVm(handle = handle)
        assertEquals("Half typed", restored.uiState.value.draft.name)
    }

    @Test
    fun targetHours_acceptsDigitsOnly() = runTest {
        val vm = newVm()
        vm.onTargetHoursChange("1a2.5")
        assertEquals("125", vm.uiState.value.draft.targetHours)
    }
}

class AssessmentFormViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository().apply { units.value = listOf(unit(id = 1)) }
    private val assessments = FakeAssessmentRepository(units)
    private val clock = MutableClock()
    private val today: LocalDate = TEST_NOW.atZone(TEST_ZONE).toLocalDate()

    private fun newVm(assessmentId: Long = NEW) =
        AssessmentFormViewModel(1, assessmentId, SavedStateHandle(), assessments, units, clock)

    private fun AssessmentFormViewModel.fillValid(weighting: String = "30") {
        onTitleChange("Assignment 3")
        onWeightingChange(weighting)
        onDueDateChange(today.plusDays(7))
    }

    @Test
    fun loadsUnitCodeAndOtherWeighting() = runTest {
        assessments.assessments.value = listOf(assessment(id = 5, weighting = 40))
        val state = newVm().uiState.value
        assertEquals("COS30017", state.unitCode)
        assertEquals(40, state.otherWeightingTotal)
    }

    @Test
    fun save_createsNotStartedAssessmentWithChosenReminder() = runTest {
        val vm = newVm()
        vm.fillValid()
        vm.onReminderChange(ReminderOption.THREE_DAYS)
        vm.save()

        val saved = assessments.assessments.value.single()
        assertEquals(AssessmentStatus.NOT_STARTED, saved.status)
        assertEquals(3 * 24 * 60, saved.reminderOffsetMinutes)
        assertEquals(
            today.plusDays(7).atTime(23, 59).atZone(TEST_ZONE).toInstant().toEpochMilli(),
            saved.dueAt
        )
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun weightingCap_usesTotalReadAtSaveTime() = runTest {
        val vm = newVm()
        vm.fillValid(weighting = "50")
        // Another assessment appears after the form loaded (e.g. created on another screen).
        assessments.assessments.value = listOf(assessment(id = 9, weighting = 60))
        vm.save()

        assertNotNull(vm.uiState.value.errors.weighting)
        assertEquals(60, vm.uiState.value.otherWeightingTotal)
        assertEquals(1, assessments.assessments.value.size)
    }

    @Test
    fun editing_excludesOwnWeightingAndKeepsStatus() = runTest {
        assessments.assessments.value = listOf(
            assessment(id = 5, weighting = 60, status = AssessmentStatus.IN_PROGRESS),
            assessment(id = 6, weighting = 40)
        )
        val vm = newVm(assessmentId = 5)
        assertEquals("60", vm.uiState.value.draft.weighting)

        vm.onTitleChange("Renamed")
        vm.save()

        val saved = assessments.assessments.value.single { it.id == 5L }
        assertEquals("Renamed", saved.title)
        assertEquals(AssessmentStatus.IN_PROGRESS, saved.status)
    }

    @Test
    fun missingDueDate_blocksSave() = runTest {
        val vm = newVm()
        vm.onTitleChange("Exam")
        vm.onWeightingChange("50")
        vm.save()
        assertNotNull(vm.uiState.value.errors.dueAt)
        assertTrue(assessments.assessments.value.isEmpty())
    }

    @Test
    fun repositoryFailure_surfacesMessage() = runTest {
        assessments.failWrites = true
        val vm = newVm()
        vm.fillValid()
        vm.save()
        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun unknownUnit_reportsNotFound() = runTest {
        val vm = AssessmentFormViewModel(99, NEW, SavedStateHandle(), assessments, units, clock)
        assertTrue(vm.uiState.value.notFound)
    }
}
