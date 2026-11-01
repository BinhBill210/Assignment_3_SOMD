package com.example.assignment_3.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.domain.UnitErrors
import com.example.assignment_3.ui.dashboard.DashboardContent
import com.example.assignment_3.ui.dashboard.DashboardUiState
import com.example.assignment_3.ui.theme.StudyHiveTheme
import com.example.assignment_3.ui.units.AssessmentSort
import com.example.assignment_3.ui.units.StatusFilter
import com.example.assignment_3.ui.units.UnitDetailContent
import com.example.assignment_3.ui.units.UnitDetailUiState
import com.example.assignment_3.ui.units.UnitDraft
import com.example.assignment_3.ui.units.UnitFormContent
import com.example.assignment_3.ui.units.UnitFormUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless screen content rendered with hand-built state: fast, deterministic UI checks. */
@RunWith(AndroidJUnit4::class)
class ScreenContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dashboard_loadingAndErrorStates() {
        var state: DashboardUiState by mutableStateOf(DashboardUiState.Loading)
        var retries = 0
        compose.setContent { StudyHiveTheme { DashboardContent(state, {}, {}, {}, onRetry = { retries++ }) } }
        compose.onNodeWithContentDescription("Loading").assertIsDisplayed()

        state = DashboardUiState.Error("Database unavailable")
        compose.onNodeWithText("Database unavailable").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun dashboard_emptyState_offersAddUnit() {
        var addClicks = 0
        compose.setContent { StudyHiveTheme { DashboardContent(DashboardUiState.NoUnits, {}, {}, { addClicks++ }, {}) } }
        compose.onNodeWithText("Welcome to StudyHive").assertIsDisplayed()
        compose.onNodeWithText("Add your first unit").performClick()
        assertEquals(1, addClicks)
    }

    @Test
    fun unitForm_showsFieldErrors_asTextAndAccessibilitySemantics() {
        val state = UnitFormUiState(
            isNew = true,
            draft = UnitDraft(code = "x"),
            errors = UnitErrors(code = "Use a code like COS30017 (3-4 letters, 4-5 digits)", name = "Unit name is required")
        )
        compose.setContent { StudyHiveTheme { UnitFormContent(state, {}, {}, {}, {}, {}, {}) } }

        compose.onNodeWithText("Unit name is required").assertIsDisplayed()
        compose.onNodeWithTag("Unit name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Unit name is required"))
    }

    @Test
    fun unitForm_savingDisablesSaveButton() {
        compose.setContent {
            StudyHiveTheme { UnitFormContent(UnitFormUiState(isNew = true, isSaving = true), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("save_unit").assertIsNotEnabled()
    }

    @Test
    fun unitForm_dirtyClose_asksBeforeDiscarding() {
        var closed = false
        compose.setContent {
            StudyHiveTheme {
                UnitFormContent(UnitFormUiState(isNew = true, isDirty = true), onClose = { closed = true }, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertTrue("must not close before confirming", !closed)

        compose.onNodeWithText("Discard").performClick()
        assertTrue(closed)
    }

    @Test
    fun unitForm_typingForwardsToCallbacks() {
        val typed = mutableListOf<String>()
        compose.setContent {
            StudyHiveTheme { UnitFormContent(UnitFormUiState(isNew = true), {}, onCodeChange = { typed += it }, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("Unit code").performTextInput("COS30017")
        assertEquals("COS30017", typed.last())
    }

    @Test
    fun unitDetail_deleteDialog_statesCascadeConsequences() {
        var confirmed = false
        val state = UnitDetailUiState.Content(
            unit = StudyUnit(1, "COS30017", "Mobile", 0, 600, 0),
            assessments = emptyList(),
            totalAssessments = 0,
            allocatedWeighting = 0,
            filter = StatusFilter.ALL,
            sort = AssessmentSort.DUE_DATE,
            nowMillis = 0,
            pendingDelete = CascadeCounts(assessments = 4, sessions = 12)
        )
        compose.setContent {
            StudyHiveTheme { UnitDetailContent(state, {}, {}, {}, {}, {}, {}, {}, onConfirmDelete = { confirmed = true }, {}) }
        }
        compose.onNodeWithText("Delete COS30017?").assertIsDisplayed()
        compose.onNodeWithText("4 assessments and 12 study sessions", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun unitDetail_emptyListMessage_dependsOnFilter() {
        val base = UnitDetailUiState.Content(
            unit = StudyUnit(1, "COS30017", "Mobile", 0, 600, 0), assessments = emptyList(), totalAssessments = 2,
            allocatedWeighting = 60, filter = StatusFilter.SUBMITTED, sort = AssessmentSort.DUE_DATE, nowMillis = 0, pendingDelete = null
        )
        compose.setContent { StudyHiveTheme { UnitDetailContent(base, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("No assessments match \"Submitted\".").assertIsDisplayed()
    }
}
