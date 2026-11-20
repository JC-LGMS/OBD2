package com.obd2.jeancyril.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.ObdViewModel
import com.obd2.jeancyril.R
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.ConnectionState

object Routes {
    const val CONNECTION = "connexion"
    const val ACCEL = "accel"
    const val FUEL = "fuel"
    const val JOURNAL = "journal"
    const val BACKUP = "backup"
    const val CHANGELOG = "changelog"
}

private fun extraTitle(route: String): String = when (route) {
    Routes.CONNECTION -> "Connexion ELM327"
    Routes.ACCEL -> "Mesure d'accélération"
    Routes.FUEL -> "Consommation"
    Routes.JOURNAL -> "Journal des actions"
    Routes.BACKUP -> "Sauvegarde"
    Routes.CHANGELOG -> "Nouveautés"
    else -> "OBD2"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdApp(vm: ObdViewModel) {
    val connection by vm.connection.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1"
    }
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var extra by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = extra != null) { extra = null }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        vm.clearMessage()
        snackbar.showSnackbar(text)
    }

    val tabs = listOf("Tableau" to "📊", "Capteurs" to "🎛️", "Codes" to "🛠️", "Courbes" to "📈", "Véhicule" to "🚗")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (extra == null) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (extra != null) extraTitle(extra!!) else "OBD2",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (extra == null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "v$versionName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { extra = Routes.CHANGELOG }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (extra != null) {
                        TextButton(onClick = { extra = null }) { Text("‹", fontSize = 26.sp) }
                    }
                },
                actions = {
                    if (extra == null) PlugStatus(connection) { extra = Routes.CONNECTION }
                },
            )
        },
        bottomBar = {
            if (extra == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.second, fontSize = 18.sp) },
                            label = { Text(item.first, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (extra) {
            Routes.CONNECTION -> ConnectionScreen(vm, modifier)
            Routes.ACCEL -> AccelerationScreen(vm, modifier)
            Routes.FUEL -> FuelScreen(vm, modifier)
            Routes.JOURNAL -> JournalScreen(vm, modifier)
            Routes.BACKUP -> BackupScreen(vm, modifier)
            Routes.CHANGELOG -> ChangelogScreen(modifier)
            else -> when (tab) {
                0 -> DashboardScreen(vm, modifier, onOpenCodes = { tab = 2 }, onOpenExtra = { extra = it })
                1 -> SensorsScreen(vm, modifier)
                2 -> DtcScreen(vm, modifier)
                3 -> ChartsScreen(vm, modifier)
                else -> GarageScreen(vm, modifier, onOpenExtra = { extra = it })
            }
        }
    }
}

@Composable
private fun PlugStatus(connection: ConnectionState, onClick: () -> Unit) {
    val color = statusColor(connection.status)
    Column(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text("🔌", fontSize = 18.sp)
        }
        Text(
            when (connection.status) {
                ConnStatus.CONNECTED -> "Connexion OK"
                ConnStatus.SIMULATOR -> "Simulateur ELM327"
                ConnStatus.CONNECTING -> "Connexion..."
                ConnStatus.ERROR -> "Erreur"
                ConnStatus.DISCONNECTED -> "Se connecter..."
            },
            fontSize = 9.sp,
            color = color,
            maxLines = 1,
        )
    }
}
