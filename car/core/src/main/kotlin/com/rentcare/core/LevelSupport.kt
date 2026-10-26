package com.rentcare.core

/**
 * Result of the capability check done before reading fuel / SOC (RENTCARE-6).
 * Emulator 2.1.0: EV SOC = READ; Fuel = ERROR_NOT_SUPPORTED_PROPERTY.
 */
enum class LevelSupport {
    SUPPORTED, UNSUPPORTED, DENIED, UNKNOWN;

    companion object {
        /** [accessType] = Pleos AccessType name, or [error] = the capability call's failure message. */
        fun of(accessType: String?, error: String?): LevelSupport = when {
            accessType == "READ" || accessType == "READ_WRITE" -> SUPPORTED
            accessType == "NONE" || accessType == "WRITE" -> UNSUPPORTED
            error == null -> UNKNOWN
            "NOT_SUPPORTED" in error || "not supported" in error -> UNSUPPORTED
            "Security" in error || "permission" in error.lowercase() -> DENIED
            else -> UNKNOWN
        }
    }
}
