package com.example.assignment_3.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.assignment_3.data.local.dao.AssessmentDao
import com.example.assignment_3.data.local.dao.StudySessionDao
import com.example.assignment_3.data.local.dao.StudyUnitDao
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit

@Database(
    entities = [StudyUnit::class, Assessment::class, StudySession::class],
    version = 1,
    exportSchema = true
)
abstract class StudyHiveDatabase : RoomDatabase() {
    abstract fun studyUnitDao(): StudyUnitDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun studySessionDao(): StudySessionDao

    companion object {
        const val NAME = "studyhive.db"

        fun build(context: Context): StudyHiveDatabase =
            Room.databaseBuilder(context, StudyHiveDatabase::class.java, NAME).build()

        fun inMemory(context: Context): StudyHiveDatabase =
            Room.inMemoryDatabaseBuilder(context, StudyHiveDatabase::class.java).build()
    }
}
