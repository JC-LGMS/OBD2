package com.obd2.jeancyril.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.LogEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun bluetoothGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@Composable
fun ConnectionScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val connection by vm.connection.collectAsState()
    val context = LocalContext.current
    val adapter = remember { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    var granted by remember { mutableStateOf(bluetoothGranted(context)) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) error = "Sans autorisation Bluetooth, la liste des appareils reste inaccessible."
    }

    LaunchedEffect(granted, refresh) {
        if (adapter == null || !granted) {
            devices = emptyList()
            return@LaunchedEffect
        }
        runCatching { adapter.bondedDevices?.toList().orEmpty() }
            .onSuccess { list ->
                devices = list.sortedBy { device -> runCatching { device.name }.getOrNull().orEmpty().lowercase() }
                error = null
            }
            .onFailure { error = it.message ?: "Lecture des appareils impossible" }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConnectionBanner(connection) {}
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("État de la liaison", style = MaterialTheme.typography.titleSmall)
                connection.detail.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (adapter == null) {
            EmptyHint("Cet appareil n'expose pas de Bluetooth. Le simulateur permet de parcourir toutes les fonctions de l'application.")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !granted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Autorisation Bluetooth nécessaire", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Android 12 et plus exige l'autorisation « Appareils à proximité » pour lister et joindre l'ELM327.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                        Text("Autoriser le Bluetooth")
                    }
                }
            }
        } else {
            SectionTitle("Appareils associés")
            if (devices.isEmpty()) {
                EmptyHint("Aucun appareil associé. Associez d'abord l'ELM327 dans les réglages Bluetooth du téléphone (code PIN souvent 1234 ou 0000).")
            } else {
                devices.forEach { device ->
                    DeviceRow(device) { vm.connectBluetooth(device) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refresh++ }) { Text("Actualiser") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("Réglages Bluetooth") }
            }
        }

        Button(onClick = vm::connectSimulator, modifier = Modifier.fillMaxWidth()) {
            Text("Lancer le simulateur ELM327")
        }
        if (connection.status == ConnStatus.CONNECTED || connection.status == ConnStatus.SIMULATOR || connection.status == ConnStatus.CONNECTING) {
            OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Déconnecter")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Branchement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Brancher l'ELM327 sur la prise OBD-II (sous le volant, côté conducteur).\n" +
                        "2. Mettre le contact ; le moteur peut rester éteint pour lire les codes.\n" +
                        "3. Associer le dongle dans les réglages Bluetooth (PIN 1234 ou 0000).\n" +
                        "4. Revenir ici et toucher le nom du dongle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Un dongle bas de gamme peut ne pas exposer tous les capteurs. Les codes constructeur (P1xxx…) restent lisibles, mais leur traduction dépend du modèle : l'application affiche alors une explication générique.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Cette version gère le Bluetooth classique (SPP) et le BLE des ELM327 v2.1 (service FFE0/FFE1). Le type de liaison réellement utilisé apparaît dans l'état de connexion.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothDevice, onConnect: () -> Unit) {
    val name = remember(device) { runCatching { device.name }.getOrNull() ?: "Appareil sans nom" }
    Card(
        onClick = onConnect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📶", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(device.address)
                        when (device.type) {
                            BluetoothDevice.DEVICE_TYPE_LE -> append(" · BLE")
                            BluetoothDevice.DEVICE_TYPE_CLASSIC -> append(" · Classique")
                            BluetoothDevice.DEVICE_TYPE_DUAL -> append(" · Classique / BLE")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Connecter", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AccelerationScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val accel by vm.accel.collectAsState()
    val connection by vm.connection.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val connected = connection.status == ConnStatus.CONNECTED || connection.status == ConnStatus.SIMULATOR

    LaunchedEffect(accel.running) {
        while (accel.running) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    val elapsed = accel.startedAt?.let { (now - it) / 1000.0 }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Vitesse actuelle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatValue(accel.currentSpeed, 0),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("km/h", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatColumn("Chrono", elapsed?.let { "${formatValue(it, 1)} s" } ?: accel.time0to100?.let { "${formatValue(it, 1)} s" } ?: "—")
                    StatColumn("Accélération", "${formatValue(accel.currentG, 2)} g")
                    StatColumn("Maximum", "${formatValue(accel.maxG, 2)} g")
                }
            }
        }
        Text(
            when {
                !connected -> "Connectez l'ELM327 ou lancez le simulateur."
                accel.running && accel.startedAt == null && accel.waitingForStop -> "En attente de l'arrêt du véhicule pour démarrer le chrono…"
                accel.running && accel.startedAt != null -> "Mesure en cours depuis l'arrêt…"
                accel.time0to100 != null -> "Dernier 0–100 km/h : ${formatValue(accel.time0to100, 1)} s"
                else -> "Prêt pour une mesure."
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startAccel, enabled = connected && !accel.running, modifier = Modifier.weight(1f)) {
                Text("Démarrer")
            }
            OutlinedButton(onClick = vm::stopAccel, enabled = accel.running, modifier = Modifier.weight(1f)) {
                Text("Arrêter")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Comment la mesure fonctionne", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Le chrono part automatiquement quand la vitesse lue sur le calculateur passe sous 1,5 km/h, puis s'arrête à 100 km/h. L'accélération en g est calculée à partir de la variation de vitesse (Δv/Δt).\n\nUtilisez une route fermée ou un circuit, jamais la voie publique, et restez maître du véhicule. La précision dépend de la fréquence de lecture de l'ELM327.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FuelScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val fuel by vm.fuel.collectAsState()
    val connection by vm.connection.collectAsState()
    val connected = connection.status == ConnStatus.CONNECTED || connection.status == ConnStatus.SIMULATOR

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn("Instantanée", fuel.instant?.let { "${formatValue(it, 1)} L/100" } ?: "—")
                    StatColumn("Moyenne", fuel.average?.let { "${formatValue(it, 1)} L/100" } ?: "—")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn("Distance", "${formatValue(fuel.distanceKm, 1)} km")
                    StatColumn("Carburant", "${formatValue(fuel.liters, 2)} L")
                }
            }
        }
        if (!connected) {
            EmptyHint("Connectez l'ELM327 pour calculer la consommation à partir des données du calculateur.")
        }
        Button(onClick = vm::resetTrip, modifier = Modifier.fillMaxWidth()) {
            Text("Remettre les statistiques à zéro")
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Calcul", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "La consommation est déduite du débit d'air (MAF) et d'un rapport stœchiométrique essence de 14,7:1 (masse volumique 745 g/L). Si le calculateur expose le PID 015E, c'est lui qui est utilisé.\n\nLa vitesse provient du PID 010D : sur certains véhicules, elle n'est pas disponible au ralenti. Les résultats sont indicatifs et ne remplacent pas un calcul de plein à plein. Les hybrides et les moteurs à injection directe peuvent s'écarter du modèle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun JournalScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val car by vm.activeCar.collectAsState()
    var addingNote by remember { mutableStateOf(false) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val current = car
            if (current == null) {
                EmptyHint("Créez d'abord un véhicule dans l'onglet Véhicule : le journal lui sera associé.")
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(current.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${current.log.size} action(s) enregistrée(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { addingNote = true }) { Text("＋ Note") }
                    }
                }
            }
        }
        val entries = car?.log?.sortedByDescending { it.at }.orEmpty()
        if (entries.isEmpty() && car != null) {
            item { EmptyHint("Aucune action enregistrée. Connexions, lectures de codes, effacements, mesures et notes apparaîtront ici.") }
        }
        items(entries) { entry -> LogRow(entry) }
        item {
            Text(
                "Le journal est conservé par véhicule, sur le téléphone, et inclus dans la sauvegarde.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (addingNote) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingNote = false },
            title = { Text("Ajouter une note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Observation, réparation, plein…") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addNote(text)
                        addingNote = false
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { addingNote = false }) { Text("Annuler") } },
        )
    }
}

private fun logIcon(kind: String): String = when (kind) {
    "connexion" -> "🔌"
    "codes" -> "🛠️"
    "effacement" -> "🧹"
    "mesure" -> "🏁"
    "consommation" -> "⛽"
    "véhicule" -> "🚗"
    "photos" -> "📷"
    "note" -> "📝"
    "sauvegarde" -> "💾"
    "erreur" -> "⚠️"
    else -> "ℹ️"
}

@Composable
private fun LogRow(entry: LogEntry) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(logIcon(entry.kind), fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatDate(entry.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BackupScreen(vm: ObdViewModel, modifier: Modifier = Modifier) {
    val cars by vm.cars.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val photos = cars.sumOf { it.images.size }
    val actions = cars.sumOf { it.log.size }
    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(Date()) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${cars.size} véhicule(s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$photos photo(s) · $actions action(s) de journal · réglages du tableau de bord",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { exportLauncher.launch("ODB2-sauvegarde-$stamp.zip") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exporter la sauvegarde") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restaurer une sauvegarde") }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Contenu et précautions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "L'archive ZIP contient les fiches véhicules, leurs photos, le journal des actions et les réglages. Elle est écrite à l'emplacement que vous choisissez (Stockage, Drive, clé USB…).\n\nLa restauration remplace les véhicules, le journal et les réglages actuels, puis réinstalle les photos. Enregistrez la sauvegarde hors du téléphone : elle survit à une désinstallation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
