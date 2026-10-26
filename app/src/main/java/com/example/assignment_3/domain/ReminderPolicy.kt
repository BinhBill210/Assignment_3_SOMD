package com.example.assignment_3.domain

/**
 * Pure rules deciding when a deadline reminder fires and whether a fired reminder is still
 * relevant. Kept free of Android types so every boundary can be covered by JVM tests.
 */
object ReminderPolicy {

    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * Milliseconds from [nowMillis] until the reminder should fire, or null when no reminder
     * should be scheduled (reminder off, already submitted, or the trigger time has passed).
     */
    fun delayUntilReminder(
        nowMillis: Long,
        dueAtMillis: Long,
        offsetMinutes: Int?,
        status: AssessmentStatus
    ): Long? {
        if (offsetMinutes == null || status == AssessmentStatus.SUBMITTED) return null
        val triggerAt = dueAtMillis - offsetMinutes * MILLIS_PER_MINUTE
        val delay = triggerAt - nowMillis
        return if (delay > 0) delay else null
    }

    /**
     * Called by the worker when it wakes up. The assessment may have been deleted, submitted,
     * had its reminder switched off or its deadline moved since the work was enqueued.
     */
    fun shouldNotify(
        status: AssessmentStatus?,
        currentDueAtMillis: Long?,
        currentOffsetMinutes: Int?,
        scheduledDueAtMillis: Long
    ): Boolean =
        status != null &&
            status != AssessmentStatus.SUBMITTED &&
            currentOffsetMinutes != null &&
            currentDueAtMillis == scheduledDueAtMillis
}
