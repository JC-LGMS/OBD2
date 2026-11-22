package com.obd2.jeancyril

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obd2.jeancyril.data.AccelState
import com.obd2.jeancyril.data.Car
import com.obd2.jeancyril.data.ConnStatus
import com.obd2.jeancyril.data.ConnectionState
import com.obd2.jeancyril.data.Dtc
import com.obd2.jeancyril.data.DtcState
import com.obd2.jeancyril.data.FuelFill
import com.obd2.jeancyril.data.FuelState
import com.obd2.jeancyril.data.GarageStore
import com.obd2.jeancyril.data.JournalState
import com.obd2.jeancyril.data.LiveState
import com.obd2.jeancyril.data.LogEntry
import com.obd2.jeancyril.data.MonitorStatus
import com.obd2.jeancyril.data.Sample
import com.obd2.jeancyril.data.SensorOverride
import com.obd2.jeancyril.data.SensorStyles
import com.obd2.jeancyril.data.Settings
import com.obd2.jeancyril.data.TripMath
import com.obd2.jeancyril.obd.BleLink
import com.obd2.jeancyril.obd.BluetoothLink
import com.obd2.jeancyril.obd.Dtcs
import com.obd2.jeancyril.obd.Elm327
import com.obd2.jeancyril.obd.Link
import com.obd2.jeancyril.obd.Pids
import com.obd2.jeancyril.obd.SimulatorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObdViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GarageStore(app)

    private val _cars = MutableStateFlow(store.loadCars())
    val cars: StateFlow<List<Car>> = _cars.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val activeCar: StateFlow<Car?> = combine(_cars, _settings) { cars, settings ->
        cars.firstOrNull { it.id == settings.activeCar }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val _monitor = MutableStateFlow(MonitorStatus())
    val monitor: StateFlow<MonitorStatus> = _monitor.asStateFlow()

    private val _dtcs = MutableStateFlow(DtcState())
    val dtcs: StateFlow<DtcState> = _dtcs.asStateFlow()

    private val _fuel = MutableStateFlow(FuelState())
    val fuel: StateFlow<FuelState> = _fuel.asStateFlow()

    private val _fills = MutableStateFlow<List<FuelFill>>(emptyList())
    val fills: StateFlow<List<FuelFill>> = _fills.asStateFlow()

    private val _journal = MutableStateFlow(JournalState())
    val journal: StateFlow<JournalState> = _journal.asStateFlow()

    private val _accel = MutableStateFlow(AccelState())
    val accel: StateFlow<AccelState> = _accel.asStateFlow()

    private val _supported = MutableStateFlow<Set<String>?>(null)
    val supported: StateFlow<Set<String>?> = _supported.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        store.loadWarning?.let { _message.value = it }
    }

    private var elm: Elm327? = null
    private var pollJob: Job? = null
    private var connectJob: Job? = null
    private var sessionToken = 0
    private var lastTick = 0L
    private var lastSpeed = 0.0
    private val carLock = Any()
    private val historyLock = Any()
    private val history = HashMap<String, java.util.ArrayDeque<Sample>>()
    @Volatile
    private var journalRequest = 0

    fun clearMessage() {
        _message.value = null
    }

    // ---------------------------------------------------------------- connexion

    fun connectSimulator() {
        stopSession()
        val token = sessionToken
        val session = Elm327(SimulatorLink())
        _connection.value = ConnectionState(ConnStatus.CONNECTING, "Simulateur", "Initialisation…")
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.initialize() }
                .onSuccess {
                    if (token != sessionToken) {
                        session.close()
                        return@onSuccess
                    }
                    elm = session
                    _connection.value = ConnectionState(ConnStatus.SIMULATOR, "Simulateur ELM327", "Données simulées, aucun véhicule requis")
                    _supported.value = Pids.all.map { it.id }.toSet()
                    runCatching { session.readMonitor() }.getOrNull()?.let { _monitor.value = it }
                    log("connexion", "Session simulateur démarrée")
                    startPolling(session, simulated = true)
                }
                .onFailure {
                    session.close()
                    if (token == sessionToken) {
                        _connection.value = ConnectionState(ConnStatus.ERROR, "Simulateur", it.message ?: "Échec du simulateur")
                    }
                }
        }
    }

    fun connectBluetooth(device: BluetoothDevice) {
        stopSession()
        val name = try {
            device.name
        } catch (e: SecurityException) {
            null
        } ?: device.address
        _connection.value = ConnectionState(ConnStatus.CONNECTING, name, "Ouverture de la liaison Bluetooth…")
        val token = sessionToken
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // Un dongle BLE n'ouvre pas de socket SPP, et un dongle classique n'expose pas de GATT :
            // on tente la liaison correspondant au type annoncé, puis l'autre en secours.
            val attempts = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> listOf(true, false)
                else -> listOf(false, true)
            }
            var lastError = "Connexion impossible"
            for (useBle in attempts) {
                if (token != sessionToken) return@launch
                var pendingLink: Link? = null
                try {
                    pendingLink = if (useBle) BleLink.connect(context, device) else BluetoothLink.connect(device)
                    val session = Elm327(pendingLink)
                    session.initialize()
                    if (token != sessionToken) {
                        session.close()
                        return@launch
                    }
                    elm = session
                    _connection.value = ConnectionState(
                        ConnStatus.CONNECTED,
                        name,
                        if (useBle) "BLE (GATT)" else "Bluetooth classique (SPP)",
                    )
                    log("connexion", "ELM327 connecté : $name")
                    val supported = runCatching { session.readSupported() }.getOrNull()
                    if (!supported.isNullOrEmpty()) _supported.value = supported
                    runCatching { session.readMonitor() }.getOrNull()?.let { _monitor.value = it }
                    startPolling(session, simulated = false)
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message ?: "Connexion impossible"
                    runCatching { pendingLink?.close() }
                }
            }
            if (token == sessionToken) {
                _connection.value = ConnectionState(ConnStatus.ERROR, name, lastError)
            }
        }
    }

    fun disconnect() {
        val wasConnected = _connection.value.status != ConnStatus.DISCONNECTED
        stopSession()
        _connection.value = ConnectionState()
        if (wasConnected) log("connexion", "Déconnexion de l'ELM327")
    }

    private fun stopSession() {
        sessionToken++
        connectJob?.cancel()
        connectJob = null
        pollJob?.cancel()
        pollJob = null
        runCatching { elm?.close() }
        elm = null
        lastTick = 0L
        lastSpeed = 0.0
        _supported.value = null
        _live.value = LiveState()
        synchronized(historyLock) { history.clear() }
        _dtcs.value = DtcState()
        _monitor.value = MonitorStatus()
    }

    // ------------------------------------------------------------------ polling

    private fun startPolling(session: Elm327, simulated: Boolean) {
        lastTick = 0L
        lastSpeed = 0.0
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var emptyTicks = 0
            var extraIndex = 0
            try {
                while (isActive) {
                    val current = _settings.value
                    val dashboard = current.dashboard.ifEmpty { Settings.DEFAULT_DASHBOARD }
                    val pool = _supported.value?.let { ids -> Pids.all.map { it.id }.filter { it in ids } }
                        ?: Pids.all.map { it.id }
                    val ids = LinkedHashSet<String>()
                    ids += dashboard
                    // Vitesse et débit d'air restent lus même si l'utilisateur les retire du
                    // tableau de bord : ils alimentent le chrono 0-100 et la consommation.
                    ids += "010D"
                    ids += "0110"
                    if (pool.isNotEmpty()) ids += pool[extraIndex++ % pool.size]

                    val values = LinkedHashMap<String, Double>()
                    for (id in ids) {
                        val pid = Pids.byId(id) ?: continue
                        runCatching { session.readPid(id) }.getOrNull()?.let { values[id] = it }
                    }
                    if (values.isEmpty()) emptyTicks++ else emptyTicks = 0
                    if (emptyTicks >= 10 && simulated.not()) {
                        throw IllegalStateException("Le calculateur ne répond plus (contact coupé ?)")
                    }
                    if (values.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        publish(values, now)
                        updateTrip(values, now)
                    }
                    delay(250)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (elm !== session) return@launch
                _connection.value = ConnectionState(
                    ConnStatus.ERROR,
                    _connection.value.name,
                    e.message ?: "Liaison perdue",
                )
                log("erreur", "Liaison ELM327 interrompue : ${e.message}")
                runCatching { session.close() }
                if (elm === session) elm = null
            }
        }
    }

    private fun publish(values: Map<String, Double>, now: Long) {
        synchronized(historyLock) {
            values.forEach { (id, value) ->
                val samples = history.getOrPut(id) { java.util.ArrayDeque() }
                samples.addLast(Sample(now, value))
                while (samples.size > 300) samples.removeFirst()
            }
        }
        _live.value = LiveState(
            values = _live.value.values + values,
            updatedAt = now,
        )
    }

    fun history(id: String): List<Sample> = synchronized(historyLock) {
        history[id]?.toList().orEmpty()
    }

    private fun updateTrip(values: Map<String, Double>, now: Long) {
        val speed = values["010D"]
        if (speed == null) {
            lastTick = now
            return
        }
        val diesel = TripMath.looksDiesel(activeCar.value?.engine.orEmpty())
        val fuelRate = values["015E"]
            ?: if (!diesel) values["0110"]?.let { TripMath.fuelLitersPerHour(it) } else null
        val dt = if (lastTick == 0L) 0.0 else (now - lastTick) / 1000.0
        if (dt > 0.0) {
            if (dt > 5.0) {
                // Trou de plusieurs secondes : ne pas inventer distance ni carburant.
                lastTick = now
                lastSpeed = speed
                return
            }
            val (distance, accumulatedLiters) = TripMath.advance(
                _fuel.value.distanceKm,
                _fuel.value.liters,
                speed,
                fuelRate,
                dt,
            )
            val liters = if (fuelRate != null) accumulatedLiters else _fuel.value.liters
            _fuel.value = FuelState(
                instant = TripMath.instantL100(speed, fuelRate),
                average = if (fuelRate != null && distance > 0.05) liters / distance * 100.0 else null,
                distanceKm = distance,
                liters = liters,
            )
            updateAccel(speed, now, dt)
        }
        lastTick = now
        lastSpeed = speed
    }

    private fun updateAccel(speed: Double?, now: Long, dt: Double) {
        if (speed == null) return
        var state = _accel.value.copy(currentSpeed = speed)
        if (dt > 0.0) {
            val g = (speed - lastSpeed) / 3.6 / dt / 9.81
            if (g > 0) {
                state = state.copy(currentG = g, maxG = maxOf(state.maxG, g))
            } else {
                state = state.copy(currentG = 0.0)
            }
        }
        if (state.running) {
            val start = state.startedAt
            if (start == null) {
                if (speed < 1.5) {
                    state = state.copy(startedAt = now, waitingForStop = false)
                } else if (!state.waitingForStop) {
                    state = state.copy(waitingForStop = true)
                }
            } else if (speed >= 100.0) {
                val seconds = (now - start) / 1000.0
                state = state.copy(running = false, time0to100 = seconds, startedAt = null)
                val text = "0 à 100 km/h en %.1f s".format(seconds)
                _message.value = text
                log("mesure", text)
            }
        }
        _accel.value = state
    }

    fun startAccel() {
        _accel.value = _accel.value.copy(
            running = true,
            time0to100 = null,
            startedAt = null,
            maxG = 0.0,
            waitingForStop = _accel.value.currentSpeed > 1.5,
        )
    }

    fun stopAccel() {
        _accel.value = _accel.value.copy(running = false, startedAt = null)
    }

    fun resetTrip() {
        _fuel.value = FuelState()
        log("consommation", "Statistiques de consommation remises à zéro")
        _message.value = "Consommation remise à zéro"
    }

    // -------------------------------------------------------------- diagnostics

    fun refreshMonitor() {
        val session = elm ?: return noSession()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.readMonitor() }
                .onSuccess { _monitor.value = it }
                .onFailure { _message.value = it.message ?: "Lecture des moniteurs impossible" }
        }
    }

    fun readCodes() {
        val session = elm ?: return noSession()
        val brand = activeCar.value?.brand
        _dtcs.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stored = session.readDtcs("03").map { Dtc(it, Dtcs.describe(it, brand), "Mémorisé") }
                val pending = session.readDtcs("07").map { Dtc(it, Dtcs.describe(it, brand), "En attente") }
                val permanent = session.readDtcs("0A").map { Dtc(it, Dtcs.describe(it, brand), "Permanent") }
                Triple(stored, pending, permanent)
            }.onSuccess { (stored, pending, permanent) ->
                _dtcs.value = DtcState(stored, pending, permanent, false, System.currentTimeMillis())
                runCatching { session.readMonitor() }.getOrNull()?.let { _monitor.value = it }
                val total = stored.size + pending.size + permanent.size
                log("codes", if (total == 0) "Aucun code défaut" else "$total code(s) défaut lu(s)")
                _message.value = if (total == 0) "Aucun code défaut" else "$total code(s) défaut"
            }.onFailure {
                _dtcs.update { state -> state.copy(loading = false) }
                _message.value = it.message ?: "Lecture des codes impossible"
            }
        }
    }

    fun clearCodes() {
        val session = elm ?: return noSession()
        viewModelScope.launch(Dispatchers.IO) {
            buildReport()?.let { log("rapport", it) }
            runCatching { session.clearDtcs() }
                .onSuccess { ok ->
                    if (ok) {
                        _dtcs.value = DtcState()
                        runCatching { session.readMonitor() }.getOrNull()?.let { _monitor.value = it }
                        log("effacement", "Codes défauts effacés")
                        _message.value = "Codes défauts effacés"
                    } else {
                        _message.value = "L'ELM327 a refusé l'effacement"
                    }
                }
                .onFailure { _message.value = it.message ?: "Effacement impossible" }
        }
    }

    fun readVin(onResult: (String?) -> Unit) {
        val session = elm ?: run {
            noSession()
            onResult(null)
            return
        }
        viewModelScope.launch {
            val vin = withContext(Dispatchers.IO) { runCatching { session.readVin() }.getOrNull() }
            onResult(vin)
            if (vin == null) _message.value = "VIN non disponible sur ce véhicule"
        }
    }

    fun refreshSupported() {
        val session = elm ?: return noSession()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.readSupported() }
                .onSuccess { set ->
                    _supported.value = set
                    _message.value = "${set.size} capteur(s) supporté(s)"
                }
                .onFailure { _message.value = it.message ?: "Lecture des capteurs impossible" }
        }
    }

    private fun noSession() {
        _message.value = "Connectez d'abord l'ELM327 ou lancez le simulateur"
    }

    // ------------------------------------------------------------------ véhicule

    fun selectCar(id: String) {
        updateSettings { it.copy(activeCar = id) }
        _journal.value = JournalState()
        _fills.value = emptyList()
    }

    fun addCar(brand: String, model: String, year: String, engine: String, vin: String, plate: String): String {
        val car = Car(
            brand = brand.trim(),
            model = model.trim(),
            year = year.trim(),
            engine = engine.trim(),
            vin = vin.trim(),
            plate = plate.trim().uppercase(),
        )
        synchronized(carLock) {
            _cars.value = _cars.value + car
            store.saveCars(_cars.value)
        }
        updateSettings { it.copy(activeCar = car.id) }
        log("véhicule", "Véhicule créé : ${car.title}")
        return car.id
    }

    fun updateCarDetails(id: String, brand: String, model: String, year: String, engine: String, vin: String, plate: String) {
        updateCar(id) {
            it.copy(
                brand = brand.trim(),
                model = model.trim(),
                year = year.trim(),
                engine = engine.trim(),
                vin = vin.trim(),
                plate = plate.trim().uppercase(),
            )
        }
        log("véhicule", "Fiche véhicule modifiée")
    }

    fun deleteCar(id: String) {
        val car = _cars.value.firstOrNull { it.id == id } ?: return
        synchronized(carLock) {
            _cars.value = _cars.value.filterNot { it.id == id }
            store.saveCars(_cars.value)
        }
        car.images.forEach { store.deleteImage(it) }
        store.deleteLog(id)
        store.deleteFills(id)
        if (_settings.value.activeCar == id) {
            updateSettings { it.copy(activeCar = _cars.value.firstOrNull()?.id) }
            _journal.value = JournalState()
            _fills.value = emptyList()
        }
    }

    fun addCarImages(uris: List<Uri>) {
        val id = _settings.value.activeCar ?: run {
            _message.value = "Créez d'abord un véhicule"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val names = uris.mapNotNull { uri -> runCatching { store.saveImage(uri) }.getOrNull() }
            if (names.isEmpty()) {
                _message.value = "Aucune image lisible"
                return@launch
            }
            updateCar(id) { it.copy(images = it.images + names) }
            log("photos", "${names.size} photo(s) ajoutée(s)")
            _message.value = "${names.size} photo(s) ajoutée(s)"
        }
    }

    fun deleteCarImage(name: String) {
        val id = _settings.value.activeCar ?: return
        updateCar(id) { it.copy(images = it.images - name) }
        store.deleteImage(name)
    }

    fun imageFile(name: String): java.io.File = store.imageFile(name)

    fun addNote(text: String) {
        if (text.isBlank()) return
        log("note", text.trim())
        _message.value = "Note enregistrée"
    }

    fun log(kind: String, text: String) {
        val id = _settings.value.activeCar ?: return
        val entry = LogEntry(kind = kind, text = text)
        store.appendLog(id, entry)
        updateCar(id) { car -> car.copy(logCount = car.logCount + 1) }
    }

    /**
     * Charge une page du journal (100 entrées). `reset=true` repart des plus récentes.
     * La recherche et les filtres s'appliquent à tout l'historique, pas seulement aux pages déjà vues.
     */
    fun loadJournal(
        carId: String? = _settings.value.activeCar,
        reset: Boolean = true,
        query: String = "",
        kinds: Set<String> = emptySet(),
    ) {
        if (carId == null) {
            _journal.value = JournalState()
            return
        }
        val request = ++journalRequest
        viewModelScope.launch(Dispatchers.IO) {
            _journal.update { it.copy(loading = true) }
            val offset = if (reset) 0 else _journal.value.entries.size
            runCatching { store.loadLogPage(carId, offset, 100, query, kinds) }
                .onSuccess { page ->
                    if (request != journalRequest) return@onSuccess
                    val newEntries = page.entries.asReversed()
                    _journal.update { old ->
                        val entries = if (reset) newEntries else (old.entries + newEntries).distinct()
                        JournalState(entries, page.total, entries.size < page.total, false)
                    }
                }
                .onFailure {
                    if (request != journalRequest) return@onFailure
                    _journal.update { state -> state.copy(loading = false) }
                    _message.value = it.message ?: "Lecture du journal impossible"
                }
        }
    }

    fun loadMoreJournal(query: String = "", kinds: Set<String> = emptySet()) {
        loadJournal(reset = false, query = query, kinds = kinds)
    }

    fun loadFills(carId: String? = _settings.value.activeCar) {
        if (carId == null) {
            _fills.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _fills.value = store.loadFills(carId).sortedBy { it.odometerKm }
        }
    }

    fun addFill(liters: Double, odometerKm: Int) {
        val id = _settings.value.activeCar ?: run {
            _message.value = "Créez d'abord un véhicule"
            return
        }
        if (liters <= 0.0 || odometerKm <= 0) {
            _message.value = "Litres et kilométrage doivent être positifs"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val fills = store.loadFills(id).sortedBy { it.odometerKm }
            val last = fills.lastOrNull()
            if (last != null && odometerKm <= last.odometerKm) {
                _message.value = "Le kilométrage doit dépasser ${last.odometerKm} km"
                return@launch
            }
            store.appendFill(id, FuelFill(odometerKm = odometerKm, liters = liters))
            _fills.value = store.loadFills(id).sortedBy { it.odometerKm }
            log("plein", "Plein : %.2f L à %d km".format(liters, odometerKm))
            _message.value = "Plein enregistré"
        }
    }

    fun buildReport(): String? {
        val car = activeCar.value ?: return null
        val state = _dtcs.value
        if (state.lastReadAt == 0L) return null
        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
            .format(java.util.Date(state.lastReadAt))
        val text = StringBuilder()
        text.appendLine("Rapport OBD2 — $date")
        text.appendLine("Véhicule : ${car.title}")
        if (car.plate.isNotBlank()) text.appendLine("Plaque : ${car.plate}")
        if (car.vin.isNotBlank()) text.appendLine("VIN : ${car.vin}")
        text.appendLine("Témoin moteur : ${if (_monitor.value.mil) "allumé" else "éteint"}")
        text.appendLine("Codes signalés : ${_monitor.value.dtcCount}")
        listOf("Mémorisé" to state.stored, "En attente" to state.pending, "Permanent" to state.permanent)
            .forEach { (label, codes) ->
                if (codes.isNotEmpty()) {
                    text.appendLine()
                    text.appendLine("$label (${codes.size}) :")
                    codes.forEach { text.appendLine("- ${it.code} : ${it.description}") }
                }
            }
        if (state.stored.isEmpty() && state.pending.isEmpty() && state.permanent.isEmpty()) {
            text.appendLine()
            text.appendLine("Aucun code défaut.")
        }
        return text.toString().trim()
    }

    private fun updateCar(id: String, transform: (Car) -> Car) {
        synchronized(carLock) {
            val updated = _cars.value.map { if (it.id == id) transform(it) else it }
            _cars.value = updated
            store.saveCars(updated)
        }
    }

    // ----------------------------------------------------------------- réglages

    fun toggleDashboard(id: String) {
        updateSettings {
            val dashboard = if (id in it.dashboard) it.dashboard - id else it.dashboard + id
            it.copy(dashboard = dashboard)
        }
    }

    fun saveSensorOverride(id: String, override: SensorOverride?) {
        updateSettings {
            val overrides = if (override == null) it.overrides - id else it.overrides + (id to override)
            it.copy(overrides = overrides)
        }
    }

    fun setSensorStyle(id: String, style: String) {
        val current = _settings.value.overrides[id] ?: SensorOverride()
        saveSensorOverride(id, current.copy(style = style))
    }

    fun cycleSensorStyle(id: String) {
        val current = _settings.value.overrides[id]?.style
        setSensorStyle(id, SensorStyles.next(current ?: SensorStyles.defaultFor(id)))
    }

    fun setThemeMode(mode: String) {
        if (mode !in setOf("dark", "light", "system")) return
        updateSettings { it.copy(themeMode = mode) }
    }

    private fun updateSettings(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        store.saveSettings(updated)
    }

    // --------------------------------------------------------------- sauvegarde

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.exportBackup(uri) } }
                .onSuccess {
                    log("sauvegarde", "Sauvegarde exportée")
                    _message.value = "Sauvegarde exportée"
                }
                .onFailure { _message.value = it.message ?: "Export impossible" }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.importBackup(uri) } }
                .onSuccess { summary ->
                    _cars.value = store.loadCars()
                    _settings.value = store.loadSettings()
                    loadJournal()
                    log("sauvegarde", "Sauvegarde restaurée : $summary")
                    _message.value = "Sauvegarde restaurée : $summary"
                }
                .onFailure { _message.value = it.message ?: "Restauration impossible" }
        }
    }
}
