package com.example.assignment_3.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.domain.ReminderPolicy

/**
 * Runs when a reminder is due. It re-reads the assessment rather than trusting the values captured
 * at scheduling time, because the user may have edited, submitted or deleted it since.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val assessmentId = inputData.getLong(KEY_ASSESSMENT_ID, -1L)
        val scheduledDueAt = inputData.getLong(KEY_DUE_AT, -1L)
        if (assessmentId < 0) return Result.failure()

        val container = (applicationContext as StudyHiveApplication).container
        val current = container.assessmentRepository.getAssessment(assessmentId)
            ?: return Result.success()
        if (!ReminderPolicy.shouldNotify(
                status = current.status,
                currentDueAtMillis = current.dueAt,
                currentOffsetMinutes = current.reminderOffsetMinutes,
                scheduledDueAtMillis = scheduledDueAt
            )
        ) {
            return Result.success()
        }
        val unit = container.studyUnitRepository.getUnit(current.unitId)
        container.notificationHelper.showDeadlineReminder(current, unit?.code.orEmpty())
        return Result.success()
    }

    companion object {
        const val KEY_ASSESSMENT_ID = "assessment_id"
        const val KEY_DUE_AT = "due_at"
        const val TAG = "deadline-reminder"
    }
}
