package com.obd2.jeancyril.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Liaison BLE des ELM327 « v2.1 » : service FFE0, caractéristique FFE1,
 * réponses par notifications. Connexion bloquante, comme [BluetoothLink].
 */
class BleLink private constructor(
    private val gatt: BluetoothGatt,
    private val characteristic: BluetoothGattCharacteristic,
    private val buffer: StringBuilder,
    private val lock: Any,
) : Link {

    companion object {
        private val SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun connect(context: Context, device: BluetoothDevice): BleLink {
            val connected = CountDownLatch(1)
            val ready = CountDownLatch(1)
            val lock = Any()
            val buffer = StringBuilder()
            val characteristicRef = arrayOfNulls<BluetoothGattCharacteristic>(1)
            var error: String? = null

            fun fail(message: String) {
                synchronized(lock) { if (error == null) error = message }
                connected.countDown()
                ready.countDown()
            }

            fun append(bytes: ByteArray) {
                synchronized(lock) {
                    buffer.append(String(bytes, Charsets.US_ASCII))
                }
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connected.countDown()
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        fail("Déconnexion BLE (statut $status)")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val service = gatt.getService(SERVICE)
                    val characteristic = service?.getCharacteristic(CHARACTERISTIC)
                    if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                        fail("Service BLE FFE0/FFE1 introuvable sur ce dongle")
                        return
                    }
                    characteristicRef[0] = characteristic
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CCCD)
                    if (descriptor == null) {
                        ready.countDown()
                        return
                    }
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!gatt.writeDescriptor(descriptor)) fail("Activation des notifications BLE refusée")
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    if (descriptor.uuid == CCCD) {
                        if (status == BluetoothGatt.GATT_SUCCESS) ready.countDown()
                        else fail("Notifications BLE refusées (statut $status)")
                    }
                }

                @Deprecated("Remplacé par la surcharge avec value sur Android 13+")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    characteristic.value?.let { append(it) }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    append(value)
                }
            }

            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw ElmException("Connexion BLE impossible (adaptateur indisponible)")
            if (!connected.await(12, TimeUnit.SECONDS)) {
                runCatching { gatt.close() }
                throw ElmException("Délai BLE dépassé : dongle non joignable")
            }
            synchronized(lock) { error }?.let {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                throw ElmException(it)
            }
            if (!ready.await(8, TimeUnit.SECONDS)) {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                throw ElmException("Le dongle BLE n'a pas activé ses notifications")
            }
            val characteristic = characteristicRef[0]
                ?: run {
                    runCatching { gatt.close() }
                    throw ElmException("Caractéristique BLE FFE1 absente")
                }
            return BleLink(gatt, characteristic, buffer, lock)
        }
    }

    override fun send(command: String): String {
        val payload = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
        synchronized(lock) { buffer.setLength(0) }
        var written = false
        for (attempt in 0..2) {
            written = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    characteristic.value = payload
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            } catch (e: SecurityException) {
                throw ElmException("Permission Bluetooth refusée")
            }
            if (written) break
            Thread.sleep(60)
        }
        if (!written) throw ElmException("Écriture BLE refusée par le dongle")
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            synchronized(lock) {
                if (buffer.contains('>')) return buffer.toString().replace(">", "")
            }
            Thread.sleep(10)
        }
        return synchronized(lock) { buffer.toString().replace(">", "") }
    }

    override fun close() {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }
}
