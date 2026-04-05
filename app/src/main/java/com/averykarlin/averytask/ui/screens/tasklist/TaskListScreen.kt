package com.averykarlin.averytask.ui.screens.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.ui.navigation.AveryTaskRoute
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AveryTask",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: sort options */ }) {
                        Icon(
                            imageVector = Icons.Default.SortByAlpha,
                            contentDescription = "Sort"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Task",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(tasks, key = { it.id }) { task ->
                    val project = projects.find { it.id == task.projectId }
                    TaskItem(
                        task = task,
                        project = project,
                        onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                        onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(task.id)) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    project: ProjectEntity?,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.dueDate?.let { millis ->
                        val label = formatDueDate(millis)
                        Text(
                            text = label.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = label.color
                        )
                    }

                    if (project != null) {
                        ProjectChip(project)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            PriorityDot(task.priority)
        }
    }
}

@Composable
private fun PriorityDot(priority: Int) {
    val color = when (priority) {
        1 -> Color(0xFF4A90D9) // Low — blue
        2 -> Color(0xFFF5C542) // Medium — yellow
        3 -> Color(0xFFE8872A) // High — orange
        4 -> Color(0xFFD93025) // Urgent — red
        else -> Color(0xFFAAAAAA) // None — gray
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ProjectChip(project: ProjectEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(project.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class DueDateLabel(val text: String, val color: Color)

@Composable
private fun formatDueDate(epochMillis: Long): DueDateLabel {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()

    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfTomorrow = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfDayAfter = cal.timeInMillis

    val overdue = Color(0xFFD93025)
    val normal = MaterialTheme.colorScheme.onSurfaceVariant

    return when {
        epochMillis < startOfToday -> DueDateLabel("Overdue", overdue)
        epochMillis < startOfTomorrow -> DueDateLabel("Today", Color(0xFFE8872A))
        epochMillis < startOfDayAfter -> DueDateLabel("Tomorrow", normal)
        else -> {
            val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            DueDateLabel(fmt.format(Date(epochMillis)), normal)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No tasks yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap + to add your first task",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
