package com.obd2.jeancyril.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.SensorOverride
import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.obd.Pid
import com.obd2.jeancyril.obd.Pids

@Composable
fun SensorsScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val values by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()
    val supported by vm.supported.collectAsState()
    val connection by vm.connection.collectAsState()
    var editing by remember { mutableStateOf<Pid?>(null) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tous les capteurs OBD-II", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Basculez un capteur pour l'afficher au tableau de bord. ✏️ permet de le renommer et d'ajuster sa plage. Les explications restent accessibles dans chaque fiche.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = vm::refreshSupported,
                            enabled = connection.status != ConnStatus.DISCONNECTED && connection.status != ConnStatus.ERROR,
                        ) { Text("Interroger l'ELM327") }
                        Text(
                            supported?.let { "${it.size} capteurs supportés" } ?: "support non encore interrogé",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(Pids.all) { pid ->
            val override = settings.overrides[pid.id]
            val isSupported = supported?.contains(pid.id)
            SensorRow(
                pid = pid,
                override = override,
                value = values.values[pid.id],
                inDashboard = pid.id in settings.dashboard,
                isSupported = isSupported,
                onToggle = { vm.toggleDashboard(pid.id) },
                onEdit = { editing = pid },
            )
        }
        item {
            Text(
                "L'ensemble des PID normalisés est interrogé. Un capteur absent du véhicule répond « non supporté » et n'affiche pas de valeur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { pid ->
        SensorEditDialog(
            pid = pid,
            override = settings.overrides[pid.id],
            onDismiss = { editing = null },
            onSave = { override ->
                vm.saveSensorOverride(pid.id, override)
                editing = null
            },
            onReset = {
                vm.saveSensorOverride(pid.id, null)
                editing = null
            },
        )
    }
}

@Composable
private fun SensorRow(
    pid: Pid,
    override: SensorOverride?,
    value: Double?,
    inDashboard: Boolean,
    isSupported: Boolean?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pid.icon, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pid.displayLabel(override),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSupported == false) {
                        Spacer(Modifier.width(6.dp))
                        Text("non supporté", style = MaterialTheme.typography.labelSmall, color = ObdColors.warn)
                    }
                }
                Text(
                    pid.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatValue(value, pid.decimals)} ${pid.displayUnit(override)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = inDashboard, onCheckedChange = { onToggle() })
                TextButton(onClick = onEdit) { Text("✏️") }
            }
        }
    }
}

@Composable
private fun SensorEditDialog(
    pid: Pid,
    override: SensorOverride?,
    onDismiss: () -> Unit,
    onSave: (SensorOverride) -> Unit,
    onReset: () -> Unit,
) {
    var label by remember(pid) { mutableStateOf(override?.label ?: "") }
    var unit by remember(pid) { mutableStateOf(override?.unit ?: "") }
    var min by remember(pid) { mutableStateOf(override?.min?.toString() ?: "") }
    var max by remember(pid) { mutableStateOf(override?.max?.toString() ?: "") }
    var style by remember(pid) { mutableStateOf(override?.style ?: SensorStyles.defaultFor(pid.id)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pid.icon} ${pid.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(pid.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nom affiché") },
                    placeholder = { Text(pid.name) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unité") },
                    placeholder = { Text(pid.unit) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it },
                        label = { Text("Minimum") },
                        placeholder = { Text(pid.min.toString()) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = { max = it },
                        label = { Text("Maximum") },
                        placeholder = { Text(pid.max.toString()) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Commande OBD-II : ${pid.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Affichage au tableau de bord", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SensorStyles.order.forEach { item ->
                        FilterChip(
                            selected = style == item,
                            onClick = { style = item },
                            label = { Text(SensorStyles.label(item)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    SensorOverride(
                        label = label.trim().takeIf { it.isNotBlank() },
                        unit = unit.trim().takeIf { it.isNotBlank() },
                        min = min.toDoubleOrNull(),
                        max = max.toDoubleOrNull(),
                        style = style,
                    ),
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Réinitialiser") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
