package com.example.assignment_3.notifications

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

interface ReminderScheduler {
    fun schedule(assessmentId: Long, dueAtMillis: Long, delayMillis: Long)
    fun cancel(assessmentId: Long)
}

/**
 * WorkManager rather than AlarmManager: reminders must survive the app being killed and the device
 * rebooting, and a few minutes of delivery slack is acceptable for a "due tomorrow" reminder.
 * Exact alarms would need SCHEDULE_EXACT_ALARM, which Android reserves for alarm/calendar apps.
 */
class WorkManagerReminderScheduler(private val workManager: WorkManager) : ReminderScheduler {

    override fun schedule(assessmentId: Long, dueAtMillis: Long, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_ASSESSMENT_ID to assessmentId,
                    ReminderWorker.KEY_DUE_AT to dueAtMillis
                )
            )
            .addTag(ReminderWorker.TAG)
            .build()
        // REPLACE: editing a deadline swaps the pending reminder instead of adding a second one.
        workManager.enqueueUniqueWork(uniqueName(assessmentId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(assessmentId: Long) {
        workManager.cancelUniqueWork(uniqueName(assessmentId))
    }

    companion object {
        fun uniqueName(assessmentId: Long) = "reminder-$assessmentId"
    }
}
