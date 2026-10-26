package com.example.assignment_3.domain

enum class AssessmentType(val label: String) {
    ASSIGNMENT("Assignment"),
    EXAM("Exam"),
    QUIZ("Quiz"),
    PRESENTATION("Presentation"),
    LAB("Lab"),
    OTHER("Other")
}

enum class AssessmentStatus(val label: String) {
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    SUBMITTED("Submitted")
}

enum class SessionSource { MANUAL, TIMER }

/** The reminder lead times offered in the assessment form. [minutes] is what Room stores. */
enum class ReminderOption(val minutes: Int?, val label: String) {
    OFF(null, "No reminder"),
    THREE_HOURS(3 * 60, "3 hours before"),
    ONE_DAY(24 * 60, "1 day before"),
    THREE_DAYS(3 * 24 * 60, "3 days before");

    companion object {
        fun fromMinutes(minutes: Int?): ReminderOption =
            entries.firstOrNull { it.minutes == minutes } ?: OFF
    }
}

/** Thrown by the repository when the unique index on `units.code` rejects a write. */
class DuplicateUnitCodeException(val code: String) : Exception("Unit code $code already exists")
