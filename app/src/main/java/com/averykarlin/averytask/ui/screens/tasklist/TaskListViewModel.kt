package com.averykarlin.averytask.ui.screens.tasklist

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class SortOption(val label: String) {
    DUE_DATE("Due Date"),
    PRIORITY("Priority"),
    CREATED("Date Created"),
    ALPHABETICAL("Alphabetical")
}

enum class ViewMode(val label: String) {
    UPCOMING("Upcoming"),
    LIST("List")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    private val rootTasks: StateFlow<List<TaskEntity>> = taskRepository.getIncompleteRootTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId

    private val _currentSort = MutableStateFlow(SortOption.DUE_DATE)
    val currentSort: StateFlow<SortOption> = _currentSort

    private val _viewMode = MutableStateFlow(ViewMode.UPCOMING)
    val viewMode: StateFlow<ViewMode> = _viewMode

    val filteredTasks: StateFlow<List<TaskEntity>> =
        combine(rootTasks, _selectedProjectId, _currentSort) { taskList, projectId, sort ->
            val filtered = if (projectId == null) taskList else taskList.filter { it.projectId == projectId }
            sortTasks(filtered, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedTasks: StateFlow<Map<String, List<TaskEntity>>> =
        combine(rootTasks, _selectedProjectId, _currentSort) { taskList, projectId, sort ->
            val filtered = if (projectId == null) taskList else taskList.filter { it.projectId == projectId }
            groupByDate(filtered, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val overdueCount: StateFlow<Int> = rootTasks.map { tasks ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        tasks.count { it.dueDate != null && it.dueDate < startOfToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val subtasksMap: StateFlow<Map<Long, List<TaskEntity>>> = rootTasks.flatMapLatest { tasks ->
        val parentIds = tasks.map { it.id }
        if (parentIds.isEmpty()) {
            flowOf(emptyMap<Long, List<TaskEntity>>())
        } else {
            val flows = parentIds.map { id ->
                taskRepository.getSubtasks(id).map { subtasks: List<TaskEntity> -> id to subtasks }
            }
            combine(flows) { pairs: Array<Pair<Long, List<TaskEntity>>> -> pairs.toMap() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onSelectProject(projectId: Long?) {
        _selectedProjectId.value = projectId
    }

    fun onChangeSort(sort: SortOption) {
        _currentSort.value = sort
    }

    fun onChangeViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun onAddTask(title: String, dueDate: Long? = null, priority: Int = 0, projectId: Long? = null) {
        viewModelScope.launch {
            try {
                taskRepository.addTask(title = title, dueDate = dueDate, priority = priority, projectId = projectId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to add task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onAddSubtask(title: String, parentTaskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.addSubtask(title = title, parentTaskId = parentTaskId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to add subtask", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCurrentlyCompleted) {
                    taskRepository.uncompleteTask(taskId)
                } else {
                    taskRepository.completeTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to toggle complete", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onToggleSubtaskComplete(subtaskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCompleted) {
                    taskRepository.uncompleteTask(subtaskId)
                } else {
                    taskRepository.completeTask(subtaskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to toggle subtask", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onDeleteTask(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.deleteTask(taskId)
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to delete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onCompleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.completeTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task completed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.uncompleteTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to complete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun onDeleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                val savedTask = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                taskRepository.deleteTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.insertTask(savedTask)
                }
            } catch (e: Exception) {
                Log.e("TaskListVM", "Failed to delete task", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    private fun sortTasks(tasks: List<TaskEntity>, sort: SortOption): List<TaskEntity> =
        when (sort) {
            SortOption.DUE_DATE -> tasks.sortedWith(
                compareBy<TaskEntity> { it.dueDate == null }
                    .thenBy { it.dueDate }
                    .thenByDescending { it.priority }
            )
            SortOption.PRIORITY -> tasks.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate == null }
                    .thenBy { it.dueDate }
            )
            SortOption.CREATED -> tasks.sortedByDescending { it.createdAt }
            SortOption.ALPHABETICAL -> tasks.sortedBy { it.title.lowercase() }
        }

    private fun groupByDate(tasks: List<TaskEntity>, sort: SortOption): Map<String, List<TaskEntity>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfTomorrow = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfDayAfterTomorrow = calendar.timeInMillis

        calendar.timeInMillis = startOfToday
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val endOfWeek = calendar.timeInMillis

        val grouped = linkedMapOf<String, MutableList<TaskEntity>>()

        for (task in tasks) {
            val bucket = when {
                task.dueDate == null -> "No Date"
                task.dueDate < startOfToday -> "Overdue"
                task.dueDate < startOfTomorrow -> "Today"
                task.dueDate < startOfDayAfterTomorrow -> "Tomorrow"
                task.dueDate < endOfWeek -> "This Week"
                else -> "Later"
            }
            grouped.getOrPut(bucket) { mutableListOf() }.add(task)
        }

        val order = listOf("Overdue", "Today", "Tomorrow", "This Week", "Later", "No Date")
        return order
            .filter { it in grouped }
            .associateWith { sortTasks(grouped[it]!!, sort) }
    }
}
