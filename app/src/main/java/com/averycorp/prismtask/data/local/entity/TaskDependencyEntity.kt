package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Directed "blocker → blocked" edge between two tasks. Cycle
 * prevention is a write-site check, not a constraint —
 * `DependencyCycleGuard` performs the DFS before the row is
 * inserted (mirrors the
 * [com.averycorp.prismtask.domain.automation.AutomationEngine.handleEvent]
 * `lineage: Set<Long>` walk).
 *
 * Both FKs CASCADE on task delete: when a task is hard-deleted, the
 * graph it participated in goes with it.
 */
@Entity(
    tableName = "task_dependencies",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["blocker_task_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["blocked_task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["blocker_task_id", "blocked_task_id"], unique = true),
        Index("blocked_task_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class TaskDependencyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "blocker_task_id")
    val blockerTaskId: Long,
    @ColumnInfo(name = "blocked_task_id")
    val blockedTaskId: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
