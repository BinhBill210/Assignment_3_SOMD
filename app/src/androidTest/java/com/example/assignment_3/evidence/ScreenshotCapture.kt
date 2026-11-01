package com.example.assignment_3.evidence

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.notifications.ReminderWorker
import com.example.assignment_3.testing.TestAppContainer
import com.example.assignment_3.ui.StudyHiveApp
import com.example.assignment_3.ui.theme.StudyHiveTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not part of the test suite: captures report screenshots of the real UI over seeded demo data.
 * Full-screen captures via UiAutomation so dialogs, sheets and the notification shade are included.
 *
 *   adb shell am instrument -w -e class com.example.assignment_3.evidence.ScreenshotCapture \
 *     com.example.assignment_3.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/com.example.assignment_3/files/screenshots/. docs/evidence/screenshots/
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotCapture {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = compose.activity.application as StudyHiveApplication
    private lateinit var originalContainer: AppContainer
    private lateinit var container: TestAppContainer
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private var dark by mutableStateOf(false)

    @Before
    fun setUp() {
        originalContainer = app.container
        container = TestAppContainer(app)
        app.container = container
        compose.activity.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @After
    fun tearDown() {
        container.database.close()
        app.container = originalContainer
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700) // let ripples, snackbars and sheet animations settle
        val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 5_000)

    private fun seed(): Long = runBlocking {
        val now = System.currentTimeMillis()
        val units = container.database.studyUnitDao()
        val assessments = container.database.assessmentDao()
        val sessions = container.database.studySessionDao()
        val mobile = units.insert(StudyUnit(code = "COS30017", name = "Software Development for Mobile Devices", colorIndex = 5, targetMinutesPerWeek = 600, createdAt = now))
        val dataVis = units.insert(StudyUnit(code = "COS30045", name = "Data Visualisation", colorIndex = 3, targetMinutesPerWeek = 480, createdAt = now))
        val arch = units.insert(StudyUnit(code = "SWE30003", name = "Software Architectures and Design", colorIndex = 1, targetMinutesPerWeek = 480, createdAt = now))

        fun a(unit: Long, title: String, type: AssessmentType, weight: Int, dueIn: Long, status: AssessmentStatus, reminder: Int?, notes: String = "") =
            Assessment(unitId = unit, title = title, type = type, weighting = weight, dueAt = now + dueIn, status = status,
                reminderOffsetMinutes = reminder, notes = notes, createdAt = now, updatedAt = now)

        val a3 = assessments.insert(a(mobile, "Assignment 3 - StudyHive", AssessmentType.ASSIGNMENT, 40, 2 * day + 5 * hour, AssessmentStatus.IN_PROGRESS, 1440,
            "Room, WorkManager reminders, Compose UI tests and the aggregation investigation."))
        assessments.insert(a(mobile, "Assignment 2 - DeskHive", AssessmentType.ASSIGNMENT, 30, -20 * day, AssessmentStatus.SUBMITTED, null))
        assessments.insert(a(mobile, "Final exam", AssessmentType.EXAM, 30, 30 * day, AssessmentStatus.NOT_STARTED, 4320))
        assessments.insert(a(dataVis, "Project prototype", AssessmentType.PRESENTATION, 35, 5 * day, AssessmentStatus.NOT_STARTED, 1440))
        assessments.insert(a(dataVis, "Lab quiz 6", AssessmentType.QUIZ, 5, -1 * day, AssessmentStatus.NOT_STARTED, 180))
        assessments.insert(a(arch, "Design report", AssessmentType.ASSIGNMENT, 40, 9 * day, AssessmentStatus.IN_PROGRESS, 1440))

        sessions.insertAll(
            listOf(
                StudySession(assessmentId = a3, startedAt = now - 3 * hour, durationMinutes = 50, note = "Instrumented DAO tests", source = SessionSource.TIMER),
                StudySession(assessmentId = a3, startedAt = now - 1 * day, durationMinutes = 95, note = "Benchmark harness", source = SessionSource.MANUAL),
                StudySession(assessmentId = a3, startedAt = now - 2 * day, durationMinutes = 40, note = "", source = SessionSource.TIMER)
            )
        )
        a3
    }

    @Test
    fun captureReportScreenshots() {
        compose.setContent { StudyHiveTheme(darkTheme = dark) { StudyHiveApp() } }
        waitFor(hasText("Add your first unit"))
        shot("01_dashboard_empty")

        // Validation on an empty form, then a filled form.
        compose.onNodeWithText("Add your first unit").performClick()
        waitFor(hasTestTag("save_unit"))
        compose.onNodeWithTag("save_unit").performScrollTo().performClick()
        waitFor(hasText("Unit code is required"))
        shot("02_unit_form_validation")
        compose.onNodeWithContentDescription("Close").performClick()

        val a3 = seed()
        waitFor(hasText("Assignment 3 - StudyHive"))
        shot("03_dashboard")

        compose.onNodeWithText("Units").performClick()
        waitFor(hasText("Data Visualisation"))
        shot("04_unit_list")

        compose.onNodeWithText("Software Development for Mobile Devices").performClick()
        waitFor(hasContentDescription("Add assessment"))
        shot("05_unit_detail")

        compose.onNodeWithContentDescription("Delete unit").performClick()
        waitFor(hasText("Delete", substring = true))
        shot("06_delete_unit_confirmation")
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithContentDescription("Add assessment").performClick()
        waitFor(hasTestTag("save_assessment"))
        compose.onNodeWithTag("save_assessment").performScrollTo().performClick()
        waitFor(hasText("Title is required"))
        shot("07_assessment_form_validation")
        compose.onNodeWithContentDescription("Close").performClick()

        compose.onNodeWithText("Assignment 3 - StudyHive").performClick()
        waitFor(hasText("Log session"))
        shot("08_assessment_detail_reminders_blocked")

        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        // The detail screen re-reads notification permission when it resumes; navigate away and back.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Assignment 3 - StudyHive").performClick()
        waitFor(hasText("Log session"))
        compose.onNodeWithTag("assessment_detail_list").performScrollToNode(hasText("Benchmark harness", substring = true))
        shot("09_assessment_detail_sessions")

        compose.onNodeWithText("Log session").performScrollTo().performClick()
        waitFor(hasTestTag("save_session"))
        compose.onNodeWithTag("Duration (minutes)").performTextInput("0")
        compose.onNodeWithTag("save_session").performClick()
        shot("10_session_sheet_validation")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntilDoesNotExist(hasTestTag("save_session"), timeoutMillis = 5_000)

        compose.onNodeWithTag("assessment_detail_list").performScrollToNode(hasText("Benchmark harness", substring = true))
        compose.onAllNodes(hasContentDescription("Delete session")).onFirst().performClick()
        waitFor(hasText("Undo"))
        shot("11_session_deleted_undo")
        compose.onNodeWithText("Undo").performClick()

        compose.onNodeWithTag("assessment_detail_list").performScrollToNode(hasText("Start focus"))
        compose.onNodeWithText("Start focus").performClick()
        waitFor(hasText("Start"))
        compose.onNodeWithText("Start").performClick()
        Thread.sleep(3_200)
        shot("12_focus_timer_running")
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Discard this session?"))
        shot("13_focus_timer_discard_guard")
        compose.onNodeWithText("Discard").performClick()

        waitFor(hasText("Log session"))
        compose.onNodeWithContentDescription("Back").performClick() // to unit detail
        waitFor(hasContentDescription("Add assessment"))
        compose.onNodeWithContentDescription("Back").performClick() // to unit list
        waitFor(hasText("Dashboard"))
        compose.onNodeWithText("Dashboard").performClick()
        // In the app the theme follows the system setting, and so do the bar icons; match that here.
        dark = true
        compose.activity.runOnUiThread {
            compose.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            )
        }
        waitFor(hasText("Assignment 3 - StudyHive"))
        shot("14_dashboard_dark")
        dark = false
        compose.activity.runOnUiThread { compose.activity.enableEdgeToEdge() }

        // The real worker, run immediately instead of waiting for its delay.
        val due = runBlocking { container.database.assessmentDao().getById(a3)!!.dueAt }
        val worker = TestListenableWorkerBuilder<ReminderWorker>(app)
            .setInputData(workDataOf(ReminderWorker.KEY_ASSESSMENT_ID to a3, ReminderWorker.KEY_DUE_AT to due))
            .build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker.doWork() })
        Thread.sleep(6_000) // let the heads-up banner time out, otherwise the shade omits the notification
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(2_000)
        shot("15_deadline_notification")
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
    }
}
