package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_templates",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateProjectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("templateProjectId"), Index("userId")]
)
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Firebase UID for sync
    val userId: String? = null,
    // Backend ID for sync
    val remoteId: Int? = null,
    val name: String,
    val description: String? = null,
    // emoji
    val icon: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "template_title")
    val templateTitle: String? = null,
    @ColumnInfo(name = "template_description")
    val templateDescription: String? = null,
    @ColumnInfo(name = "template_priority")
    val templatePriority: Int? = null,
    @ColumnInfo(name = "templateProjectId")
    val templateProjectId: Long? = null,
    @ColumnInfo(name = "template_tags_json")
    // JSON array of tag IDs
    val templateTagsJson: String? = null,
    @ColumnInfo(name = "template_recurrence_json")
    val templateRecurrenceJson: String? = null,
    @ColumnInfo(name = "template_duration")
    // minutes
    val templateDuration: Int? = null,
    @ColumnInfo(name = "template_subtasks_json")
    // JSON array of subtask titles
    val templateSubtasksJson: String? = null,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
