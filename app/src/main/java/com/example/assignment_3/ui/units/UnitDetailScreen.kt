package com.example.assignment_3.ui.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.AssessmentCard
import com.example.assignment_3.ui.components.ConfirmDialog
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.components.UnitColorDot
import com.example.assignment_3.ui.theme.unitColor
import com.example.assignment_3.ui.util.formatMinutes

@Composable
fun UnitDetailScreen(
    onBack: () -> Unit,
    onEditUnit: (Long) -> Unit,
    onAddAssessment: (Long) -> Unit,
    onAssessmentClick: (Long) -> Unit,
    onDeleted: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    viewModel: UnitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val deletedCode = (state as? UnitDetailUiState.Deleted)?.code

    LaunchedEffect(deletedCode) {
        if (deletedCode != null) currentOnDeleted("$deletedCode deleted")
    }
    LaunchedEffect(message?.id) {
        message?.let {
            onShowSnackbar(it.text, null)
            viewModel.consumeMessage()
        }
    }

    UnitDetailContent(
        state = state,
        onBack = onBack,
        onEditUnit = onEditUnit,
        onAddAssessment = onAddAssessment,
        onAssessmentClick = onAssessmentClick,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDetailContent(
    state: UnitDetailUiState,
    onBack: () -> Unit,
    onEditUnit: (Long) -> Unit,
    onAddAssessment: (Long) -> Unit,
    onAssessmentClick: (Long) -> Unit,
    onFilterChange: (StatusFilter) -> Unit,
    onSortChange: (AssessmentSort) -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    val content = state as? UnitDetailUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.unit?.code ?: "Unit") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = { onEditUnit(content.unit.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit unit")
                        }
                        IconButton(onClick = onRequestDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete unit")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (content != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddAssessment(content.unit.id) },
                    // ExtendedFAB hides its text from accessibility and takes its label from the icon.
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add assessment") },
                    text = { Text("Add assessment") }
                )
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            UnitDetailUiState.Loading, is UnitDetailUiState.Deleted -> LoadingState(modifier)
            UnitDetailUiState.NotFound -> ErrorState("This unit no longer exists.", modifier)
            is UnitDetailUiState.Error -> ErrorState(state.message, modifier)
            is UnitDetailUiState.Content -> UnitDetailList(state, onAssessmentClick, onFilterChange, onSortChange, modifier)
        }
    }

    content?.pendingDelete?.let { counts ->
        ConfirmDialog(
            title = "Delete ${content.unit.code}?",
            text = "This also deletes ${counts.assessments} assessment(s) and ${counts.sessions} study session(s), " +
                "and cancels their reminders. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete
        )
    }
}

@Composable
private fun UnitDetailList(
    state: UnitDetailUiState.Content,
    onAssessmentClick: (Long) -> Unit,
    onFilterChange: (StatusFilter) -> Unit,
    onSortChange: (AssessmentSort) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("unit_detail_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UnitColorDot(state.unit.colorIndex, size = 16)
                        Spacer(Modifier.width(8.dp))
                        Text(state.unit.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Weekly target: ${formatMinutes(state.unit.targetMinutesPerWeek)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("${state.allocatedWeighting}% of 100% weighting allocated", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (state.allocatedWeighting / 100f).coerceIn(0f, 1f) },
                        color = unitColor(state.unit.colorIndex),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item(key = "controls") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatusFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
                SortMenu(state.sort, onSortChange)
            }
        }
        if (state.assessments.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.totalAssessments == 0) {
                        "No assessments yet. Add one to start tracking its deadline."
                    } else {
                        "No assessments match \"${state.filter.label}\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp).testTag("assessments_empty")
                )
            }
        }
        items(state.assessments, key = { it.id }) { a ->
            AssessmentCard(
                title = a.title,
                type = a.type,
                weighting = a.weighting,
                dueAt = a.dueAt,
                status = a.status,
                colorIndex = state.unit.colorIndex,
                nowMillis = state.nowMillis,
                onClick = { onAssessmentClick(a.id) }
            )
        }
    }
}

@Composable
private fun SortMenu(current: AssessmentSort, onSortChange: (AssessmentSort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort by ${current.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AssessmentSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == current) "✓ ${option.label}" else option.label) },
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
