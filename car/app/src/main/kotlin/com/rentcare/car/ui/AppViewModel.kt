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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID

data class ReturnInputs(
    val gasolineModel: GasolineModel? = null,
    val fuelWonPerLiter: Int? = null,
    val charger: ChargeOption? = null,
    val stations: List<GeoPoint> = emptyList(),
)

data class ReturnUi(
    val state: VehicleState = VehicleState(),
    val trip: TripEstimate? = null,
    val tripError: String? = null,
    val readError: String? = null,
    val assessment: Assessment? = null,
    val departure: DeparturePlan? = null,
    val loading: Boolean = false,
    val routeMessage: String? = null,
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
    val attachments: Int = 0,
    val statusError: String? = null,
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

    // ---- Rental ----

    fun saveRental(r: Rental) {
        deps.store.saveRental(r)
        _rental.value = r
        refreshReturn()
    }

    fun setLanguage(lang: String) {
        _rental.value?.let { saveRental(it.copy(language = lang)) }
    }

    /** Rental ended: wipe rental data, reports, codes. Vehicle state is not ours to reset. */
    fun endRental() {
        work.reset() // cancel sends, polls, reads; invalidate late callbacks first
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
        _inputs.update(f)
        recompute()
    }

    fun refreshReturn() {
        val r = _rental.value ?: return
        work.launch { token ->
            _ret.update { it.copy(loading = true, readError = null, tripError = null) }
            val now = System.currentTimeMillis()
            var readError: String? = null
            val state = when (r.powertrain) {
                Powertrain.GASOLINE -> {
                    val fuel = runCatching { deps.vehicle.fuelPercent() }
                        .onFailure { readError = it.message }.getOrNull()
                    VehicleState(fuelPercent = fuel?.let { Reading(it, now) })
                }
                Powertrain.EV -> {
                    val soc = runCatching { deps.vehicle.evSoc() }.onFailure { readError = it.message }.getOrNull()
                    val cap = runCatching { deps.vehicle.evBatteryCapacity() }.getOrNull()
                    VehicleState(soc = soc?.let { Reading(it, now) }, batteryCapacityKwh = cap?.let { it / 1000.0 }?.takeIf { it > 0 })
                }
            }
            val (rawTrip, tripError) = readTrip(r)
            val trip = rawTrip?.copy(socAtRequest = state.soc?.value)
            _telltales.value = runCatching { deps.vehicle.activeTelltales() }.getOrDefault(emptyList())
            _ret.update { it.copy(state = state, trip = trip, tripError = tripError, readError = readError, loading = false) }
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
        work.launch { token ->
            val res = deps.navi.routeWithWaypoints(
                GeoPoint(lat, lon, r.returnPlace.name, r.returnPlace.address),
                _inputs.value.stations.take(2),
            )
            _ret.update { it.copy(routeMessage = res.fold({ "ROUTE_OK" }, { "ROUTE_FAIL: ${it.message}" })) }
        }
    }

    /** Let the user pick stops in the car navigation (no typing): chargers for EV, a place search otherwise. */
    fun openStationSearch(gasolineKeyword: String) {
        val ev = _rental.value?.powertrain == Powertrain.EV
        work.launch { token ->
            val ok = deps.navi.openScreen(
                if (ev) NavigationScreenRequest.NearbyChargingStations else NavigationScreenRequest.SearchPlace(gasolineKeyword),
            )
            if (work.isCurrent(token) && !ok) _ret.update { it.copy(routeMessage = "NAVI_UNAVAILABLE") }
        }
    }

    /** Read the waypoints the user set in the car navigation (they are already in its route). */
    fun importNaviWaypoints() {
        work.launch { token ->
            val res = deps.navi.waypoints()
            if (!work.isCurrent(token)) return@launch
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

    // ---- Incident report ----

    fun updateDraft(f: (IncidentDraft) -> IncidentDraft) = _report.update { it.copy(draft = f(it.draft)) }

    /** Read warning lights and pre-select a matching report type (renter can change it). */
    fun loadWarnings() {
        work.launch { token ->
            val list = runCatching { deps.vehicle.activeTelltales() }.getOrDefault(emptyList())
            if (!work.isCurrent(token)) return@launch
            _telltales.value = list
            val suggested = Warnings.suggestType(list.map { it.type.name })
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
            val res = deps.api.createIncident(pending.request, pending.idempotencyKey)
            if (!work.isCurrent(token)) return@launch
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
        val bytes = deps.api.mapTile(z, x, y) ?: return null
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
            demo = true,
            rental = RentalDto(
                r.rentalId, r.vehiclePlate, r.vehicleModel, r.powertrain, r.returnPlace.name,
                Instant.ofEpochMilli(r.returnAtMillis).toString(),
            ),
            vehicle = if (readAt == null) null else VehicleDto(st.fuelPercent?.value, st.soc?.value, readAt),
            warnings = Warnings.forRequest(_telltales.value.map { it.type.name }),
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
            when (val res = deps.api.status(rec.id, rec.incidentToken)) {
                is ApiResult.Ok -> _report.update {
                    it.copy(
                        acknowledged = res.value.status.reached(com.rentcare.core.IncidentStatus.ACKNOWLEDGED),
                        status = res.value.status,
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
            probe("Tire.getLowTirePressure(LEFT_FRONT)") { v.lowTirePressure(WheelValue.LEFT_FRONT).toString() }
            probe("Safety.getTelltalesInfo (ON/BLINK)") {
                v.activeTelltales().joinToString { "${it.type}:${it.state}" }.ifEmpty { "(none on)" }
            }
            probe("CarInfo.getCapabilityInfo (keys)") { v.capabilityKeys().joinToString().ifEmpty { "(empty)" } }
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
        if (BuildConfig.DEBUG) {
            // Never log location values (security guide): keep the API name and outcome only.
            val value = if (row.name.startsWith("NaviHelper.getCurrentLocationInfo")) "<redacted>" else row.value
            android.util.Log.i("RentcareProbe", "${if (row.ok) "OK" else "FAIL"} | ${row.name} | $value")
        }
        _probe.update { it + row }
    }

    override fun onCleared() {
        // Gateways live in the Application; nothing pending here to release.
        super.onCleared()
    }
}

