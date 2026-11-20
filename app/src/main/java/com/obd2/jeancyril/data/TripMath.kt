package com.obd2.jeancyril.data

/**
 * Calculs purs de consommation : testables sur la JVM, sans Android.
 * Le débit massique d'air (MAF, g/s) est converti en L/h avec un rapport
 * stœchiométrique essence 14,7:1 et une densité de 745 g/L.
 */
object TripMath {
    fun fuelLitersPerHour(mafGramsPerSecond: Double): Double =
        mafGramsPerSecond * 3600.0 / (14.7 * 745.0)

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
}
