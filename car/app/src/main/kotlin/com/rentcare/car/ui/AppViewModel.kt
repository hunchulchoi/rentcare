package com.rentcare.car.ui

import ai.pleos.playground.vehicle.constant.area.WheelValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rentcare.car.BuildConfig
import com.rentcare.car.RentcareApp
import androidx.compose.ui.graphics.asImageBitmap
import ai.pleos.playground.navi.data.NavigationScreenRequest
import com.rentcare.car.data.ApiResult
import com.rentcare.car.data.PendingReport
import com.rentcare.car.pleos.ActiveTelltale
import com.rentcare.car.pleos.GeoPoint
import com.rentcare.core.Assessment
import com.rentcare.core.ChargeOption
import com.rentcare.core.DeparturePlan
import com.rentcare.core.DeparturePlanner
import com.rentcare.core.GasolineModel
import com.rentcare.core.IncidentCreated
import com.rentcare.core.IncidentRequest
import com.rentcare.core.IncidentType
import com.rentcare.core.LocationDto
import com.rentcare.core.LocationSource
import com.rentcare.core.NOTE_MAX_LENGTH
import com.rentcare.core.Powertrain
import com.rentcare.core.WaypointCoords
import com.rentcare.core.TireCheck
import com.rentcare.core.LevelSupport
import com.rentcare.core.AttachPolicy
import com.rentcare.core.LampSignal
import com.rentcare.core.Reading
import com.rentcare.core.Gear
import com.rentcare.core.InputGate
import com.rentcare.core.Rental
import com.rentcare.core.RentalWork
import com.rentcare.core.RentalDto
import com.rentcare.core.ReturnPlanner
import com.rentcare.core.TripEstimate
import com.rentcare.core.VehicleDto
import com.rentcare.core.VehicleState
import com.rentcare.core.Warnings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import com.rentcare.core.PhoneReportLink

data class ReturnInputs(
    val gasolineModel: GasolineModel? = null,
    val fuelWonPerLiter: Int? = null,
    val charger: ChargeOption? = null,
    val stations: List<GeoPoint> = emptyList(),
    /** Debug build only: simulated fuel % for the demo (the emulator has no fuel API). Always labelled. */
    val demoFuelPercent: Int? = null,
)

data class ReturnUi(
    val state: VehicleState = VehicleState(),
    /** The level is the simulated demo value, not read from the car. Shown as such, never sent to the vendor. */
    val simulatedLevel: Boolean = false,
    val trip: TripEstimate? = null,
    val tripError: String? = null,
    val readError: String? = null,
    val assessment: Assessment? = null,
    val departure: DeparturePlan? = null,
    val loading: Boolean = false,
    val routeMessage: String? = null,
    /** Navigation request in flight (route, place search, waypoint import); buttons wait, a spinner shows. */
    val naviBusy: Boolean = false,
    /** Stops the user set in the car navigation (read back, not sent by us). */
    val naviStops: List<NaviStop> = emptyList(),
)

/** A waypoint from the car navigation. Coordinates may be missing (emulator 2.1.0 reports 0,0). */
data class NaviStop(val name: String, val address: String?, val remainMeters: Int, val latitude: Double?, val longitude: Double?)

data class LocationDraft(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val readAtMillis: Long?,
    val source: LocationSource,
    val edited: Boolean,
)

data class IncidentDraft(
    val type: IncidentType? = null,
    val note: String = "",
    val location: LocationDraft? = null,
    val locationLoading: Boolean = false,
    val locationError: Boolean = false,
    val confirmedRecipient: Boolean = false,
)

sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    /** Not received. Show retry; the same idempotency key is kept. */
    data class Failed(val message: String, val retryable: Boolean) : SendState
}

data class ReportUi(
    val draft: IncidentDraft = IncidentDraft(),
    val send: SendState = SendState.Idle,
    val received: IncidentCreated? = null,
    /** Stored but not yet received by the server. While set, the form is replaced by a resend panel. */
    val pending: IncidentRequest? = null,
    val acknowledged: Boolean = false,
    /** Latest status from the server (RECEIVED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED). */
    val status: com.rentcare.core.IncidentStatus? = null,
    /** Vendor's estimate while handling (ISO), shown as their estimate only. */
    val expectedAt: String? = null,
    val expectedSetAt: String? = null,
    val acknowledgedAt: String? = null,
    val resolvedAt: String? = null,
    val attachments: Int = 0,
    val statusError: String? = null,
    val statusLoading: Boolean = false,
)

data class ProbeRow(val name: String, val value: String, val ok: Boolean, val atMillis: Long)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val deps = app as RentcareApp
    private val planner = ReturnPlanner()

    private val _rental = MutableStateFlow(deps.store.loadRental())
    val rental: StateFlow<Rental?> = _rental

    private val _inputs = MutableStateFlow(ReturnInputs())
    val inputs: StateFlow<ReturnInputs> = _inputs

    private val _ret = MutableStateFlow(ReturnUi())
    val ret: StateFlow<ReturnUi> = _ret

    private val _report = MutableStateFlow(ReportUi(received = deps.store.loadReceived(), pending = deps.store.loadPending()?.request, status = deps.store.loadReceived()?.status))
    val report: StateFlow<ReportUi> = _report

    private val _telltales = MutableStateFlow<List<ActiveTelltale>>(emptyList())
    val telltales: StateFlow<List<ActiveTelltale>> = _telltales

    private val _gear = MutableStateFlow<Gear?>(null)

    /** RENTCARE-17: detailed input only when the platform (or Park, if unknown) allows it. */
    val inputGate: StateFlow<InputGate.Decision> =
        combine(deps.driving.requiresDistractionOptimization, _gear) { dO, g -> InputGate.decide(dO, g) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, InputGate.Decision.BLOCKED_NOT_PARKED)

    private val _probe = MutableStateFlow<List<ProbeRow>>(emptyList())
    val probe: StateFlow<List<ProbeRow>> = _probe
    private val _probeRunning = MutableStateFlow(false)
    val probeRunning: StateFlow<Boolean> = _probeRunning

    /** RENTCARE-13: rental code for the phone basic report. */
    data class PhoneLinkUi(val link: PhoneReportLink? = null, val registering: Boolean = false, val error: String? = null)

    private val _phoneLink = MutableStateFlow(PhoneLinkUi(deps.store.loadPhoneLink()))
    val phoneLink: StateFlow<PhoneLinkUi> = _phoneLink

    /** All work for the current rental; reset on rental end (Codex review: late replies). */
    private val work = RentalWork(viewModelScope)
    private var statusJob: Job? = null
    private var liveJob: Job? = null

    init {
        deps.vehicle.start()
        deps.navi.start()
        deps.driving.start()
        viewModelScope.launch {
            // Staleness is time-based: re-run the planner so a 10-minute-old value turns into
            // "info missing" even when no new event arrives (Codex review).
            while (isActive) {
                delay(30_000)
                recompute()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                runCatching { deps.vehicle.gearUpdates().collect { _gear.value = it } }
                _gear.value = null
                delay(5_000)
            }
        }
        if (_report.value.received != null) startStatusPolling()
        // Rentals saved before RENTCARE-13, or a registration that failed offline.
        if (_rental.value != null && _phoneLink.value.link?.registered == null) registerPhoneLink()
        viewModelScope.launch {
            // Re-subscribe when the rental (and so the powertrain) changes.
            _rental.collect { r -> startLiveLevel(r?.powertrain) }
        }
    }

    /**
     * RENTCARE-6: keep the level fresh from SDK change events, so the verdict updates
     * while the user charges/refuels without pressing refresh.
     */
    private fun startLiveLevel(powertrain: Powertrain?) {
        liveJob?.cancel()
        if (powertrain == null) return
        liveJob = work.launch { token ->
            while (isActive) {
                val flow = if (powertrain == Powertrain.EV) deps.vehicle.socUpdates() else deps.vehicle.fuelUpdates()
                runCatching {
                    flow.collect { v ->
                        if (v == null) return@collect
                        val reading = Reading(v, System.currentTimeMillis())
                        _ret.update {
                            it.copy(state = if (powertrain == Powertrain.EV) it.state.copy(soc = reading) else it.state.copy(fuelPercent = reading))
                        }
                        recompute()
                    }
                }
                delay(5_000) // listener failed (unsupported / service restart): retry later
            }
        }
    }

    val language: String get() = _rental.value?.language ?: "ko"

    /**
     * Vendor enrollment code typed on the rental screen (non-demo builds, docs/api.md v13). Memory only;
     * used by the next new registration, then cleared. Demo builds register as the demo vendor without one.
     */
    private val _enrollmentCode = MutableStateFlow("")
    val enrollmentCode: StateFlow<String> = _enrollmentCode
    fun setEnrollmentCode(code: String) { _enrollmentCode.value = code.take(16) }

    /** Register (or re-register) the saved rental. Offline is fine: the card shows a retry. */
    fun registerPhoneLink() {
        work.launch { registerNow() }
    }

    /** One registration at a time: a replay rotates the token, so two in flight would void each other. */
    private val registration = kotlinx.coroutines.sync.Mutex()

    /**
     * The car's rental token (its only server credential, RENTCARE-36), registering first when needed.
     * Null when there is no rental or the server cannot be reached / refuses (the card shows why).
     */
    private suspend fun registerNow(): String? = registration.withLock {
        val r = _rental.value ?: return@withLock null
        val plan = PhoneReportLink.plan(_phoneLink.value.link, r) { UUID.randomUUID().toString() }
        plan.releaseToken?.let { old -> viewModelScope.launch { withTimeoutOrNull(5_000) { deps.api.releaseRental(old) } } }
        if (plan.link != _phoneLink.value.link) {
            deps.store.savePhoneLink(plan.link)
            _phoneLink.value = PhoneLinkUi(plan.link)
        }
        if (!plan.register) return@withLock plan.link.registered?.rentalToken
        // Non-demo: the code is stored with the link (encrypted) before sending, so a lost response can be
        // replayed with the same key and code after an app restart (recheck P2).
        var link = plan.link
        if (!BuildConfig.DEMO_MODE && link.enrollmentCode == null && _enrollmentCode.value.isNotBlank()) {
            link = link.withEnrollmentCode(_enrollmentCode.value)
            deps.store.savePhoneLink(link)
        }
        _phoneLink.value = PhoneLinkUi(link, registering = true)
        val code = if (BuildConfig.DEMO_MODE) null else link.enrollmentCode
        val res = deps.api.registerRental(PhoneReportLink.request(r, BuildConfig.DEMO_MODE, code), link.idempotencyKey)
        if (_rental.value == null) return@withLock null // rental ended meanwhile
        when (res) {
            is ApiResult.Ok -> {
                val done = link.registeredWith(res.value)
                deps.store.savePhoneLink(done)
                _phoneLink.value = PhoneLinkUi(done)
                _enrollmentCode.value = ""
                res.value.rentalToken
            }
            is ApiResult.Http -> {
                // A refused code (wrong, used, expired) is useless for a retry: drop it so staff can enter a new one.
                val kept = if (res.code == 401) link.withEnrollmentCode(null).also { deps.store.savePhoneLink(it) } else link
                _phoneLink.value = PhoneLinkUi(kept, error = res.error?.error ?: "http_${res.code}")
                null
            }
            is ApiResult.Network -> {
                _phoneLink.update { it.copy(registering = false, error = "network") }
                null
            }
        }
    }

    /** Server says the token is no longer valid (expired / released elsewhere): register again next time. */
    private fun forgetRegistration() {
        val link = _phoneLink.value.link ?: return
        val reset = link.copy(registered = null, idempotencyKey = UUID.randomUUID().toString())
        deps.store.savePhoneLink(reset)
        _phoneLink.value = PhoneLinkUi(reset)
    }

    // ---- Rental ----

    /** RENTCARE-35: detailed input, saves and contract edits only while parked (UI is disabled too). */
    private val parked get() = inputGate.value == InputGate.Decision.ALLOWED

    fun saveRental(r: Rental) {
        if (!parked) return
        deps.store.saveRental(r)
        _rental.value = r
        refreshReturn()
        registerPhoneLink()
    }

    fun setLanguage(lang: String) {
        _rental.value?.let { saveRental(it.copy(language = lang)) }
    }

    /** Rental ended: wipe rental data, reports, codes. Vehicle state is not ours to reset. */
    fun endRental() {
        if (!parked) return
        // Tell the server first (best effort, 5 s): the attach code and car token stop working.
        // Offline is fine: local data is wiped anyway and the code expires on its own (24 h).
        _report.value.received?.let { rec ->
            viewModelScope.launch { withTimeoutOrNull(5_000) { deps.api.release(rec.id, rec.incidentToken) } }
        }
        _phoneLink.value.link?.registered?.let { reg ->
            viewModelScope.launch { withTimeoutOrNull(5_000) { deps.api.releaseRental(reg.rentalToken) } }
        }
        _phoneLink.value = PhoneLinkUi()
        work.reset() // cancel sends, polls, reads; invalidate late callbacks first
        _uploads.value = emptyList()
        statusJob?.cancel()
        liveJob?.cancel()
        deps.store.clearAll()
        _rental.value = null
        _ret.value = ReturnUi()
        _report.value = ReportUi()
        _inputs.value = ReturnInputs()
    }

    // ---- Return preparation ----

    fun updateInputs(f: (ReturnInputs) -> ReturnInputs) {
        if (!parked) return
        _inputs.update(f)
        recompute()
    }

    /** Debug only: set or clear the simulated demo fuel level, then re-read. */
    fun setDemoFuel(percent: Int?) {
        if (!BuildConfig.DEBUG || !parked) return
        _inputs.update { it.copy(demoFuelPercent = percent?.coerceIn(0, 100)) }
        refreshReturn()
    }

    fun refreshReturn() {
        val r = _rental.value ?: return
        work.launch { token ->
            _ret.update { it.copy(loading = true, readError = null, tripError = null) }
            val now = System.currentTimeMillis()
            var readError: String? = null
            // RENTCARE-6: check the capability first; unsupported / denied levels are not read.
            val support = runCatching {
                if (r.powertrain == Powertrain.GASOLINE) deps.vehicle.fuelPercentAccess() else deps.vehicle.evSocAccess()
            }.fold({ LevelSupport.of(it, null) }, { LevelSupport.of(null, it.message ?: it.javaClass.simpleName) })
            val readable = support == LevelSupport.SUPPORTED || support == LevelSupport.UNKNOWN
            val demoFuel = _inputs.value.demoFuelPercent?.takeIf { BuildConfig.DEBUG && r.powertrain == Powertrain.GASOLINE }
            val state = when (r.powertrain) {
                Powertrain.GASOLINE -> if (demoFuel != null) {
                    VehicleState(fuelPercent = Reading(demoFuel, now), levelSupport = LevelSupport.SUPPORTED)
                } else {
                    val fuel = if (!readable) null else runCatching { deps.vehicle.fuelPercent() }
                        .onFailure { readError = it.message }.getOrNull()
                    VehicleState(fuelPercent = fuel?.let { Reading(it, now) }, levelSupport = support)
                }
                Powertrain.EV -> {
                    val soc = if (!readable) null else runCatching { deps.vehicle.evSoc() }.onFailure { readError = it.message }.getOrNull()
                    val cap = runCatching { deps.vehicle.evBatteryCapacity() }.getOrNull()
                    VehicleState(
                        soc = soc?.let { Reading(it, now) }, batteryCapacityKwh = cap?.let { it / 1000.0 }?.takeIf { it > 0 },
                        levelSupport = support,
                    )
                }
            }
            val (rawTrip, tripError) = readTrip(r)
            val trip = rawTrip?.copy(socAtRequest = state.soc?.value)
            _telltales.value = runCatching { deps.vehicle.activeTelltales() }.getOrDefault(emptyList())
            _ret.update {
                it.copy(state = state, simulatedLevel = demoFuel != null, trip = trip, tripError = tripError,
                    readError = if (demoFuel != null) null else readError, loading = false)
            }
            recompute()
        }
    }

    private suspend fun readTrip(r: Rental): Pair<TripEstimate?, String?> {
        val lat = r.returnPlace.latitude
        val lon = r.returnPlace.longitude
        if (lat == null || lon == null) return null to "NO_COORDINATES"
        val res = deps.navi.trip(GeoPoint(lat, lon, r.returnPlace.name, r.returnPlace.address))
        return res.fold(
            onSuccess = { ev ->
                val i = ev.info
                // Emulator 2.1.0: distance in meters, eta in seconds of travel time (515,725 m / 31,565 s Seongnam→Jeju).
                TripEstimate(i.distance, i.eta, i.estimatedSocAtDest.takeIf { it in 0..100 }, ev.atMillis) to null
            },
            onFailure = { null to (it.message ?: "trip failed") },
        )
    }

    private fun recompute() {
        val r = _rental.value ?: return
        val ui = _ret.value
        val inp = _inputs.value
        val now = System.currentTimeMillis()
        val a = planner.assess(r, ui.state, ui.trip, now, inp.gasolineModel, inp.fuelWonPerLiter, inp.charger)
        _ret.update { it.copy(assessment = a, departure = DeparturePlanner.plan(r, ui.trip, a, now)) }
    }

    fun startGuidance() {
        val r = _rental.value ?: return
        val lat = r.returnPlace.latitude
        val lon = r.returnPlace.longitude
        if (lat == null || lon == null) {
            _ret.update { it.copy(routeMessage = "NO_COORDINATES") }
            return
        }
        naviTask { token ->
            val res = deps.navi.routeWithWaypoints(
                GeoPoint(lat, lon, r.returnPlace.name, r.returnPlace.address),
                _inputs.value.stations.take(2),
            )
            if (work.isCurrent(token)) _ret.update { it.copy(routeMessage = res.fold({ "ROUTE_OK" }, { "ROUTE_FAIL: ${it.message}" })) }
        }
    }

    /** One navigation request at a time; [ReturnUi.naviBusy] is set until it succeeds, fails or times out. */
    private fun naviTask(block: suspend (RentalWork.Token) -> Unit) {
        if (_ret.value.naviBusy) return
        _ret.update { it.copy(naviBusy = true, routeMessage = null) }
        work.launch { token ->
            try {
                block(token)
            } finally {
                if (work.isCurrent(token)) _ret.update { it.copy(naviBusy = false) }
            }
        }
    }

    /** Let the user pick stops in the car navigation (no typing): chargers for EV, a place search otherwise. */
    fun openStationSearch(gasolineKeyword: String) {
        val ev = _rental.value?.powertrain == Powertrain.EV
        naviTask { token ->
            val ok = deps.navi.openScreen(
                if (ev) NavigationScreenRequest.NearbyChargingStations else NavigationScreenRequest.SearchPlace(gasolineKeyword),
            )
            if (work.isCurrent(token) && !ok) _ret.update { it.copy(routeMessage = "NAVI_UNAVAILABLE") }
        }
    }

    /** Read the waypoints the user set in the car navigation (they are already in its route). */
    fun importNaviWaypoints() {
        naviTask { token ->
            val res = deps.navi.waypoints()
            if (!work.isCurrent(token)) return@naviTask
            res.onSuccess { list ->
                val stops = list.map { w ->
                    val c = WaypointCoords.pick(w.latitude, w.longitude, w.displayLatitude, w.displayLongitude)
                    NaviStop(w.name, w.address?.ifBlank { null }, w.remainDistance, c?.first, c?.second)
                }
                _ret.update {
                    it.copy(naviStops = stops, routeMessage = if (stops.isEmpty()) "NO_WAYPOINTS" else "IMPORTED ${stops.size}")
                }
            }.onFailure { e -> _ret.update { it.copy(routeMessage = "ROUTE_FAIL: ${e.message}") } }
        }
    }

    // ---- Car direct attach (RENTCARE-12) ----

    data class CarUpload(val key: Long, val uri: String, val sent: Long, val total: Long, val state: State) {
        sealed interface State {
            data object Waiting : State
            data object Uploading : State
            data object Done : State
            data class Failed(val message: String, val retryable: Boolean) : State
            data class Rejected(val check: AttachPolicy.Check) : State
        }
    }

    private val _uploads = MutableStateFlow<List<CarUpload>>(emptyList())
    val uploads: StateFlow<List<CarUpload>> = _uploads
    private var uploadKey = 0L

    /** Files the renter picked in the car's photo picker. Uploaded one by one. */
    fun attachFromCar(uris: List<android.net.Uri>) {
        val added = uris.map { CarUpload(++uploadKey, it.toString(), 0, 0, CarUpload.State.Waiting) }
        _uploads.update { it + added }
        added.forEach { upload(it.key) }
    }

    fun retryUpload(key: Long) {
        _uploads.update { l -> l.map { if (it.key == key) it.copy(state = CarUpload.State.Waiting, sent = 0) else it } }
        upload(key)
    }

    private fun upload(key: Long) {
        val rec = _report.value.received ?: return
        work.launch { token ->
            val item = _uploads.value.firstOrNull { it.key == key } ?: return@launch
            val cr = getApplication<android.app.Application>().contentResolver
            val uri = android.net.Uri.parse(item.uri)
            val mime = cr.getType(uri)
            val size = runCatching {
                cr.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                }
            }.getOrNull()
            val inFlight = _uploads.value.count { it.key != key && it.state is CarUpload.State.Uploading }
            fun set(f: (CarUpload) -> CarUpload) = _uploads.update { l -> l.map { if (it.key == key) f(it) else it } }
            when (val check = AttachPolicy.check(mime, size, already = _report.value.attachments + inFlight)) {
                is AttachPolicy.Check.Ok -> {
                    set { it.copy(state = CarUpload.State.Uploading, total = size!!) }
                    val res = deps.api.uploadAttachment(
                        rec.id, rec.incidentToken, check.fileName, mime!!, size!!,
                        open = { cr.openInputStream(uri) ?: throw java.io.IOException("cannot open") },
                        onProgress = { sent, total -> set { it.copy(sent = sent, total = total) } },
                    )
                    if (!work.isCurrent(token)) return@launch
                    when (res) {
                        is ApiResult.Ok -> {
                            set { it.copy(state = CarUpload.State.Done, sent = it.total) }
                            _report.update { it.copy(attachments = res.value.attachments) }
                        }
                        is ApiResult.Network -> set { it.copy(state = CarUpload.State.Failed(res.message, retryable = true)) }
                        is ApiResult.Http -> set {
                            it.copy(state = CarUpload.State.Failed("${res.code} ${res.error?.error ?: ""}".trim(), retryable = res.code >= 500 || res.code == 429))
                        }
                    }
                }
                else -> set { it.copy(state = CarUpload.State.Rejected(check)) }
            }
        }
    }

    // ---- Incident report ----

    fun updateDraft(f: (IncidentDraft) -> IncidentDraft) = _report.update { it.copy(draft = f(it.draft)) }

    /** Read warning lights and pre-select a matching report type (renter can change it). */
    private val _tire = MutableStateFlow<TireCheck.Result?>(null)
    /** RENTCARE-33: what the car's own signals say about tire pressure (no diagnosis). */
    val tire: StateFlow<TireCheck.Result?> = _tire

    private val _volt = MutableStateFlow(LampSignal.UNKNOWN)
    /** RENTCARE-32: 12V battery low lamp (dashboard). ON = low signal only; no prediction. */
    val volt: StateFlow<LampSignal> = _volt

    fun loadWarnings() {
        work.launch { token ->
            val read = runCatching { deps.vehicle.activeTelltales() }
            val list = read.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            val lamp = read.getOrNull()?.let { l ->
                val tpms = l.firstOrNull { it.type.name == "TPMS" }
                TireCheck.LampReading(
                    when (tpms?.state?.name) { null, "OFF" -> TireCheck.Lamp.OFF; "BLINK" -> TireCheck.Lamp.BLINK; else -> TireCheck.Lamp.ON },
                    now,
                )
            }
            // Per-wheel values only where the car supports them (emulator: pressure unsupported, threshold 137).
            val wheels = mapOf(
                TireCheck.Wheel.FL to WheelValue.LEFT_FRONT, TireCheck.Wheel.FR to WheelValue.RIGHT_FRONT,
                TireCheck.Wheel.RL to WheelValue.LEFT_REAR, TireCheck.Wheel.RR to WheelValue.RIGHT_REAR,
            )
            // Per-wheel pressure needs PRIVATE_CAR_SAFETY: debug builds only (RENTCARE-39).
            val ps = deps.privateSignals
            val pressures = if (!ps.available) emptyMap()
            else wheels.mapNotNull { (w, v) -> runCatching { ps.tirePressure(v) }.getOrNull()?.let { w to it } }.toMap()
            val thresholds = wheels.mapNotNull { (w, v) -> runCatching { deps.vehicle.lowTirePressure(v) }.getOrNull()?.let { w to it } }.toMap()
            if (!work.isCurrent(token)) return@launch
            _telltales.value = list
            val tire = TireCheck.of(lamp, pressures, thresholds, now)
            _tire.value = tire
            _volt.value = LampSignal.of(read.getOrNull()?.map { it.type.name }, if (read.isSuccess) now else null, "LOW_VOLT_BATTERY", now)
            val suggested = if (tire.warningCodes().isNotEmpty()) IncidentType.TIRE else Warnings.suggestType(list.map { it.type.name })
            if (suggested != null) updateDraft { if (it.type == null) it.copy(type = suggested) else it }
        }
    }

    fun loadVehicleLocation() {
        work.launch { token ->
            updateDraft { it.copy(locationLoading = true, locationError = false) }
            val loc = deps.navi.currentLocation()
            updateDraft {
                if (loc == null) {
                    it.copy(locationLoading = false, locationError = true)
                } else {
                    it.copy(
                        locationLoading = false,
                        location = LocationDraft(
                            loc.info.latitude, loc.info.longitude, loc.info.address.ifBlank { null },
                            loc.atMillis, LocationSource.VEHICLE, edited = false,
                        ),
                    )
                }
            }
        }
    }

    fun editLocation(lat: Double, lon: Double) = updateDraft { d ->
        val prev = d.location
        d.copy(
            location = LocationDraft(
                lat, lon, prev?.address, prev?.readAtMillis,
                prev?.source ?: LocationSource.MANUAL, edited = true,
            ),
        )
    }

    /** Send a new report from the draft. An unsent report must be resent or discarded first. */
    fun send() {
        val r = _rental.value ?: return
        if (_report.value.pending != null) return
        val draft = _report.value.draft
        val type = draft.type ?: return
        if (!draft.confirmedRecipient || _report.value.send == SendState.Sending) return
        if (inputGate.value != InputGate.Decision.ALLOWED) return

        val pending = PendingReport(UUID.randomUUID().toString(), buildRequest(r, type, draft))
        deps.store.savePending(pending)
        _report.update { it.copy(pending = pending.request) }
        transmit(pending)
    }

    /**
     * Resend the stored report unchanged (same key and body), also after an app restart.
     * One tap, no typing, so it is not gated by [inputGate].
     */
    fun resend() {
        if (_report.value.send == SendState.Sending) return
        val pending = deps.store.loadPending() ?: return _report.update { it.copy(pending = null) }
        transmit(pending)
    }

    private fun transmit(pending: PendingReport) {
        work.launch { token ->
            _report.update { it.copy(send = SendState.Sending) }
            // v13: the rental token is the car's credential; register first if the rental is not registered yet.
            val rentalToken = registerNow()
            if (!work.isCurrent(token)) return@launch
            if (rentalToken == null) {
                _report.update { it.copy(send = SendState.Failed(_phoneLink.value.error ?: "not_registered", retryable = true)) }
                return@launch
            }
            val res = deps.api.createIncident(pending.request, pending.idempotencyKey, rentalToken)
            if (!work.isCurrent(token)) return@launch
            if (res is ApiResult.Http && res.code == 401) {
                // Token expired or revoked: keep the report, register again on the next try.
                forgetRegistration()
                _report.update { it.copy(send = SendState.Failed("401 ${res.error?.error ?: ""}".trim(), retryable = true)) }
                return@launch
            }
            when (res) {
                is ApiResult.Ok -> {
                    deps.store.saveReceived(res.value)
                    deps.store.savePending(null)
                    _report.update {
                        it.copy(send = SendState.Idle, received = res.value, pending = null, draft = IncidentDraft(), status = res.value.status)
                    }
                    startStatusPolling()
                }
                is ApiResult.Network -> _report.update {
                    it.copy(send = SendState.Failed(res.message, retryable = true))
                }
                is ApiResult.Http -> {
                    val retryable = res.code >= 500 || res.code == 429
                    // Bad request: drop it so the user can fix the draft (kept in memory) and send a new one.
                    if (!retryable) deps.store.savePending(null)
                    _report.update {
                        it.copy(
                            send = SendState.Failed("${res.code} ${res.error?.error ?: ""}".trim(), retryable),
                            pending = if (retryable) it.pending else null,
                        )
                    }
                }
            }
        }
    }

    private val tileCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(48)

    /** Decoded map tile for the location map, cached in memory. */
    suspend fun mapTile(z: Int, x: Int, y: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val key = "$z/$x/$y"
        tileCache.get(key)?.let { return it }
        val rentalToken = _phoneLink.value.link?.registered?.rentalToken ?: return null
        val bytes = deps.api.mapTile(z, x, y, rentalToken) ?: return null
        val img = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        tileCache.put(key, img)
        return img
    }

    /** User discards an unsent report (e.g. to change it). */
    fun discardPending() {
        deps.store.savePending(null)
        _report.update { it.copy(send = SendState.Idle, pending = null) }
    }

    fun newReport() {
        _uploads.value = emptyList()
        statusJob?.cancel()
        deps.store.saveReceived(null)
        _report.value = ReportUi()
    }

    private fun buildRequest(r: Rental, type: IncidentType, d: IncidentDraft): IncidentRequest {
        val st = _ret.value.state
        val readAt = (st.soc?.readAtMillis ?: st.fuelPercent?.readAtMillis)?.let { Instant.ofEpochMilli(it).toString() }
        return IncidentRequest(
            vendorId = r.vendorId.ifBlank { BuildConfig.VENDOR_ID },
            type = type,
            language = r.language,
            note = d.note.trim().take(NOTE_MAX_LENGTH).ifBlank { null },
            occurredAt = Instant.now().toString(),
            demo = BuildConfig.DEMO_MODE,
            rental = RentalDto(
                r.rentalId, r.vehiclePlate, r.vehicleModel, r.powertrain, r.returnPlace.name,
                Instant.ofEpochMilli(r.returnAtMillis).toString(),
            ),
            // A simulated demo level is not a car reading: never sent as one.
            vehicle = if (readAt == null || _ret.value.simulatedLevel) null else VehicleDto(st.fuelPercent?.value, st.soc?.value, readAt),
            warnings = Warnings.forRequest(_telltales.value.map { it.type.name }, extra = _tire.value?.warningCodes().orEmpty()),
            location = d.location?.let {
                LocationDto(
                    it.latitude, it.longitude, it.address,
                    it.readAtMillis?.let { ms -> Instant.ofEpochMilli(ms).toString() },
                    it.source, it.edited,
                )
            },
        )
    }

    private fun startStatusPolling() {
        statusJob?.cancel()
        statusJob = work.launch { token ->
            while (isActive) {
                refreshStatusOnce()
                // Keep polling through 조치중 until the vendor marks it done.
                if (_report.value.status?.isFinal == true) break
                delay(15_000)
            }
        }
    }

    fun refreshStatusOnce() {
        val rec = _report.value.received ?: return
        work.launch { token ->
            _report.update { it.copy(statusLoading = true) }
            val res = deps.api.status(rec.id, rec.incidentToken)
            if (!work.isCurrent(token)) return@launch
            _report.update { it.copy(statusLoading = false) }
            when (res) {
                is ApiResult.Ok -> _report.update {
                    it.copy(
                        acknowledged = res.value.status.reached(com.rentcare.core.IncidentStatus.ACKNOWLEDGED),
                        status = res.value.status,
                        expectedAt = res.value.expectedAt,
                        acknowledgedAt = res.value.acknowledgedAt,
                        resolvedAt = res.value.resolvedAt,
                        expectedSetAt = res.value.expectedSetAt,
                        attachments = res.value.attachments,
                        statusError = null,
                    )
                }
                is ApiResult.Http -> _report.update { it.copy(statusError = "${res.code}") }
                is ApiResult.Network -> _report.update { it.copy(statusError = res.message) }
            }
        }
    }

    // ---- SDK probe (RENTCARE-1) ----

    fun runProbe() {
        if (!BuildConfig.DEBUG || _probeRunning.value) return
        viewModelScope.launch {
            _probeRunning.value = true
            _probe.value = emptyList()
            val v = deps.vehicle
            probe("Fuel.checkFuelPercentCapabilities") { v.fuelPercentAccess() }
            probe("Fuel.getFuelPercent (%)") { v.fuelPercent().toString() }
            probe("EvBattery.checkEvBatteryStateOfChargeCapabilities") { v.evSocAccess() }
            probe("EvBattery.getEvBatteryStateOfCharge (%)") { v.evSoc().toString() }
            probe("EvBattery.getEvBatteryCapacity (Wh)") { v.evBatteryCapacity().toString() }
            probe("Odometer.getRangeRemainingDistance (unit?)") { v.rangeRemaining().toString() }
            probe("Gear.getCurrentGearSelectionState") { v.gear() }
            probe("Tire.checkTirePressureCapabilities (wheels)") { deps.privateSignals.tirePressureWheels().joinToString().ifEmpty { "(none)" } }
            probe("Tire.getTirePressure (LEFT_FRONT)") { deps.privateSignals.tirePressure(WheelValue.LEFT_FRONT).toString() }
            probe("Tire.checkLowTirePressureCapabilities (wheels)") { v.lowTirePressureWheels().joinToString().ifEmpty { "(none)" } }
            for (w in listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR)) {
                probe("Tire.getLowTirePressure ($w)") { v.lowTirePressure(w).toString() }
            }
            probe("Safety.getTelltalesInfo (ON/BLINK)") {
                v.activeTelltales().joinToString { "${it.type}:${it.state}" }.ifEmpty { "(none on)" }
            }
            probe("CarInfo.getCapabilityInfo (keys)") { deps.privateSignals.capabilityKeys().joinToString().ifEmpty { "(empty)" } }
            val n = deps.navi
            probe("NaviHelper.initialize") {
                n.start()
                withTimeoutOrNull(5_000) { n.connected.first { it != null } }
                when (n.connected.value) {
                    true -> "connected"
                    false -> throw IllegalStateException(n.connectError ?: "failed")
                    null -> throw IllegalStateException("timeout")
                }
            }
            probe("NaviHelper.getCurrentLocationInfo") {
                val l = n.currentLocation() ?: throw IllegalStateException("no event in 5s")
                "lat=${l.info.latitude}, lon=${l.info.longitude}, addr=${l.info.address}"
            }
            probe("NaviHelper.getWaypointInfo → onWayPointInfo") {
                n.waypoints().getOrThrow().joinToString(" | ") {
                    "${it.name}: lat=${it.latitude}, lon=${it.longitude}, disp=${it.displayLatitude},${it.displayLongitude}, " +
                        "poi=${it.poiProvider}/${it.poiId.isNotBlank()}, remain=${it.remainDistance}m"
                }.ifEmpty { "(no waypoints)" }
            }
            probe("NaviHelper.requestTrip → onTripInfo (Jeju airport)") {
                val t = n.trip(GeoPoint(33.5066, 126.4929, "제주국제공항")).getOrThrow().info
                "distance=${t.distance}, eta=${t.eta}, estimatedSocAtDest=${t.estimatedSocAtDest}, energy=${t.energyConsumption}"
            }
            _probeRunning.value = false
        }
    }

    private suspend fun probe(name: String, block: suspend () -> String) {
        val row = try {
            ProbeRow(name, block(), ok = true, System.currentTimeMillis())
        } catch (e: Exception) {
            ProbeRow(name, e.message ?: e.javaClass.simpleName, ok = false, System.currentTimeMillis())
        }
        // Security guide: no values in logs (location, waypoints, addresses). The screen shows them.
        if (BuildConfig.DEBUG) android.util.Log.i("RentcareProbe", com.rentcare.core.ProbeLog.line(row.name, row.ok))
        _probe.update { it + row }
    }

    override fun onCleared() {
        // Gateways live in the Application; nothing pending here to release.
        super.onCleared()
    }
}

