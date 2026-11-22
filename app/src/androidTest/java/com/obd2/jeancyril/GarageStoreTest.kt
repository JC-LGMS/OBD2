package com.obd2.jeancyril

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obd2.jeancyril.data.Car
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
            lightTheme = true,
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
        assertTrue(loadedSettings.lightTheme)

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
        store.saveCars(listOf(car))
        store.saveSettings(Settings(activeCar = car.id, lightTheme = true))

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
        assertTrue(restored.imageFile(imageName).exists())
        assertTrue(restored.loadSettings().lightTheme)

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
}
