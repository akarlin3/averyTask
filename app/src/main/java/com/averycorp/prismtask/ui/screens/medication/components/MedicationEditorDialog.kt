package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.domain.model.medication.MedicationTier

/**
 * Replacement for the old [MedDialog]. Operates on [MedicationTier] +
 * [MedicationSlotSelection] directly rather than the legacy
 * `SelfCareStepEntity` shape, so the create / edit path persists to
 * `medications` + `medication_medication_slots` + `medication_slot_overrides`
 * instead of the `self_care_steps` / `self_care_logs.tiers_by_time` path.
 */
@Composable
fun MedicationEditorDialog(
    title: String,
    initialName: String = "",
    initialTier: MedicationTier = MedicationTier.ESSENTIAL,
    initialNotes: String = "",
    initialSelections: List<MedicationSlotSelection> = emptyList(),
    activeSlots: List<MedicationSlotEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        tier: MedicationTier,
        notes: String,
        selections: List<MedicationSlotSelection>
    ) -> Unit,
    onCreateNewSlot: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var tier by remember { mutableStateOf(initialTier) }
    var notes by remember { mutableStateOf(initialNotes) }
    var selections by remember { mutableStateOf(initialSelections) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    placeholder = { Text("e.g. Lamotrigine 200mg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MedicationTierRadio(
                    selected = tier,
                    onSelected = { tier = it },
                    modifier = Modifier.fillMaxWidth()
                )
                MedicationSlotPicker(
                    activeSlots = activeSlots,
                    selections = selections,
                    onSelectionsChange = { selections = it },
                    onCreateNewSlot = onCreateNewSlot,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. take with food") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selections.isEmpty() && activeSlots.isNotEmpty()) {
                    Text(
                        text = "Pick at least one slot so the medication appears on the Today screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), tier, notes.trim(), selections) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
