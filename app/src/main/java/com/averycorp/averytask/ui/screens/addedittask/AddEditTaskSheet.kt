package com.averycorp.averytask.ui.screens.addedittask

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.averytask.data.local.entity.ProjectEntity
import com.averycorp.averytask.data.local.entity.TagEntity
import com.averycorp.averytask.ui.components.RecurrenceSelector
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Entry point for presenting the task editor as a modal bottom sheet from any
 * screen. Creates its own [AddEditTaskViewModel] scoped to the caller's
 * ViewModelStoreOwner (typically the containing NavBackStackEntry) and seeds
 * it with the supplied taskId / create-mode defaults.
 *
 * @param taskId existing task to edit, or null for create mode.
 * @param projectId pre-selected project for create mode (ignored in edit mode).
 * @param initialDate pre-set due date for create mode (ignored in edit mode).
 * @param initialTab tab to open first (0=Details, 1=Schedule, 2=Organize).
 * @param onDismiss invoked after the sheet has finished closing.
 * @param onDeleteTask optional handler invoked when the user confirms deletion
 *   from the Organize tab. When supplied, the parent is responsible for
 *   performing the delete (typically via a delete-with-undo VM call) and the
 *   sheet will dismiss itself. When null, the sheet falls back to calling
 *   [AddEditTaskViewModel.deleteTask] directly and deletion has no undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskSheetHost(
    taskId: Long?,
    projectId: Long?,
    initialDate: Long?,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onDeleteTask: ((Long) -> Unit)? = null,
) {
    val viewModel: AddEditTaskViewModel = hiltViewModel(key = "addedit_task_sheet")

    LaunchedEffect(taskId, projectId, initialDate) {
        viewModel.initialize(taskId = taskId, projectId = projectId, initialDate = initialDate)
    }

    AddEditTaskSheet(
        viewModel = viewModel,
        initialTab = initialTab,
        onDismiss = onDismiss,
        onDeleteTask = onDeleteTask
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AddEditTaskSheet(
    viewModel: AddEditTaskViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onDeleteTask: ((Long) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    val titleFocusRequester = remember { FocusRequester() }

    fun attemptDismiss() {
        if (viewModel.hasUnsavedChanges) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { attemptDismiss() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            // Sticky header: close / screen title / save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attemptDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
                Text(
                    text = if (viewModel.isEditMode) "Edit Task" else "New Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            if (viewModel.saveTask()) onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = "Save",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Title field (large, always visible above tabs)
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = {
                    Text(
                        text = "Task Title",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge,
                isError = viewModel.titleError,
                supportingText = if (viewModel.titleError) {
                    { Text("Title is required") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(titleFocusRequester)
            )

            // Priority circles row (always visible above tabs)
            PriorityCircleRow(
                selected = viewModel.priority,
                onSelect = viewModel::onPriorityChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tab bar
            val tabs = listOf("Details", "Schedule", "Organize")
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index)
                                    FontWeight.SemiBold
                                else
                                    FontWeight.Normal
                            )
                        }
                    )
                }
            }
            HorizontalDivider()

            // Swipeable tab content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        0 -> DetailsTabContent(viewModel)
                        1 -> ScheduleTabContent(viewModel)
                        2 -> OrganizeTabContent(
                            viewModel = viewModel,
                            onDeleteTask = onDeleteTask,
                            onDismiss = onDismiss
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Auto-focus the title field when creating a new task.
    LaunchedEffect(Unit) {
        if (!viewModel.isEditMode) {
            titleFocusRequester.requestFocus()
        }
    }

    // Back button / gesture: route through attemptDismiss so unsaved changes
    // prompt for confirmation before closing.
    BackHandler { attemptDismiss() }

    // Discard confirmation dialog
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

/**
 * Header row of 5 priority dots (None / Low / Medium / High / Urgent).
 * Tap to select; the selected dot shows a checkmark overlay.
 */
@Composable
private fun PriorityCircleRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColors = LocalPriorityColors.current
    val levels = listOf(
        0 to priorityColors.none,
        1 to priorityColors.low,
        2 to priorityColors.medium,
        3 to priorityColors.high,
        4 to priorityColors.urgent
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        levels.forEach { (level, color) ->
            PriorityCircle(
                color = color,
                selected = selected == level,
                onClick = { onSelect(level) }
            )
        }
    }
}

@Composable
private fun PriorityCircle(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tab content composables
// ---------------------------------------------------------------------------

@Composable
private fun DetailsTabContent(viewModel: AddEditTaskViewModel) {
    val context = LocalContext.current
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()

    var notesExpanded by remember { mutableStateOf(viewModel.notes.isNotBlank()) }
    var showAddLinkDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAddImageAttachment(context, it) }
    }

    // Description
    OutlinedTextField(
        value = viewModel.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text("Description") },
        minLines = 3,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )

    // Notes (collapsible)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { notesExpanded = !notesExpanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("Notes")
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (notesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (notesExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AnimatedVisibility(visible = notesExpanded) {
        OutlinedTextField(
            value = viewModel.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text("Notes") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Attachments (only in edit mode)
    if (viewModel.isEditMode) {
        SectionLabel("Attachments")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Image")
            }
            OutlinedButton(onClick = { showAddLinkDialog = true }) {
                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Link")
            }
        }
        if (attachments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        onDelete = { viewModel.onDeleteAttachment(context, attachment) }
                    )
                }
            }
        }
    }

    // Add link dialog
    if (showAddLinkDialog) {
        var linkUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddLinkDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (linkUrl.isNotBlank()) {
                            viewModel.onAddLinkAttachment(linkUrl.trim())
                            showAddLinkDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLinkDialog = false }) { Text("Cancel") }
            },
            title = { Text("Add Link") },
            text = {
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTabContent(viewModel: AddEditTaskViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    // Due date
    SectionLabel("Due Date")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateQuickChip("Today", viewModel.dueDate, todayMillis()) {
            viewModel.onDueDateChange(todayMillis())
        }
        DateQuickChip("Tomorrow", viewModel.dueDate, tomorrowMillis()) {
            viewModel.onDueDateChange(tomorrowMillis())
        }
        DateQuickChip("+1 Week", viewModel.dueDate, weekFromNowMillis()) {
            viewModel.onDueDateChange(weekFromNowMillis())
        }
        FilterChip(
            selected = false,
            onClick = { showDatePicker = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Date")
                }
            }
        )
        if (viewModel.dueDate != null) {
            FilterChip(
                selected = false,
                onClick = { viewModel.onDueDateChange(null) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            )
        }
    }
    if (viewModel.dueDate != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Due: ${formatDateSmart(viewModel.dueDate!!)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { viewModel.onDueDateChange(null) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear date",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Due time
    SectionLabel("Due Time")
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showTimePicker = true }) {
            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = viewModel.dueTime?.let { formatTime(it) } ?: "No Time"
            )
        }
        if (viewModel.dueTime != null) {
            IconButton(onClick = { viewModel.onDueTimeChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear time", modifier = Modifier.size(18.dp))
            }
        }
    }

    // Reminder
    SectionLabel("Reminder")
    val hasDate = viewModel.dueDate != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDate) { showReminderDialog = true }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (viewModel.reminderOffset != null)
                Icons.Default.Notifications
            else
                Icons.Default.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (hasDate)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (!hasDate) "Set a due date first"
            else reminderOffsetLabel(viewModel.reminderOffset),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasDate)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    // Recurrence
    SectionLabel("Recurrence")
    RecurrenceSelector(
        currentRule = viewModel.recurrenceRule,
        onRuleChanged = viewModel::onRecurrenceRuleChange
    )

    // Date picker dialog
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = viewModel.dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDueDateChange(state.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    // Reminder picker dialog
    if (showReminderDialog) {
        ReminderPickerDialog(
            currentOffset = viewModel.reminderOffset,
            onSelect = { offset ->
                viewModel.onReminderOffsetChange(offset)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    // Time picker dialog
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            viewModel.dueTime?.let { timeInMillis = it }
        }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val picked = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, state.hour)
                    set(Calendar.MINUTE, state.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.onDueTimeChange(picked.timeInMillis)
                showTimePicker = false
            }
        ) {
            TimePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OrganizeTabContent(
    viewModel: AddEditTaskViewModel,
    onDeleteTask: ((Long) -> Unit)?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    var showProjectPicker by remember { mutableStateOf(false) }
    var showCreateProjectForm by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showNewTagForm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ---- Project section ----
    SectionLabel("Project")
    if (projects.isEmpty()) {
        EmptyProjectsCard(onCreate = {
            showProjectPicker = true
            showCreateProjectForm = true
        })
    } else {
        ProjectSelectorCard(
            selectedProject = projects.find { it.id == viewModel.projectId },
            onClick = { showProjectPicker = true }
        )
    }

    // ---- Tags section ----
    SectionLabel("Tags")
    if (allTags.isEmpty() && !showNewTagForm) {
        EmptyTagsCard(onCreate = { showNewTagForm = true })
    } else {
        TagFlowSelector(
            tags = allTags,
            selectedTagIds = viewModel.selectedTagIds,
            expanded = tagsExpanded,
            onToggleExpanded = { tagsExpanded = !tagsExpanded },
            onToggleTag = { tagId ->
                val newSet = if (tagId in viewModel.selectedTagIds) {
                    viewModel.selectedTagIds - tagId
                } else {
                    viewModel.selectedTagIds + tagId
                }
                viewModel.onSelectedTagIdsChange(newSet)
            },
            onAddTag = { showNewTagForm = true },
            showNewTagForm = showNewTagForm,
            onCancelNewTag = { showNewTagForm = false },
            onCreateTag = { name, color ->
                viewModel.createAndAssignTag(name, color)
                showNewTagForm = false
            }
        )
    }

    // ---- Parent task section ----
    // TODO: Add searchable parent-task picker so tasks can be nested as subtasks
    // from the Organize tab. For now we expose a minimal read-only indicator
    // when a parent is already set (e.g. when editing a task opened from a
    // subtask row) so the relationship is visible and can be cleared.
    if (viewModel.parentTaskId != null) {
        SectionLabel("Parent Task")
        ParentTaskIndicator(
            parentTaskId = viewModel.parentTaskId!!,
            onClear = { viewModel.onParentTaskIdChange(null) }
        )
    }

    // ---- Delete task (edit mode only) ----
    if (viewModel.isEditMode) {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Delete Task",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // ---- Project picker sheet ----
    if (showProjectPicker) {
        ProjectPickerSheet(
            projects = projects,
            selectedProjectId = viewModel.projectId,
            showCreateForm = showCreateProjectForm,
            onShowCreateForm = { showCreateProjectForm = true },
            onHideCreateForm = { showCreateProjectForm = false },
            onSelect = { id ->
                viewModel.onProjectIdChange(id)
                showProjectPicker = false
                showCreateProjectForm = false
            },
            onCreate = { name, color ->
                viewModel.createAndSelectProject(name, color)
                showProjectPicker = false
                showCreateProjectForm = false
            },
            onDismiss = {
                showProjectPicker = false
                showCreateProjectForm = false
            }
        )
    }

    // ---- Delete confirmation ----
    if (showDeleteConfirm) {
        val taskTitle = viewModel.title.trim().ifEmpty { "this task" }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task") },
            text = { Text("Delete '$taskTitle'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val id = viewModel.currentEditingTaskId
                        val callback = onDeleteTask
                        if (id != null && callback != null) {
                            callback(id)
                            onDismiss()
                        } else {
                            scope.launch {
                                viewModel.deleteTask()
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Organize tab: Project selector
// ---------------------------------------------------------------------------

@Composable
private fun ProjectSelectorCard(
    selectedProject: ProjectEntity?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedProject != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseColorOr(selectedProject.color, Color.Gray))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedProject.icon,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedProject.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "No Project",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Change project",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EmptyProjectsCard(onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onCreate)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Create Your First Project",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPickerSheet(
    projects: List<ProjectEntity>,
    selectedProjectId: Long?,
    showCreateForm: Boolean,
    onShowCreateForm: () -> Unit,
    onHideCreateForm: () -> Unit,
    onSelect: (Long?) -> Unit,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select Project",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    ProjectPickerRow(
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = "None",
                        trailing = {
                            if (selectedProjectId == null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        onClick = { onSelect(null) }
                    )
                }
                items(projects, key = { it.id }) { project ->
                    ProjectPickerRow(
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseColorOr(project.color, Color.Gray))
                            )
                        },
                        label = "${project.icon} ${project.name}",
                        trailing = {
                            if (selectedProjectId == project.id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        onClick = { onSelect(project.id) }
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    if (showCreateForm) {
                        InlineCreateProjectForm(
                            onCreate = onCreate,
                            onCancel = onHideCreateForm
                        )
                    } else {
                        ProjectPickerRow(
                            leading = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = "Create New Project",
                            labelColor = MaterialTheme.colorScheme.primary,
                            onClick = onShowCreateForm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerRow(
    leading: @Composable () -> Unit,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineCreateProjectForm(
    onCreate: (name: String, color: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PROJECT_COLORS.first()) }

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PROJECT_COLORS.forEach { color ->
                ColorDot(
                    color = color,
                    selected = color == selectedColor,
                    onClick = { selectedColor = color }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Organize tab: Tag selector
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagFlowSelector(
    tags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleTag: (Long) -> Unit,
    onAddTag: () -> Unit,
    showNewTagForm: Boolean,
    onCancelNewTag: () -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val showAllThreshold = 12
    val collapsedLimit = 8
    val shouldCollapse = tags.size > showAllThreshold && !expanded
    val visibleTags = if (shouldCollapse) tags.take(collapsedLimit) else tags

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        visibleTags.forEach { tag ->
            TagToggleChip(
                tag = tag,
                selected = tag.id in selectedTagIds,
                onClick = { onToggleTag(tag.id) }
            )
        }
        if (shouldCollapse) {
            ShowMoreChip(
                count = tags.size,
                onClick = onToggleExpanded
            )
        }
        NewTagChip(onClick = onAddTag)
    }

    if (showNewTagForm) {
        Spacer(modifier = Modifier.height(4.dp))
        InlineCreateTagForm(
            onCreate = onCreateTag,
            onCancel = onCancelNewTag
        )
    }
}

@Composable
private fun TagToggleChip(
    tag: TagEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tagColor = parseColorOr(tag.color, Color.Gray)
    val bg = if (selected) tagColor else Color.Transparent
    val textColor = if (selected) Color.White else tagColor
    val borderColor = tagColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun NewTagChip(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = color,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "New Tag",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ShowMoreChip(count: Int, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Show All ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyTagsCard(onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onCreate)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Add Tags to Organize Tasks",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineCreateTagForm(
    onCreate: (name: String, color: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(TAG_COLORS.first()) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tag Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TAG_COLORS.forEach { color ->
                ColorDot(
                    color = color,
                    selected = color == selectedColor,
                    onClick = { selectedColor = color }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Organize tab: Parent task indicator
// ---------------------------------------------------------------------------

@Composable
private fun ParentTaskIndicator(
    parentTaskId: Long,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Subtask of task #$parentTaskId",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove parent",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Organize tab: Shared helpers
// ---------------------------------------------------------------------------

@Composable
private fun ColorDot(
    color: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val parsed = parseColorOr(color, Color.Gray)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(parsed)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun parseColorOr(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

private val PROJECT_COLORS = listOf(
    "#4A90D9", "#7B61FF", "#E8872A", "#D93025",
    "#2E7D32", "#00897B", "#F4B400", "#8E24AA"
)

private val TAG_COLORS = listOf(
    "#6B7280", "#4A90D9", "#7B61FF", "#2E7D32",
    "#E8872A", "#D93025", "#00897B", "#F4B400"
)
