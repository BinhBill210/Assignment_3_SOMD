package com.example.assignment_3.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe destinations. Arguments are constructor properties, so a missing or wrongly typed id
 * is a compile error rather than a runtime crash (A2 used string routes without arguments).
 * An id of [NEW] means "create" for the shared create/edit form routes.
 */
const val NEW = 0L

@Serializable
data object DashboardRoute

@Serializable
data object UnitListRoute

@Serializable
data class UnitDetailRoute(val unitId: Long)

@Serializable
data class UnitFormRoute(val unitId: Long = NEW)

@Serializable
data class AssessmentDetailRoute(val assessmentId: Long)

@Serializable
data class AssessmentFormRoute(val unitId: Long, val assessmentId: Long = NEW)

@Serializable
data class FocusTimerRoute(val assessmentId: Long)
