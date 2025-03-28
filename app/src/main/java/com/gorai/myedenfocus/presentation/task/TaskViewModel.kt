package com.gorai.myedenfocus.presentation.task

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gorai.myedenfocus.domain.model.Task
import com.gorai.myedenfocus.domain.repository.SubjectRepository
import com.gorai.myedenfocus.domain.repository.TaskRepository
import com.gorai.myedenfocus.presentation.navArgs
import com.gorai.myedenfocus.util.Priority
import com.gorai.myedenfocus.util.SnackbarEvent
import com.gorai.myedenfocus.service.TaskNotificationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val navArgs: TaskScreenNavArgs = savedStateHandle.navArgs()
    private val _state = MutableStateFlow(TaskState())
    val state = combine(
        _state,
        subjectRepository.getAllSubjects()
    ) { state, subjects ->
        state.copy(subjects = subjects)
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
        initialValue = TaskState()
    )

    private val _snackbarEventFlow = MutableSharedFlow<SnackbarEvent>()
    val snackbarEventFlow = _snackbarEventFlow.asSharedFlow()

    init {
        fetchTask()
        fetchSubject()
    }

    fun onEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.OnTitleChange -> {
                _state.update {
                    it.copy(title = event.title)
                }
            }
            is TaskEvent.OnDescriptionChange -> {
                _state.update {
                    it.copy(description = event.description)
                }
            }
            is TaskEvent.OnDateChange -> {
                _state.update {
                    it.copy(dueDate = event.millis)
                }
            }
            is TaskEvent.OnPriorityChange -> {
                _state.update {
                    it.copy(priority = event.priority)
                }
            }
            is TaskEvent.OnTaskDurationChange -> {
                _state.update {
                    it.copy(taskDuration = event.minutes)
                }
            }
            is TaskEvent.OnIsCompleteChange -> {
                viewModelScope.launch {
                    try {
                        // Get current state
                        val currentState = _state.value
                        
                        // Toggle completion state
                        _state.update {
                            it.copy(isTaskComplete = !it.isTaskComplete)
                        }
                        
                        // Only update in database if we have a valid task ID
                        currentState.currentTaskId?.let { taskId ->
                            // Get existing task
                            taskRepository.getTaskById(taskId)?.let { existingTask ->
                                // Create updated task with toggled completion
                                val updatedTask = existingTask.copy(
                                    isComplete = !existingTask.isComplete,
                                    completedAt = if (!existingTask.isComplete) System.currentTimeMillis() else null
                                )
                                // Save to database
                                taskRepository.upsertTask(updatedTask)
                                
                                _snackbarEventFlow.emit(
                                    SnackbarEvent.ShowSnackbar(
                                        message = if (updatedTask.isComplete) "Topic marked as complete" else "Topic marked as incomplete",
                                        duration = SnackbarDuration.Short
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        _snackbarEventFlow.emit(
                            SnackbarEvent.ShowSnackbar(
                                message = "Couldn't update task status: ${e.message}",
                                duration = SnackbarDuration.Long
                            )
                        )
                    }
                }
            }
            is TaskEvent.OnRelatedSubjectSelect -> {
                _state.update {
                    it.copy(
                        relatedToSubject = event.subject.name,
                        subjectId = event.subject.subjectId
                    )
                }
            }
            TaskEvent.SaveTask -> saveTask()
            TaskEvent.DeleteTask -> deleteTask()
        }
    }

    private fun deleteTask() {
        viewModelScope.launch {
            try {
                val currentTaskId = state.value.currentTaskId
                if (currentTaskId != null) {
                    withContext(Dispatchers.IO) {
                        taskRepository.deleteTask(taskId = currentTaskId)
                    }
                    _snackbarEventFlow.emit(
                        SnackbarEvent.ShowSnackbar(message = "Topic deleted successfully")
                    )
                    _snackbarEventFlow.emit(SnackbarEvent.NavigateUp)
                } else {
                    _snackbarEventFlow.emit(
                        SnackbarEvent.ShowSnackbar(message = "No topic to delete")
                    )
                }
            } catch (e: Exception) {
                _snackbarEventFlow.emit(
                    SnackbarEvent.ShowSnackbar(
                        message = "Couldn't delete topic. ${e.message}",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

    private fun saveTask() {
        viewModelScope.launch {
            try {
                val task = createTaskFromState()
                taskRepository.upsertTask(task)
                
                _snackbarEventFlow.emit(
                    SnackbarEvent.ShowSnackbar(message = "Topic saved successfully")
                )
                _snackbarEventFlow.emit(SnackbarEvent.NavigateUp)
            } catch(e: Exception) {
                _snackbarEventFlow.emit(
                    SnackbarEvent.ShowSnackbar(
                        message = e.message ?: "Couldn't save topic",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

    private fun createTaskFromState(): Task {
        val currentState = state.value
        
        if (currentState.title.isBlank()) {
            throw IllegalStateException("Task title cannot be empty")
        }
        
        if (currentState.subjectId == null) {
            throw IllegalStateException("Please select a subject")
        }
        
        if (currentState.dueDate == null) {
            throw IllegalStateException("Please select a due date")
        }
        
        return Task(
            taskId = currentState.currentTaskId,
            title = currentState.title,
            description = currentState.description,
            dueDate = currentState.dueDate,
            priority = currentState.priority.value,
            relatedToSubject = currentState.relatedToSubject ?: "",
            isComplete = currentState.isTaskComplete,
            taskSubjectId = currentState.subjectId,
            taskDuration = currentState.taskDuration
        )
    }

    private fun fetchTask() {
        viewModelScope.launch {
            navArgs.taskId?.let { id ->
                taskRepository.getTaskById(id)?.let { task ->
                    _state.update { it: TaskState ->
                        it.copy(
                            title = task.title,
                            description = task.description,
                            dueDate = task.dueDate,
                            isTaskComplete = task.isComplete,
                            relatedToSubject = task.relatedToSubject,
                            priority = Priority.fromInt(task.priority),
                            subjectId = task.taskSubjectId,
                            currentTaskId = task.taskId,
                            taskDuration = task.taskDuration
                        )
                    }
                }
            }
        }
    }
    private fun fetchSubject() {
        viewModelScope.launch {
            navArgs.subjectId?.let { id ->
                subjectRepository.getSubjectById(id)?.let { subject ->
                    _state.update {
                        it.copy(
                            subjectId = subject.subjectId,
                            relatedToSubject = subject.name
                        )
                    }
                }
            }
        }
    }
}