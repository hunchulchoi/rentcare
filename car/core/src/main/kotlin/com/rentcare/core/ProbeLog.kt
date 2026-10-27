package com.rentcare.core

/**
 * Debug log line for the SDK probe (security guide: no sensitive data in dev/test logs).
 * Only the API name and outcome; values (location, waypoints, addresses, input) stay on screen.
 */
object ProbeLog {
    fun line(name: String, ok: Boolean): String = "${if (ok) "OK" else "FAIL"} | $name"
}
