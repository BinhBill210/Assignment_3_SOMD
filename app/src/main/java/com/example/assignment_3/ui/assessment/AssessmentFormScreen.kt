package com.example.assignment_3.ui.assessment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.ReminderOption
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.DateField
import com.example.assignment_3.ui.components.DiscardChangesDialog
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.FormTextField
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.components.TimeField
import java.time.LocalDate

@Composable
fun AssessmentFormScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    viewModel: AssessmentFormViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val context = LocalContext.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) currentOnSaved(if (state.isNew) "Assessment added" else "Assessment updated")
    }
    LaunchedEffect(state.message?.id) {
        state.message?.let {
            onShowSnackbar(it.text, null)
            viewModel.consumeMessage()
        }
    }

    // The permission is requested in context - only when the user saves a valid assessment that has a
    // reminder - and saving goes ahead whatever they answer, so refusing never blocks their work.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.save()
    }
    val onSave = onSave@{
        if (!viewModel.validateForSave()) return@onSave
        val needsPermission = state.draft.reminder != ReminderOption.OFF &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.save()
    }

    AssessmentFormContent(
        state = state,
        today = LocalDate.now(),
        onClose = onClose,
        onTitleChange = viewModel::onTitleChange,
        onTypeChange = viewModel::onTypeChange,
        onWeightingChange = viewModel::onWeightingChange,
        onDueDateChange = viewModel::onDueDateChange,
        onDueTimeChange = viewModel::onDueTimeChange,
        onReminderChange = viewModel::onReminderChange,
        onNotesChange = viewModel::onNotesChange,
        onSave = onSave
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentFormContent(
    state: AssessmentFormUiState,
    today: LocalDate,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTypeChange: (AssessmentType) -> Unit,
    onWeightingChange: (String) -> Unit,
    onDueDateChange: (LocalDate) -> Unit,
    onDueTimeChange: (Int, Int) -> Unit,
    onReminderChange: (ReminderOption) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val guardLeave = state.isDirty && !state.isSaved
    BackHandler(enabled = guardLeave) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (state.isNew) "New assessment" else "Edit assessment") +
                            if (state.unitCode.isNotEmpty()) " · ${state.unitCode}" else ""
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (guardLeave) showDiscard = true else onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingState(modifier)
            state.notFound -> ErrorState("This assessment or its unit no longer exists.", modifier)
            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val d = state.draft
                FormTextField(d.title, onTitleChange, "Title", state.errors.title)
                OptionDropdown(
                    label = "Type",
                    selected = d.type,
                    options = AssessmentType.entries,
                    optionLabel = { it.label },
                    onSelect = onTypeChange
                )
                FormTextField(
                    value = d.weighting,
                    onValueChange = onWeightingChange,
                    label = "Weighting (%)",
                    error = state.errors.weighting,
                    keyboardType = KeyboardType.Number,
                    supportingText = "${state.otherWeightingTotal}% already allocated to other assessments in this unit"
                )
                DateField(
                    label = "Due date",
                    date = d.dueDate,
                    onDateSelected = onDueDateChange,
                    error = state.errors.dueAt,
                    selectableFrom = if (state.isNew) today else null
                )
                TimeField(label = "Due time", time = d.dueTime, onTimeSelected = onDueTimeChange)
                OptionDropdown(
                    label = "Reminder",
                    selected = d.reminder,
                    options = ReminderOption.entries,
                    optionLabel = { it.label },
                    onSelect = onReminderChange,
                    error = state.errors.reminder
                )
                FormTextField(
                    value = d.notes,
                    onValueChange = onNotesChange,
                    label = "Notes (optional)",
                    error = null,
                    singleLine = false,
                    minLines = 3
                )
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("save_assessment")
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save assessment")
                }
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(onDiscard = { showDiscard = false; onClose() }, onKeepEditing = { showDiscard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    error: String? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag(label)
                .semantics { if (error != null) error(error) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
