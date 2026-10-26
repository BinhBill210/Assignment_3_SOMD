package com.example.assignment_3.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.data.local.model.UnitWithAssessments
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyUnitDao {

    @Query("SELECT * FROM units ORDER BY code")
    fun observeAll(): Flow<List<StudyUnit>>

    @Query("SELECT * FROM units WHERE id = :id")
    fun observeById(id: Long): Flow<StudyUnit?>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: Long): StudyUnit?

    /** ABORT so the unique index on `code` surfaces as SQLiteConstraintException. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(unit: StudyUnit): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(unit: StudyUnit): Int

    @Delete
    suspend fun delete(unit: StudyUnit): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM assessments WHERE unitId = :unitId) AS assessments,
            (SELECT COUNT(*) FROM study_sessions s
                JOIN assessments a ON s.assessmentId = a.id
                WHERE a.unitId = :unitId) AS sessions
        """
    )
    suspend fun cascadeCounts(unitId: Long): CascadeCounts

    // ------------------------------------------------------------ dashboard progress

    /** Strategy A: SQLite aggregates, one row per unit leaves the database. */
    @Query(PROGRESS_SQL)
    fun observeProgress(weekStart: Long, weekEnd: Long): Flow<List<UnitProgress>>

    @Query(PROGRESS_SQL)
    suspend fun getProgress(weekStart: Long, weekEnd: Long): List<UnitProgress>

    /** Strategy B: the full object graph, aggregated in Kotlin. Kept for the investigation. */
    @Transaction
    @Query("SELECT * FROM units ORDER BY code")
    fun observeUnitsWithAssessments(): Flow<List<UnitWithAssessments>>

    @Transaction
    @Query("SELECT * FROM units ORDER BY code")
    suspend fun getUnitsWithAssessments(): List<UnitWithAssessments>
}

private const val PROGRESS_SQL = """
    SELECT
        u.id AS unitId, u.code, u.name, u.colorIndex, u.targetMinutesPerWeek,
        (SELECT COUNT(*) FROM assessments a WHERE a.unitId = u.id) AS assessmentCount,
        (SELECT COUNT(*) FROM assessments a
            WHERE a.unitId = u.id AND a.status = 'SUBMITTED') AS submittedCount,
        (SELECT COALESCE(SUM(s.durationMinutes), 0) FROM study_sessions s
            JOIN assessments a ON s.assessmentId = a.id
            WHERE a.unitId = u.id
              AND s.startedAt >= :weekStart AND s.startedAt < :weekEnd) AS minutesThisWeek
    FROM units u
    ORDER BY u.code
"""
