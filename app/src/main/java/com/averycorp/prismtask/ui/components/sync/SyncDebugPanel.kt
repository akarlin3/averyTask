package com.averycorp.prismtask.ui.components.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.remote.sync.SyncLogEntry
import com.averycorp.prismtask.ui.theme.LocalPrismColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDebugPanel(
    viewModel: SyncIndicatorViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val colors = LocalPrismColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listenersActive by viewModel.listenersActive.collectAsStateWithLifecycle()
    val listenerSnapshots by viewModel.listenerSnapshots.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val pendingEntries by viewModel.pendingEntries.collectAsStateWithLifecycle()
    val tokenExpiresAt by viewModel.backendTokenExpiresAt.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.syncStateRepository.isSignedIn.collectAsStateWithLifecycle()
    val isOnline by viewModel.syncStateRepository.isOnline.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Sync Debug Panel",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Debug builds only · Week 1 multi-device testing",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted
            )

            if (actionMessage != null) {
                Text(
                    text = actionMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
                TextButton(onClick = viewModel::consumeActionMessage) {
                    Text("Dismiss", color = colors.muted)
                }
            }

            SectionHeader("Ambient State")
            DebugRow("Signed In", if (isSignedIn) "yes" else "no")
            DebugRow("Online", if (isOnline) "yes" else "no")
            DebugRow(
                "Backend JWT Expiry",
                tokenExpiresAt?.let { formatTimestamp(it) } ?: "unknown"
            )

            SectionHeader("Firestore Listeners")
            DebugRow(
                label = "Listeners",
                value = if (listenersActive) "active (${listenerSnapshots.size} cols)" else "inactive"
            )
            if (listenerSnapshots.isEmpty()) {
                Text(
                    text = "No snapshots received yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            } else {
                listenerSnapshots.entries
                    .sortedByDescending { it.value }
                    .forEach { (collection, ts) ->
                        DebugRow(label = collection, value = formatTimestamp(ts))
                    }
            }

            SectionHeader("Pending Queue (${pendingEntries.size})")
            if (pendingEntries.isEmpty()) {
                Text(
                    text = "No pending entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            } else {
                pendingEntries.take(20).forEach { entry -> PendingEntryRow(entry) }
                if (pendingEntries.size > 20) {
                    Text(
                        text = "… ${pendingEntries.size - 20} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted
                    )
                }
            }

            SectionHeader("Recent Sync Log (last ${minOf(50, logEntries.size)})")
            LogList(entries = logEntries.takeLast(50).reversed())

            Divider(color = colors.border)

            SectionHeader("Manual Actions")
            Button(
                onClick = viewModel::forceSync,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Force Full Sync")
            }
            OutlinedButton(
                onClick = viewModel::clearOfflineQueue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Offline Queue")
            }
            Button(
                onClick = viewModel::resetSyncState,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.urgentAccent)
            ) {
                Text("Reset Sync State (Wipe Metadata)")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = colors.muted)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalPrismColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = colors.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DebugRow(label: String, value: String) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PendingEntryRow(entry: SyncMetadataEntity) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${entry.entityType}:${entry.localId}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "${entry.pendingAction ?: "dead"} · retry=${entry.retryCount}",
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.retryCount >= 5) colors.urgentAccent else colors.muted,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LogList(entries: List<SyncLogEntry>) {
    val colors = LocalPrismColors.current
    if (entries.isEmpty()) {
        Text(
            text = "No log entries yet.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(entries) { entry ->
                val tint = when (entry.level) {
                    SyncLogEntry.Level.ERROR -> colors.urgentAccent
                    SyncLogEntry.Level.WARN -> colors.secondary
                    SyncLogEntry.Level.INFO -> colors.primary
                    SyncLogEntry.Level.DEBUG -> colors.muted
                }
                Text(
                    text = "${formatTimestamp(entry.timestampMs)}  ${entry.format()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
