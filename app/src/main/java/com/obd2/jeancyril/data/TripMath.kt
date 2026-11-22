package com.obd2.jeancyril.data

/**
 * Calculs purs de consommation : testables sur la JVM, sans Android.
 * Le débit massique d'air (MAF, g/s) est converti en L/h avec un rapport
 * stœchiométrique essence 14,7:1 et une densité de 745 g/L.
 */
object TripMath {
    fun fuelLitersPerHour(mafGramsPerSecond: Double): Double =
        mafGramsPerSecond * 3600.0 / (14.7 * 745.0)

    private val dieselPattern = Regex(
        "(\\bdci\\b|\\bhdi\\b|\\btdi\\b|\\bcdi\\b|\\btdci\\b|\\bjtd\\b|multijet|diesel|bluehdi|crdi|d4d|\\bd\\b)",
        RegexOption.IGNORE_CASE,
    )

    /** Le calcul MAF → L/100 utilise un rapport essence ; sur diesel il fausse la valeur. */
    fun looksDiesel(engine: String): Boolean = dieselPattern.containsMatchIn(engine)

    fun instantL100(speedKmh: Double, litersPerHour: Double?): Double? {
        if (litersPerHour == null || speedKmh < 15.0) return null
        return litersPerHour / speedKmh * 100.0
    }

    fun advance(distanceKm: Double, liters: Double, speedKmh: Double?, lph: Double?, dtSeconds: Double): Pair<Double, Double> {
        if (dtSeconds <= 0.0 || dtSeconds > 30.0) return distanceKm to liters
        val newDistance = distanceKm + (speedKmh ?: 0.0) / 3.6 * dtSeconds / 1000.0
        val newLiters = liters + (lph ?: 0.0) / 3600.0 * dtSeconds
        return newDistance to newLiters
    }

    /** Consommation entre deux pleins : litres du plein courant / distance depuis le précédent. */
    fun fullToFullL100(previous: FuelFill, current: FuelFill): Double? {
        val km = current.odometerKm - previous.odometerKm
        if (km <= 0 || current.liters <= 0.0) return null
        return current.liters / km * 100.0
    }

    /** Moyenne sur tous les pleins : litres ajoutés après le premier / distance totale. */
    fun averageFullToFull(fills: List<FuelFill>): Double? {
        if (fills.size < 2) return null
        val ordered = fills.sortedBy { it.odometerKm }
        val km = ordered.last().odometerKm - ordered.first().odometerKm
        val liters = ordered.drop(1).sumOf { it.liters }
        if (km <= 0 || liters <= 0.0) return null
        return liters / km * 100.0
    }
}
