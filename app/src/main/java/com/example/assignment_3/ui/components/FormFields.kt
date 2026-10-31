package com.example.assignment_3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.assignment_3.ui.theme.UnitPalette
import com.example.assignment_3.ui.util.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Outlined field whose error text is also exposed to TalkBack through the error semantics. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    testTag: String = label
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { if (error != null) error(error) }
    )
}

@Composable
fun ColorPicker(selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UnitPalette.forEachIndexed { index, color ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .semantics { contentDescription = "Colour ${index + 1}" }
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(color))
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black)
            }
        }
    }
}

/** Read-only field that opens a Material 3 date picker; [selectableFrom] limits past dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    selectableFrom: LocalDate? = null,
    selectableUntil: LocalDate? = null
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        value = date?.let(::formatDate) ?: "",
        label = label,
        error = error,
        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Choose $label") },
        onOpen = { open = true },
        modifier = modifier
    )
    if (open) {
        val state = rememberDatePickerState(
            // DatePicker works in UTC midnight millis, independent of the device time zone.
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return (selectableFrom == null || !d.isBefore(selectableFrom)) &&
                        (selectableUntil == null || !d.isAfter(selectableUntil))
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: LocalTime, onTimeSelected: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        value = time.format(DateTimeFormatter.ofPattern("h:mm a", LocalConfiguration.current.locales[0])),
        label = label,
        error = null,
        icon = { Icon(Icons.Outlined.Schedule, contentDescription = "Choose $label") },
        onOpen = { open = true },
        modifier = modifier
    )
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onTimeSelected(state.hour, state.minute); open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    error: String?,
    icon: @Composable () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = { IconButton(onClick = onOpen) { icon() } },
        modifier = modifier.fillMaxWidth().testTag(label).semantics { if (error != null) error(error) }
    )
}
