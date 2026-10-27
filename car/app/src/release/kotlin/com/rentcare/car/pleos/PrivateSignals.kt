package com.rentcare.car.pleos

import ai.pleos.playground.vehicle.constant.area.WheelValue

/**
 * Release stub: per-wheel tire pressure and the capability list need PRIVATE_* permissions that Pleos has
 * not confirmed for third-party apps (RENTCARE-26). Release never calls them; the app shows "unknown".
 */
class PrivateSignals(@Suppress("UNUSED_PARAMETER") v: VehicleGateway) {
    val available = false

    suspend fun tirePressure(wheel: WheelValue): Float? = throw UnsupportedOperationException("not in release")
    suspend fun tirePressureWheels(): List<WheelValue> = emptyList()
    suspend fun capabilityKeys(): List<String> = emptyList()
}
