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
class GarageStore(private val context: Context, rootOverride: File? = null) {
    private val root = (rootOverride ?: File(context.filesDir, "garage")).apply { mkdirs() }
    private val imagesDir = File(root, "images").apply { mkdirs() }
    private val logsDir = File(root, "logs").apply { mkdirs() }
    private val fillsDir = File(root, "fills").apply { mkdirs() }
    private val carsFile = File(root, "cars.json")
    private val settingsFile = File(root, "settings.json")

    companion object {
        private const val MAX_BACKUP_BYTES = 1024L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    }

    @Volatile
    var loadWarning: String? = null
        private set

    @Synchronized
    fun loadCars(): List<Car> = try {
        if (!carsFile.exists()) {
            emptyList()
        } else {
            val array = JSONArray(carsFile.readText())
            val parsed = (0 until array.length()).map { carFromJson(array.getJSONObject(it)) }
            // Migration des anciennes versions : le journal était stocké dans cars.json.
            parsed.forEach { item ->
                if (item.legacyLog.isNotEmpty() && !hasLog(item.car.id)) {
                    appendEntries(item.car.id, item.legacyLog)
                }
            }
            parsed.map { it.car }
        }
    } catch (e: Exception) {
        quarantine(carsFile)
        loadWarning = "Le fichier des véhicules était illisible : une copie .bad a été conservée."
        emptyList()
    }

    @Synchronized
    fun saveCars(cars: List<Car>) {
        val array = JSONArray()
        cars.forEach { array.put(carToJson(it)) }
        writeAtomically(carsFile, array.toString())
    }

    @Synchronized
    fun loadSettings(): Settings = try {
        if (!settingsFile.exists()) {
            Settings()
        } else {
            settingsFromJson(JSONObject(settingsFile.readText()))
        }
    } catch (e: Exception) {
        quarantine(settingsFile)
        loadWarning = "Le fichier des réglages était illisible : une copie .bad a été conservée."
        Settings()
    }

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
            themeMode = json.optString("themeMode").ifBlank {
                if (json.optBoolean("lightTheme", false)) "light" else "dark"
            },
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
        json.put("themeMode", settings.themeMode)
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

    fun logFile(carId: String): File = File(logsDir, "${File(carId).name}.jsonl")

    private fun hasLog(carId: String): Boolean =
        logFile(carId).let { it.exists() && it.length() > 0 }

    @Synchronized
    fun appendLog(carId: String, entry: LogEntry) = appendEntries(carId, listOf(entry))

    private fun appendEntries(carId: String, entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        logsDir.mkdirs()
        java.io.FileOutputStream(logFile(carId), true).bufferedWriter(Charsets.UTF_8).use { writer ->
            entries.forEach { entry ->
                writer.write(entryToJson(entry).toString())
                writer.newLine()
            }
        }
    }

    @Synchronized
    fun loadLog(carId: String): List<LogEntry> {
        val file = logFile(carId)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    runCatching { entryFromJson(JSONObject(line)) }.getOrNull()
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun deleteLog(carId: String) {
        logFile(carId).delete()
    }

    /**
     * Une page du journal, la plus récente d'abord côté UI. `offset` 0 = dernières entrées.
     * Le fichier est parcouru en flux : seules les `offset + limit` dernières entrées filtrées
     * sont gardées en mémoire.
     */
    @Synchronized
    fun loadLogPage(
        carId: String,
        offset: Int,
        limit: Int,
        query: String = "",
        kinds: Set<String> = emptySet(),
    ): JournalPage {
        val file = logFile(carId)
        if (!file.exists()) return JournalPage(emptyList(), 0)
        val needle = query.trim()
        val buffer = java.util.ArrayDeque<LogEntry>()
        var total = 0
        runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val entry = runCatching { entryFromJson(JSONObject(line)) }.getOrNull()
                        ?: return@forEach
                    if (needle.isNotEmpty() &&
                        !entry.text.contains(needle, ignoreCase = true) &&
                        !entry.kind.contains(needle, ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    if (kinds.isNotEmpty() && entry.kind !in kinds) return@forEach
                    total++
                    buffer.addLast(entry)
                    if (buffer.size > offset + limit) buffer.removeFirst()
                }
            }
        }
        if (offset >= total) return JournalPage(emptyList(), total)
        val list = buffer.toList()
        val end = list.size - offset
        if (end <= 0) return JournalPage(emptyList(), total)
        val start = maxOf(0, end - limit)
        return JournalPage(list.subList(start, end), total)
    }

    fun fillFile(carId: String): File = File(fillsDir, "${File(carId).name}.jsonl")

    @Synchronized
    fun appendFill(carId: String, fill: FuelFill) {
        fillsDir.mkdirs()
        java.io.FileOutputStream(fillFile(carId), true).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(
                JSONObject().apply {
                    put("at", fill.at)
                    put("odometerKm", fill.odometerKm)
                    put("liters", fill.liters)
                }.toString(),
            )
            writer.newLine()
        }
    }

    @Synchronized
    fun loadFills(carId: String): List<FuelFill> {
        val file = fillFile(carId)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    runCatching {
                        val json = JSONObject(line)
                        FuelFill(
                            at = json.optLong("at", System.currentTimeMillis()),
                            odometerKm = json.optInt("odometerKm", 0),
                            liters = json.optDouble("liters", 0.0),
                        )
                    }.getOrNull()
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun deleteFills(carId: String) {
        fillFile(carId).delete()
    }

    private fun entryToJson(entry: LogEntry): JSONObject = JSONObject().apply {
        put("at", entry.at)
        put("kind", entry.kind)
        put("text", entry.text)
    }

    private fun entryFromJson(json: JSONObject): LogEntry = LogEntry(
        at = json.optLong("at", System.currentTimeMillis()),
        kind = json.optString("kind", "info"),
        text = json.optString("text", ""),
    )

    fun exportBackup(uri: Uri) {
        val files = (imagesDir.listFiles()?.toList().orEmpty() +
            logsDir.listFiles()?.toList().orEmpty() +
            fillsDir.listFiles()?.toList().orEmpty())
        val totalBytes = carsFile.length() + settingsFile.length() + files.sumOf { it.length() }
        if (totalBytes > MAX_BACKUP_BYTES) {
            throw IOException("Sauvegarde trop volumineuse (maximum 1 Go)")
        }
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
                logsDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("logs/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                fillsDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("fills/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Restaure une sauvegarde ZIP. Les données existantes ne sont remplacées qu'après validation. */
    fun importBackup(uri: Uri): String {
        val temp = File(context.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        val tempImages = File(temp, "images").apply { mkdirs() }
        val tempLogs = File(temp, "logs").apply { mkdirs() }
        val tempFills = File(temp, "fills").apply { mkdirs() }
        var carsBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Impossible de lire le fichier choisi")
            input.use { stream ->
                ZipInputStream(stream).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        when {
                            name == "cars.json" -> {
                                carsBytes = readCapped(zip, 8L * 1024 * 1024)
                                    ?: throw IOException("cars.json trop volumineux")
                                total += carsBytes!!.size
                            }
                            name == "settings.json" -> {
                                settingsBytes = readCapped(zip, 1024L * 1024)
                                    ?: throw IOException("settings.json trop volumineux")
                                total += settingsBytes!!.size
                            }
                            name.startsWith("images/") && !name.endsWith("/") -> {
                                val target = File(tempImages, File(name).name)
                                val written = copyCapped(zip, target, MAX_IMAGE_BYTES)
                                    ?: throw IOException("Image trop volumineuse dans la sauvegarde")
                                total += written
                            }
                            name.startsWith("logs/") && !name.endsWith("/") -> {
                                val target = File(tempLogs, File(name).name)
                                val remaining = MAX_BACKUP_BYTES - total
                                val written = copyCapped(zip, target, remaining)
                                    ?: throw IOException("Sauvegarde trop volumineuse (maximum 1 Go)")
                                total += written
                            }
                            name.startsWith("fills/") && !name.endsWith("/") -> {
                                val target = File(tempFills, File(name).name)
                                val remaining = MAX_BACKUP_BYTES - total
                                val written = copyCapped(zip, target, remaining)
                                    ?: throw IOException("Sauvegarde trop volumineuse (maximum 1 Go)")
                                total += written
                            }
                        }
                        if (total > MAX_BACKUP_BYTES) throw IOException("Sauvegarde trop volumineuse (maximum 1 Go)")
                    }
                }
            }
            val carsText = carsBytes?.let { String(it) }
                ?: throw IOException("Sauvegarde invalide : cars.json manquant")
            val carsArray = try {
                JSONArray(carsText)
            } catch (e: Exception) {
                throw IOException("Sauvegarde invalide : cars.json illisible")
            }
            val parsedCars = (0 until carsArray.length()).map { carFromJson(carsArray.getJSONObject(it)) }
            val settings = settingsBytes?.let { bytes ->
                runCatching { settingsFromJson(JSONObject(String(bytes))) }.getOrNull()
            } ?: Settings()

            // Images et journaux d'abord : si une copie échoue, cars.json reste cohérent.
            tempImages.listFiles()?.forEach { file ->
                val destination = imageFile(file.name)
                if (!file.renameTo(destination)) file.copyTo(destination, overwrite = true)
            }
            tempLogs.listFiles()?.forEach { file ->
                val destination = File(logsDir, file.name)
                if (!file.renameTo(destination)) file.copyTo(destination, overwrite = true)
            }
            tempFills.listFiles()?.forEach { file ->
                val destination = File(fillsDir, file.name)
                if (!file.renameTo(destination)) file.copyTo(destination, overwrite = true)
            }
            val cars = parsedCars.map { item ->
                val car = item.car
                if (item.legacyLog.isNotEmpty() && !hasLog(car.id)) {
                    appendEntries(car.id, item.legacyLog)
                    car.copy(logCount = maxOf(car.logCount, item.legacyLog.size))
                } else {
                    car
                }
            }
            saveCars(cars)
            saveSettings(settings)
            return "${cars.size} véhicule(s) restauré(s)"
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun copyCapped(input: java.io.InputStream, target: File, limit: Long): Long? {
        var total = 0L
        val buffer = ByteArray(8192)
        target.outputStream().use { output ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) return null
                output.write(buffer, 0, count)
            }
        }
        return total
    }

    private fun readCapped(input: java.io.InputStream, limit: Long): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            output.write(buffer, 0, count)
            if (output.size() > limit) return null
        }
    }

    private fun quarantine(file: File) {
        if (!file.exists()) return
        val base = File(file.parentFile, file.name + ".bad")
        var target = base
        var index = 1
        while (target.exists()) {
            target = File(file.parentFile, "${base.name}.$index")
            index++
        }
        runCatching {
            if (!file.renameTo(target)) {
                file.copyTo(target)
                file.delete()
            }
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
        put("logCount", car.logCount)
    }

    private fun carFromJson(json: JSONObject): ParsedCar {
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
        val logCount = if (json.has("logCount")) json.optInt("logCount", log.size) else log.size
        val car = Car(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            brand = json.optString("brand"),
            model = json.optString("model"),
            year = json.optString("year"),
            engine = json.optString("engine"),
            vin = json.optString("vin"),
            plate = json.optString("plate"),
            images = images,
            logCount = logCount,
        )
        return ParsedCar(car, log)
    }

    private data class ParsedCar(val car: Car, val legacyLog: List<LogEntry>)
}
