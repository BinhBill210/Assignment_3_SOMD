package com.example.assignment_3.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.SessionSource

/** Named StudyUnit because `Unit` is Kotlin's void type. */
@Entity(tableName = "units", indices = [Index(value = ["code"], unique = true)])
data class StudyUnit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val colorIndex: Int,
    val targetMinutesPerWeek: Int,
    val createdAt: Long
)

@Entity(
    tableName = "assessments",
    foreignKeys = [
        ForeignKey(
            entity = StudyUnit::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("dueAt")]
)
data class Assessment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val title: String,
    val type: AssessmentType,
    val weighting: Int,
    val dueAt: Long,
    val status: AssessmentStatus,
    val reminderOffsetMinutes: Int?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Assessment::class,
            parentColumns = ["id"],
            childColumns = ["assessmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("assessmentId")]
)
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assessmentId: Long,
    val startedAt: Long,
    val durationMinutes: Int,
    val note: String,
    val source: SessionSource
)
