package com.obd2.jeancyril.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Persistance locale : un fichier JSON pour le garage, un pour les réglages,
 * et les photos dans filesDir/garage/images. Aucune dépendance externe.
 */
class GarageStore(private val context: Context) {
    private val root = File(context.filesDir, "garage").apply { mkdirs() }
    private val imagesDir = File(root, "images").apply { mkdirs() }
    private val carsFile = File(root, "cars.json")
    private val settingsFile = File(root, "settings.json")

    @Synchronized
    fun loadCars(): List<Car> = runCatching {
        if (!carsFile.exists()) return emptyList()
        val array = JSONArray(carsFile.readText())
        (0 until array.length()).map { carFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveCars(cars: List<Car>) {
        val array = JSONArray()
        cars.forEach { array.put(carToJson(it)) }
        writeAtomically(carsFile, array.toString())
    }

    @Synchronized
    fun loadSettings(): Settings = runCatching {
        if (!settingsFile.exists()) return Settings()
        settingsFromJson(JSONObject(settingsFile.readText()))
    }.getOrDefault(Settings())

    private fun settingsFromJson(json: JSONObject): Settings {
        val dashboard = json.optJSONArray("dashboard")?.let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        } ?: Settings.DEFAULT_DASHBOARD
        val overrides = mutableMapOf<String, SensorOverride>()
        json.optJSONObject("overrides")?.let { obj ->
            obj.keys().forEach { key ->
                val value = obj.getJSONObject(key)
                overrides[key] = SensorOverride(
                    label = value.optString("label").takeIf { it.isNotBlank() },
                    unit = value.optString("unit").takeIf { it.isNotBlank() },
                    min = if (value.has("min")) value.getDouble("min") else null,
                    max = if (value.has("max")) value.getDouble("max") else null,
                    style = value.optString("style").takeIf { it.isNotBlank() },
                )
            }
        }
        return Settings(
            dashboard = dashboard,
            activeCar = json.optString("activeCar").takeIf { it.isNotBlank() },
            overrides = overrides,
            lightTheme = json.optBoolean("lightTheme", false),
        )
    }

    @Synchronized
    fun saveSettings(settings: Settings) {
        val json = JSONObject()
        json.put("dashboard", JSONArray(settings.dashboard.toList()))
        settings.activeCar?.let { json.put("activeCar", it) }
        val overrides = JSONObject()
        settings.overrides.forEach { (id, value) ->
            val obj = JSONObject()
            value.label?.let { obj.put("label", it) }
            value.unit?.let { obj.put("unit", it) }
            value.min?.let { obj.put("min", it) }
            value.max?.let { obj.put("max", it) }
            value.style?.let { obj.put("style", it) }
            overrides.put(id, obj)
        }
        json.put("overrides", overrides)
        json.put("lightTheme", settings.lightTheme)
        writeAtomically(settingsFile, json.toString())
    }

    fun imageFile(name: String): File = File(imagesDir, File(name).name)

    fun saveImage(uri: Uri): String? {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val extension = if (mime?.contains("png") == true) "png" else "jpg"
        val name = "${UUID.randomUUID()}.$extension"
        val target = imageFile(name)
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        val copied = runCatching {
            input.use { source ->
                target.outputStream().use { destination -> source.copyTo(destination) }
            }
        }.isSuccess
        if (!copied || target.length() > 20L * 1024 * 1024) {
            target.delete()
            return null
        }
        return name
    }

    fun deleteImage(name: String) {
        imageFile(name).delete()
    }

    fun exportBackup(uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Impossible d'écrire le fichier choisi")
        output.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("cars.json"))
                zip.write(carsFile.takeIf { it.exists() }?.readBytes() ?: "[]".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("settings.json"))
                zip.write(settingsFile.takeIf { it.exists() }?.readBytes() ?: "{}".toByteArray())
                zip.closeEntry()
                imagesDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("images/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Restaure une sauvegarde ZIP. Les données existantes ne sont remplacées qu'après validation. */
    fun importBackup(uri: Uri): String {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Impossible de lire le fichier choisi")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val allowed = name == "cars.json" || name == "settings.json" ||
                        (name.startsWith("images/") && !name.endsWith("/"))
                    if (!allowed) continue
                    val bytes = readCapped(zip, 8 * 1024 * 1024)
                        ?: throw IOException("Image trop volumineuse dans la sauvegarde")
                    total += bytes.size
                    if (total > 40L * 1024 * 1024) throw IOException("Sauvegarde trop volumineuse")
                    entries[name] = bytes
                }
            }
        }
        val carsText = entries["cars.json"]?.let { String(it) }
            ?: throw IOException("Sauvegarde invalide : cars.json manquant")
        val carsArray = try {
            JSONArray(carsText)
        } catch (e: Exception) {
            throw IOException("Sauvegarde invalide : cars.json illisible")
        }
        val cars = (0 until carsArray.length()).map { carFromJson(carsArray.getJSONObject(it)) }
        val settings = entries["settings.json"]?.let { bytes ->
            runCatching { settingsFromJson(JSONObject(String(bytes))) }.getOrNull()
        } ?: Settings()

        saveCars(cars)
        saveSettings(settings)
        entries.filterKeys { it.startsWith("images/") }.forEach { (name, bytes) ->
            imageFile(name.removePrefix("images/")).writeBytes(bytes)
        }
        return "${cars.size} véhicule(s) restauré(s)"
    }

    private fun readCapped(input: java.io.InputStream, limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            output.write(buffer, 0, count)
            if (output.size() > limit) return null
        }
    }

    private fun writeAtomically(target: File, text: String) {
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.writeText(text)
            temporary.delete()
        }
    }

    private fun carToJson(car: Car): JSONObject = JSONObject().apply {
        put("id", car.id)
        put("brand", car.brand)
        put("model", car.model)
        put("year", car.year)
        put("engine", car.engine)
        put("vin", car.vin)
        put("plate", car.plate)
        put("images", JSONArray(car.images))
        put("log", JSONArray().apply {
            car.log.forEach { entry ->
                put(JSONObject().apply {
                    put("at", entry.at)
                    put("kind", entry.kind)
                    put("text", entry.text)
                })
            }
        })
    }

    private fun carFromJson(json: JSONObject): Car {
        val images = json.optJSONArray("images")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList()
        val log = json.optJSONArray("log")?.let { array ->
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                LogEntry(
                    at = entry.optLong("at", System.currentTimeMillis()),
                    kind = entry.optString("kind", "info"),
                    text = entry.optString("text", ""),
                )
            }
        } ?: emptyList()
        return Car(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            brand = json.optString("brand"),
            model = json.optString("model"),
            year = json.optString("year"),
            engine = json.optString("engine"),
            vin = json.optString("vin"),
            plate = json.optString("plate"),
            images = images,
            log = log,
        )
    }
}
