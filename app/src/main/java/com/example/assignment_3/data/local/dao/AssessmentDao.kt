package com.example.assignment_3.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.domain.AssessmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {

    @Query(
        """
        SELECT a.*, u.code AS unitCode, u.name AS unitName, u.colorIndex AS unitColorIndex
        FROM assessments a JOIN units u ON a.unitId = u.id
        WHERE a.status != 'SUBMITTED'
        ORDER BY a.dueAt
        """
    )
    fun observeOutstanding(): Flow<List<AssessmentWithUnit>>

    @Query(
        """
        SELECT a.*, u.code AS unitCode, u.name AS unitName, u.colorIndex AS unitColorIndex
        FROM assessments a JOIN units u ON a.unitId = u.id
        WHERE a.id = :id
        """
    )
    fun observeWithUnit(id: Long): Flow<AssessmentWithUnit?>

    @Query("SELECT * FROM assessments WHERE unitId = :unitId ORDER BY dueAt")
    fun observeForUnit(unitId: Long): Flow<List<Assessment>>

    @Query("SELECT * FROM assessments WHERE id = :id")
    suspend fun getById(id: Long): Assessment?

    @Query("SELECT id FROM assessments WHERE unitId = :unitId")
    suspend fun getIdsForUnit(unitId: Long): List<Long>

    @Query("SELECT COALESCE(SUM(weighting), 0) FROM assessments WHERE unitId = :unitId AND id != :excludingId")
    suspend fun weightingTotal(unitId: Long, excludingId: Long): Int

    @Insert
    suspend fun insert(assessment: Assessment): Long

    @Update
    suspend fun update(assessment: Assessment): Int

    @Query("UPDATE assessments SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: AssessmentStatus, updatedAt: Long): Int

    @Delete
    suspend fun delete(assessment: Assessment): Int
}
