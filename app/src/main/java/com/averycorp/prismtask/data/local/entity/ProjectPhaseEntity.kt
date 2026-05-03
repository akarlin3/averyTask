package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An ordered phase within a [ProjectEntity]. Phases group tasks into
 * milestones-of-effort with optional date bounds and a version anchor
 * (e.g. "v2.0 — multi-user"). Audit:
 * `docs/audits/PRISMTASK_TIMELINE_CLASS_AUDIT.md` § P3 + § "Investigation
 * items 9–10".
 *
 * Lower [orderIndex] comes first; the value is user-controlled via
 * drag-to-reorder. [completedAt] is set when every task with this phase
 * id reports done.
 */
@Entity(
    tableName = "project_phases",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("project_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class ProjectPhaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "color_key")
    val colorKey: String? = null,
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,
    @ColumnInfo(name = "version_anchor")
    val versionAnchor: String? = null,
    @ColumnInfo(name = "version_note")
    val versionNote: String? = null,
    @ColumnInfo(name = "order_index", defaultValue = "0")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
