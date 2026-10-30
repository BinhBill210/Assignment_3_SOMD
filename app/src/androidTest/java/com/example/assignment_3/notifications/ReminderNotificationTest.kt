package com.example.assignment_3.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.testing.TestAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration of the reminder pipeline: WorkManagerReminderScheduler enqueues unique delayed work,
 * the test driver fast-forwards the delay, ReminderWorker re-reads Room and posts the notification.
 */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as StudyHiveApplication
    private lateinit var originalContainer: AppContainer
    private lateinit var container: TestAppContainer
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerReminderScheduler
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        originalContainer = app.container
        container = TestAppContainer(context)
        app.container = container
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerReminderScheduler(workManager)
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        container.database.close()
        app.container = originalContainer
    }

    private fun insertAssessment(status: AssessmentStatus = AssessmentStatus.NOT_STARTED): Assessment = runBlocking {
        val unitId = container.database.studyUnitDao()
            .insert(StudyUnit(code = "COS30017", name = "Mobile", colorIndex = 0, targetMinutesPerWeek = 600, createdAt = 0))
        val a = Assessment(
            unitId = unitId, title = "Assignment 3", type = AssessmentType.ASSIGNMENT, weighting = 40,
            dueAt = System.currentTimeMillis() + 86_400_000, status = status, reminderOffsetMinutes = 60,
            notes = "", createdAt = 0, updatedAt = 0
        )
        a.copy(id = container.database.assessmentDao().insert(a))
    }

    private fun workFor(id: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.uniqueName(id)).get()

    private fun awaitFinished(id: Long): WorkInfo {
        repeat(50) {
            val info = workFor(id).single()
            if (info.state.isFinished) return info
            Thread.sleep(100)
        }
        error("work for $id did not finish")
    }

    @Test
    fun rescheduling_replacesInsteadOfDuplicating() {
        scheduler.schedule(7, dueAtMillis = 1, delayMillis = 3_600_000)
        scheduler.schedule(7, dueAtMillis = 2, delayMillis = 7_200_000)

        val active = workFor(7).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, active.size)
    }

    @Test
    fun cancel_cancelsTheUniqueWork() {
        scheduler.schedule(8, dueAtMillis = 1, delayMillis = 3_600_000)
        scheduler.cancel(8)
        assertTrue(workFor(8).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun dueReminder_postsNotificationForPendingAssessment() {
        val a = insertAssessment()
        scheduler.schedule(a.id, a.dueAt, delayMillis = 3_600_000)
        val workId = workFor(a.id).single().id

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(workId)

        assertEquals(WorkInfo.State.SUCCEEDED, awaitFinished(a.id).state)
        val posted = notificationManager.activeNotifications.single { it.id == a.id.toInt() }
        assertEquals(NotificationHelper.CHANNEL_DEADLINES, posted.notification.channelId)
        assertEquals(
            "COS30017 · Assignment 3",
            posted.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
        )
    }

    @Test
    fun reminderForSubmittedAssessment_isSuppressed() {
        val a = insertAssessment(status = AssessmentStatus.SUBMITTED)
        scheduler.schedule(a.id, a.dueAt, delayMillis = 3_600_000)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(workFor(a.id).single().id)

        assertEquals(WorkInfo.State.SUCCEEDED, awaitFinished(a.id).state)
        assertTrue(notificationManager.activeNotifications.none { it.id == a.id.toInt() })
    }

    @Test
    fun reminderWhoseDeadlineMoved_isSuppressed() {
        val a = insertAssessment()
        scheduler.schedule(a.id, a.dueAt - 1, delayMillis = 3_600_000)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(workFor(a.id).single().id)

        awaitFinished(a.id)
        assertTrue(notificationManager.activeNotifications.none { it.id == a.id.toInt() })
    }
}
