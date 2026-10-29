package com.example.assignment_3.ui.assessment

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.ReminderOption
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.notifications.NotificationHelper
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.ConfirmDialog
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.components.SectionHeader
import com.example.assignment_3.ui.components.UnitColorDot
import com.example.assignment_3.ui.util.formatDate
import com.example.assignment_3.ui.util.formatDueDateTime
import com.example.assignment_3.ui.util.formatMinutes
import com.example.assignment_3.ui.util.formatRelativeDue
import kotlinx.coroutines.launch

@Composable
fun AssessmentDetailScreen(
    onBack: () -> Unit,
    onEdit: (unitId: Long, assessmentId: Long) -> Unit,
    onStartFocus: (Long) -> Unit,
    onDeleted: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    viewModel: AssessmentDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Re-check on every ON_RESUME: the user may have just enabled notifications in system
    // settings, and the banner should disappear as soon as they come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    val helper = remember(context) { NotificationHelper(context.applicationContext) }
    var notificationsEnabled by remember { mutableStateOf(helper.canPostNotifications()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsEnabled = helper.canPostNotifications()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state is AssessmentDetailUiState.Deleted) {
        if (state is AssessmentDetailUiState.Deleted) currentOnDeleted("Assessment deleted")
    }
    LaunchedEffect(message?.id) {
        message?.let {
            onShowSnackbar(it.text, null)
            viewModel.consumeMessage()
        }
    }

    AssessmentDetailContent(
        state = state,
        notificationsEnabled = notificationsEnabled,
        onBack = onBack,
        onEdit = onEdit,
        onStartFocus = onStartFocus,
        onStatusChange = viewModel::onStatusChange,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onLogSession = viewModel::openNewSession,
        onEditSession = viewModel::openEditSession,
        onDeleteSession = { session ->
            viewModel.deleteSession(session)
            // rememberCoroutineScope: showSnackbar suspends, and this starts from a click, not composition.
            scope.launch {
                if (onShowSnackbar("Session deleted", "Undo")) viewModel.restoreSession(session)
            }
        },
        onOpenNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )

    editor?.let {
        SessionSheet(
            state = it,
            onDateChange = viewModel::onEditorDateChange,
            onDurationChange = viewModel::onEditorDurationChange,
            onNoteChange = viewModel::onEditorNoteChange,
            onSave = viewModel::saveSession,
            onDismiss = viewModel::closeEditor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentDetailContent(
    state: AssessmentDetailUiState,
    notificationsEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: (Long, Long) -> Unit,
    onStartFocus: (Long) -> Unit,
    onStatusChange: (AssessmentStatus) -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onLogSession: () -> Unit,
    onEditSession: (StudySession) -> Unit,
    onDeleteSession: (StudySession) -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val content = state as? AssessmentDetailUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.item?.unitCode ?: "Assessment") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (content != null) {
                        val a = content.item.assessment
                        IconButton(onClick = { onEdit(a.unitId, a.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit assessment")
                        }
                        IconButton(onClick = onRequestDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete assessment")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            AssessmentDetailUiState.Loading, AssessmentDetailUiState.Deleted -> LoadingState(modifier)
            AssessmentDetailUiState.NotFound -> ErrorState("This assessment no longer exists.", modifier)
            is AssessmentDetailUiState.Error -> ErrorState(state.message, modifier)
            is AssessmentDetailUiState.Content -> DetailList(
                state, notificationsEnabled, onStartFocus, onStatusChange, onLogSession,
                onEditSession, onDeleteSession, onOpenNotificationSettings, modifier
            )
        }
    }

    if (content?.showDeleteDialog == true) {
        ConfirmDialog(
            title = "Delete \"${content.item.assessment.title}\"?",
            text = "Its ${content.sessions.size} study session(s) and any pending reminder will also be removed.",
            confirmLabel = "Delete",
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailList(
    state: AssessmentDetailUiState.Content,
    notificationsEnabled: Boolean,
    onStartFocus: (Long) -> Unit,
    onStatusChange: (AssessmentStatus) -> Unit,
    onLogSession: () -> Unit,
    onEditSession: (StudySession) -> Unit,
    onDeleteSession: (StudySession) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier
) {
    val a = state.item.assessment
    val reminder = ReminderOption.fromMinutes(a.reminderOffsetMinutes)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("assessment_detail_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitColorDot(state.item.unitColorIndex)
                    Spacer(Modifier.width(8.dp))
                    Text("${state.item.unitCode} · ${state.item.unitName}", style = MaterialTheme.typography.labelLarge)
                }
                Text(a.title, style = MaterialTheme.typography.headlineSmall)
                Text("${a.type.label} · ${a.weighting}% of final grade", style = MaterialTheme.typography.bodyMedium)
                Text("Due ${formatDueDateTime(a.dueAt)}", style = MaterialTheme.typography.bodyLarge)
                if (a.status != AssessmentStatus.SUBMITTED) {
                    Text(
                        formatRelativeDue(a.dueAt, state.nowMillis),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (a.dueAt <= state.nowMillis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item(key = "status") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AssessmentStatus.entries.forEachIndexed { index, status ->
                    SegmentedButton(
                        selected = a.status == status,
                        onClick = { onStatusChange(status) },
                        shape = SegmentedButtonDefaults.itemShape(index, AssessmentStatus.entries.size),
                        label = { Text(status.label, maxLines = 1) }
                    )
                }
            }
        }
        item(key = "reminder") {
            val blocked = reminder != ReminderOption.OFF && a.status != AssessmentStatus.SUBMITTED && !notificationsEnabled
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("reminder_card")
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (blocked) {
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                a.status == AssessmentStatus.SUBMITTED -> "Submitted - no reminder needed"
                                reminder == ReminderOption.OFF -> "No reminder set"
                                else -> "Reminder: ${reminder.label}"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (blocked) {
                            Text("Notifications are turned off for StudyHive, so this reminder won't appear.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (blocked) TextButton(onClick = onOpenNotificationSettings) { Text("Settings") }
                }
            }
        }
        if (a.notes.isNotBlank()) {
            item(key = "notes") {
                Column {
                    SectionHeader("Notes")
                    Text(a.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "sessions_header") {
            SectionHeader("Study sessions", trailing = "Total ${formatMinutes(state.totalMinutes)}")
        }
        item(key = "session_actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onStartFocus(a.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Timer, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start focus")
                }
                OutlinedButton(onClick = onLogSession, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log session")
                }
            }
        }
        if (state.sessions.isEmpty()) {
            item(key = "no_sessions") {
                Text(
                    "No study sessions yet. Start a focus timer or log time you've already spent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.sessions, key = { it.id }) { session ->
            ListItem(
                headlineContent = { Text(formatMinutes(session.durationMinutes)) },
                supportingContent = {
                    Text(
                        listOf(
                            formatDate(session.startedAt),
                            if (session.source == SessionSource.TIMER) "Focus timer" else null,
                            session.note.ifBlank { null }
                        ).filterNotNull().joinToString(" · ")
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDeleteSession(session) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete session")
                    }
                },
                modifier = Modifier.clickable(onClickLabel = "Edit session") { onEditSession(session) }
            )
        }
    }
}
