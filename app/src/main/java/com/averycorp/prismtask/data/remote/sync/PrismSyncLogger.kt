package com.averycorp.prismtask.data.remote.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

const val PRISM_SYNC_TAG = "PrismSync"

/**
 * Single record in the sync log ring buffer. Also the payload written to
 * [android.util.Log] under the shared [PRISM_SYNC_TAG] so `adb logcat -s PrismSync`
 * surfaces every sync event.
 */
data class SyncLogEntry(
    val timestampMs: Long,
    val level: Level,
    val operation: String,
    val entity: String? = null,
    val id: String? = null,
    val status: String? = null,
    val durationMs: Long? = null,
    val detail: String? = null,
    val throwable: Throwable? = null
) {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun format(): String {
        val parts = mutableListOf<String>()
        parts += "[$PRISM_SYNC_TAG] $operation"
        if (entity != null) parts += "$entity=${id ?: "?"}"
        if (status != null) parts += "status=$status"
        if (durationMs != null) parts += "duration=${durationMs}ms"
        if (detail != null) parts += "detail=$detail"
        return parts.joinToString(" | ")
    }
}

/**
 * Unified logger for every sync-related surface (Firebase [SyncService],
 * [BackendSyncService], [com.averycorp.prismtask.data.remote.SyncTracker],
 * network state transitions, token refresh). Writes structured entries to
 * [android.util.Log] and also keeps the most recent [MAX_ENTRIES] in an
 * in-memory ring buffer consumed by the debug panel.
 */
@Singleton
class PrismSyncLogger
@Inject
constructor() {
    private val lock = Any()
    private val buffer = ArrayDeque<SyncLogEntry>()
    private val _entries = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val entries: StateFlow<List<SyncLogEntry>> = _entries.asStateFlow()

    fun debug(
        operation: String,
        entity: String? = null,
        id: String? = null,
        status: String? = null,
        durationMs: Long? = null,
        detail: String? = null
    ) = record(
        SyncLogEntry(System.currentTimeMillis(), SyncLogEntry.Level.DEBUG, operation, entity, id, status, durationMs, detail)
    )

    fun info(
        operation: String,
        entity: String? = null,
        id: String? = null,
        status: String? = null,
        durationMs: Long? = null,
        detail: String? = null
    ) = record(
        SyncLogEntry(System.currentTimeMillis(), SyncLogEntry.Level.INFO, operation, entity, id, status, durationMs, detail)
    )

    fun warn(
        operation: String,
        entity: String? = null,
        id: String? = null,
        status: String? = "warn",
        durationMs: Long? = null,
        detail: String? = null,
        throwable: Throwable? = null
    ) = record(
        SyncLogEntry(System.currentTimeMillis(), SyncLogEntry.Level.WARN, operation, entity, id, status, durationMs, detail, throwable)
    )

    fun error(
        operation: String,
        entity: String? = null,
        id: String? = null,
        status: String? = "failed",
        durationMs: Long? = null,
        detail: String? = null,
        throwable: Throwable? = null
    ) = record(
        SyncLogEntry(System.currentTimeMillis(), SyncLogEntry.Level.ERROR, operation, entity, id, status, durationMs, detail, throwable)
    )

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    private fun record(entry: SyncLogEntry) {
        synchronized(lock) {
            while (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
        val msg = entry.format()
        val t = entry.throwable
        when (entry.level) {
            SyncLogEntry.Level.DEBUG -> if (t != null) Log.d(PRISM_SYNC_TAG, msg, t) else Log.d(PRISM_SYNC_TAG, msg)
            SyncLogEntry.Level.INFO -> if (t != null) Log.i(PRISM_SYNC_TAG, msg, t) else Log.i(PRISM_SYNC_TAG, msg)
            SyncLogEntry.Level.WARN -> if (t != null) Log.w(PRISM_SYNC_TAG, msg, t) else Log.w(PRISM_SYNC_TAG, msg)
            SyncLogEntry.Level.ERROR -> if (t != null) Log.e(PRISM_SYNC_TAG, msg, t) else Log.e(PRISM_SYNC_TAG, msg)
        }
    }

    companion object {
        const val MAX_ENTRIES: Int = 200
    }
}
