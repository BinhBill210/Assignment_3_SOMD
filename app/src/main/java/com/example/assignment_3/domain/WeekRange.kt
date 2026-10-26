package com.example.assignment_3.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A Monday-to-Monday window, half open: [startMillis, endMillis). */
data class WeekRange(val startMillis: Long, val endMillis: Long) {
    operator fun contains(millis: Long): Boolean = millis in startMillis until endMillis

    companion object {
        fun containing(millis: Long, zone: ZoneId): WeekRange {
            val monday = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val start = monday.atStartOfDay(zone)
            return WeekRange(start.toInstant().toEpochMilli(), start.plusWeeks(1).toInstant().toEpochMilli())
        }
    }
}
