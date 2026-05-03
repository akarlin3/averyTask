package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * External anchor pinned to a [ProjectEntity] (and optionally a
 * [ProjectPhaseEntity]). The variant payload — calendar deadline,
 * numeric threshold, or boolean gate — is stored as JSON in [anchorJson]
 * via `ExternalAnchorJsonAdapter` (mirrors the
 * [com.averycorp.prismtask.domain.automation.AutomationJsonAdapter]
 * polymorphic pattern).
 *
 * [phaseId] FK is SET_NULL on phase delete so anchors survive
 * phase reshuffles; the project FK is CASCADE.
 */
@Entity(
    tableName = "external_anchors",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectPhaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["phase_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("project_id"),
        Index("phase_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class ExternalAnchorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "phase_id")
    val phaseId: Long? = null,
    val label: String,
    @ColumnInfo(name = "anchor_json")
    val anchorJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
