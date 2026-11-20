package com.obd2.jeancyril

import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.data.TripMath
import com.obd2.jeancyril.obd.Dtcs
import com.obd2.jeancyril.obd.Pids
import com.obd2.jeancyril.obd.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdLogicTest {

    @Test
    fun decodeRpm() {
        assertEquals(1726.0, Pids.byId("010C")!!.decode(intArrayOf(0x1A, 0xF8))!!, 0.01)
    }

    @Test
    fun decodeCoolantAndSpeed() {
        assertEquals(50.0, Pids.byId("0105")!!.decode(intArrayOf(90))!!, 0.01)
        assertEquals(120.0, Pids.byId("010D")!!.decode(intArrayOf(120))!!, 0.01)
    }

    @Test
    fun decodeDtcLetters() {
        assertEquals("P0133", Dtcs.decode(0x01, 0x33))
        assertEquals("P0420", Dtcs.decode(0x04, 0x20))
        assertEquals("U0100", Dtcs.decode(0xC1, 0x00))
        assertEquals("B0101", Dtcs.decode(0x81, 0x01))
    }

    @Test
    fun parseResponseWithoutSpaces() {
        val data = Protocol.payload("410C1AF8", 0x41, 0x0C)
        assertEquals(listOf(0x1A, 0xF8), data!!.toList())
    }

    @Test
    fun parseCanMultiline() {
        val response = "0: 41 0D 32 00 00 00 00\r1: 00 00 00 00 00 00 00"
        val data = Protocol.payload(response, 0x41, 0x0D)
        assertEquals(0x32, data!![0])
    }

    @Test
    fun readStoredDtcs() {
        assertEquals(listOf("P0133", "P0420"), Protocol.dtcs("43 01 33 04 20", 0x43))
    }

    @Test
    fun readIso9141DtcsWithCountByte() {
        assertEquals(listOf("P0133", "P0420"), Protocol.dtcs("43 02 01 33 04 20", 0x43))
    }

    @Test
    fun monitorStatus() {
        val monitor = Protocol.monitor("41 01 82 07 65 04")
        assertTrue(monitor.read)
        assertTrue(monitor.mil)
        assertEquals(2, monitor.dtcCount)
        assertTrue(monitor.readiness.first { it.first == "Catalyseur" }.second)
        assertFalse(monitor.readiness.first { it.first == "Circuit EVAP" }.second)
    }

    @Test
    fun readVin() {
        val response = "49 02 01 57 56 57 5A 5A 5A 31 4A 5A 58 57 30 30 30 30 30 31"
        assertEquals("WVWZZZ1JZXW000001", Protocol.vin(response))
    }

    @Test
    fun supportedByteParsing() {
        // Réponse 0100 du simulateur : PID 0x0C (régime) et 0x0D (vitesse) présents.
        val supported = Protocol.supported(0x00, intArrayOf(0xBE, 0x1F, 0xB8, 0x13))
        assertTrue("010C" in supported)
        assertTrue("010D" in supported)
    }

    @Test
    fun fuelMath() {
        val maf = 14.7 * 745.0 / 3600.0
        assertEquals(1.0, TripMath.fuelLitersPerHour(maf), 1e-6)
        assertNull(TripMath.instantL100(4.0, 5.0))
        assertNull(TripMath.instantL100(14.9, 5.0))
        assertEquals(10.0, TripMath.instantL100(50.0, 5.0)!!, 1e-6)
    }

    @Test
    fun tripDistanceAndLiters() {
        val (distance, liters) = TripMath.advance(0.0, 0.0, 36.0, 10.0, 10.0)
        assertEquals(0.1, distance, 1e-6)
        assertEquals(10.0 / 360.0, liters, 1e-6)
    }

    @Test
    fun brandSpecificDtcDescriptions() {
        assertTrue(Dtcs.describe("P1351", "Citroën").contains("préchauffage"))
        assertTrue(Dtcs.describe("P1351", "Peugeot").contains("préchauffage"))
        assertTrue(Dtcs.describe("P1135", "Toyota").contains("sonde A/F"))
        assertTrue(Dtcs.describe("P1602", "Volkswagen").contains("alimentation"))
        assertTrue(Dtcs.describe("P0638", "Dacia").contains("papillon"))
        assertFalse(Dtcs.describe("P1135", "Citroën").contains("sonde A/F"))
    }

    @Test
    fun sensorDisplayStyles() {
        assertEquals(SensorStyles.DIAL, SensorStyles.defaultFor("010C"))
        assertEquals(SensorStyles.DIAL, SensorStyles.defaultFor("010D"))
        assertEquals(SensorStyles.BAR, SensorStyles.defaultFor("0105"))
        assertEquals(SensorStyles.DIGITAL, SensorStyles.defaultFor("0142"))
        assertEquals(SensorStyles.DIGITAL, SensorStyles.next(SensorStyles.DIAL))
        assertEquals(SensorStyles.DIAL, SensorStyles.next(SensorStyles.BAR))
    }
}
