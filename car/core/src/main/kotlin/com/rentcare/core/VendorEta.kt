package com.rentcare.core

import kotlin.math.ceil

/**
 * The vendor's own expected time (docs/api.md v7). Shown only while IN_PROGRESS and labeled as their estimate;
 * the app never computes one.
 */
sealed interface VendorEta {
    data object Hidden : VendorEta
    data class Upcoming(val atMillis: Long, val setAtMillis: Long?, val minutesLeft: Int) : VendorEta
    data class Passed(val atMillis: Long) : VendorEta

    companion object {
        fun of(status: IncidentStatus?, expectedAt: String?, expectedSetAt: String?, nowMillis: Long): VendorEta {
            if (status != IncidentStatus.IN_PROGRESS) return Hidden
            val at = IsoTime.millis(expectedAt) ?: return Hidden
            val left = at - nowMillis
            return if (left <= 0) Passed(at) else Upcoming(at, IsoTime.millis(expectedSetAt), ceil(left / 60_000.0).toInt())
        }
    }
}
