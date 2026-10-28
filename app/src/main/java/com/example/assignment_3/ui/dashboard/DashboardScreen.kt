package com.example.assignment_3.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.AssessmentCard
import com.example.assignment_3.ui.components.EmptyState
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.components.SectionHeader
import com.example.assignment_3.ui.components.UnitProgressCard
import com.example.assignment_3.ui.util.formatMinutes

@Composable
fun DashboardScreen(
    onAssessmentClick: (Long) -> Unit,
    onUnitClick: (Long) -> Unit,
    onAddUnit: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(state, onAssessmentClick, onUnitClick, onAddUnit, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onAssessmentClick: (Long) -> Unit,
    onUnitClick: (Long) -> Unit,
    onAddUnit: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("StudyHive") }) }) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            DashboardUiState.Loading -> LoadingState(modifier)
            is DashboardUiState.Error -> ErrorState(state.message, modifier, onRetry)
            DashboardUiState.NoUnits -> EmptyState(
                icon = Icons.Outlined.School,
                title = "Welcome to StudyHive",
                body = "Add the units you're studying this semester, then track their assessments, deadlines and study time.",
                actionLabel = "Add your first unit",
                onAction = onAddUnit,
                modifier = modifier
            )
            is DashboardUiState.Content -> DashboardList(state, onAssessmentClick, onUnitClick, modifier)
        }
    }
}

@Composable
private fun DashboardList(
    state: DashboardUiState.Content,
    onAssessmentClick: (Long) -> Unit,
    onUnitClick: (Long) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("This week", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${formatMinutes(state.minutesThisWeek)} studied · ${state.dueSoon.size} due in the next 7 days",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.overdue.isNotEmpty()) {
                        Text(
                            "${state.overdue.size} overdue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item(key = "units_header") { SectionHeader("Your units") }
        item(key = "units_row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.progress, key = { it.unitId }) { progress ->
                    UnitProgressCard(progress, onClick = { onUnitClick(progress.unitId) }, modifier = Modifier.width(240.dp))
                }
            }
        }
        if (state.overdue.isNotEmpty()) {
            item(key = "overdue_header") { SectionHeader("Overdue", trailing = "${state.overdue.size}") }
            assessmentItems(state.overdue, state.nowMillis, onAssessmentClick)
        }
        item(key = "soon_header") { SectionHeader("Due in the next 7 days", trailing = "${state.dueSoon.size}") }
        if (state.isAllCaughtUp) {
            item(key = "caught_up") {
                Text(
                    "Nothing due in the next 7 days. Nice work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            assessmentItems(state.dueSoon, state.nowMillis, onAssessmentClick)
        }
        if (state.laterCount > 0) {
            item(key = "later") {
                Text(
                    "+${state.laterCount} more due later",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.assessmentItems(
    items: List<AssessmentWithUnit>,
    nowMillis: Long,
    onClick: (Long) -> Unit
) {
    items(items, key = { "assessment_${it.assessment.id}" }) { item ->
        val a = item.assessment
        AssessmentCard(
            title = a.title,
            type = a.type,
            weighting = a.weighting,
            dueAt = a.dueAt,
            status = a.status,
            colorIndex = item.unitColorIndex,
            nowMillis = nowMillis,
            unitCode = item.unitCode,
            onClick = { onClick(a.id) }
        )
    }
}
