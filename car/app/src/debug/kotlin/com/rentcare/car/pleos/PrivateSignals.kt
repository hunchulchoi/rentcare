package com.rentcare.car.pleos

import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue

/**
 * Debug build only. SDK calls that need PRIVATE_CAR_SAFETY / PRIVATE_CAR_INFO, which are not in the
 * public docs (RENTCARE-26). The release source set has a stub that never calls the SDK (RENTCARE-39).
 */
class PrivateSignals(private val v: VehicleGateway) {
    val available = true

    /** Current tire pressure for one wheel (unit per SDK; kPa on the emulator console). */
    suspend fun tirePressure(wheel: WheelValue): Float? = v.call { ok, fail ->
        v.vehicle.getTire().getTirePressure(WheelArea(wheel), ok, fail)
    }

    /** Wheels that report a current pressure (empty = not supported). */
    suspend fun tirePressureWheels(): List<WheelValue> = v.call<List<WheelValue>> { ok, fail ->
        v.vehicle.getTire().checkTirePressureCapabilities({ _, areas -> ok(areas.flatMap { it.values.toList() }.distinct()) }, fail)
    }

    @android.annotation.SuppressLint("RestrictedApi") // debug probe only; never in release
    suspend fun capabilityKeys(): List<String> = v.call<List<String>> { ok, fail ->
        v.vehicle.getCarInfo().getCapabilityInfo({ ok(it?.keys?.sorted().orEmpty()) }, fail)
    }
}
