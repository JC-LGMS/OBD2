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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.Dtc
import com.obd2.jeancyril.data.MonitorStatus
import android.content.Intent

@Composable
fun DtcScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val dtcs by vm.dtcs.collectAsState()
    val monitor by vm.monitor.collectAsState()
    val car by vm.activeCar.collectAsState()
    val connection by vm.connection.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val connected = connection.status == ConnStatus.CONNECTED || connection.status == ConnStatus.SIMULATOR
    val allEmpty = dtcs.stored.isEmpty() && dtcs.pending.isEmpty() && dtcs.permanent.isEmpty()

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MonitorCard(monitor) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::readCodes,
                    enabled = connected && !dtcs.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("Lire les codes") }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = connected && !dtcs.loading,
                ) { Text("Effacer") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::refreshMonitor, enabled = connected) { Text("Actualiser les moniteurs") }
                Text(
                    if (dtcs.lastReadAt > 0) "Lu à ${formatDate(dtcs.lastReadAt)}" else "Pas encore lu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val report = vm.buildReport()
                    if (report != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Rapport OBD2")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Partager le rapport"))
                    }
                },
                enabled = dtcs.lastReadAt > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Partager le rapport") }
        }
        if (dtcs.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (dtcs.lastReadAt == 0L) {
            item {
                EmptyHint(
                    "Connectez l'ELM327, contact mis, puis appuyez sur « Lire les codes ». La lecture interroge les codes mémorisés, en attente et permanents.",
                )
            }
        } else if (allEmpty) {
            item {
                EmptyHint("Aucun code défaut dans le calculateur. Le témoin moteur est éteint et les moniteurs sont propres.")
            }
        } else {
            if (dtcs.stored.isNotEmpty()) {
                item { SectionTitle("Codes mémorisés (${dtcs.stored.size})") }
                items(dtcs.stored, key = { "stored-${it.code}" }) { DtcCard(it) }
            }
            if (dtcs.pending.isNotEmpty()) {
                item { SectionTitle("Codes en attente (${dtcs.pending.size})") }
                items(dtcs.pending, key = { "pending-${it.code}" }) { DtcCard(it) }
            }
            if (dtcs.permanent.isNotEmpty()) {
                item { SectionTitle("Codes permanents (${dtcs.permanent.size})") }
                items(dtcs.permanent, key = { "permanent-${it.code}" }) { DtcCard(it) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Multi-marques", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (car?.brand?.isNotBlank() == true) {
                            "Explications adaptées à ${car?.brand} lorsque le code est documenté par le constructeur (Toyota, Citroën/Peugeot, Renault/Dacia, Volkswagen)."
                        } else {
                            "Sélectionnez un véhicule dans l'onglet Véhicule pour obtenir les explications propres à sa marque (Toyota, Citroën/Peugeot, Renault/Dacia, Volkswagen)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Les codes P0xxx affichés ici sont normalisés : ils sont valables sur Toyota, Citroën, Dacia, Renault, Peugeot, Volkswagen et la plupart des véhicules OBD-II. Les codes P1xxx/P2xxx/P3xxx sont propres au constructeur : l'explication générique peut ne pas correspondre à votre modèle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "L'effacement ne répare rien : il éteint le témoin jusqu'à ce que le défaut revienne. Notez les codes et les circonstances avant d'effacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Effacer les codes défauts ?",
            text = "Cette commande efface les codes mémorisés et les données de diagnostic adaptatif. Les codes permanents peuvent réapparaître tant que le défaut n'est pas réparé. Un rapport complet est enregistré dans le journal avant l'effacement.",
            confirmLabel = "Effacer",
            onConfirm = vm::clearCodes,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun MonitorCard(monitor: MonitorStatus) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (monitor.read && monitor.mil) "🔴" else "🟢", fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !monitor.read -> "Autodiagnostic non interrogé"
                            monitor.mil -> "Témoin moteur allumé"
                            else -> "Témoin moteur éteint"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (monitor.read) "${monitor.dtcCount} code(s) signalé(s) par l'UCE" else "Lire les moniteurs du calculateur (PID 0101)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (monitor.read && monitor.readiness.isNotEmpty()) {
                Text("Résultats de l'autodiagnostic", style = MaterialTheme.typography.labelLarge)
                monitor.readiness.forEach { (name, ready) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ready) "✅" else "⏳", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            if (ready) "prêt" else "non prêt",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ready) ObdColors.ok else ObdColors.warn,
                        )
                    }
                }
                Text(
                    "Un moniteur « non prêt » signifie que le calculateur n'a pas encore terminé son cycle de test après un effacement de codes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monitor.read && monitor.readiness.isEmpty()) {
                Text(
                    "Ce calculateur ne détaille pas ses moniteurs non continus.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DtcCard(dtc: Dtc) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dtc.code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dtc.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(dtc.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
