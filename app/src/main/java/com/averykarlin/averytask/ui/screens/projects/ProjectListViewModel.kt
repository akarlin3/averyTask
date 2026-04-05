package com.averykarlin.averytask.ui.screens.projects

import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.dao.ProjectWithCount
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    val projects: StateFlow<List<ProjectWithCount>> = projectRepository.getProjectWithTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onDeleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            try {
                projectRepository.deleteProject(project)
            } catch (e: Exception) {
                Log.e("ProjectListVM", "Failed to delete project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }
}
