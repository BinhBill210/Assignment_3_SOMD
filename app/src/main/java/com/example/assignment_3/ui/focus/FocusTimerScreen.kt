package com.example.assignment_3.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.assignment_3.ui.AppViewModelProvider
import com.example.assignment_3.ui.components.ConfirmDialog
import com.example.assignment_3.ui.components.ErrorState
import com.example.assignment_3.ui.components.LoadingState
import com.example.assignment_3.ui.util.formatElapsed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    viewModel: FocusTimerViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer = state.timer
    val currentOnSaved by rememberUpdatedState(onSaved)
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    // Display-only tick. Elapsed time is derived from stored timestamps, so a late tick or the
    // effect being cancelled while the screen is off never loses time.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.isRunning) {
        while (timer.isRunning) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Keep the screen awake only while timing, and always release it when leaving the screen.
    val view = LocalView.current
    DisposableEffect(view, timer.isRunning) {
        view.keepScreenOn = timer.isRunning
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = timer.hasStarted) { showDiscard = true }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) currentOnSaved("Focus session saved")
    }
    LaunchedEffect(state.message?.id) {
        state.message?.let {
            onShowSnackbar(it.text, null)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus timer") },
                navigationIcon = {
                    IconButton(onClick = { if (timer.hasStarted) showDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingState(modifier)
            state.notFound -> ErrorState("This assessment no longer exists.", modifier)
            // Centred when it fits; scrolls in landscape or at large font sizes instead of clipping the buttons.
            else -> BoxWithConstraints(modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.assessmentTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        formatElapsed(timer.elapsedAt(now)),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.testTag("elapsed").semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Text(
                        when {
                            timer.isRunning -> "Focusing - the screen will stay on"
                            timer.hasStarted -> "Paused"
                            else -> "Start when you're ready"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (timer.isRunning) {
                            FilledTonalButton(onClick = viewModel::pause, modifier = Modifier.weight(1f)) { Text("Pause") }
                        } else {
                            Button(onClick = viewModel::start, modifier = Modifier.weight(1f)) {
                                Text(if (timer.hasStarted) "Resume" else "Start")
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::stopAndSave,
                            enabled = timer.hasStarted && !state.isSaving,
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop & save") }
                    }
                }
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard this session?",
            text = "The time on the timer hasn't been saved. Use \"Stop & save\" to keep it.",
            confirmLabel = "Discard",
            onConfirm = {
                showDiscard = false
                viewModel.discard()
                onBack()
            },
            onDismiss = { showDiscard = false }
        )
    }
}
