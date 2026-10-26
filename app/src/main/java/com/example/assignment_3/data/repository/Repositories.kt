package com.example.assignment_3.data.repository

import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.domain.AssessmentStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts the ViewModels depend on. Interfaces let ViewModel tests run on the JVM
 * against in-memory fakes instead of a device database.
 */
interface StudyUnitRepository {
    fun observeUnits(): Flow<List<StudyUnit>>
    fun observeUnit(id: Long): Flow<StudyUnit?>
    fun observeProgress(weekStart: Long, weekEnd: Long): Flow<List<UnitProgress>>
    suspend fun getUnit(id: Long): StudyUnit?

    /** Inserts when `id == 0`, otherwise updates. @throws DuplicateUnitCodeException */
    suspend fun saveUnit(unit: StudyUnit): Long
    suspend fun cascadeCounts(unitId: Long): CascadeCounts

    /** Deletes the unit (Room cascades children) and cancels every child reminder. */
    suspend fun deleteUnit(unit: StudyUnit)
}

interface AssessmentRepository {
    fun observeOutstanding(): Flow<List<AssessmentWithUnit>>
    fun observeForUnit(unitId: Long): Flow<List<Assessment>>
    fun observeAssessment(id: Long): Flow<AssessmentWithUnit?>
    suspend fun getAssessment(id: Long): Assessment?
    suspend fun weightingTotal(unitId: Long, excludingId: Long): Int

    /** Inserts or updates, then schedules, replaces or cancels the reminder to match. */
    suspend fun saveAssessment(assessment: Assessment): Long
    suspend fun updateStatus(id: Long, status: AssessmentStatus)
    suspend fun deleteAssessment(assessment: Assessment)
}

interface StudySessionRepository {
    fun observeSessions(assessmentId: Long): Flow<List<StudySession>>

    /** Inserts when `id == 0`, otherwise updates. Re-inserting a deleted session restores it. */
    suspend fun saveSession(session: StudySession): Long
    suspend fun deleteSession(session: StudySession)
}
