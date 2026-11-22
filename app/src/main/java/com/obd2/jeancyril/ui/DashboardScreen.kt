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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.Car
import com.obd2.jeancyril.data.MonitorStatus
import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.obd.Pid
import com.obd2.jeancyril.obd.Pids

@Composable
fun DashboardScreen(
    vm: ObdViewModel,
    modifier: Modifier = Modifier,
    onOpenCodes: () -> Unit,
    onOpenExtra: (String) -> Unit,
    onOpenGarage: () -> Unit,
) {
    val live by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()
    val cars by vm.cars.collectAsState()
    val car by vm.activeCar.collectAsState()
    val fuel by vm.fuel.collectAsState()
    val accel by vm.accel.collectAsState()
    val monitor by vm.monitor.collectAsState()

    var info by remember { mutableStateOf<Pid?>(null) }
    var customize by remember { mutableStateOf(false) }
    var choosingCar by remember { mutableStateOf(false) }
    val sensors = settings.dashboard.mapNotNull { Pids.byId(it) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { CarHeader(car) { choosingCar = true } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(
                    icon = "⛽",
                    label = "Conso instantanée",
                    value = fuel.instant?.let { "${formatValue(it, 1)} L/100" } ?: "—",
                    onClick = { onOpenExtra(Routes.FUEL) },
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    icon = "🏁",
                    label = "0–100 km/h",
                    value = accel.time0to100?.let { "${formatValue(it, 1)} s" } ?: "—",
                    onClick = { onOpenExtra(Routes.ACCEL) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SectionTitle("Tableau de bord") {
                TextButton(onClick = { customize = true }) { Text("Personnaliser") }
            }
        }
        if (sensors.isEmpty()) {
            item {
                EmptyHint("Aucun capteur sélectionné. Appuyer sur « Personnaliser » ou activer des capteurs dans l'onglet Capteurs.")
            }
        }
        items(sensors.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { pid ->
                    SensorTile(
                        pid = pid,
                        value = live.values[pid.id],
                        override = settings.overrides[pid.id],
                        onClick = { info = pid },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { MonitorSummary(monitor, onOpenCodes) }
        item {
            Text(
                "Valeurs lues en direct sur le calculateur via l'ELM327. Appuyer sur un indicateur pour son explication.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    info?.let { pid ->
        val override = settings.overrides[pid.id]
        SensorInfoDialog(
            pid = pid,
            override = override,
            onStyle = { vm.setSensorStyle(pid.id, it) },
            onDismiss = { info = null },
        )
    }

    if (customize) {
        AlertDialog(
            onDismissRequest = { customize = false },
            title = { Text("Capteurs du tableau de bord") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(Pids.all) { pid ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = pid.id in settings.dashboard,
                                onCheckedChange = { vm.toggleDashboard(pid.id) },
                            )
                            Text(pid.icon, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pid.name, style = MaterialTheme.typography.bodyMedium)
                                Text(pid.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (pid.id in settings.dashboard) {
                                TextButton(onClick = { vm.cycleSensorStyle(pid.id) }) {
                                    Text(
                                        SensorStyles.label(
                                            settings.overrides[pid.id]?.style ?: SensorStyles.defaultFor(pid.id),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { customize = false }) { Text("Terminé") } },
        )
    }

    if (choosingCar) {
        AlertDialog(
            onDismissRequest = { choosingCar = false },
            title = { Text("Véhicules") },
            text = {
                if (cars.isEmpty()) {
                    Text("Aucun véhicule. Créez-en un dans l'onglet Véhicule.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cars, key = { it.id }) { item ->
                            Card(
                                onClick = {
                                    vm.selectCar(item.id)
                                    choosingCar = false
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (item.id == car?.id) "✅" else "🚘", fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            item.plate.ifBlank { item.subtitle },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            vm.selectCar(item.id)
                                            choosingCar = false
                                            onOpenGarage()
                                        },
                                    ) { Text("Détails") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingCar = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun CarHeader(car: Car?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🚗", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(car?.title ?: "Aucun véhicule actif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    car?.let {
                        listOf(
                            it.subtitle,
                            it.plate.takeIf { plate -> plate.isNotBlank() }?.let { plate -> "Plaque $plate" },
                            it.vin.takeIf { vin -> vin.isNotBlank() }?.let { vin -> "VIN $vin" },
                        ).filterNotNull().joinToString(" · ")
                    } ?: "Créez un profil dans l'onglet Véhicule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonitorSummary(monitor: MonitorStatus, onOpenCodes: () -> Unit) {
    val ready = monitor.readiness.count { it.second }
    val total = monitor.readiness.size
    Card(
        onClick = onOpenCodes,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (monitor.read && monitor.mil) "🔴" else "🟢", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (monitor.read && monitor.mil) "Témoin moteur allumé" else "Témoin moteur éteint",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        !monitor.read -> "Moniteurs non interrogés — appuyer pour lire"
                        monitor.dtcCount > 0 -> "${monitor.dtcCount} code(s) signalé(s) · moniteurs $ready/$total prêts"
                        else -> "Aucun code signalé par l'UCE · moniteurs $ready/$total prêts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Codes ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}
