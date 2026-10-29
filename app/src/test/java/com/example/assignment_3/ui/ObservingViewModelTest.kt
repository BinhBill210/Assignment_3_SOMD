package com.example.assignment_3.ui

import androidx.lifecycle.SavedStateHandle
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.testing.DAY
import com.example.assignment_3.testing.FakeAssessmentRepository
import com.example.assignment_3.testing.FakeStudySessionRepository
import com.example.assignment_3.testing.FakeStudyUnitRepository
import com.example.assignment_3.testing.HOUR
import com.example.assignment_3.testing.MainDispatcherRule
import com.example.assignment_3.testing.MutableClock
import com.example.assignment_3.testing.TEST_NOW
import com.example.assignment_3.testing.assessment
import com.example.assignment_3.testing.unit
import com.example.assignment_3.ui.assessment.AssessmentDetailUiState
import com.example.assignment_3.ui.assessment.AssessmentDetailViewModel
import com.example.assignment_3.ui.dashboard.DashboardUiState
import com.example.assignment_3.ui.dashboard.DashboardViewModel
import com.example.assignment_3.ui.focus.FocusTimerViewModel
import com.example.assignment_3.ui.units.AssessmentSort
import com.example.assignment_3.ui.units.StatusFilter
import com.example.assignment_3.ui.units.UnitDetailUiState
import com.example.assignment_3.ui.units.UnitDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val NOW = TEST_NOW.toEpochMilli()

/** Screens use WhileSubscribed, so a test must subscribe for the upstream Room flows to start. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.subscribe(flow: StateFlow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

private fun progress(id: Long, code: String, minutes: Int = 0) =
    UnitProgress(id, code, "Unit $code", 0, 600, 0, 0, minutes)

class DashboardViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository()
    private val assessments = FakeAssessmentRepository(units)
    private val clock = MutableClock()

    @Test
    fun startsLoading_thenNoUnitsWhenDatabaseIsEmpty() = runTest {
        val vm = DashboardViewModel(units, assessments, clock)
        assertEquals(DashboardUiState.Loading, vm.uiState.value)
        subscribe(vm.uiState)
        assertEquals(DashboardUiState.NoUnits, vm.uiState.value)
    }

    @Test
    fun splitsOutstandingIntoOverdueSoonAndLater() = runTest {
        units.units.value = listOf(unit(id = 1))
        units.progress.value = listOf(progress(1, "COS30017", minutes = 90))
        assessments.assessments.value = listOf(
            assessment(id = 1, dueAt = NOW - HOUR),
            assessment(id = 2, dueAt = NOW + 2 * DAY),
            assessment(id = 3, dueAt = NOW + 7 * DAY),
            assessment(id = 4, dueAt = NOW + 7 * DAY + 1),
            assessment(id = 5, dueAt = NOW + DAY, status = AssessmentStatus.SUBMITTED)
        )
        val vm = DashboardViewModel(units, assessments, clock)
        subscribe(vm.uiState)

        val state = vm.uiState.value as DashboardUiState.Content
        assertEquals(listOf(1L), state.overdue.map { it.assessment.id })
        assertEquals("7 days exactly is still 'soon'", listOf(2L, 3L), state.dueSoon.map { it.assessment.id })
        assertEquals(1, state.laterCount)
        assertEquals(90, state.minutesThisWeek)
    }

    @Test
    fun reactsToDatabaseChanges() = runTest {
        units.units.value = listOf(unit(id = 1))
        units.progress.value = listOf(progress(1, "COS30017"))
        val vm = DashboardViewModel(units, assessments, clock)
        subscribe(vm.uiState)
        assertTrue((vm.uiState.value as DashboardUiState.Content).isAllCaughtUp)

        assessments.assessments.value = listOf(assessment(id = 1, dueAt = NOW + DAY))

        assertEquals(1, (vm.uiState.value as DashboardUiState.Content).dueSoon.size)
    }

    @Test
    fun clockTick_movesAssessmentIntoOverdue() = runTest {
        units.units.value = listOf(unit(id = 1))
        units.progress.value = listOf(progress(1, "COS30017"))
        assessments.assessments.value = listOf(assessment(id = 1, dueAt = NOW + 30_000))
        val vm = DashboardViewModel(units, assessments, clock, tickMillis = 60_000)
        subscribe(vm.uiState)
        assertTrue((vm.uiState.value as DashboardUiState.Content).overdue.isEmpty())

        clock.advanceBy(60_000)
        testScheduler.advanceTimeBy(60_001)

        assertEquals(1, (vm.uiState.value as DashboardUiState.Content).overdue.size)
    }
}

class UnitDetailViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository().apply { units.value = listOf(unit(id = 1)) }
    private val assessments = FakeAssessmentRepository(units).apply {
        assessments.value = listOf(
            assessment(id = 1, weighting = 50, dueAt = NOW + 3 * DAY),
            assessment(id = 2, weighting = 10, dueAt = NOW + 1 * DAY, status = AssessmentStatus.SUBMITTED),
            assessment(id = 3, weighting = 30, dueAt = NOW + 2 * DAY, status = AssessmentStatus.IN_PROGRESS)
        )
    }
    private val handle = SavedStateHandle()
    private fun newVm(unitId: Long = 1) = UnitDetailViewModel(unitId, handle, units, assessments, MutableClock())

    @Test
    fun filterAndSort_deriveTheVisibleList() = runTest {
        val vm = newVm()
        subscribe(vm.uiState)
        var state = vm.uiState.value as UnitDetailUiState.Content
        assertEquals(listOf(2L, 3L, 1L), state.assessments.map { it.id })
        assertEquals(90, state.allocatedWeighting)

        vm.onFilterChange(StatusFilter.SUBMITTED)
        state = vm.uiState.value as UnitDetailUiState.Content
        assertEquals(listOf(2L), state.assessments.map { it.id })
        assertEquals("total is unaffected by the filter", 3, state.totalAssessments)

        vm.onFilterChange(StatusFilter.ALL)
        vm.onSortChange(AssessmentSort.WEIGHTING)
        state = vm.uiState.value as UnitDetailUiState.Content
        assertEquals(listOf(1L, 3L, 2L), state.assessments.map { it.id })
    }

    @Test
    fun filter_isRestoredFromSavedState() = runTest {
        newVm().onFilterChange(StatusFilter.IN_PROGRESS)
        val restored = newVm()
        subscribe(restored.uiState)
        assertEquals(StatusFilter.IN_PROGRESS, (restored.uiState.value as UnitDetailUiState.Content).filter)
    }

    @Test
    fun delete_showsCascadeCountsThenDeletes() = runTest {
        units.cascade = CascadeCounts(assessments = 3, sessions = 8)
        val vm = newVm()
        subscribe(vm.uiState)

        vm.requestDelete()
        assertEquals(CascadeCounts(3, 8), (vm.uiState.value as UnitDetailUiState.Content).pendingDelete)

        vm.confirmDelete()
        assertEquals(UnitDetailUiState.Deleted("COS30017"), vm.uiState.value)
        assertEquals(1L, units.deleted.single().id)
    }

    @Test
    fun deleteFailure_keepsContentAndShowsMessage() = runTest {
        units.failWrites = true
        val vm = newVm()
        subscribe(vm.uiState)
        vm.requestDelete()
        vm.confirmDelete()

        assertTrue(vm.uiState.value is UnitDetailUiState.Content)
        assertNotNull(vm.message.value)
    }

    @Test
    fun missingUnit_isNotFound() = runTest {
        val vm = newVm(unitId = 404)
        subscribe(vm.uiState)
        assertEquals(UnitDetailUiState.NotFound, vm.uiState.value)
    }
}

class AssessmentDetailViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository().apply { units.value = listOf(unit(id = 1)) }
    private val assessments = FakeAssessmentRepository(units).apply { assessments.value = listOf(assessment(id = 1)) }
    private val sessions = FakeStudySessionRepository()
    // Lazy: property initialisers run before the rule installs the test Main dispatcher, and a
    // ViewModel built then binds viewModelScope to the wrong dispatcher and never runs its work.
    private val vm by lazy { AssessmentDetailViewModel(1, assessments, sessions, MutableClock()) }

    @Test
    fun statusChange_isPersisted() = runTest {
        subscribe(vm.uiState)
        vm.onStatusChange(AssessmentStatus.SUBMITTED)
        assertEquals(AssessmentStatus.SUBMITTED, (vm.uiState.value as AssessmentDetailUiState.Content).item.assessment.status)
        assertNotNull(vm.message.value)
    }

    @Test
    fun logSession_validatesThenSavesAndClosesSheet() = runTest {
        subscribe(vm.uiState)
        vm.openNewSession()
        vm.onEditorDurationChange("0")
        vm.saveSession()
        assertNotNull(vm.editor.value!!.errors.duration)
        assertTrue(sessions.sessions.value.isEmpty())

        vm.onEditorDurationChange("45")
        vm.saveSession()
        assertNull(vm.editor.value)
        val content = vm.uiState.value as AssessmentDetailUiState.Content
        assertEquals(45, content.totalMinutes)
        assertTrue("a session logged today ends now", sessions.sessions.value.single().startedAt <= NOW)
    }

    @Test
    fun deleteThenUndo_restoresTheSameSession() = runTest {
        val session = StudySession(7, 1, NOW - HOUR, 30, "notes", SessionSource.MANUAL)
        sessions.sessions.value = listOf(session)
        subscribe(vm.uiState)

        vm.deleteSession(session)
        assertEquals(0, (vm.uiState.value as AssessmentDetailUiState.Content).sessions.size)
        vm.restoreSession(session)
        assertEquals(listOf(session), (vm.uiState.value as AssessmentDetailUiState.Content).sessions)
    }

    @Test
    fun editSession_keepsOriginalTimeOfDay() = runTest {
        val session = StudySession(7, 1, NOW - 2 * DAY + 5 * HOUR, 30, "", SessionSource.TIMER)
        sessions.sessions.value = listOf(session)
        subscribe(vm.uiState)

        vm.openEditSession(session)
        vm.onEditorDurationChange("50")
        vm.saveSession()

        val saved = sessions.sessions.value.single()
        assertEquals(session.startedAt, saved.startedAt)
        assertEquals(50, saved.durationMinutes)
        assertEquals(SessionSource.TIMER, saved.source)
    }

    @Test
    fun confirmDelete_movesToDeleted() = runTest {
        subscribe(vm.uiState)
        vm.requestDelete()
        assertTrue((vm.uiState.value as AssessmentDetailUiState.Content).showDeleteDialog)
        vm.confirmDelete()
        assertEquals(AssessmentDetailUiState.Deleted, vm.uiState.value)
    }
}

class FocusTimerViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val units = FakeStudyUnitRepository().apply { units.value = listOf(unit(id = 1)) }
    private val assessments = FakeAssessmentRepository(units).apply { assessments.value = listOf(assessment(id = 1)) }
    private val sessions = FakeStudySessionRepository()
    private val clock = MutableClock()
    private val handle = SavedStateHandle()
    private fun newVm() = FocusTimerViewModel(1, handle, assessments, sessions, clock)

    @Test
    fun elapsed_isDerivedFromTimestamps_acrossPauseAndResume() = runTest {
        val vm = newVm()
        subscribe(vm.uiState)
        vm.start()
        clock.advanceBy(10 * 60_000)
        vm.pause()
        clock.advanceBy(60 * 60_000) // paused time must not count
        vm.start()
        clock.advanceBy(5 * 60_000)

        assertEquals(15 * 60_000L, vm.uiState.value.timer.elapsedAt(clock.millis()))
    }

    @Test
    fun underOneMinute_isNotSaved() = runTest {
        val vm = newVm()
        subscribe(vm.uiState)
        vm.start()
        clock.advanceBy(59_999)
        vm.stopAndSave()

        assertTrue(sessions.sessions.value.isEmpty())
        assertNotNull(vm.uiState.value.message)
        assertTrue("timer keeps running", vm.uiState.value.timer.isRunning)
    }

    @Test
    fun stopAndSave_writesTimerSessionAndResets() = runTest {
        val vm = newVm()
        subscribe(vm.uiState)
        vm.start()
        clock.advanceBy(25 * 60_000 + 30_000)
        vm.stopAndSave()

        val saved = sessions.sessions.value.single()
        assertEquals(25, saved.durationMinutes)
        assertEquals(SessionSource.TIMER, saved.source)
        assertEquals(NOW, saved.startedAt)
        assertTrue(vm.uiState.value.isSaved)
        assertTrue(!vm.uiState.value.timer.hasStarted)
    }

    @Test
    fun runningTimer_survivesProcessDeath() = runTest {
        newVm().start()
        clock.advanceBy(3 * 60_000)

        val restored = newVm()
        subscribe(restored.uiState)
        assertTrue(restored.uiState.value.timer.isRunning)
        assertEquals(3 * 60_000L, restored.uiState.value.timer.elapsedAt(clock.millis()))
    }
}
