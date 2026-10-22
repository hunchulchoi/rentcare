package com.rentcare.car.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface NaviEvent {
    data class Location(val info: CurrentLocationInfo, val atMillis: Long) : NaviEvent
    data class Trip(val info: TripInfo, val atMillis: Long) : NaviEvent
    data class Error(val code: NaviErrorCode) : NaviEvent
    data object RoutingCompleted : NaviEvent
    data object RouteCancelled : NaviEvent
}

data class GeoPoint(val latitude: Double, val longitude: Double, val name: String, val address: String? = null)

/**
 * Wrapper over Pleos NaviHelper. Requests return immediately; results arrive as listener events.
 * Requires a navigation app on the car. "Route requested" ≠ "route computed": wait for
 * [NaviEvent.RoutingCompleted] or [NaviEvent.Error].
 */
class NaviGateway(context: Context) {
    private val navi = NaviHelper(context.applicationContext)
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<NaviEvent> = _events

    /** null = connecting, true = connected, false = failed (no navigation service / permission). */
    private val _connected = MutableStateFlow<Boolean?>(null)
    val connected: StateFlow<Boolean?> = _connected
    var connectError: String? = null
        private set
    private var started = false

    /**
     * Results are uncorrelated listener events, so only one request may be in flight:
     * otherwise a late result of a timed-out request could answer the next one.
     */
    private val lane = Mutex()

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _events.tryEmit(NaviEvent.Location(currentLocationInfo, System.currentTimeMillis()))
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            _events.tryEmit(NaviEvent.Trip(tripInfo, System.currentTimeMillis()))
        }

        override fun onError(errorCode: NaviErrorCode) {
            _events.tryEmit(NaviEvent.Error(errorCode))
        }

        override fun onRoutingCompleted() {
            _events.tryEmit(NaviEvent.RoutingCompleted)
        }

        override fun onRouteCancelled() {
            _events.tryEmit(NaviEvent.RouteCancelled)
        }
    }

    fun start() {
        if (started) return
        started = true
        navi.initialize(
            onConnected = {
                navi.addListener(listener)
                _connected.value = true
            },
            onFailed = { t ->
                connectError = "${t.javaClass.simpleName}: ${t.message}"
                _connected.value = false
                started = false // allow a later start() to retry
            },
        )
    }

    fun stop() {
        if (!started) return
        navi.removeListener(listener)
        navi.release()
        started = false
        _connected.value = null
    }

    private suspend fun awaitConnected(timeoutMs: Long): Boolean {
        start()
        return withTimeoutOrNull(timeoutMs) { connected.first { it != null } } == true
    }

    /** Current position from the car navigation. null when unavailable within [timeoutMs]. */
    suspend fun currentLocation(timeoutMs: Long = 5_000): NaviEvent.Location? = lane.withLock {
        if (!awaitConnected(timeoutMs)) return@withLock null
        withTimeoutOrNull(timeoutMs) {
            events.onSubscription { navi.getCurrentLocationInfo() }
                .mapNotNull { it as? NaviEvent.Location }.first()
        }
    }

    /** Distance / ETA / estimated SOC at destination for [dest] without starting guidance. */
    suspend fun trip(dest: GeoPoint, timeoutMs: Long = 10_000): Result<NaviEvent.Trip> = lane.withLock {
        if (!awaitConnected(timeoutMs)) return@withLock Result.failure(IllegalStateException(connectError ?: "navi not connected"))
        val ev = withTimeoutOrNull(timeoutMs) {
            events.onSubscription { navi.requestTrip(TripRequest(dest.latitude, dest.longitude, "", dest.name, "")) }
                .mapNotNull { it as? NaviEvent.Trip ?: (it as? NaviEvent.Error) }.first()
        } ?: return@withLock Result.failure(IllegalStateException("trip timeout"))
        when (ev) {
            is NaviEvent.Trip -> Result.success(ev)
            is NaviEvent.Error -> Result.failure(IllegalStateException("navi error ${ev.code}"))
            else -> Result.failure(IllegalStateException("unexpected"))
        }
    }

    /**
     * Start guidance to the return place via up to 2 stations.
     * Result is only "routing completed" — not arrival, not a guarantee the user follows it.
     */
    suspend fun routeWithWaypoints(dest: GeoPoint, waypoints: List<GeoPoint>, timeoutMs: Long = 15_000): Result<Unit> = lane.withLock {
        require(waypoints.size <= 2) { "max 2 waypoints" }
        if (!awaitConnected(timeoutMs)) return@withLock Result.failure(IllegalStateException(connectError ?: "navi not connected"))
        val destination = RouteInfo(
            dest.latitude, dest.longitude, null, null, dest.name, "", dest.address ?: "", "",
            RouteOption.RECOMMENDED, "", false,
        )
        val wps = waypoints.mapIndexed { i, p ->
            RequestWaypointInfo(
                p.latitude, p.longitude, p.name,
                if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                "", p.address ?: "", "", "",
            )
        }
        val ev = withTimeoutOrNull(timeoutMs) {
            events.onSubscription { navi.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, wps)) }
                .mapNotNull {
                when (it) {
                    NaviEvent.RoutingCompleted, is NaviEvent.Error -> it
                    else -> null
                }
            }.first()
        } ?: return@withLock Result.failure(IllegalStateException("route timeout"))
        if (ev is NaviEvent.Error) Result.failure(IllegalStateException("navi error ${ev.code}")) else Result.success(Unit)
    }
}
