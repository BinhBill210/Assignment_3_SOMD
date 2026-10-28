package com.example.assignment_3.ui.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.domain.DuplicateUnitCodeException
import com.example.assignment_3.domain.UnitErrors
import com.example.assignment_3.domain.UnitValidator
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

@Serializable
data class UnitDraft(
    val code: String = "",
    val name: String = "",
    val targetHours: String = "",
    val colorIndex: Int = 0
)

data class UnitFormUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val draft: UnitDraft = UnitDraft(),
    val errors: UnitErrors = UnitErrors(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDirty: Boolean = false,
    val message: UserMessage? = null
)

private data class UnitFormStatus(
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val errors: UnitErrors = UnitErrors(),
    val attemptedSave: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val message: UserMessage? = null
)

class UnitFormViewModel(
    private val unitId: Long,
    savedStateHandle: SavedStateHandle,
    private val unitRepository: StudyUnitRepository,
    private val clock: Clock
) : ViewModel() {

    private val isNew = unitId == NEW
    private val draft = SavedDraft(savedStateHandle, "unit_draft", UnitDraft.serializer(), UnitDraft())
    private val baseline = SavedDraft(savedStateHandle, "unit_baseline", UnitDraft.serializer(), UnitDraft())
    private val status = MutableStateFlow(UnitFormStatus(isLoading = !isNew))
    private var original: StudyUnit? = null

    val uiState: StateFlow<UnitFormUiState> = combine(draft.state, baseline.state, status) { d, b, s ->
        UnitFormUiState(
            isNew = isNew,
            isLoading = s.isLoading,
            notFound = s.notFound,
            draft = d,
            errors = s.errors,
            isSaving = s.isSaving,
            isSaved = s.isSaved,
            isDirty = d != b,
            message = s.message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UnitFormUiState(isNew = isNew, isLoading = !isNew))

    init {
        if (!isNew) loadOriginal()
    }

    private fun loadOriginal() {
        viewModelScope.launch {
            val unit = unitRepository.getUnit(unitId)
            original = unit
            if (unit == null) {
                status.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val loaded = UnitDraft(
                code = unit.code,
                name = unit.name,
                targetHours = (unit.targetMinutesPerWeek / 60).toString(),
                colorIndex = unit.colorIndex
            )
            // After process death the restored draft holds the user's unsaved edits; keep them.
            if (!draft.wasRestored) {
                draft.update { loaded }
                baseline.update { loaded }
            }
            status.update { it.copy(isLoading = false) }
        }
    }

    fun onCodeChange(value: String) = edit { it.copy(code = value) }
    fun onNameChange(value: String) = edit { it.copy(name = value) }
    fun onTargetHoursChange(value: String) = edit { it.copy(targetHours = value.filter(Char::isDigit).take(3)) }
    fun onColorChange(index: Int) = edit { it.copy(colorIndex = index) }

    /** Errors appear only after the first save attempt, then update live as the user fixes them. */
    private fun edit(transform: (UnitDraft) -> UnitDraft) {
        draft.update(transform)
        status.update { s ->
            if (s.attemptedSave) s.copy(errors = validate(draft.value)) else s
        }
    }

    fun save() {
        if (status.value.isSaving) return
        val current = draft.value
        val errors = validate(current)
        status.update { it.copy(attemptedSave = true, errors = errors) }
        if (!errors.isValid) return

        status.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val code = UnitValidator.normaliseCode(current.code)
            val unit = StudyUnit(
                id = if (isNew) 0 else unitId,
                code = code,
                name = current.name.trim(),
                colorIndex = current.colorIndex,
                targetMinutesPerWeek = current.targetHours.trim().toInt() * 60,
                createdAt = original?.createdAt ?: clock.millis()
            )
            try {
                unitRepository.saveUnit(unit)
                baseline.update { current }
                status.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: DuplicateUnitCodeException) {
                status.update {
                    it.copy(isSaving = false, errors = it.errors.copy(code = "You already have a unit with code $code"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.update {
                    it.copy(isSaving = false, message = UserMessage.of("Couldn't save the unit. Please try again."))
                }
            }
        }
    }

    fun consumeMessage() = status.update { it.copy(message = null) }

    private fun validate(d: UnitDraft) = UnitValidator.validate(d.code, d.name, d.targetHours)
}
