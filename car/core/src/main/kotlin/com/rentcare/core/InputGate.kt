package com.rentcare.core

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/**
 * Whether detailed input (typing notes, editing location, reviewing attachments) is allowed.
 * The platform's CarUxRestrictions decide first. Availability of non-DO apps while driving
 * depends on the regional policy, so we never assume the system blocks us.
 */
object InputGate {
    enum class Decision { ALLOWED, BLOCKED_DRIVING, BLOCKED_NOT_PARKED }

    fun decide(requiresDistractionOptimization: Boolean?, gear: Gear?): Decision = when (requiresDistractionOptimization) {
        true -> Decision.BLOCKED_DRIVING
        false -> Decision.ALLOWED
        // No restriction info (service unavailable): be conservative, allow only in Park.
        null -> if (gear == Gear.PARK) Decision.ALLOWED else Decision.BLOCKED_NOT_PARKED
    }
}
