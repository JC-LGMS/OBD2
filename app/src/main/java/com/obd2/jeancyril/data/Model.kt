package com.obd2.jeancyril.data

import java.util.UUID

data class LogEntry(
    val at: Long = System.currentTimeMillis(),
    val kind: String = "info",
    val text: String,
)

data class Car(
    val id: String = UUID.randomUUID().toString(),
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val engine: String = "",
    val vin: String = "",
    val plate: String = "",
    val images: List<String> = emptyList(),
    val log: List<LogEntry> = emptyList(),
) {
    val title: String
        get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Véhicule" }

    val subtitle: String
        get() = listOf(year, engine).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Profil sans détail" }
}

data class SensorOverride(
    val label: String? = null,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val style: String? = null,
)

object SensorStyles {
    const val DIGITAL = "chiffres"
    const val BAR = "jauge"
    const val DIAL = "cadran"
    val order = listOf(DIGITAL, BAR, DIAL)

    fun label(style: String?): String = when (style) {
        BAR -> "Jauge"
        DIAL -> "Cadran"
        else -> "Chiffres"
    }

    fun defaultFor(pid: String): String = when (pid) {
        "010C", "010D" -> DIAL
        "0105", "0104", "0111", "012F" -> BAR
        else -> DIGITAL
    }

    fun next(current: String?): String {
        val index = order.indexOf(current).takeIf { it >= 0 } ?: 0
        return order[(index + 1) % order.size]
    }
}

data class Settings(
    val dashboard: Set<String> = DEFAULT_DASHBOARD,
    val activeCar: String? = null,
    val overrides: Map<String, SensorOverride> = emptyMap(),
    val lightTheme: Boolean = false,
) {
    companion object {
        val DEFAULT_DASHBOARD = setOf("010C", "010D", "0105", "0104", "0111", "012F")
    }
}

data class Sample(val at: Long, val value: Double)

data class Dtc(
    val code: String,
    val description: String,
    val type: String,
)

data class MonitorStatus(
    val mil: Boolean = false,
    val dtcCount: Int = 0,
    val readiness: List<Pair<String, Boolean>> = emptyList(),
    val read: Boolean = false,
)

enum class ConnStatus { DISCONNECTED, CONNECTING, CONNECTED, SIMULATOR, ERROR }

data class ConnectionState(
    val status: ConnStatus = ConnStatus.DISCONNECTED,
    val name: String = "",
    val detail: String = "",
)

data class LiveState(
    val values: Map<String, Double> = emptyMap(),
    val history: Map<String, List<Sample>> = emptyMap(),
    val updatedAt: Long = 0L,
)

data class FuelState(
    val instant: Double? = null,
    val average: Double? = null,
    val distanceKm: Double = 0.0,
    val liters: Double = 0.0,
)

data class AccelState(
    val running: Boolean = false,
    val waitingForStop: Boolean = false,
    val time0to100: Double? = null,
    val currentSpeed: Double = 0.0,
    val currentG: Double = 0.0,
    val maxG: Double = 0.0,
    val startedAt: Long? = null,
)

data class DtcState(
    val stored: List<Dtc> = emptyList(),
    val pending: List<Dtc> = emptyList(),
    val permanent: List<Dtc> = emptyList(),
    val loading: Boolean = false,
    val lastReadAt: Long = 0L,
)

object Brands {
    val known = listOf("Toyota", "Citroën", "Dacia", "Renault", "Peugeot", "Volkswagen")
}
