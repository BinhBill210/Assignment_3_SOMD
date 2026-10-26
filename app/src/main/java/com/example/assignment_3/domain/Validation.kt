package com.example.assignment_3.domain

/**
 * Field-level validation for the three forms. Each validator returns every failing field at once
 * (the A2 basket validator did the same) so the user can fix the form in a single pass.
 */

data class UnitErrors(
    val code: String? = null,
    val name: String? = null,
    val targetHours: String? = null
) {
    val isValid: Boolean get() = code == null && name == null && targetHours == null
}

object UnitValidator {
    const val MAX_NAME_LENGTH = 60
    const val MIN_TARGET_HOURS = 1
    const val MAX_TARGET_HOURS = 40
    private val CODE_PATTERN = Regex("^[A-Z]{3,4}[0-9]{4,5}$")

    /** Codes are stored upper-case and trimmed so "cos30017 " and "COS30017" collide as duplicates. */
    fun normaliseCode(raw: String): String = raw.trim().uppercase()

    fun validate(code: String, name: String, targetHoursText: String): UnitErrors {
        val normalised = normaliseCode(code)
        val hours = targetHoursText.trim().toIntOrNull()
        return UnitErrors(
            code = when {
                normalised.isEmpty() -> "Unit code is required"
                !CODE_PATTERN.matches(normalised) -> "Use a code like COS30017 (3-4 letters, 4-5 digits)"
                else -> null
            },
            name = when {
                name.isBlank() -> "Unit name is required"
                name.trim().length > MAX_NAME_LENGTH -> "Keep the name under $MAX_NAME_LENGTH characters"
                else -> null
            },
            targetHours = when {
                targetHoursText.isBlank() -> "Weekly target is required"
                hours == null -> "Enter a whole number of hours"
                hours !in MIN_TARGET_HOURS..MAX_TARGET_HOURS ->
                    "Target must be between $MIN_TARGET_HOURS and $MAX_TARGET_HOURS hours"
                else -> null
            }
        )
    }
}

data class AssessmentErrors(
    val title: String? = null,
    val weighting: String? = null,
    val dueAt: String? = null,
    val reminder: String? = null
) {
    val isValid: Boolean get() = title == null && weighting == null && dueAt == null && reminder == null
}

object AssessmentValidator {
    const val MAX_TITLE_LENGTH = 80
    const val MAX_TOTAL_WEIGHTING = 100

    /**
     * @param otherWeightingTotal the sum of every *other* assessment's weighting in the same unit,
     *        read from Room, so editing an assessment does not count its old weighting twice.
     * @param isNew new assessments must be due in the future; existing ones may already be overdue.
     */
    fun validate(
        title: String,
        weightingText: String,
        dueAtMillis: Long?,
        reminderMinutes: Int?,
        otherWeightingTotal: Int,
        nowMillis: Long,
        isNew: Boolean
    ): AssessmentErrors {
        val weighting = weightingText.trim().toIntOrNull()
        val remaining = (MAX_TOTAL_WEIGHTING - otherWeightingTotal).coerceAtLeast(0)
        val dueError = when {
            dueAtMillis == null -> "Choose a due date"
            isNew && dueAtMillis <= nowMillis -> "The due date must be in the future"
            else -> null
        }
        return AssessmentErrors(
            title = when {
                title.isBlank() -> "Title is required"
                title.trim().length > MAX_TITLE_LENGTH -> "Keep the title under $MAX_TITLE_LENGTH characters"
                else -> null
            },
            weighting = when {
                weightingText.isBlank() -> "Weighting is required"
                weighting == null -> "Enter a whole number"
                weighting !in 1..MAX_TOTAL_WEIGHTING -> "Weighting must be between 1 and 100%"
                weighting > remaining ->
                    "This unit already has $otherWeightingTotal% allocated - at most $remaining% remains"
                else -> null
            },
            dueAt = dueError,
            reminder = if (
                dueError == null && dueAtMillis != null && reminderMinutes != null &&
                dueAtMillis > nowMillis &&
                ReminderPolicy.delayUntilReminder(
                    nowMillis, dueAtMillis, reminderMinutes, AssessmentStatus.NOT_STARTED
                ) == null
            ) {
                "That reminder time has already passed - choose a shorter lead time"
            } else {
                null
            }
        )
    }
}

data class SessionErrors(val duration: String? = null, val date: String? = null) {
    val isValid: Boolean get() = duration == null && date == null
}

object SessionValidator {
    const val MAX_SESSION_MINUTES = 12 * 60

    fun validate(durationText: String, startedAtMillis: Long, nowMillis: Long): SessionErrors {
        val minutes = durationText.trim().toIntOrNull()
        return SessionErrors(
            duration = when {
                durationText.isBlank() -> "Duration is required"
                minutes == null -> "Enter minutes as a whole number"
                minutes !in 1..MAX_SESSION_MINUTES -> "Duration must be between 1 and $MAX_SESSION_MINUTES minutes"
                else -> null
            },
            date = if (startedAtMillis > nowMillis) "A session can't be in the future" else null
        )
    }
}
