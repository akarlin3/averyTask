package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Risk register entry under a [ProjectEntity]. [level] is the
 * [com.averycorp.prismtask.domain.model.RiskLevel] enum name (LOW /
 * MEDIUM / HIGH); [resolvedAt] non-null marks the risk as retired
 * (kept for history, hidden from active register views).
 */
@Entity(
    tableName = "project_risks",
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
data class ProjectRiskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val title: String,
    val level: String,
    val mitigation: String? = null,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
