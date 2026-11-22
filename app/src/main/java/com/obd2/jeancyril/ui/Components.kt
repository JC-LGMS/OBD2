package com.obd2.jeancyril.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.ConnectionState
import com.obd2.jeancyril.data.Sample
import com.obd2.jeancyril.data.SensorOverride
import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.obd.Pid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatValue(value: Double?, decimals: Int): String =
    if (value == null || value.isNaN()) "—" else String.format(Locale.FRANCE, "%.${decimals}f", value)

fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ms))

fun Pid.displayLabel(override: SensorOverride?): String =
    override?.label?.takeIf { it.isNotBlank() } ?: name

fun Pid.displayUnit(override: SensorOverride?): String =
    override?.unit?.takeIf { it.isNotBlank() } ?: unit

fun Pid.displayMin(override: SensorOverride?): Double = override?.min ?: min

fun Pid.displayMax(override: SensorOverride?): Double = override?.max ?: max

fun statusColor(status: ConnStatus): Color = when (status) {
    ConnStatus.CONNECTED -> ObdColors.ok
    ConnStatus.SIMULATOR -> ObdColors.accent
    ConnStatus.CONNECTING -> ObdColors.warn
    ConnStatus.ERROR -> ObdColors.bad
    ConnStatus.DISCONNECTED -> ObdColors.idle
}

fun statusTitle(connection: ConnectionState): String = when (connection.status) {
    ConnStatus.CONNECTED -> "ELM327 connecté"
    ConnStatus.SIMULATOR -> "Mode simulateur"
    ConnStatus.CONNECTING -> "Connexion…"
    ConnStatus.ERROR -> "Erreur de connexion"
    ConnStatus.DISCONNECTED -> "Non connecté"
}

@Composable
fun ConnectionBanner(connection: ConnectionState, onClick: () -> Unit) {
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
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor(connection.status)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(statusTitle(connection), style = MaterialTheme.typography.titleSmall)
                Text(
                    connection.name.ifBlank {
                        "Appuyer pour connecter l'ELM327 ou utiliser le simulateur"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Connecter ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GaugeTile(
    pid: Pid,
    value: Double?,
    override: SensorOverride?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val min = pid.displayMin(override)
    val max = pid.displayMax(override)
    val fraction = tileFraction(value, min, max)
    val color = tileColor(value, fraction)
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TileHeader(pid, override)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatValue(value, pid.decimals),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    pid.displayUnit(override),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private fun tileFraction(value: Double?, min: Double, max: Double): Float =
    value?.let { ((it - min) / (max - min).coerceAtLeast(0.001)).coerceIn(0.0, 1.0).toFloat() } ?: 0f

private fun tileColor(value: Double?, fraction: Float): Color = when {
    value == null -> ObdColors.idle
    fraction > 0.9f -> ObdColors.bad
    fraction > 0.72f -> ObdColors.warn
    else -> ObdColors.ok
}

@Composable
private fun TileHeader(pid: Pid, override: SensorOverride?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pid.icon, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            pid.displayLabel(override),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tuile « chiffres » : valeur seule, sans jauge. */
@Composable
fun DigitalTile(
    pid: Pid,
    value: Double?,
    override: SensorOverride?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileHeader(pid, override)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatValue(value, pid.decimals), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(
                    pid.displayUnit(override),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** Tuile « cadran » : arc gradué et aiguille, adaptée au compte-tours et à la vitesse. */
@Composable
fun DialTile(
    pid: Pid,
    value: Double?,
    override: SensorOverride?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val min = pid.displayMin(override)
    val max = pid.displayMax(override)
    val fraction = tileFraction(value, min, max)
    val color = tileColor(value, fraction)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TileHeader(pid, override)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(118.dp).padding(6.dp)) {
                    val strokeWidth = 9.dp.toPx()
                    val inset = strokeWidth / 2f + 2.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f,
                    )
                    drawArc(
                        color = track,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = 270f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    val angle = Math.toRadians((135.0 + 270.0 * fraction))
                    val radius = size.minDimension / 2f - inset - strokeWidth
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawLine(
                        color = color,
                        start = center,
                        end = Offset(
                            center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                            center.y + (kotlin.math.sin(angle) * radius).toFloat(),
                        ),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color, radius = 4f, center = center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatValue(value, pid.decimals),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        pid.displayUnit(override),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SensorTile(
    pid: Pid,
    value: Double?,
    override: SensorOverride?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (override?.style ?: SensorStyles.defaultFor(pid.id)) {
        SensorStyles.BAR -> GaugeTile(pid, value, override, onClick, modifier)
        SensorStyles.DIAL -> DialTile(pid, value, override, onClick, modifier)
        else -> DigitalTile(pid, value, override, onClick, modifier)
    }
}

@Composable
fun SensorInfoDialog(
    pid: Pid,
    override: SensorOverride?,
    onStyle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pid.icon} ${pid.displayLabel(override)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${pid.description}\n\nUnité : ${pid.displayUnit(override)}\nPlage : ${formatValue(pid.displayMin(override), pid.decimals)} à ${formatValue(pid.displayMax(override), pid.decimals)} ${pid.displayUnit(override)}\nCommande OBD-II : ${pid.id}",
                )
                Text("Affichage au tableau de bord", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val current = override?.style ?: SensorStyles.defaultFor(pid.id)
                    SensorStyles.order.forEach { style ->
                        FilterChip(
                            selected = current == style,
                            onClick = { onStyle(style) },
                            label = { Text(SensorStyles.label(style)) },
                        )
                    }
                }
                Text(
                    "Chiffres : valeur seule. Jauge : barre de progression. Cadran : aiguille circulaire, adaptée au compte-tours et à la vitesse.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}


@Composable
fun MiniStat(
    icon: String,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
}

@Composable
fun EmptyHint(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LineChart(
    samples: List<Sample>,
    minValue: Double,
    maxValue: Double,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier) {
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (samples.size < 2) return@Canvas
        val first = samples.first().at
        val span = (samples.last().at - first).coerceAtLeast(1L)
        val range = (maxValue - minValue).coerceAtLeast(0.001)
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = ((sample.at - first).toFloat() / span) * size.width
            val normalized = ((sample.value - minValue) / range).coerceIn(0.0, 1.0).toFloat()
            val y = size.height - normalized * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun LocalImage(file: File?, modifier: Modifier = Modifier, description: String? = null) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, file?.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val source = file?.takeIf { it.exists() } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 800 || bounds.outHeight / (sample * 2) >= 800) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(source.absolutePath, options)?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
