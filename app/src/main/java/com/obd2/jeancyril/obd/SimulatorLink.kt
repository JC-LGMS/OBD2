package com.obd2.jeancyril.obd

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * ELM327 simulé : permet de vérifier toute l'application sur l'émulateur,
 * sans dongle ni véhicule. Le trajet est joué en boucle (arrêt, accélération
 * jusqu'à 100 km/h, maintien, freinage) et deux codes défauts sont présents.
 */
class SimulatorLink : Link {
    private val startedAt = System.currentTimeMillis()
    private var faulty = true

    override fun send(command: String): String {
        Thread.sleep(15)
        val cmd = command.trim().uppercase().replace(" ", "")
        return when {
            cmd.startsWith("AT") -> if (cmd == "ATZ") "ELM327 v1.5 (simulateur)" else "OK"
            cmd == "04" -> {
                faulty = false
                "44"
            }
            cmd == "03" -> if (faulty) "43 01 33 04 20" else "43 00 00"
            cmd == "07" -> if (faulty) "47 01 33" else "47 00 00"
            cmd == "0A" -> if (faulty) "4A 04 20" else "4A 00 00"
            cmd == "0101" -> if (faulty) "41 01 82 07 65 04" else "41 01 00 07 65 04"
            cmd == "0902" -> vin()
            cmd.startsWith("01") && cmd.length == 4 -> pid(cmd.substring(2).toInt(16))
            else -> "NO DATA"
        }
    }

    override fun close() = Unit

    private fun phase(): Double = ((System.currentTimeMillis() - startedAt) / 1000.0) % 50.0

    private fun speed(): Double {
        val p = phase()
        return when {
            p < 5 -> 0.0
            p < 28 -> (p - 5) / 23.0 * 100.0
            p < 42 -> 100.0
            else -> max(0.0, 100.0 - (p - 42) / 8.0 * 100.0)
        }
    }

    private fun pid(value: Int): String {
        val speed = speed()
        val p = phase()
        val idle = speed < 0.5
        val load = min(100.0, 12.0 + speed * 0.7)
        val throttle = min(100.0, speed)
        val maf = 3.5 + speed * 0.16
        val data: IntArray = when (value) {
            0x00 -> bitmap(0x00)
            0x20 -> bitmap(0x20)
            0x40 -> bitmap(0x40)
            0x01 -> intArrayOf(if (faulty) 0x82 else 0x00, 0x07, 0x65, 0x04)
            0x04 -> one((load * 255 / 100).toInt())
            0x05 -> one(88 + (4 * sin(p / 10.0)).toInt() + 40)
            0x06 -> one((128 + 4 * sin(p)).toInt())
            0x07 -> one(126)
            0x0B -> one((30 + speed / 2).toInt())
            0x0C -> two(((if (idle) 780.0 else 900.0 + speed * 32) * 4).toInt())
            0x0D -> one(speed.toInt())
            0x0E -> one((((8 + speed / 12) + 64) * 2).toInt())
            0x0F -> one(25 + (speed / 10).toInt() + 40)
            0x10 -> two((maf * 100).toInt())
            0x11 -> one((throttle * 255 / 100).toInt())
            0x14 -> one(90 + (20 * sin(p * 2)).toInt())
            0x1F -> two(((System.currentTimeMillis() - startedAt) / 1000).toInt())
            0x21 -> two(120)
            0x23 -> two(3500 + speed.toInt() * 30)
            0x2F -> one(((64 - (System.currentTimeMillis() - startedAt) / 600000.0) * 255 / 100).toInt())
            0x31 -> two(45)
            0x33 -> one(101)
            0x42 -> two(14100)
            0x46 -> one(21 + 40)
            0x5C -> one(85 + (speed / 5).toInt() + 40)
            0x5E -> two((maf * 3600 / (14.7 * 745) * 20).toInt())
            else -> return "NO DATA"
        }
        val bytes = intArrayOf(0x41, value) + data
        return bytes.joinToString(" ") { "%02X".format(it and 0xFF) }
    }

    private fun one(v: Int): IntArray = intArrayOf(v.coerceIn(0, 255))

    private fun two(v: Int): IntArray {
        val clamped = v.coerceIn(0, 65535)
        return intArrayOf((clamped shr 8) and 0xFF, clamped and 0xFF)
    }

    private fun bitmap(base: Int): IntArray {
        val bytes = IntArray(4)
        fun set(pid: Int) {
            val offset = pid - base - 1
            if (offset in 0..31) {
                bytes[offset / 8] = bytes[offset / 8] or (1 shl (7 - offset % 8))
            }
        }
        set(0x01)
        for (item in Pids.all) {
            val pid = item.id.substring(2).toInt(16)
            if (pid in base + 1..base + 32) set(pid)
        }
        if (base <= 0x20) set(base + 32)
        return bytes
    }

    private fun vin(): String {
        val text = "WVWZZZ1JZXW000001"
        val bytes = intArrayOf(0x49, 0x02, 0x01) + text.map { it.code.toByte().toInt() % 256 }.toIntArray()
        return bytes.joinToString(" ") { "%02X".format(it and 0xFF) }
    }
}
