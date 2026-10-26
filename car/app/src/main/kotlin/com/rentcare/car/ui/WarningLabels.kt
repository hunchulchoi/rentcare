package com.rentcare.car.ui

import com.rentcare.car.R

/** Readable name of a car warning lamp (Warnings.FAULT_CODES); null = show the code as is. */
fun warningLabel(code: String): Int? = when (code) {
    "TPMS" -> R.string.warn_tpms
    "LOW_VOLT_BATTERY" -> R.string.warn_low_volt_battery
    "ENGINE" -> R.string.warn_engine
    "ENGINE_OIL_PRESSURE" -> R.string.warn_engine_oil_pressure
    "ENGINE_TEMPERATURE" -> R.string.warn_engine_temperature
    "MASTER_WARNING" -> R.string.warn_master_warning
    "ABS" -> R.string.warn_abs
    "ESC" -> R.string.warn_esc
    "MDPS" -> R.string.warn_mdps
    "AIRBAG" -> R.string.warn_airbag
    "EV_SYSTEM" -> R.string.warn_ev_system
    "HEV_SYSTEM" -> R.string.warn_hev_system
    "POWER_DOWN" -> R.string.warn_power_down
    "LOW_EV_BATTERY" -> R.string.warn_low_ev_battery
    "LOW_FUEL" -> R.string.warn_low_fuel
    "DOOR_OPEN" -> R.string.warn_door_open
    "TRUNK_OPEN" -> R.string.warn_trunk_open
    "IMMOBILIZER" -> R.string.warn_immobilizer
    else -> null
}
