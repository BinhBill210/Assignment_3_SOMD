package com.example.assignment_3.ui.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.ui.util.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

enum class StatusFilter(val label: String, val status: AssessmentStatus?) {
    ALL("All", null),
    NOT_STARTED("Not started", AssessmentStatus.NOT_STARTED),
    IN_PROGRESS("In progress", AssessmentStatus.IN_PROGRESS),
    SUBMITTED("Submitted", AssessmentStatus.SUBMITTED)
}

enum class AssessmentSort(val label: String) { DUE_DATE("Due date"), WEIGHTING("Weighting") }

sealed interface UnitDetailUiState {
    data object Loading : UnitDetailUiState
    data object NotFound : UnitDetailUiState
    data class Deleted(val code: String) : UnitDetailUiState
    data class Content(
        val unit: StudyUnit,
        val assessments: List<Assessment>,
        val totalAssessments: Int,
        val allocatedWeighting: Int,
        val filter: StatusFilter,
        val sort: AssessmentSort,
        val nowMillis: Long,
        val pendingDelete: CascadeCounts?
    ) : UnitDetailUiState
    data class Error(val message: String) : UnitDetailUiState
}

class UnitDetailViewModel(
    private val unitId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val unitRepository: StudyUnitRepository,
    private val assessmentRepository: AssessmentRepository,
    private val clock: Clock
) : ViewModel() {

    // Filter and sort survive process death: they are UI choices, not persisted business data.
    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, StatusFilter.ALL)
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, AssessmentSort.DUE_DATE)
    private val pendingDelete = MutableStateFlow<CascadeCounts?>(null)
    private val deletedCode = MutableStateFlow<String?>(null)

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val uiState: StateFlow<UnitDetailUiState> = combine(
        unitRepository.observeUnit(unitId),
        assessmentRepository.observeForUnit(unitId),
        combine(filter, sort, ::Pair),
        pendingDelete,
        deletedCode
    ) { unit, assessments, (filter, sort), pending, deleted ->
        when {
            deleted != null -> UnitDetailUiState.Deleted(deleted)
            unit == null -> UnitDetailUiState.NotFound
            else -> UnitDetailUiState.Content(
                unit = unit,
                assessments = assessments
                    .filter { filter.status == null || it.status == filter.status }
                    .let { list ->
                        when (sort) {
                            AssessmentSort.DUE_DATE -> list.sortedBy { it.dueAt }
                            AssessmentSort.WEIGHTING -> list.sortedByDescending { it.weighting }
                        }
                    },
                totalAssessments = assessments.size,
                allocatedWeighting = assessments.sumOf { it.weighting },
                filter = filter,
                sort = sort,
                nowMillis = clock.millis(),
                pendingDelete = pending
            )
        }
    }
        .catch { emit(UnitDetailUiState.Error(it.message ?: "Couldn't load this unit")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitDetailUiState.Loading)

    fun onFilterChange(value: StatusFilter) {
        savedStateHandle[KEY_FILTER] = value
    }

    fun onSortChange(value: AssessmentSort) {
        savedStateHandle[KEY_SORT] = value
    }

    /** Loads the cascade counts first so the dialog can say exactly what will be removed. */
    fun requestDelete() {
        viewModelScope.launch {
            pendingDelete.value = runCatching { unitRepository.cascadeCounts(unitId) }
                .getOrElse { CascadeCounts(0, 0) }
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val unit = (uiState.value as? UnitDetailUiState.Content)?.unit ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            try {
                unitRepository.deleteUnit(unit)
                deletedCode.value = unit.code
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = UserMessage.of("Couldn't delete ${unit.code}. Please try again.")
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val KEY_SORT = "sort"
    }
}
