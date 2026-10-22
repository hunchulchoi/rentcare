package com.rentcare.car.pleos

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AAOS CarUxRestrictions (Pleos driving restriction guide):
 * read the current restriction, then follow onUxRestrictionsChanged.
 * [requiresDistractionOptimization] stays null when the car service is unavailable.
 */
class DrivingGateway(context: Context) {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val _requiresDO = MutableStateFlow<Boolean?>(null)
    val requiresDistractionOptimization: StateFlow<Boolean?> = _requiresDO

    fun start() {
        if (car != null) return
        runCatching {
            val c = Car.createCar(appContext) ?: return
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            m.registerListener { r -> _requiresDO.value = r.isRequiresDistractionOptimization }
            _requiresDO.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization
            car = c
            manager = m
        }.onFailure { Log.w(TAG, "CarUxRestrictions unavailable: ${it.javaClass.simpleName}") }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        _requiresDO.value = null
    }

    private companion object {
        const val TAG = "RentcareDriving"
    }
}
