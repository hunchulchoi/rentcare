package com.rentcare.core

/**
 * What we can say about tire pressure from the car's own signals (RENTCARE-33). Never a diagnosis:
 * no puncture verdict, and "no low signal" is not "tires are fine".
 *
 * Emulator 2.1.0: per-wheel pressure (TIRE_PRESSURE) is not supported; the low-pressure threshold is
 * 137 kPa on all wheels; the TPMS lamp is the only live signal (set in Virtual Console).
 */
object TireCheck {
    enum class Wheel { FL, FR, RL, RR }
    enum class Lamp { OFF, ON, BLINK }
    enum class Reason { UNSUPPORTED, STALE, ERROR }

    data class LampReading(val lamp: Lamp, val readAtMillis: Long)

    sealed interface Result {
        /** Known positions below the car's low-pressure threshold. */
        data class LowWheels(val wheels: List<Wheel>) : Result
        /** TPMS lamp on, wheel position not available. */
        data object LowPositionUnknown : Result
        /** Blinking TPMS lamp: the monitoring system reports a fault. */
        data object TpmsFault : Result
        /** No low-pressure signal right now. Not a statement that the tires are fine. */
        data object NoLowSignal : Result
        data class Unknown(val reason: Reason) : Result

        /** Codes added to the report's warnings (docs/api.md). */
        fun warningCodes(): List<String> = when (this) {
            is LowWheels -> listOf("TPMS") + wheels.map { "TIRE_LOW_${it.name}" }
            LowPositionUnknown -> listOf("TPMS")
            TpmsFault -> listOf("TPMS_FAULT")
            else -> emptyList()
        }
    }

    private const val STALE_MS = 10 * 60_000L

    fun of(lamp: LampReading?, pressures: Map<Wheel, Float>, thresholds: Map<Wheel, Float>, nowMillis: Long): Result {
        val low = Wheel.entries.filter { w ->
            val p = pressures[w]; val t = thresholds[w]
            p != null && t != null && p > 0f && p < t
        }
        if (low.isNotEmpty()) return Result.LowWheels(low)
        if (lamp == null) return Result.Unknown(Reason.UNSUPPORTED)
        if (nowMillis - lamp.readAtMillis > STALE_MS) return Result.Unknown(Reason.STALE)
        return when (lamp.lamp) {
            Lamp.BLINK -> Result.TpmsFault
            Lamp.ON -> Result.LowPositionUnknown
            Lamp.OFF -> Result.NoLowSignal
        }
    }
}
