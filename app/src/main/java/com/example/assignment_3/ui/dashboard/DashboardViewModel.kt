package com.example.assignment_3.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.WeekRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object NoUnits : DashboardUiState
    data class Content(
        val nowMillis: Long,
        val overdue: List<AssessmentWithUnit>,
        val dueSoon: List<AssessmentWithUnit>,
        val laterCount: Int,
        val progress: List<UnitProgress>
    ) : DashboardUiState {
        val minutesThisWeek: Int get() = progress.sumOf { it.minutesThisWeek }
        val isAllCaughtUp: Boolean get() = overdue.isEmpty() && dueSoon.isEmpty()
    }
    data class Error(val message: String) : DashboardUiState

    companion object {
        const val DUE_SOON_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

        fun from(
            nowMillis: Long,
            outstanding: List<AssessmentWithUnit>,
            progress: List<UnitProgress>
        ): DashboardUiState {
            if (progress.isEmpty()) return NoUnits
            val (overdue, upcoming) = outstanding.partition { it.assessment.dueAt <= nowMillis }
            val (soon, later) = upcoming.partition { it.assessment.dueAt - nowMillis <= DUE_SOON_WINDOW_MILLIS }
            return Content(nowMillis, overdue, soon, later.size, progress)
        }
    }
}

/**
 * The dashboard is time-dependent ("overdue", "this week") as well as data-dependent, so a
 * one-minute clock tick is combined with the Room flows. flatMapLatest re-subscribes to the
 * progress query with the correct week window whenever the tick crosses into a new week.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val unitRepository: StudyUnitRepository,
    private val assessmentRepository: AssessmentRepository,
    private val clock: Clock,
    private val tickMillis: Long = 60_000L
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    private val now = flow {
        while (true) {
            emit(clock.millis())
            delay(tickMillis)
        }
    }

    val uiState: StateFlow<DashboardUiState> = retryTrigger
        .flatMapLatest {
            now.flatMapLatest { nowMillis ->
                val week = WeekRange.containing(nowMillis, clock.zone)
                combine(
                    assessmentRepository.observeOutstanding(),
                    unitRepository.observeProgress(week.startMillis, week.endMillis)
                ) { outstanding, progress -> DashboardUiState.from(nowMillis, outstanding, progress) }
            }.catch { emit(DashboardUiState.Error(it.message ?: "Couldn't load your dashboard")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    fun retry() {
        retryTrigger.value++
    }
}
