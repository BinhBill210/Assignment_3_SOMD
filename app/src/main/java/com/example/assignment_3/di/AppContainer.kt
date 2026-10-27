package com.example.assignment_3.di

import android.content.Context
import androidx.work.WorkManager
import com.example.assignment_3.data.local.StudyHiveDatabase
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.RoomAssessmentRepository
import com.example.assignment_3.data.repository.RoomStudySessionRepository
import com.example.assignment_3.data.repository.RoomStudyUnitRepository
import com.example.assignment_3.data.repository.StudySessionRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.notifications.NotificationHelper
import com.example.assignment_3.notifications.ReminderScheduler
import com.example.assignment_3.notifications.WorkManagerReminderScheduler
import java.time.Clock

/**
 * Manual dependency injection. With three repositories and one scheduler a hand-written container
 * is clearer than Hilt's code generation; it is created once per process by the Application.
 */
interface AppContainer {
    val clock: Clock
    val studyUnitRepository: StudyUnitRepository
    val assessmentRepository: AssessmentRepository
    val studySessionRepository: StudySessionRepository
    val notificationHelper: NotificationHelper
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val clock: Clock = Clock.systemDefaultZone()

    private val database: StudyHiveDatabase by lazy { StudyHiveDatabase.build(appContext) }

    private val reminderScheduler: ReminderScheduler by lazy {
        WorkManagerReminderScheduler(WorkManager.getInstance(appContext))
    }

    override val notificationHelper: NotificationHelper by lazy { NotificationHelper(appContext) }

    override val studyUnitRepository: StudyUnitRepository by lazy {
        RoomStudyUnitRepository(database.studyUnitDao(), database.assessmentDao(), reminderScheduler)
    }

    override val assessmentRepository: AssessmentRepository by lazy {
        RoomAssessmentRepository(database.assessmentDao(), reminderScheduler, clock)
    }

    override val studySessionRepository: StudySessionRepository by lazy {
        RoomStudySessionRepository(database.studySessionDao())
    }
}
