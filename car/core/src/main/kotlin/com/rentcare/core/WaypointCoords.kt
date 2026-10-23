package com.rentcare.core

/**
 * Navigation waypoints may report 0,0 for the route position (seen on emulator 2.1.0) and carry the real
 * point in the display coordinates. 0,0 is never a valid stop here (Gulf of Guinea).
 */
object WaypointCoords {
    private fun valid(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && !lat.isNaN() && !lon.isNaN() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

    fun pick(lat: Double, lon: Double, displayLat: Double?, displayLon: Double?): Pair<Double, Double>? = when {
        valid(lat, lon) -> lat to lon
        valid(displayLat, displayLon) -> displayLat!! to displayLon!!
        else -> null
    }
}
