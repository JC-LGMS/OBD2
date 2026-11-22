package com.obd2.jeancyril.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.data.Brands
import com.obd2.jeancyril.data.Car
import com.obd2.jeancyril.data.ConnStatus

@Composable
fun GarageScreen(vm: ObdViewModel, modifier: Modifier = Modifier, onOpenExtra: (String) -> Unit) {
    val cars by vm.cars.collectAsState()
    val active by vm.activeCar.collectAsState()
    val settings by vm.settings.collectAsState()
    val connection by vm.connection.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Car?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addCarImages(uris)
    }
    val connected = connection.status == ConnStatus.CONNECTED || connection.status == ConnStatus.SIMULATOR
    val car = active

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (car == null) {
                EmptyHint("Aucun véhicule actif. Créez un profil pour y associer les photos, le journal des actions et les mesures.")
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚗", fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(car.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(car.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (car.vin.isNotBlank()) {
                                    Text("VIN ${car.vin}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (car.plate.isNotBlank()) {
                                    Text("Plaque ${car.plate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                                Text("＋ Photos")
                            }
                            TextButton(
                                onClick = { vm.readVin { vin -> if (!vin.isNullOrBlank()) vm.updateCarDetails(car.id, car.brand, car.model, car.year, car.engine, vin, car.plate) } },
                                enabled = connected,
                            ) { Text("Lire le VIN") }
                            TextButton(onClick = { editing = car }) { Text("Modifier") }
                            TextButton(onClick = { confirmDelete = true }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                        }
                        if (car.images.isEmpty()) {
                            Text(
                                "Aucune photo. Ajoutez-en plusieurs : carte grise, compteur, état de la carrosserie…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(car.images) { name ->
                                    LocalImage(
                                        file = vm.imageFile(name),
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewing = name },
                                        description = "Photo du véhicule",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Mes véhicules") {
                TextButton(onClick = { adding = true }) { Text("＋ Ajouter") }
            }
        }
        if (cars.isEmpty()) {
            item { EmptyHint("Aucun profil. « Ajouter » crée une fiche : marque, modèle, année, moteur, VIN.") }
        } else {
            items(cars, key = { it.id }) { car ->
                Card(
                    onClick = { vm.selectCar(car.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (car.id == active?.id) "✅" else "🚘", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(car.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(
                                    car.subtitle,
                                    car.plate.takeIf { it.isNotBlank() }?.let { "Plaque $it" },
                                    "${car.logCount} action(s) · ${car.images.size} photo(s)",
                                ).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { editing = car }) { Text("Modifier") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌗", fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Thème", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (settings.themeMode) {
                                        "light" -> "Clair"
                                        "system" -> "Suit le système"
                                        else -> "Sombre"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("dark" to "Sombre", "light" to "Clair", "system" to "Système").forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToolRow("📝", "Journal des actions", "Connexions, lectures, effacements, mesures, notes") { onOpenExtra(Routes.JOURNAL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToolRow("🏁", "Mesure d'accélération", "Chronomètre 0–100 km/h et accélération en g") { onOpenExtra(Routes.ACCEL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToolRow("⛽", "Statistiques de consommation", "Consommation instantanée et moyenne, distance") { onOpenExtra(Routes.FUEL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToolRow("💾", "Sauvegarde et restauration", "Exporter ou restaurer véhicules, photos et journal") { onOpenExtra(Routes.BACKUP) }
                }
            }
        }
        item {
            Text(
                "Le profil actif reçoit automatiquement les actions et les photos. Les données restent sur le téléphone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (adding) {
        CarEditDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { brand, model, year, engine, vin, plate ->
                vm.addCar(brand, model, year, engine, vin, plate)
                adding = false
            },
        )
    }
    editing?.let { car ->
        CarEditDialog(
            initial = car,
            onDismiss = { editing = null },
            onSave = { brand, model, year, engine, vin, plate ->
                vm.updateCarDetails(car.id, brand, model, year, engine, vin, plate)
                editing = null
            },
        )
    }
    viewing?.let { name ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("Photo du véhicule") },
            text = {
                LocalImage(
                    file = vm.imageFile(name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    description = "Photo du véhicule",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCarImage(name)
                    viewing = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text("Fermer") } },
        )
    }
    if (confirmDelete && car != null) {
        ConfirmDialog(
            title = "Supprimer ${car.title} ?",
            text = "La fiche, ses photos et son journal seront supprimés définitivement. Les autres véhicules sont conservés.",
            confirmLabel = "Supprimer",
            onConfirm = { vm.deleteCar(car.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ToolRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CarEditDialog(
    initial: Car?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var year by remember { mutableStateOf(initial?.year.orEmpty()) }
    var engine by remember { mutableStateOf(initial?.engine.orEmpty()) }
    var vin by remember { mutableStateOf(initial?.vin.orEmpty()) }
    var plate by remember { mutableStateOf(initial?.plate.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau véhicule" else "Modifier le véhicule") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Marque", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Brands.known) { item ->
                        FilterChip(
                            selected = brand == item,
                            onClick = { brand = item },
                            label = { Text(item) },
                        )
                    }
                }
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marque") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modèle") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Année") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = engine,
                    onValueChange = { engine = it },
                    label = { Text("Motorisation (ex. 1.5 dCi 95)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it.uppercase() },
                    label = { Text("VIN (17 caractères)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Plaque d'immatriculation") },
                    singleLine = true,
                )
                Text(
                    "Le VIN peut être lu automatiquement sur l'ELM327 depuis la fiche du véhicule.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(brand, model, year, engine, vin, plate) },
                enabled = brand.isNotBlank() || model.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
