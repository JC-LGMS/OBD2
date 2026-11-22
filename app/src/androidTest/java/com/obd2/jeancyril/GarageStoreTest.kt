package com.obd2.jeancyril

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obd2.jeancyril.data.Car
import com.obd2.jeancyril.data.FuelFill
import com.obd2.jeancyril.data.GarageStore
import com.obd2.jeancyril.data.LogEntry
import com.obd2.jeancyril.data.SensorOverride
import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GarageStoreTest {

    @Test
    fun carsAndSettingsRoundTrip() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        val store = GarageStore(context, dir)

        val entry = LogEntry(at = 1_700_000_000_000, kind = "note", text = "Vidange")
        val car = Car(
            brand = "Renault",
            model = "Clio",
            year = "2019",
            engine = "1.5 dCi",
            vin = "VF1RFB00000000001",
            plate = "AB-123-CD",
            images = listOf("photo.png"),
            logCount = 1,
        )
        store.appendLog(car.id, entry)
        val settings = Settings(
            dashboard = setOf("010C", "010D"),
            activeCar = car.id,
            overrides = mapOf(
                "010C" to SensorOverride(label = "Tours", style = SensorStyles.DIAL),
            ),
            themeMode = "light",
        )
        store.saveCars(listOf(car))
        store.saveSettings(settings)

        val loadedCar = store.loadCars().single()
        val loadedSettings = store.loadSettings()
        assertEquals("Renault", loadedCar.brand)
        assertEquals("AB-123-CD", loadedCar.plate)
        assertEquals("VF1RFB00000000001", loadedCar.vin)
        assertEquals(1, loadedCar.logCount)
        assertEquals("Vidange", store.loadLog(car.id).single().text)
        assertEquals(car.id, loadedSettings.activeCar)
        assertEquals(setOf("010C", "010D"), loadedSettings.dashboard)
        assertEquals(SensorStyles.DIAL, loadedSettings.overrides["010C"]?.style)
        assertEquals("light", loadedSettings.themeMode)

        dir.deleteRecursively()
    }

    @Test
    fun corruptFileIsQuarantinedInsteadOfOverwritten() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        dir.mkdirs()
        File(dir, "cars.json").writeText("{ pas du json")
        val store = GarageStore(context, dir)

        assertTrue(store.loadCars().isEmpty())
        assertTrue(store.loadWarning != null)
        assertTrue(File(dir, "cars.json.bad").exists())

        dir.deleteRecursively()
    }

    @Test
    fun backupRoundTripRestoresCarAndImage() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        val targetDir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        val store = GarageStore(context, sourceDir)

        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val sourceImage = File(context.cacheDir, "source-${System.nanoTime()}.png")
        sourceImage.writeBytes(imageBytes)
        val imageName = store.saveImage(android.net.Uri.fromFile(sourceImage))!!
        val car = Car(brand = "Toyota", model = "Yaris", plate = "XY-987-ZW", images = listOf(imageName), logCount = 1)
        store.appendLog(car.id, LogEntry(at = 42, kind = "note", text = "Plein"))
        store.appendFill(car.id, FuelFill(at = 43, odometerKm = 12_000, liters = 40.0))
        store.appendFill(car.id, FuelFill(at = 44, odometerKm = 12_600, liters = 42.0))
        store.saveCars(listOf(car))
        store.saveSettings(Settings(activeCar = car.id, themeMode = "light"))

        val zip = File(context.cacheDir, "backup-test-${System.nanoTime()}.zip")
        store.exportBackup(android.net.Uri.fromFile(zip))
        assertTrue(zip.length() > 0)

        val restored = GarageStore(context, targetDir)
        restored.importBackup(android.net.Uri.fromFile(zip))
        val loaded = restored.loadCars().single()
        assertEquals("XY-987-ZW", loaded.plate)
        assertEquals(imageName, loaded.images.single())
        assertEquals(1, loaded.logCount)
        assertEquals("Plein", restored.loadLog(loaded.id).single().text)
        assertEquals(2, restored.loadFills(loaded.id).size)
        assertTrue(restored.imageFile(imageName).exists())
        assertEquals("light", restored.loadSettings().themeMode)

        sourceDir.deleteRecursively()
        targetDir.deleteRecursively()
        sourceImage.delete()
        zip.delete()
    }

    @Test
    fun legacyLogFromCarsJsonIsMigratedToJournal() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        dir.mkdirs()
        File(dir, "cars.json").writeText(
            """[{"id":"legacy-1","brand":"Peugeot","model":"208","log":[{"at":10,"kind":"note","text":"Ancien"}]}]""",
        )
        val store = GarageStore(context, dir)

        val car = store.loadCars().single()
        assertEquals("legacy-1", car.id)
        assertEquals(1, car.logCount)
        assertEquals("Ancien", store.loadLog("legacy-1").single().text)

        dir.deleteRecursively()
    }

    @Test
    fun journalPagingAndSearch() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "garage-test-${System.nanoTime()}")
        val store = GarageStore(context, dir)
        val carId = "car-page"
        listOf(
            LogEntry(at = 1, kind = "note", text = "Frein avant"),
            LogEntry(at = 2, kind = "codes", text = "P0420"),
            LogEntry(at = 3, kind = "note", text = "Frein arrière"),
            LogEntry(at = 4, kind = "mesure", text = "0-100"),
            LogEntry(at = 5, kind = "connexion", text = "Dongle"),
        ).forEach { store.appendLog(carId, it) }

        val newest = store.loadLogPage(carId, offset = 0, limit = 2)
        assertEquals(5, newest.total)
        assertEquals(listOf("0-100", "Dongle"), newest.entries.map { it.text })

        val older = store.loadLogPage(carId, offset = 2, limit = 2)
        assertEquals(listOf("P0420", "Frein arrière"), older.entries.map { it.text })

        val searched = store.loadLogPage(carId, offset = 0, limit = 10, query = "frein")
        assertEquals(2, searched.total)
        assertEquals(listOf("Frein avant", "Frein arrière"), searched.entries.map { it.text })

        val filtered = store.loadLogPage(carId, offset = 0, limit = 10, kinds = setOf("note"))
        assertEquals(2, filtered.total)

        dir.deleteRecursively()
    }
}
