package com.averycorp.prismtask.ui.components.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.remote.sync.SyncErrorSample
import com.averycorp.prismtask.ui.components.SyncState
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailsSheet(
    state: SyncState,
    lastSyncAt: Long,
    pendingCount: Int,
    recentErrors: List<SyncErrorSample>,
    onDismiss: () -> Unit,
    onForceSync: () -> Unit,
    onDismissErrors: () -> Unit
) {
    val colors = LocalPrismColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Sync Status",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )

            LabeledRow(label = "Current State", value = state.describe())
            LabeledRow(
                label = "Last Successful Sync",
                value = if (lastSyncAt > 0) formatTimestamp(lastSyncAt) else "Never"
            )
            LabeledRow(
                label = "Pending Changes",
                value = if (pendingCount == 0) "None" else "$pendingCount queued"
            )

            Divider(color = colors.border)

            Text(
                text = "Recent Errors",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            if (recentErrors.isEmpty()) {
                Text(
                    text = "No recent errors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            } else {
                recentErrors.forEach { sample ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatTimestamp(sample.timestampMs)} · ${sample.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted
                        )
                        Text(
                            text = sample.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground
                        )
                    }
                }
                TextButton(onClick = onDismissErrors) {
                    Text("Clear Errors", color = colors.primary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onForceSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Force Sync Now")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    val colors = LocalPrismColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun SyncState.describe(): String = when (this) {
    is SyncState.Synced -> "Synced"
    is SyncState.Syncing -> "Syncing…"
    is SyncState.Pending -> "$count pending"
    is SyncState.Offline -> if (count > 0) "Offline — $count queued" else "Offline"
    is SyncState.Error -> "Error: $message"
    is SyncState.NotSignedIn -> "Signed out"
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

internal fun formatTimestamp(ms: Long): String =
    if (ms <= 0) "never" else TIMESTAMP_FORMAT.format(Date(ms))
