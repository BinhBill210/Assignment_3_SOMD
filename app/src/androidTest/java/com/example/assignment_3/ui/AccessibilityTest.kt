package com.example.assignment_3.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.AssessmentType
import com.example.assignment_3.domain.SessionSource
import com.example.assignment_3.testing.TestAppContainer
import com.example.assignment_3.ui.theme.StudyHiveTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks every main screen (light and dark theme, and 200% font size) and checks the semantics tree
 * the way TalkBack sees it: every clickable element needs a spoken label and a touch target of at
 * least 48dp. Text contrast is checked separately in the JVM `ThemeContrastTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = compose.activity.application as StudyHiveApplication
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

    private fun seed() = runBlocking {
        val unitId = container.database.studyUnitDao()
            .insert(StudyUnit(code = "COS30017", name = "Mobile Development", colorIndex = 5, targetMinutesPerWeek = 600, createdAt = 0))
        val assessmentId = container.database.assessmentDao().insert(
            Assessment(
                unitId = unitId, title = "Assignment 3", type = AssessmentType.ASSIGNMENT, weighting = 40,
                dueAt = System.currentTimeMillis() + 2 * day, status = AssessmentStatus.IN_PROGRESS,
                reminderOffsetMinutes = 1440, notes = "Room + WorkManager", createdAt = 0, updatedAt = 0
            )
        )
        container.database.studySessionDao().insert(
            StudySession(
                assessmentId = assessmentId, startedAt = System.currentTimeMillis() - 3_600_000,
                durationMinutes = 45, note = "Wrote DAO tests", source = SessionSource.TIMER
            )
        )
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 5_000)

    private fun checkScreen(screen: String) {
        compose.waitForIdle()
        val minTouchPx = with(compose.density) { 48.dp.toPx() } - 1f
        val problems = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
            val config = node.config
            val label = listOfNotNull(
                config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
                config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
                config.getOrNull(SemanticsProperties.EditableText)?.text
            ).joinToString().trim()
            // touchBoundsInRoot includes the area Material adds around small components to reach 48dp.
            val touch = node.touchBoundsInRoot
            when {
                label.isEmpty() -> "unlabelled clickable at ${node.boundsInRoot}"
                touch.width < minTouchPx || touch.height < minTouchPx ->
                    "'$label' touch target ${touch.width.toInt()}x${touch.height.toInt()}px < 48dp"
                else -> null
            }
        }
        assertTrue("$screen: $problems", problems.isEmpty())
    }

    private fun walkAllScreens(darkTheme: Boolean) {
        seed()
        compose.setContent { StudyHiveTheme(darkTheme = darkTheme) { StudyHiveApp() } }

        waitFor(hasText("Assignment 3"))
        checkScreen("Dashboard")

        compose.onNodeWithText("Assignment 3").performClick()
        waitFor(hasText("Log session"))
        checkScreen("Assessment detail (top)")
        compose.onNodeWithTag("assessment_detail_list").performScrollToNode(hasText("Wrote DAO tests", substring = true))
        checkScreen("Assessment detail (sessions)")

        compose.onNodeWithText("Log session").performClick()
        waitFor(hasTestTag("save_session"))
        checkScreen("Session sheet")
        Espresso.pressBack() // the sheet is its own window, so dismiss it like a user would
        compose.waitUntilDoesNotExist(hasTestTag("save_session"), timeoutMillis = 5_000)

        compose.onNodeWithText("Start focus").performClick()
        waitFor(hasText("Start when you're ready"))
        checkScreen("Focus timer")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Log session"))

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Units"))
        compose.onNodeWithText("Units").performClick()
        waitFor(hasText("Mobile Development"))
        checkScreen("Unit list")

        compose.onNodeWithText("Mobile Development").performClick()
        waitFor(hasContentDescription("Add assessment"))
        checkScreen("Unit detail")

        compose.onNodeWithContentDescription("Add assessment").performClick()
        waitFor(hasTestTag("save_assessment"))
        compose.onNodeWithTag("save_assessment").performScrollTo().performClick()
        waitFor(hasText("Title is required"))
        checkScreen("Assessment form with errors")
    }

    @Test
    fun allScreens_passAccessibilityChecks_lightTheme() = walkAllScreens(darkTheme = false)

    @Test
    fun allScreens_passAccessibilityChecks_darkTheme() = walkAllScreens(darkTheme = true)

    @Test
    fun emptyDashboardAndUnitForm_passChecks_atDoubleFontSize() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                StudyHiveTheme { StudyHiveApp() }
            }
        }
        waitFor(hasText("Add your first unit"))
        checkScreen("Empty dashboard at 200% text")

        compose.onNodeWithText("Add your first unit").performScrollTo().performClick()
        waitFor(hasTestTag("save_unit"))
        // The save button must still be reachable when large text pushes it off screen.
        compose.onNodeWithTag("save_unit").performScrollTo().performClick()
        waitFor(hasText("Unit code is required"))
        checkScreen("Unit form at 200% text")
    }
}
