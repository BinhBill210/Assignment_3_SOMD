package com.example.assignment_3.ui.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.WeekRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

sealed interface UnitListUiState {
    data object Loading : UnitListUiState
    data object Empty : UnitListUiState
    data class Content(val units: List<UnitProgress>) : UnitListUiState
    data class Error(val message: String) : UnitListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class UnitListViewModel(
    private val unitRepository: StudyUnitRepository,
    private val clock: Clock
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<UnitListUiState> = retryTrigger
        .flatMapLatest {
            val week = WeekRange.containing(clock.millis(), clock.zone)
            unitRepository.observeProgress(week.startMillis, week.endMillis)
                .map { if (it.isEmpty()) UnitListUiState.Empty else UnitListUiState.Content(it) }
                .catch { emit(UnitListUiState.Error(it.message ?: "Couldn't load your units")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitListUiState.Loading)

    fun retry() {
        retryTrigger.value++
    }
}
