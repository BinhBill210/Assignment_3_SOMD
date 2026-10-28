package com.example.assignment_3.ui.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudySessionRepository
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.SessionErrors
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.domain.SessionValidator
import com.example.assignment_3.ui.util.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

sealed interface AssessmentDetailUiState {
    data object Loading : AssessmentDetailUiState
    data object NotFound : AssessmentDetailUiState
    data object Deleted : AssessmentDetailUiState
    data class Content(
        val item: AssessmentWithUnit,
        val sessions: List<StudySession>,
        val nowMillis: Long,
        val showDeleteDialog: Boolean
    ) : AssessmentDetailUiState {
        val totalMinutes: Int get() = sessions.sumOf { it.durationMinutes }
    }
    data class Error(val message: String) : AssessmentDetailUiState
}

/** State of the create/edit session bottom sheet; null when the sheet is closed. */
data class SessionEditorState(
    val sessionId: Long = 0,
    val date: LocalDate,
    val durationText: String = "",
    val note: String = "",
    val originalStartedAt: Long? = null,
    val source: SessionSource = SessionSource.MANUAL,
    val errors: SessionErrors = SessionErrors(),
    val isSaving: Boolean = false
) {
    val isNew: Boolean get() = sessionId == 0L
}

class AssessmentDetailViewModel(
    private val assessmentId: Long,
    private val assessmentRepository: AssessmentRepository,
    private val sessionRepository: StudySessionRepository,
    private val clock: Clock
) : ViewModel() {

    private val showDeleteDialog = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)

    private val _editor = MutableStateFlow<SessionEditorState?>(null)
    val editor: StateFlow<SessionEditorState?> = _editor.asStateFlow()

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val uiState: StateFlow<AssessmentDetailUiState> = combine(
        assessmentRepository.observeAssessment(assessmentId),
        sessionRepository.observeSessions(assessmentId),
        showDeleteDialog,
        deleted
    ) { item, sessions, showDelete, isDeleted ->
        when {
            isDeleted -> AssessmentDetailUiState.Deleted
            item == null -> AssessmentDetailUiState.NotFound
            else -> AssessmentDetailUiState.Content(item, sessions, clock.millis(), showDelete)
        }
    }
        .catch { emit(AssessmentDetailUiState.Error(it.message ?: "Couldn't load this assessment")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssessmentDetailUiState.Loading)

    fun onStatusChange(status: AssessmentStatus) = runWrite("Couldn't update the status") {
        assessmentRepository.updateStatus(assessmentId, status)
        if (status == AssessmentStatus.SUBMITTED) _message.value = UserMessage.of("Marked as submitted - reminder cancelled")
    }

    fun requestDelete() {
        showDeleteDialog.value = true
    }

    fun dismissDelete() {
        showDeleteDialog.value = false
    }

    fun confirmDelete() {
        val content = uiState.value as? AssessmentDetailUiState.Content ?: return
        showDeleteDialog.value = false
        runWrite("Couldn't delete the assessment") {
            assessmentRepository.deleteAssessment(content.item.assessment)
            deleted.value = true
        }
    }

    // ------------------------------------------------------------ session editor

    fun openNewSession() {
        _editor.value = SessionEditorState(date = today())
    }

    fun openEditSession(session: StudySession) {
        _editor.value = SessionEditorState(
            sessionId = session.id,
            date = Instant.ofEpochMilli(session.startedAt).atZone(clock.zone).toLocalDate(),
            durationText = session.durationMinutes.toString(),
            note = session.note,
            originalStartedAt = session.startedAt,
            source = session.source
        )
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun onEditorDateChange(date: LocalDate) = _editor.update { it?.copy(date = date) }
    fun onEditorDurationChange(text: String) = _editor.update { it?.copy(durationText = text.filter(Char::isDigit).take(4)) }
    fun onEditorNoteChange(text: String) = _editor.update { it?.copy(note = text) }

    fun saveSession() {
        val state = _editor.value ?: return
        if (state.isSaving) return
        val startedAt = startedAtFor(state)
        val errors = SessionValidator.validate(state.durationText, startedAt, clock.millis())
        if (!errors.isValid) {
            _editor.value = state.copy(errors = errors)
            return
        }
        _editor.value = state.copy(isSaving = true, errors = errors)
        viewModelScope.launch {
            try {
                sessionRepository.saveSession(
                    StudySession(
                        id = state.sessionId,
                        assessmentId = assessmentId,
                        startedAt = startedAt,
                        durationMinutes = state.durationText.trim().toInt(),
                        note = state.note.trim(),
                        source = state.source
                    )
                )
                _editor.value = null
                _message.value = UserMessage.of(if (state.isNew) "Study session logged" else "Session updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editor.update { it?.copy(isSaving = false) }
                _message.value = UserMessage.of("Couldn't save the session. Please try again.")
            }
        }
    }

    fun deleteSession(session: StudySession) = runWrite("Couldn't delete the session") {
        sessionRepository.deleteSession(session)
    }

    /** Undo for a deleted session: re-inserting with the same id restores it exactly. */
    fun restoreSession(session: StudySession) = runWrite("Couldn't restore the session") {
        sessionRepository.saveSession(session)
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun runWrite(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = UserMessage.of("$failure. Please try again.")
            }
        }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone).toLocalDate()

    /**
     * Keeps the original time of day when only the date of an existing session changes. A new
     * session logged for today is assumed to have just finished; earlier days default to midday.
     */
    private fun startedAtFor(state: SessionEditorState): Long {
        val zone = clock.zone
        state.originalStartedAt?.let { original ->
            val time = Instant.ofEpochMilli(original).atZone(zone).toLocalTime()
            return state.date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        if (state.date == today()) {
            val minutes = state.durationText.trim().toLongOrNull() ?: 0L
            return (clock.millis() - minutes * 60_000L)
                .coerceAtLeast(state.date.atStartOfDay(zone).toInstant().toEpochMilli())
        }
        return state.date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }
}
