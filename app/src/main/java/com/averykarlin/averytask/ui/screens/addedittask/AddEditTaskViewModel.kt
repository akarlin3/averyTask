package com.averykarlin.averytask.ui.screens.addedittask

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: Long? = savedStateHandle.get<Long>("taskId")?.takeIf { it != -1L }
    val isEditMode: Boolean = taskId != null

    private var existingTask: TaskEntity? = null

    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var dueDate by mutableStateOf<Long?>(null)
        private set
    var dueTime by mutableStateOf<Long?>(null)
        private set
    var priority by mutableIntStateOf(0)
        private set
    var projectId by mutableStateOf<Long?>(null)
        private set
    var parentTaskId by mutableStateOf<Long?>(null)
        private set
    var titleError by mutableStateOf(false)
        private set

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (taskId != null) {
            viewModelScope.launch {
                taskRepository.getTaskById(taskId).firstOrNull()?.let { task ->
                    existingTask = task
                    title = task.title
                    description = task.description.orEmpty()
                    dueDate = task.dueDate
                    dueTime = task.dueTime
                    priority = task.priority
                    projectId = task.projectId
                    parentTaskId = task.parentTaskId
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value
        if (value.isNotBlank()) titleError = false
    }

    fun onDescriptionChange(value: String) { description = value }
    fun onDueDateChange(value: Long?) { dueDate = value }
    fun onDueTimeChange(value: Long?) { dueTime = value }
    fun onPriorityChange(value: Int) { priority = value }
    fun onProjectIdChange(value: Long?) { projectId = value }

    suspend fun saveTask(): Boolean {
        if (title.isBlank()) {
            titleError = true
            return false
        }

        val existing = existingTask
        if (existing != null) {
            taskRepository.updateTask(
                existing.copy(
                    title = title.trim(),
                    description = description.trim().ifEmpty { null },
                    dueDate = dueDate,
                    dueTime = dueTime,
                    priority = priority,
                    projectId = projectId,
                    parentTaskId = parentTaskId
                )
            )
        } else {
            taskRepository.addTask(
                title = title.trim(),
                description = description.trim().ifEmpty { null },
                dueDate = dueDate,
                dueTime = dueTime,
                priority = priority,
                projectId = projectId,
                parentTaskId = parentTaskId
            )
        }
        return true
    }

    suspend fun deleteTask() {
        taskId?.let { taskRepository.deleteTask(it) }
    }
}
