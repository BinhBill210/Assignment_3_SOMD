package com.example.assignment_3.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dueFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

fun formatDueDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(dueFormatter)

fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(dateFormatter)

fun formatDate(date: LocalDate): String = date.format(dateFormatter)

/** Human countdown used on cards, e.g. "Due in 3 days", "Due today", "Overdue by 2 days". */
fun formatRelativeDue(dueAtMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (dueAtMillis <= nowMillis) {
        val hours = ChronoUnit.HOURS.between(Instant.ofEpochMilli(dueAtMillis), Instant.ofEpochMilli(nowMillis))
        return when {
            hours < 1 -> "Overdue"
            hours < 24 -> "Overdue by $hours h"
            else -> "Overdue by ${hours / 24} day${if (hours / 24 == 1L) "" else "s"}"
        }
    }
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val dueDay = Instant.ofEpochMilli(dueAtMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, dueDay)
    return when (days) {
        0L -> {
            val hours = ChronoUnit.HOURS.between(Instant.ofEpochMilli(nowMillis), Instant.ofEpochMilli(dueAtMillis))
            if (hours < 1) "Due within the hour" else "Due today · in $hours h"
        }
        1L -> "Due tomorrow"
        else -> "Due in $days days"
    }
}

fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(
        Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60
    )
}
