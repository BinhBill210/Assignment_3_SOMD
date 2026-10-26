package com.example.assignment_3.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.assignment_3.data.local.entity.StudySession
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {

    @Query("SELECT * FROM study_sessions WHERE assessmentId = :assessmentId ORDER BY startedAt DESC")
    fun observeForAssessment(assessmentId: Long): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun getById(id: Long): StudySession?

    @Insert
    suspend fun insert(session: StudySession): Long

    @Insert
    suspend fun insertAll(sessions: List<StudySession>)

    @Update
    suspend fun update(session: StudySession): Int

    @Delete
    suspend fun delete(session: StudySession): Int
}
