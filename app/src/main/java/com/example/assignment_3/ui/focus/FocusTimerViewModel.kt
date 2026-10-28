package com.example.assignment_3.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudySessionRepository
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.domain.SessionValidator
import com.example.assignment_3.ui.util.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The timer stores *timestamps*, never a tick count: elapsed time is derived from the clock, so it
 * stays correct while the app is backgrounded and nothing drifts if a tick is late.
 */
data class TimerState(
    val accumulatedMillis: Long = 0,
    val runningSince: Long? = null,
    val firstStartedAt: Long? = null
) {
    val isRunning: Boolean get() = runningSince != null
    val hasStarted: Boolean get() = firstStartedAt != null
    fun elapsedAt(nowMillis: Long): Long = accumulatedMillis + (runningSince?.let { nowMillis - it } ?: 0)
}

data class FocusUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val assessmentTitle: String = "",
    val timer: TimerState = TimerState(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val message: UserMessage? = null
)

private data class SaveStatus(val isSaving: Boolean = false, val isSaved: Boolean = false, val message: UserMessage? = null)

class FocusTimerViewModel(
    private val assessmentId: Long,
    private val savedStateHandle: SavedStateHandle,
    assessmentRepository: AssessmentRepository,
    private val sessionRepository: StudySessionRepository,
    private val clock: Clock
) : ViewModel() {

    // Each field is a primitive in SavedStateHandle, so a running timer survives process death.
    private val timer = MutableStateFlow(
        TimerState(
            accumulatedMillis = savedStateHandle[KEY_ACCUMULATED] ?: 0L,
            runningSince = savedStateHandle.get<Long>(KEY_RUNNING_SINCE)?.takeIf { it > 0 },
            firstStartedAt = savedStateHandle.get<Long>(KEY_FIRST_STARTED)?.takeIf { it > 0 }
        )
    )
    private val saveStatus = MutableStateFlow(SaveStatus())

    val uiState: StateFlow<FocusUiState> = combine(
        assessmentRepository.observeAssessment(assessmentId).map { it?.assessment?.title },
        timer,
        saveStatus
    ) { title, t, s ->
        FocusUiState(
            isLoading = false,
            notFound = title == null,
            assessmentTitle = title.orEmpty(),
            timer = t,
            isSaving = s.isSaving,
            isSaved = s.isSaved,
            message = s.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start() {
        val now = clock.millis()
        setTimer { if (it.isRunning) it else it.copy(runningSince = now, firstStartedAt = it.firstStartedAt ?: now) }
    }

    fun pause() {
        val now = clock.millis()
        setTimer { t -> t.runningSince?.let { t.copy(accumulatedMillis = t.elapsedAt(now), runningSince = null) } ?: t }
    }

    fun discard() = setTimer { TimerState() }

    fun stopAndSave() {
        if (saveStatus.value.isSaving) return
        val t = timer.value
        val minutes = (t.elapsedAt(clock.millis()) / 60_000L).toInt()
        val startedAt = t.firstStartedAt
        if (startedAt == null || minutes < 1) {
            saveStatus.update { it.copy(message = UserMessage.of("Sessions shorter than a minute aren't saved")) }
            return
        }
        pause()
        saveStatus.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                sessionRepository.saveSession(
                    StudySession(
                        assessmentId = assessmentId,
                        startedAt = startedAt,
                        durationMinutes = minutes.coerceAtMost(SessionValidator.MAX_SESSION_MINUTES),
                        note = "Focus session",
                        source = SessionSource.TIMER
                    )
                )
                setTimer { TimerState() }
                saveStatus.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveStatus.update {
                    it.copy(isSaving = false, message = UserMessage.of("Couldn't save the session. Please try again."))
                }
            }
        }
    }

    fun consumeMessage() = saveStatus.update { it.copy(message = null) }

    private fun setTimer(transform: (TimerState) -> TimerState) {
        val next = transform(timer.value)
        timer.value = next
        savedStateHandle[KEY_ACCUMULATED] = next.accumulatedMillis
        savedStateHandle[KEY_RUNNING_SINCE] = next.runningSince ?: 0L
        savedStateHandle[KEY_FIRST_STARTED] = next.firstStartedAt ?: 0L
    }

    private companion object {
        const val KEY_ACCUMULATED = "timer_accumulated"
        const val KEY_RUNNING_SINCE = "timer_running_since"
        const val KEY_FIRST_STARTED = "timer_first_started"
    }
}
