package com.rentcare.core

/**
 * Car warning lights (Pleos TelltaleType names) that matter to the rental company.
 * We do not show our own alerts for them (the car already does); we only attach them
 * to a report so the vendor sees what the dashboard showed.
 */
object Warnings {
    const val MAX_IN_REQUEST = 20

    /** Order = priority for display. Normal indicators (lamps, turn signals, modes) are excluded. */
    val FAULT_CODES: Set<String> = linkedSetOf(
        "TPMS", "LOW_VOLT_BATTERY", "ENGINE", "ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE",
        "MASTER_WARNING", "ABS", "ESC", "MDPS", "AIRBAG", "EV_SYSTEM", "HEV_SYSTEM", "POWER_DOWN",
        "LOW_EV_BATTERY", "LOW_FUEL", "DOOR_OPEN", "TRUNK_OPEN", "IMMOBILIZER",
    )

    fun relevant(activeCodes: List<String>): List<String> = activeCodes.filter { it in FAULT_CODES }.distinct()

    fun forRequest(activeCodes: List<String>): List<String> = relevant(activeCodes).take(MAX_IN_REQUEST)

    /** Pre-select the report type the renter most likely needs. The renter can change it. */
    fun suggestType(activeCodes: List<String>): IncidentType? {
        val r = relevant(activeCodes)
        return when {
            r.isEmpty() -> null
            "TPMS" in r -> IncidentType.TIRE
            else -> IncidentType.WARNING_LIGHT
        }
    }
}
