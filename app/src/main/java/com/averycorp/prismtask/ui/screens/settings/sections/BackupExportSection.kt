package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRow

@Composable
fun BackupExportSection(
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: () -> Unit
) {
    SectionHeader("Backup & Export")

    SettingsRow(title = "Export as JSON", onClick = onExportJson)
    SettingsRow(title = "Export as CSV", onClick = onExportCsv)
    SettingsRow(title = "Import from JSON", onClick = onImportJson)

    HorizontalDivider()
}
