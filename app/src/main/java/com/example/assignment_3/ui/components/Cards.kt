package com.example.assignment_3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.ui.theme.unitColor
import com.example.assignment_3.ui.util.formatMinutes
import com.example.assignment_3.ui.util.formatRelativeDue

@Composable
fun StatusBadge(status: AssessmentStatus, modifier: Modifier = Modifier) {
    val (container, content) = when (status) {
        AssessmentStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        AssessmentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AssessmentStatus.SUBMITTED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            status.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun UnitColorDot(colorIndex: Int, modifier: Modifier = Modifier, size: Int = 12) {
    Box(modifier.size(size.dp).clip(CircleShape).background(unitColor(colorIndex)))
}

/**
 * One assessment row, shared by the dashboard (with [unitCode]) and the unit detail list (where
 * the unit is already known, so [unitCode] is null). The colour strip ties it to its unit.
 */
@Composable
fun AssessmentCard(
    title: String,
    type: AssessmentType,
    weighting: Int,
    dueAt: Long,
    status: AssessmentStatus,
    colorIndex: Int,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unitCode: String? = null
) {
    val relativeDue = if (status == AssessmentStatus.SUBMITTED) "Submitted" else formatRelativeDue(dueAt, nowMillis)
    val overdue = status != AssessmentStatus.SUBMITTED && dueAt <= nowMillis
    val description = listOfNotNull(unitCode, title, type.label, "$weighting percent", relativeDue, status.label)
        .joinToString(", ")
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(unitColor(colorIndex)))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (unitCode != null) {
                    Text(unitCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${type.label} · $weighting%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        relativeDue,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(status)
                }
            }
        }
    }
}

/** Unit summary with weekly progress; full width in the unit list, fixed width in the dashboard row. */
@Composable
fun UnitProgressCard(progress: UnitProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fraction = if (progress.targetMinutesPerWeek == 0) 0f
    else (progress.minutesThisWeek.toFloat() / progress.targetMinutesPerWeek).coerceIn(0f, 1f)
    val hoursText = "${formatMinutes(progress.minutesThisWeek)} of ${formatMinutes(progress.targetMinutesPerWeek)} this week"
    val assessmentsText = "${progress.submittedCount}/${progress.assessmentCount} assessments submitted"
    // A clickable Card already merges its children into one TalkBack item; a nested merge here
    // would split the card into an unlabelled button plus a separate text block.
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnitColorDot(progress.colorIndex)
                Spacer(Modifier.width(8.dp))
                Text(progress.code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(progress.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                color = unitColor(progress.colorIndex)
            )
            Text(hoursText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(assessmentsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
