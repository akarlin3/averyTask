package com.averykarlin.averytask.ui.screens.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: Long? = savedStateHandle.get<Long>("projectId")?.takeIf { it != -1L }
    val isEditMode: Boolean = projectId != null

    private var existingProject: ProjectEntity? = null

    var name by mutableStateOf("")
        private set
    var color by mutableStateOf("#4A90D9")
        private set
    var icon by mutableStateOf("\uD83D\uDCC1")
        private set
    var nameError by mutableStateOf(false)
        private set

    init {
        if (projectId != null) {
            viewModelScope.launch {
                projectRepository.getProjectById(projectId).firstOrNull()?.let { project ->
                    existingProject = project
                    name = project.name
                    color = project.color
                    icon = project.icon
                }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        if (value.isNotBlank()) nameError = false
    }

    fun onColorChange(value: String) { color = value }
    fun onIconChange(value: String) { icon = value }

    suspend fun saveProject(): Boolean {
        if (name.isBlank()) {
            nameError = true
            return false
        }

        val existing = existingProject
        if (existing != null) {
            projectRepository.updateProject(
                existing.copy(
                    name = name.trim(),
                    color = color,
                    icon = icon
                )
            )
        } else {
            projectRepository.addProject(
                name = name.trim(),
                color = color,
                icon = icon
            )
        }
        return true
    }

    suspend fun deleteProject() {
        existingProject?.let { projectRepository.deleteProject(it) }
    }
}
