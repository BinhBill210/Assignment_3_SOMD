package com.example.assignment_3.ui.units

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.ColorPicker
import com.example.assignment_3.ui.components.DiscardChangesDialog
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.FormTextField
import com.example.assignment_3.ui.components.LoadingState

@Composable
fun UnitFormScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    viewModel: UnitFormViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)

    // Keyed on isSaved so navigation happens exactly once, not on every recomposition.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) currentOnSaved(if (state.isNew) "Unit added" else "Unit updated")
    }
    LaunchedEffect(state.message?.id) {
        state.message?.let {
            onShowSnackbar(it.text, null)
            viewModel.consumeMessage()
        }
    }

    UnitFormContent(
        state = state,
        onClose = onClose,
        onCodeChange = viewModel::onCodeChange,
        onNameChange = viewModel::onNameChange,
        onTargetHoursChange = viewModel::onTargetHoursChange,
        onColorChange = viewModel::onColorChange,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitFormContent(
    state: UnitFormUiState,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onTargetHoursChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSave: () -> Unit
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val guardLeave = state.isDirty && !state.isSaved
    val requestClose = { if (guardLeave) showDiscard = true else onClose() }
    BackHandler(enabled = guardLeave) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New unit" else "Edit unit") },
                navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, contentDescription = "Close") } }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingState(modifier)
            state.notFound -> ErrorState("This unit no longer exists.", modifier)
            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.draft.code,
                    onValueChange = onCodeChange,
                    label = { Text("Unit code") },
                    isError = state.errors.code != null,
                    supportingText = { Text(state.errors.code ?: "e.g. COS30017") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("Unit code")
                        .semantics { state.errors.code?.let { error(it) } }
                )
                FormTextField(state.draft.name, onNameChange, "Unit name", state.errors.name)
                FormTextField(
                    value = state.draft.targetHours,
                    onValueChange = onTargetHoursChange,
                    label = "Weekly study target (hours)",
                    error = state.errors.targetHours,
                    keyboardType = KeyboardType.Number,
                    supportingText = "Most 12.5 credit point units expect about 10 hours a week"
                )
                Text("Colour", style = MaterialTheme.typography.titleSmall)
                ColorPicker(state.draft.colorIndex, onColorChange)
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("save_unit")
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save unit")
                }
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(onDiscard = { showDiscard = false; onClose() }, onKeepEditing = { showDiscard = false })
    }
}
