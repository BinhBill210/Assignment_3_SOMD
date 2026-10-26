package com.example.assignment_3.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.domain.AssessmentStatus

/** One row of the dashboard / unit list progress display. */
data class UnitProgress(
    val unitId: Long,
    val code: String,
    val name: String,
    val colorIndex: Int,
    val targetMinutesPerWeek: Int,
    val assessmentCount: Int,
    val submittedCount: Int,
    val minutesThisWeek: Int
)

data class AssessmentWithUnit(
    @Embedded val assessment: Assessment,
    val unitCode: String,
    val unitName: String,
    val unitColorIndex: Int
)

/** What a unit delete will remove, shown in the confirmation dialog. */
data class CascadeCounts(val assessments: Int, val sessions: Int)

data class AssessmentWithSessions(
    @Embedded val assessment: Assessment,
    @Relation(parentColumn = "id", entityColumn = "assessmentId")
    val sessions: List<StudySession>
)

/**
 * Strategy B of the dashboard investigation (docs/INVESTIGATION.md): the whole object graph is
 * loaded and [toUnitProgress] aggregates it in Kotlin.
 */
data class UnitWithAssessments(
    @Embedded val unit: StudyUnit,
    @Relation(entity = Assessment::class, parentColumn = "id", entityColumn = "unitId")
    val assessments: List<AssessmentWithSessions>
)

fun List<UnitWithAssessments>.toUnitProgress(weekStart: Long, weekEnd: Long): List<UnitProgress> =
    map { item ->
        UnitProgress(
            unitId = item.unit.id,
            code = item.unit.code,
            name = item.unit.name,
            colorIndex = item.unit.colorIndex,
            targetMinutesPerWeek = item.unit.targetMinutesPerWeek,
            assessmentCount = item.assessments.size,
            submittedCount = item.assessments.count { it.assessment.status == AssessmentStatus.SUBMITTED },
            minutesThisWeek = item.assessments.sumOf { a ->
                a.sessions.filter { it.startedAt in weekStart until weekEnd }.sumOf { it.durationMinutes }
            }
        )
    }.sortedBy { it.code }
