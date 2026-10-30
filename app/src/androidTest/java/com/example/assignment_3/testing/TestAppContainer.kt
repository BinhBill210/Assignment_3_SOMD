package com.example.assignment_3.testing

import android.content.Context
import com.example.assignment_3.data.local.StudyHiveDatabase
import com.example.assignment_3.data.repository.AssessmentRepository
import com.example.assignment_3.data.repository.RoomAssessmentRepository
import com.example.assignment_3.data.repository.RoomStudySessionRepository
import com.example.assignment_3.data.repository.RoomStudyUnitRepository
import com.example.assignment_3.data.repository.StudySessionRepository
import com.example.assignment_3.data.repository.StudyUnitRepository
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.notifications.NotificationHelper
import com.example.assignment_3.notifications.ReminderScheduler
import java.time.Clock

/** Records scheduler calls so tests can assert on reminder side effects without WorkManager. */
class RecordingReminderScheduler : ReminderScheduler {
    data class Scheduled(val assessmentId: Long, val dueAtMillis: Long, val delayMillis: Long)

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(assessmentId: Long, dueAtMillis: Long, delayMillis: Long) {
        scheduled += Scheduled(assessmentId, dueAtMillis, delayMillis)
    }

    override fun cancel(assessmentId: Long) {
        cancelled += assessmentId
    }
}

/** Real Room repositories over an in-memory database: what the app runs, minus disk and WorkManager. */
class TestAppContainer(
    context: Context,
    override val clock: Clock = Clock.systemDefaultZone()
) : AppContainer {
    val database: StudyHiveDatabase = StudyHiveDatabase.inMemory(context)
    val scheduler = RecordingReminderScheduler()
    override val notificationHelper = NotificationHelper(context.applicationContext)
    override val studyUnitRepository: StudyUnitRepository =
        RoomStudyUnitRepository(database.studyUnitDao(), database.assessmentDao(), scheduler)
    override val assessmentRepository: AssessmentRepository =
        RoomAssessmentRepository(database.assessmentDao(), scheduler, clock)
    override val studySessionRepository: StudySessionRepository =
        RoomStudySessionRepository(database.studySessionDao())
}
