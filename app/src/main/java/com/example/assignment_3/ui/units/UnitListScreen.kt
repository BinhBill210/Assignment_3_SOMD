package com.example.assignment_3.ui.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.EmptyState
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.components.UnitProgressCard

@Composable
fun UnitListScreen(
    onUnitClick: (Long) -> Unit,
    onAddUnit: () -> Unit,
    viewModel: UnitListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UnitListContent(state, onUnitClick, onAddUnit, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitListContent(
    state: UnitListUiState,
    onUnitClick: (Long) -> Unit,
    onAddUnit: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Units") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddUnit,
                // ExtendedFAB hides its text from accessibility and takes its label from the icon.
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add unit") },
                text = { Text("Add unit") }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            UnitListUiState.Loading -> LoadingState(modifier)
            is UnitListUiState.Error -> ErrorState(state.message, modifier, onRetry)
            UnitListUiState.Empty -> EmptyState(
                icon = Icons.Outlined.School,
                title = "No units yet",
                body = "Add a unit to start tracking its assessments and study time.",
                modifier = modifier
            )
            is UnitListUiState.Content -> LazyColumn(
                modifier = modifier.fillMaxSize().testTag("unit_list"),
                // Bottom padding keeps the last card clear of the floating action button.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.units, key = { it.unitId }) { unit ->
                    UnitProgressCard(unit, onClick = { onUnitClick(unit.unitId) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
