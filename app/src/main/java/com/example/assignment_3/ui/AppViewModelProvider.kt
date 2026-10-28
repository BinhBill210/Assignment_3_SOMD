package com.example.assignment_3.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.assignment_3.StudyHiveApplication
import com.example.assignment_3.di.AppContainer
import com.example.assignment_3.ui.assessment.AssessmentDetailViewModel
import com.example.assignment_3.ui.assessment.AssessmentFormViewModel
import com.example.assignment_3.ui.dashboard.DashboardViewModel
import com.example.assignment_3.ui.focus.FocusTimerViewModel
import com.example.assignment_3.ui.navigation.AssessmentDetailRoute
import com.example.assignment_3.ui.navigation.AssessmentFormRoute
import com.example.assignment_3.ui.navigation.FocusTimerRoute
import com.example.assignment_3.ui.navigation.UnitDetailRoute
import com.example.assignment_3.ui.navigation.UnitFormRoute
import com.example.assignment_3.ui.units.UnitDetailViewModel
import com.example.assignment_3.ui.units.UnitFormViewModel
import com.example.assignment_3.ui.units.UnitListViewModel

/**
 * Builds every ViewModel from the AppContainer. Route arguments are decoded here and passed in as
 * plain constructor values, so the ViewModels themselves stay testable on the JVM.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            DashboardViewModel(container().studyUnitRepository, container().assessmentRepository, container().clock)
        }
        initializer {
            UnitListViewModel(container().studyUnitRepository, container().clock)
        }
        initializer {
            val handle = createSavedStateHandle()
            UnitDetailViewModel(
                unitId = handle.toRoute<UnitDetailRoute>().unitId,
                savedStateHandle = handle,
                unitRepository = container().studyUnitRepository,
                assessmentRepository = container().assessmentRepository,
                clock = container().clock
            )
        }
        initializer {
            val handle = createSavedStateHandle()
            UnitFormViewModel(
                unitId = handle.toRoute<UnitFormRoute>().unitId,
                savedStateHandle = handle,
                unitRepository = container().studyUnitRepository,
                clock = container().clock
            )
        }
        initializer {
            val handle = createSavedStateHandle()
            AssessmentDetailViewModel(
                assessmentId = handle.toRoute<AssessmentDetailRoute>().assessmentId,
                assessmentRepository = container().assessmentRepository,
                sessionRepository = container().studySessionRepository,
                clock = container().clock
            )
        }
        initializer {
            val handle = createSavedStateHandle()
            val route = handle.toRoute<AssessmentFormRoute>()
            AssessmentFormViewModel(
                unitId = route.unitId,
                assessmentId = route.assessmentId,
                savedStateHandle = handle,
                assessmentRepository = container().assessmentRepository,
                unitRepository = container().studyUnitRepository,
                clock = container().clock
            )
        }
        initializer {
            val handle = createSavedStateHandle()
            FocusTimerViewModel(
                assessmentId = handle.toRoute<FocusTimerRoute>().assessmentId,
                savedStateHandle = handle,
                assessmentRepository = container().assessmentRepository,
                sessionRepository = container().studySessionRepository,
                clock = container().clock
            )
        }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as StudyHiveApplication).container
