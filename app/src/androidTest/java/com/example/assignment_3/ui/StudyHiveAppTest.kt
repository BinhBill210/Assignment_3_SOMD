package com.example.assignment_3.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.testing.TestAppContainer
import com.example.assignment_3.ui.theme.StudyHiveTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end through the real NavHost, ViewModels, repositories and Room (in memory), driving the
 * UI the way a user would. Only WorkManager is replaced, by a recording scheduler.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StudyHiveAppTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<StudyHiveApplication>()
    private lateinit var originalContainer: AppContainer
    private lateinit var container: TestAppContainer
    private val day = 24 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        originalContainer = app.container
        container = TestAppContainer(app)
        app.container = container
    }

    @After
    fun tearDown() {
        container.database.close()
        app.container = originalContainer
    }

    private fun launch() = compose.setContent { StudyHiveTheme { StudyHiveApp() } }

    // Room queries run on its own executor, which Compose's idling sync doesn't track, so wait explicitly.
    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 5_000)

    private fun waitForDescription(description: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(description), timeoutMillis = 5_000)

    private fun seedUnitWithAssessment(status: AssessmentStatus = AssessmentStatus.NOT_STARTED): Pair<Long, Long> = runBlocking {
        val unitId = container.database.studyUnitDao()
            .insert(StudyUnit(code = "COS30017", name = "Mobile Development", colorIndex = 5, targetMinutesPerWeek = 600, createdAt = 0))
        val assessmentId = container.database.assessmentDao().insert(
            Assessment(
                unitId = unitId, title = "Assignment 3", type = AssessmentType.ASSIGNMENT, weighting = 40,
                dueAt = System.currentTimeMillis() + 2 * day, status = status, reminderOffsetMinutes = 1440,
                notes = "Room + WorkManager", createdAt = 0, updatedAt = 0
            )
        )
        unitId to assessmentId
    }

    @Test
    fun createUnit_fromEmptyDashboard_appearsOnDashboard() {
        launch()
        waitForText("Add your first unit")
        compose.onNodeWithText("Add your first unit").performClick()

        compose.onNodeWithTag("save_unit").performClick()
        compose.onNodeWithText("Unit code is required").assertExists()

        compose.onNodeWithTag("Unit code").performTextInput("cos30017")
        compose.onNodeWithTag("Unit name").performTextInput("Mobile Development")
        compose.onNodeWithTag("Weekly study target (hours)").performTextInput("10")
        compose.onNodeWithTag("save_unit").performClick()

        waitForText("Unit added")
        waitForText("COS30017")
        compose.onNodeWithText("0 min of 10 h this week").assertExists()
        val saved = runBlocking { container.database.studyUnitDao().observeAll().first().single() }
        assertEquals("COS30017", saved.code)
        assertEquals(600, saved.targetMinutesPerWeek)
    }

    @Test
    fun duplicateUnitCode_isReportedOnTheField() {
        seedUnitWithAssessment()
        launch()
        compose.onNodeWithText("Units").performClick()
        waitForDescription("Add unit")
        compose.onNodeWithContentDescription("Add unit").performClick()
        compose.onNodeWithTag("Unit code").performTextInput("COS30017")
        compose.onNodeWithTag("Unit name").performTextInput("Duplicate")
        compose.onNodeWithTag("Weekly study target (hours)").performTextInput("8")
        compose.onNodeWithTag("save_unit").performClick()

        waitForText("You already have a unit with code COS30017")
        assertEquals(1, runBlocking { container.database.studyUnitDao().observeAll().first().size })
    }

    @Test
    fun unitDetail_addAssessment_opensFormWithValidation() {
        seedUnitWithAssessment()
        launch()
        compose.onNodeWithText("Units").performClick()
        waitForText("Mobile Development")
        compose.onNodeWithText("Mobile Development").performClick()
        waitForText("40% of 100% weighting allocated")

        compose.onNodeWithContentDescription("Add assessment").performClick()
        waitForText("New assessment · COS30017")
        compose.onNodeWithText("40% already allocated to other assessments in this unit").assertExists()

        compose.onNodeWithTag("save_assessment").performClick()
        waitForText("Title is required")
        compose.onNodeWithText("Choose a due date").assertExists()
    }

    @Test
    fun assessmentDetail_statusSessionAndUndo() {
        val (_, assessmentId) = seedUnitWithAssessment()
        launch()
        waitForText("Assignment 3")
        compose.onNodeWithText("Assignment 3").performClick()
        waitForText("Reminder: 1 day before")

        // Mark submitted: Room updates and the repository cancels the reminder.
        compose.onNode(hasText("Submitted") and hasClickAction()).performClick()
        waitForText("Submitted - no reminder needed")
        assertEquals(AssessmentStatus.SUBMITTED, runBlocking { container.database.assessmentDao().getById(assessmentId)!!.status })
        assertTrue(assessmentId in container.scheduler.cancelled)

        // Log a session through the bottom sheet.
        compose.onNodeWithText("Log session").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("save_session"), timeoutMillis = 5_000)
        compose.onNode(hasSetTextAction() and hasTestTag("Duration (minutes)")).performTextInput("45")
        compose.onNodeWithTag("save_session").performClick()
        waitForText("Total 45 min")

        // Delete it, then undo from the snackbar.
        compose.onNodeWithTag("assessment_detail_list").performScrollToNode(hasText("45 min"))
        compose.onNodeWithContentDescription("Delete session").performClick()
        waitForText("Total 0 min")
        waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()
        waitForText("Total 45 min")
    }

    @Test
    fun deleteAssessment_confirmsThenReturnsAndCancelsReminder() {
        val (_, assessmentId) = seedUnitWithAssessment()
        launch()
        waitForText("Assignment 3")
        compose.onNodeWithText("Assignment 3").performClick()
        waitForText("Reminder: 1 day before")

        compose.onNodeWithContentDescription("Delete assessment").performClick()
        waitForText("Delete \"Assignment 3\"?")
        compose.onNodeWithText("Delete").performClick()

        waitForText("Assessment deleted")
        waitForText("Nothing due in the next 7 days. Nice work.")
        assertEquals(null, runBlocking { container.database.assessmentDao().getById(assessmentId) })
        assertTrue(assessmentId in container.scheduler.cancelled)
    }

    @Test
    fun deleteUnit_cascadesAndReturnsToEmptyDashboard() {
        seedUnitWithAssessment()
        launch()
        waitForText("Mobile Development")
        compose.onNodeWithText("Mobile Development").performClick()
        waitForText("40% of 100% weighting allocated")
        compose.onNodeWithContentDescription("Delete unit").performClick()
        waitForText("This also deletes 1 assessment(s) and 0 study session(s)", substring = true)
        compose.onNodeWithText("Delete").performClick()

        waitForText("COS30017 deleted")
        waitForText("Welcome to StudyHive")
        assertTrue(runBlocking { container.database.assessmentDao().observeOutstanding().first().isEmpty() })
    }
}
