package com.example.assignment_3.ui.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.assignment_3.ui.components.DateField
import com.example.assignment_3.ui.components.FormTextField
import java.time.LocalDate

/**
 * A bottom sheet rather than a separate screen: logging a session is a short task the user does
 * while looking at the assessment, so the assessment stays visible behind the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSheet(
    state: SessionEditorState,
    onDateChange: (LocalDate) -> Unit,
    onDurationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (state.isNew) "Log study session" else "Edit study session", style = MaterialTheme.typography.titleLarge)
            DateField(
                label = "Date",
                date = state.date,
                onDateSelected = onDateChange,
                error = state.errors.date,
                selectableUntil = LocalDate.now()
            )
            FormTextField(
                value = state.durationText,
                onValueChange = onDurationChange,
                label = "Duration (minutes)",
                error = state.errors.duration,
                keyboardType = KeyboardType.Number
            )
            FormTextField(value = state.note, onValueChange = onNoteChange, label = "Note (optional)", error = null)
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("save_session")
            ) { Text(if (state.isNew) "Log session" else "Save changes") }
        }
    }
}
