package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps a local task to its Google Calendar event. Rows are written by the
 * backend-mediated push path (`CalendarSyncRepository.pushTask`) and
 * consulted by the pull path to skip events we originated, preventing
 * sync loops.
 *
 * Prior to migration 42 → 43 the table was device-calendar scoped and
 * did not carry a `calendar_id` / `sync_state` / `etag`. Those columns
 * were added for the backend path; see `Migrations.MIGRATION_42_43`.
 */
@Entity(
    tableName = "calendar_sync",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("calendar_id"),
        Index("sync_state")
    ]
)
data class CalendarSyncEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: Long,
    @ColumnInfo(name = "calendar_event_id")
    val calendarEventId: String,
    @ColumnInfo(name = "calendar_id", defaultValue = "primary")
    val calendarId: String = "primary",
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_version")
    val lastSyncedVersion: Long = 0,
    @ColumnInfo(name = "sync_state", defaultValue = "SYNCED")
    val syncState: String = "SYNCED",
    @ColumnInfo(name = "etag")
    val etag: String? = null
)
