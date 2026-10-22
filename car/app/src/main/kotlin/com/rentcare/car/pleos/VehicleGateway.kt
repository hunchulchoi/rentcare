package com.rentcare.car.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper over the Pleos Vehicle SDK (async onComplete/onFailed callbacks).
 * Every read can fail per car model / permission; callers must handle exceptions.
 */
class VehicleGateway(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    @Volatile private var started = false

    fun start() {
        if (started) return
        vehicle.initialize()
        started = true
    }

    /** Do not call while async callbacks are pending (SDK rule). Call from onDestroy of the app scope. */
    fun stop() {
        if (!started) return
        vehicle.release()
        started = false
    }

    // ---- Energy ----

    suspend fun fuelPercent(): Int? = call { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) }
    suspend fun fuelPercentAccess(): String = call<Any?> { ok, fail ->
        vehicle.getFuel().checkFuelPercentCapabilities({ ok(it.accessType) }, fail)
    }.toString()

    suspend fun evSoc(): Int? = call { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) }
    suspend fun evSocAccess(): String = call<Any?> { ok, fail ->
        vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities({ ok(it.accessType) }, fail)
    }.toString()

    /** Wh (Emulator 2.1.0 IONIQ 5 profile returns 77400.0 = 77.4 kWh). Convert before use. */
    suspend fun evBatteryCapacity(): Float? = call { ok, fail -> vehicle.getEvBattery().getEvBatteryCapacity(ok, fail) }

    /** Remaining range. Unit not stated in public docs; check DistanceDisplayUnit on the target car. */
    suspend fun rangeRemaining(): Float? = call { ok, fail -> vehicle.getOdometer().getRangeRemainingDistance(ok, fail) }

    suspend fun gear(): String = call<Any?> { ok, fail ->
        vehicle.getGear().getCurrentGearSelectionState(ok, fail)
    }.toString()

    // ---- Safety / tire ----

    suspend fun lowTirePressure(wheel: WheelValue): Float? = call { ok, fail ->
        vehicle.getTire().getLowTirePressure(WheelArea(wheel), ok, fail)
    }

    /** Warning lights currently ON/BLINK, e.g. TPMS, LOW_VOLT_BATTERY, LOW_FUEL. */
    suspend fun activeTelltales(): List<ActiveTelltale> = call<List<ActiveTelltale>> { ok, fail ->
        vehicle.getSafety().getTelltalesInfo({ t ->
            ok(
                t.telltales
                    .filter { it.state != TelltaleState.OFF }
                    .map { ActiveTelltale(it.type, it.state, it.warningCount) },
            )
        }, fail)
    }

    suspend fun capabilityKeys(): List<String> = call<List<String>> { ok, fail ->
        vehicle.getCarInfo().getCapabilityInfo({ ok(it?.keys?.sorted().orEmpty()) }, fail)
    }

    // ---- Subscriptions ----

    fun socUpdates(): Flow<Int?> = callbackFlow {
        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                trySend(value)
            }

            override fun onFailed(e: Exception) {
                close(e)
            }
        }
        vehicle.getEvBattery().registerEvBatteryStateOfCharge(listener)
        awaitClose { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(listener) }
    }

    fun fuelUpdates(): Flow<Int?> = callbackFlow {
        val listener = object : FuelPercentListener {
            override fun onFuelPercentUpdated(value: Int?) {
                trySend(value)
            }

            override fun onFailed(e: Exception) {
                close(e)
            }
        }
        vehicle.getFuel().registerFuelPercent(listener)
        awaitClose { vehicle.getFuel().unregisterFuelPercent(listener) }
    }

    /**
     * The SDK binds to the vehicle service asynchronously after initialize().
     * A call right after start may fail; retry a couple of times before reporting.
     */
    private suspend fun <T> call(
        attempts: Int = 3,
        block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): T {
        start()
        var last: Exception? = null
        repeat(attempts) { i ->
            try {
                return withTimeout(TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        block(
                            { if (cont.isActive) cont.resume(it) },
                            { if (cont.isActive) cont.resumeWithException(VehicleReadException(it)) },
                        )
                    }
                }
            } catch (e: Exception) {
                last = e
                if (i < attempts - 1) delay(RETRY_DELAY_MS)
            }
        }
        throw last ?: IllegalStateException("vehicle call failed")
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L
        private const val RETRY_DELAY_MS = 700L
    }
}

data class ActiveTelltale(val type: TelltaleType, val state: TelltaleState, val warningCount: Int)

class VehicleReadException(cause: Exception) :
    Exception("${cause.javaClass.simpleName}: ${cause.message}", cause)
