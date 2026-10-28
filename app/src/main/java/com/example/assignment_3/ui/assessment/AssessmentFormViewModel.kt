package com.example.assignment_3.ui.assessment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.AssessmentErrors
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.AssessmentValidator
import com.example.assignment_3.domain.ReminderOption
import com.example.assignment_3.ui.navigation.NEW
import com.example.assignment_3.ui.util.SavedDraft
import com.example.assignment_3.ui.util.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class AssessmentDraft(
    val title: String = "",
    val type: AssessmentType = AssessmentType.ASSIGNMENT,
    val weighting: String = "",
    val dueDateEpochDay: Long? = null,
    val dueMinuteOfDay: Int = DEFAULT_DUE_MINUTE,
    val reminder: ReminderOption = ReminderOption.ONE_DAY,
    val notes: String = ""
) {
    val dueDate: LocalDate? get() = dueDateEpochDay?.let(LocalDate::ofEpochDay)
    val dueTime: LocalTime get() = LocalTime.ofSecondOfDay(dueMinuteOfDay * 60L)

    companion object {
        /** 11:59 pm, the usual Canvas submission cut-off. */
        const val DEFAULT_DUE_MINUTE = 23 * 60 + 59
    }
}

data class AssessmentFormUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val unitCode: String = "",
    val otherWeightingTotal: Int = 0,
    val draft: AssessmentDraft = AssessmentDraft(),
    val errors: AssessmentErrors = AssessmentErrors(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDirty: Boolean = false,
    val message: UserMessage? = null
)

private data class FormStatus(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val unitCode: String = "",
    val otherWeightingTotal: Int = 0,
    val errors: AssessmentErrors = AssessmentErrors(),
    val attemptedSave: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val message: UserMessage? = null
)

class AssessmentFormViewModel(
    private val unitId: Long,
    private val assessmentId: Long,
    savedStateHandle: SavedStateHandle,
    private val assessmentRepository: AssessmentRepository,
    private val unitRepository: StudyUnitRepository,
    private val clock: Clock
) : ViewModel() {

    private val isNew = assessmentId == NEW
    private val draft = SavedDraft(savedStateHandle, "assessment_draft", AssessmentDraft.serializer(), AssessmentDraft())
    private val baseline = SavedDraft(savedStateHandle, "assessment_baseline", AssessmentDraft.serializer(), AssessmentDraft())
    private val status = MutableStateFlow(FormStatus())
    private var original: Assessment? = null

    val uiState: StateFlow<AssessmentFormUiState> = combine(draft.state, baseline.state, status) { d, b, s ->
        AssessmentFormUiState(
            isNew = isNew,
            isLoading = s.isLoading,
            notFound = s.notFound,
            unitCode = s.unitCode,
            otherWeightingTotal = s.otherWeightingTotal,
            draft = d,
            errors = s.errors,
            isSaving = s.isSaving,
            isSaved = s.isSaved,
            isDirty = d != b,
            message = s.message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssessmentFormUiState(isNew = isNew))

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val unit = unitRepository.getUnit(unitId)
        val existing = if (isNew) null else assessmentRepository.getAssessment(assessmentId)
        if (unit == null || (!isNew && existing == null)) {
            status.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        original = existing
        if (existing != null && !draft.wasRestored) {
            val due = Instant.ofEpochMilli(existing.dueAt).atZone(clock.zone)
            val loaded = AssessmentDraft(
                title = existing.title,
                type = existing.type,
                weighting = existing.weighting.toString(),
                dueDateEpochDay = due.toLocalDate().toEpochDay(),
                dueMinuteOfDay = due.hour * 60 + due.minute,
                reminder = ReminderOption.fromMinutes(existing.reminderOffsetMinutes),
                notes = existing.notes
            )
            draft.update { loaded }
            baseline.update { loaded }
        }
        val otherTotal = assessmentRepository.weightingTotal(unitId, assessmentId)
        status.update { it.copy(isLoading = false, unitCode = unit.code, otherWeightingTotal = otherTotal) }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }
    fun onTypeChange(value: AssessmentType) = edit { it.copy(type = value) }
    fun onWeightingChange(value: String) = edit { it.copy(weighting = value.filter(Char::isDigit).take(3)) }
    fun onDueDateChange(date: LocalDate) = edit { it.copy(dueDateEpochDay = date.toEpochDay()) }
    fun onDueTimeChange(hour: Int, minute: Int) = edit { it.copy(dueMinuteOfDay = hour * 60 + minute) }
    fun onReminderChange(value: ReminderOption) = edit { it.copy(reminder = value) }
    fun onNotesChange(value: String) = edit { it.copy(notes = value) }

    private fun edit(transform: (AssessmentDraft) -> AssessmentDraft) {
        draft.update(transform)
        status.update { s -> if (s.attemptedSave) s.copy(errors = validate(draft.value, s.otherWeightingTotal)) else s }
    }

    fun save() {
        if (status.value.isSaving || status.value.isLoading) return
        status.update { it.copy(isSaving = true, attemptedSave = true) }
        viewModelScope.launch {
            try {
                // Re-read the total at save time: another assessment may have been added since load.
                val otherTotal = assessmentRepository.weightingTotal(unitId, assessmentId)
                val current = draft.value
                val errors = validate(current, otherTotal)
                if (!errors.isValid) {
                    status.update { it.copy(isSaving = false, errors = errors, otherWeightingTotal = otherTotal) }
                    return@launch
                }
                val now = clock.millis()
                assessmentRepository.saveAssessment(
                    Assessment(
                        id = if (isNew) 0 else assessmentId,
                        unitId = unitId,
                        title = current.title.trim(),
                        type = current.type,
                        weighting = current.weighting.trim().toInt(),
                        dueAt = dueAtMillis(current)!!,
                        status = original?.status ?: AssessmentStatus.NOT_STARTED,
                        reminderOffsetMinutes = current.reminder.minutes,
                        notes = current.notes.trim(),
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now
                    )
                )
                baseline.update { current }
                status.update { it.copy(isSaving = false, isSaved = true, errors = AssessmentErrors()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.update {
                    it.copy(isSaving = false, message = UserMessage.of("Couldn't save the assessment. Please try again."))
                }
            }
        }
    }

    fun consumeMessage() = status.update { it.copy(message = null) }

    private fun dueAtMillis(d: AssessmentDraft): Long? =
        d.dueDate?.atTime(d.dueTime)?.atZone(clock.zone)?.toInstant()?.toEpochMilli()

    private fun validate(d: AssessmentDraft, otherTotal: Int) = AssessmentValidator.validate(
        title = d.title,
        weightingText = d.weighting,
        dueAtMillis = dueAtMillis(d),
        reminderMinutes = d.reminder.minutes,
        otherWeightingTotal = otherTotal,
        nowMillis = clock.millis(),
        isNew = isNew
    )
}
