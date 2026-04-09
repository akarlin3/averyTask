package com.averycorp.averytask.data.repository

import com.averycorp.averytask.data.local.dao.TagDao
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.dao.TaskTemplateDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.local.entity.TaskTagCrossRef
import com.averycorp.averytask.data.local.entity.TaskTemplateEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskTemplateRepository @Inject constructor(
    private val templateDao: TaskTemplateDao,
    private val taskDao: TaskDao,
    private val tagDao: TagDao
) {
    fun getAllTemplates(): Flow<List<TaskTemplateEntity>> = templateDao.getAllTemplates()

    fun getTemplatesByCategory(category: String): Flow<List<TaskTemplateEntity>> =
        templateDao.getTemplatesByCategory(category)

    fun getAllCategories(): Flow<List<String>> = templateDao.getAllCategories()

    suspend fun getTemplateById(id: Long): TaskTemplateEntity? = templateDao.getTemplateById(id)

    fun searchTemplates(query: String): Flow<List<TaskTemplateEntity>> =
        templateDao.searchTemplates(query)

    suspend fun createTemplate(template: TaskTemplateEntity): Long =
        templateDao.insertTemplate(template)

    suspend fun updateTemplate(template: TaskTemplateEntity) =
        templateDao.updateTemplate(template.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteTemplate(id: Long) = templateDao.deleteTemplate(id)

    /**
     * Instantiates a new [TaskEntity] from the template referenced by
     * [templateId]. Callers can override the due date (e.g., "apply this
     * template for next Monday") and the project (e.g., "use this template
     * inside my current project"); all other fields come from the template.
     *
     * Template-level subtasks (stored as a JSON array of titles) and tag
     * assignments (stored as a JSON array of tag ids) are materialized as
     * real rows so the resulting task looks identical to one built by hand.
     * The template's usage counter is bumped as a side effect.
     *
     * @return the id of the newly created root task.
     * @throws IllegalArgumentException if no template with [templateId] exists.
     */
    suspend fun createTaskFromTemplate(
        templateId: Long,
        dueDateOverride: Long? = null,
        projectIdOverride: Long? = null
    ): Long {
        val template = templateDao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found")

        val now = System.currentTimeMillis()
        val task = buildTaskFromTemplate(template, dueDateOverride, projectIdOverride, now)
        val taskId = taskDao.insert(task)

        // Create subtasks if template has them
        val subtasks = parseSubtaskTitles(template.templateSubtasksJson)
        subtasks.forEachIndexed { index, title ->
            val subtask = TaskEntity(
                title = title,
                parentTaskId = taskId,
                sortOrder = index,
                createdAt = now,
                updatedAt = now
            )
            taskDao.insert(subtask)
        }

        // Assign tags if template has them
        val tagIds = parseTagIds(template.templateTagsJson)
        tagIds.forEach { tagId ->
            tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }

        // Increment usage
        templateDao.incrementUsage(templateId, now)

        return taskId
    }

    /**
     * Captures the shape of an existing task as a reusable template. Copies
     * over the task's title/description/priority/project/recurrence/duration,
     * serializes its tag assignments and subtask titles to JSON, and stores
     * the result as a new row in `task_templates`. The source task is left
     * untouched.
     */
    suspend fun createTemplateFromTask(
        taskId: Long,
        name: String,
        icon: String? = null,
        category: String? = null
    ): Long {
        val task = taskDao.getTaskByIdOnce(taskId)
            ?: throw IllegalArgumentException("Task not found")
        val tagIds = tagDao.getTagIdsForTaskOnce(taskId)
        val subtasks = taskDao.getSubtasksOnce(taskId)

        val template = buildTemplateFromTask(
            task = task,
            tagIds = tagIds,
            subtaskTitles = subtasks.map { it.title },
            name = name,
            icon = icon,
            category = category
        )
        return templateDao.insertTemplate(template)
    }

    companion object {
        private val gson = Gson()

        /**
         * Pure transformation: produce the [TaskEntity] that should be
         * inserted when instantiating [template]. Extracted from the
         * repository method so the field-mapping contract can be tested
         * without a Room database.
         */
        fun buildTaskFromTemplate(
            template: TaskTemplateEntity,
            dueDateOverride: Long?,
            projectIdOverride: Long?,
            now: Long
        ): TaskEntity = TaskEntity(
            title = template.templateTitle ?: template.name,
            description = template.templateDescription,
            priority = template.templatePriority ?: 0,
            projectId = projectIdOverride ?: template.templateProjectId,
            recurrenceRule = template.templateRecurrenceJson,
            estimatedDuration = template.templateDuration,
            dueDate = dueDateOverride,
            createdAt = now,
            updatedAt = now
        )

        /**
         * Pure transformation: build a template that captures the content
         * fields of [task] plus its tag assignments and subtask titles. The
         * caller supplies the human-facing [name]/[icon]/[category] since
         * those aren't implied by the task itself.
         */
        fun buildTemplateFromTask(
            task: TaskEntity,
            tagIds: List<Long>,
            subtaskTitles: List<String>,
            name: String,
            icon: String? = null,
            category: String? = null
        ): TaskTemplateEntity = TaskTemplateEntity(
            name = name,
            icon = icon,
            category = category,
            templateTitle = task.title,
            templateDescription = task.description,
            templatePriority = task.priority,
            templateProjectId = task.projectId,
            templateTagsJson = if (tagIds.isNotEmpty()) gson.toJson(tagIds) else null,
            templateRecurrenceJson = task.recurrenceRule,
            templateDuration = task.estimatedDuration,
            templateSubtasksJson = if (subtaskTitles.isNotEmpty()) gson.toJson(subtaskTitles) else null
        )

        /**
         * Pure transformation: parse the JSON array of subtask titles stored
         * on a template back into a list. Returns an empty list if the JSON
         * is null, blank, or malformed so callers can iterate without extra
         * null checks.
         */
        fun parseSubtaskTitles(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                gson.fromJson(json, Array<String>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Pure transformation: parse the JSON array of tag ids stored on a
         * template. Returns an empty list if the JSON is null, blank, or
         * malformed.
         */
        fun parseTagIds(json: String?): List<Long> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                gson.fromJson(json, Array<Long>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
