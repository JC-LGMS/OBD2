package com.obd2.jeancyril.obd

import com.obd2.jeancyril.data.MonitorStatus

/**
 * Analyse des réponses ELM327. Pur Kotlin : testé sur la JVM, sans Android.
 * Hypothèse : écho coupé (ATE0), espaces coupés (ATS0), en-têtes coupés (ATH0).
 * Les réponses CAN multi-trames restent acceptées (préfixes « 0: », « 1: »).
 */
object Protocol {
    fun bytes(response: String): List<Int> {
        val out = ArrayList<Int>()
        for (raw in response.uppercase().split('\n', '\r')) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            line = line.replace(Regex("^[0-9A-F]:"), "").trim()
            if (line.isEmpty() || !line.matches(Regex("[0-9A-F ]+"))) continue
            val hex = line.replace(" ", "")
            if (hex.length % 2 != 0) continue
            var i = 0
            while (i < hex.length) {
                out.add(hex.substring(i, i + 2).toInt(16))
                i += 2
            }
        }
        return out
    }

    fun payload(response: String, header: Int, pid: Int? = null): IntArray? {
        val b = bytes(response)
        if (pid == null) {
            val i = b.indexOf(header)
            if (i < 0) return null
            return b.subList(i + 1, b.size).toIntArray()
        }
        for (i in 0 until b.size - 1) {
            if (b[i] == header && b[i + 1] == pid) {
                return b.subList(i + 2, b.size).toIntArray()
            }
        }
        return null
    }

    fun dtcs(response: String, header: Int): List<String> {
        var data = payload(response, header) ?: return emptyList()
        // Anciens protocoles (ISO 9141/KWP) : premier octet = nombre de codes.
        if (data.size % 2 == 1 && data[0] == data.size / 2) {
            data = data.copyOfRange(1, data.size)
        }
        val codes = ArrayList<String>()
        var i = 0
        while (i + 1 < data.size) {
            if (data[i] != 0 || data[i + 1] != 0) {
                codes.add(Dtcs.decode(data[i], data[i + 1]))
            }
            i += 2
        }
        return codes.distinct()
    }

    fun monitor(response: String): MonitorStatus {
        val data = payload(response, 0x41, 0x01) ?: return MonitorStatus()
        if (data.size < 4) return MonitorStatus()
        val a = data[0] and 0xFF
        val b = data[1] and 0xFF
        val c = data[2] and 0xFF
        val d = data[3] and 0xFF
        val list = ArrayList<Pair<String, Boolean>>()
        fun add(name: String, availableBit: Int, incompleteBit: Int) {
            if (availableBit and 1 == 1) {
                list.add(name to (incompleteBit and 1 == 0))
            }
        }
        add("Ratés d'allumage", (b shr 0) and 1, (b shr 3) and 1)
        add("Système de carburant", (b shr 1) and 1, (b shr 4) and 1)
        add("Composants", (b shr 2) and 1, (b shr 5) and 1)
        add("Catalyseur", (c shr 0) and 1, (d shr 0) and 1)
        add("Catalyseur chauffé", (c shr 1) and 1, (d shr 1) and 1)
        add("Circuit EVAP", (c shr 2) and 1, (d shr 2) and 1)
        add("Air secondaire", (c shr 3) and 1, (d shr 3) and 1)
        add("Sonde O2", (c shr 4) and 1, (d shr 4) and 1)
        add("Chauffage sonde O2", (c shr 5) and 1, (d shr 5) and 1)
        add("Système EGR", (c shr 6) and 1, (d shr 6) and 1)
        return MonitorStatus(
            mil = a shr 7 and 1 == 1,
            dtcCount = a and 0x7F,
            readiness = list,
            read = true,
        )
    }

    fun vin(response: String): String? {
        val data = payload(response, 0x49, 0x02) ?: return null
        val text = StringBuilder()
        var i = 0
        while (i < data.size) {
            val v = data[i] and 0xFF
            if (v == 0x49 && i + 1 < data.size && data[i + 1] == 0x02) {
                i += 3
                continue
            }
            if (v in 0x20..0x7E) text.append(v.toChar())
            i++
        }
        val cleaned = text.toString().filter { it.isLetterOrDigit() }
        val run = Regex("[A-HJ-NPR-Z0-9]{11,17}").findAll(cleaned).maxByOrNull { it.value.length }?.value ?: return null
        return if (run.length == 17) run else null
    }

    fun supported(base: Int, data: IntArray): Set<String> {
        val result = HashSet<String>()
        for (pid in Pids.all) {
            val value = pid.id.substring(2).toInt(16)
            if (value in base + 1..base + 32) {
                val offset = value - base - 1
                val byte = if (offset / 8 < data.size) data[offset / 8] and 0xFF else 0
                if ((byte shr (7 - offset % 8)) and 1 == 1) result.add(pid.id)
            }
        }
        return result
    }
}
