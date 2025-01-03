package com.gorai.myedenfocus.presentation.dashboard

import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gorai.myedenfocus.domain.model.Session
import com.gorai.myedenfocus.domain.model.Subject
import com.gorai.myedenfocus.domain.model.Task
import com.gorai.myedenfocus.domain.repository.SessionRepository
import com.gorai.myedenfocus.domain.repository.SubjectRepository
import com.gorai.myedenfocus.domain.repository.TaskRepository
import com.gorai.myedenfocus.util.SnackbarEvent
import com.gorai.myedenfocus.util.toHours
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository
): ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state = combine(
            _state,
            subjectRepository.getTotalSubjectCount(),
            subjectRepository.getTotalGoalHours(),
            subjectRepository.getAllSubjects(),
            sessionRepository.getTotalSessionsDuration()
        ) { state, subjectCount, goalHours, subjects, totalSessionDuration -> state.copy(
            totalSubjectCount = subjectCount,
            totalGoalStudyHours = goalHours.toString(),
            subjects = subjects,
            totalStudiedHours = totalSessionDuration.toHours().toString()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
        initialValue = DashboardState()
    )
    val tasks: StateFlow<List<Task>> = taskRepository.getAllUpcomingTasks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val recentSessions: StateFlow<List<Session>> = sessionRepository.getRecentFiveSessions(subjectId = 1).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    private val _snackbarEventFlow = MutableSharedFlow<SnackbarEvent>()
    val snackbarEventFlow = _snackbarEventFlow.asSharedFlow()
    fun onEvent(event: DashboardEvent) {
        when(event) {
            DashboardEvent.DeleteSubject -> TODO()
            is DashboardEvent.OnDeleteSessionButtonClick -> {
                _state.update {
                    it.copy(subjectName = event.session.toString())
                }
            }
            is DashboardEvent.OnGoalStudyHoursChange -> {
                _state.update {
                    it.copy(goalStudyHours = event.hours)
                }
            }
            is DashboardEvent.OnSubjectCardColorChange -> {
                _state.update {
                    it.copy(subjectCardColors = event.colors)
                }
            }
            is DashboardEvent.OnSubjectNameChange -> {
                _state.update {
                    it.copy(subjectName = event.name)
                }
            }
            is DashboardEvent.OnTaskIsCompleteChange -> TODO()
            DashboardEvent.SaveSubject -> saveSubject()
            DashboardEvent.DeleteSubject -> TODO()
        }
    }

    private fun saveSubject() {
        viewModelScope.launch {
            try {
                subjectRepository.upsertSubject(
                    subject =  Subject(
                        name = state.value.subjectName,
                        goalHours = state.value.goalStudyHours.toFloatOrNull() ?: 1f,
                        colors = state.value.subjectCardColors.map { it.toArgb() }
                    )
                )
                _state.update {
                    it.copy(
                        subjectName = "",
                        goalStudyHours = "",
                        subjectCardColors = Subject.subjectCardColors.random()
                    )
                    }
                _snackbarEventFlow.emit(
                    SnackbarEvent.ShowSnackbar(
                        "Subject saved successfully"
                    )
                )
                } catch (e: Exception) {
                    _snackbarEventFlow.emit(
                        SnackbarEvent.ShowSnackbar(
                            "Couldn't save subject. ${e.message}",
                            SnackbarDuration.Long
                        )
                    )
            }
        }
    }
}