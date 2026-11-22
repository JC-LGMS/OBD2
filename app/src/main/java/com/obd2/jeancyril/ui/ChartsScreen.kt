package com.obd2.jeancyril.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.Settings
import com.obd2.jeancyril.obd.Pid
import com.obd2.jeancyril.obd.Pids

@Composable
fun ChartsScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val live by vm.live.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val sensors = settings.dashboard.mapNotNull { Pids.byId(it) }
        .ifEmpty { Settings.DEFAULT_DASHBOARD.mapNotNull { Pids.byId(it) } }
    val current = sensors.firstOrNull { it.id == selectedId } ?: sensors.firstOrNull()
    val override = current?.let { settings.overrides[it.id] }
    val samples = current?.let { vm.history(it.id) }.orEmpty()

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Courbes temporelles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sensors) { pid ->
                    FilterChip(
                        selected = current?.id == pid.id,
                        onClick = { selectedId = pid.id },
                        label = {
                            Text(
                                "${pid.icon} ${pid.displayLabel(settings.overrides[pid.id])}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        }
        if (current == null) {
            item { EmptyHint("Activez au moins un capteur dans l'onglet Capteurs pour tracer une courbe.") }
        } else {
            item { ChartCard(current, override, samples, live.values[current.id]) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        "Chaque point est une lecture réelle du calculateur. L'échelle verticale suit la plage du capteur ; la fenêtre glissante conserve les 300 dernières mesures (plusieurs minutes selon le nombre de capteurs interrogés).",
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    pid: Pid,
    override: com.obd2.jeancyril.data.SensorOverride?,
    samples: List<com.obd2.jeancyril.data.Sample>,
    value: Double?,
) {
    val min = pid.displayMin(override)
    val max = pid.displayMax(override)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pid.icon, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pid.displayLabel(override), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pid.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatValue(value, pid.decimals), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    pid.displayUnit(override),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            LineChart(
                samples = samples,
                minValue = min,
                maxValue = max,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${formatValue(min, pid.decimals)} ${pid.displayUnit(override)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatValue(max, pid.decimals)} ${pid.displayUnit(override)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (samples.size < 2) {
                Text(
                    "En attente de mesures…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
