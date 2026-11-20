package com.obd2.jeancyril.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.obd2.jeancyril.data.MonitorStatus
import java.io.IOException
import java.util.UUID

class ElmException(message: String) : Exception(message)

interface Link {
    fun send(command: String): String
    fun close()
}

/** Session ELM327 : initialisation, lecture de PID, codes défauts, moniteurs, VIN. */
class Elm327(private val link: Link) {

    @Synchronized
    fun initialize() {
        val answer = link.send("ATZ")
        if (answer.isBlank()) throw ElmException("Aucune réponse de l'ELM327")
        Thread.sleep(300)
        for (command in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT1")) {
            val reply = link.send(command).uppercase()
            if (reply.contains("UNABLE TO CONNECT")) {
                throw ElmException("L'ELM327 ne dialogue avec aucun calculateur (contact mis ?)")
            }
        }
    }

    @Synchronized
    fun raw(command: String): String = link.send(command)

    fun readPid(id: String): Double? {
        val pid = id.takeLast(2).toInt(16)
        val data = Protocol.payload(raw(id), 0x41, pid) ?: return null
        return Pids.byId(id)?.decode(data)
    }

    fun readSupported(): Set<String> {
        val result = HashSet<String>()
        for (base in listOf(0x00, 0x20, 0x40)) {
            val data = Protocol.payload(raw("01%02X".format(base)), 0x41, base) ?: continue
            result += Protocol.supported(base, data)
        }
        return result
    }

    fun readDtcs(mode: String): List<String> {
        val header = when (mode) {
            "03" -> 0x43
            "07" -> 0x47
            "0A" -> 0x4A
            else -> throw IllegalArgumentException("Mode DTC inconnu : $mode")
        }
        return Protocol.dtcs(raw(mode), header)
    }

    fun readMonitor(): MonitorStatus = Protocol.monitor(raw("0101"))

    fun readVin(): String? = Protocol.vin(raw("0902"))

    fun clearDtcs(): Boolean {
        val reply = raw("04").uppercase()
        return reply.contains("44") || reply.contains("OK")
    }

    fun close() = link.close()
}

/** Liaison Bluetooth SPP (ELM327 classique). Les appels sont bloquants : à faire hors du thread UI. */
class BluetoothLink private constructor(private val socket: BluetoothSocket) : Link {
    private val input = socket.inputStream
    private val output = socket.outputStream

    companion object {
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        fun connect(device: BluetoothDevice): BluetoothLink {
            val socket = device.createRfcommSocketToServiceRecord(SPP)
            try {
                socket.connect()
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw ElmException("Connexion impossible : ${e.message ?: "appareil injoignable"}")
            }
            return BluetoothLink(socket)
        }
    }

    override fun send(command: String): String {
        try {
            output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (e: IOException) {
            throw ElmException("Écriture Bluetooth impossible : ${e.message}")
        }
        val answer = StringBuilder()
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val available = try {
                input.available()
            } catch (e: IOException) {
                throw ElmException("Lecture Bluetooth impossible : ${e.message}")
            }
            if (available > 0) {
                val buffer = ByteArray(available)
                val count = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    throw ElmException("Liaison Bluetooth perdue : ${e.message}")
                }
                if (count > 0) answer.append(String(buffer, 0, count, Charsets.US_ASCII))
                if (answer.contains('>')) break
            } else {
                Thread.sleep(10)
            }
        }
        return answer.toString().replace(">", "")
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
