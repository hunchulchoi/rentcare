package com.rentcare.core

/**
 * One dashboard warning lamp as a signal (e.g. LOW_VOLT_BATTERY for the 12V battery, RENTCARE-32).
 * OFF only means "no signal now"; UNKNOWN (not readable / stale) is never treated as normal.
 * Reading lamps needs PRIVATE_CAR_SAFETY (debug only until Pleos allows it), so release builds see UNKNOWN.
 */
enum class LampSignal {
    ON, OFF, UNKNOWN;

    companion object {
        private const val STALE_MS = 10 * 60_000L

        /** [activeCodes] = lamps ON/BLINK at [readAtMillis]; null when the read failed. */
        fun of(activeCodes: List<String>?, readAtMillis: Long?, code: String, now: Long): LampSignal = when {
            activeCodes == null || readAtMillis == null -> UNKNOWN
            now - readAtMillis > STALE_MS -> UNKNOWN
            code in activeCodes -> ON
            else -> OFF
        }
    }
}
