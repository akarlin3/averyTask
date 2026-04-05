package com.averykarlin.averytask.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.averykarlin.averytask.ui.screens.addedittask.AddEditTaskScreen
import com.averykarlin.averytask.ui.screens.tasklist.TaskListScreen

sealed class AveryTaskRoute(val route: String) {
    data object TaskList : AveryTaskRoute("task_list")
    data object AddEditTask : AveryTaskRoute("add_edit_task?taskId={taskId}") {
        fun createRoute(taskId: Long? = null): String =
            if (taskId != null) "add_edit_task?taskId=$taskId" else "add_edit_task"
    }
    data object ProjectList : AveryTaskRoute("project_list")
    data object AddEditProject : AveryTaskRoute("add_edit_project?projectId={projectId}") {
        fun createRoute(projectId: Long? = null): String =
            if (projectId != null) "add_edit_project?projectId=$projectId" else "add_edit_project"
    }
}

@Composable
fun AveryTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AveryTaskRoute.TaskList.route,
        modifier = modifier
    ) {
        composable(AveryTaskRoute.TaskList.route) {
            TaskListScreen(navController)
        }

        composable(
            route = AveryTaskRoute.AddEditTask.route,
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            AddEditTaskScreen(navController)
        }

        composable(AveryTaskRoute.ProjectList.route) {
            ProjectListPlaceholder(navController)
        }

        composable(
            route = AveryTaskRoute.AddEditProject.route,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId")?.takeIf { it != -1L }
            AddEditProjectPlaceholder(navController, projectId)
        }
    }
}

// Placeholder composables — will be replaced with real screens
@Composable
private fun ProjectListPlaceholder(navController: NavHostController) {
    androidx.compose.material3.Text("Project List")
}

@Composable
private fun AddEditProjectPlaceholder(navController: NavHostController, projectId: Long?) {
    androidx.compose.material3.Text(if (projectId != null) "Edit Project $projectId" else "Add Project")
}
