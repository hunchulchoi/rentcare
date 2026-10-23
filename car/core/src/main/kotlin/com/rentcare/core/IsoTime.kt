package com.rentcare.core

import java.time.Instant

/** Server timestamps (ISO-8601 UTC) for display in the car's local time. */
object IsoTime {
    fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it.trim()).toEpochMilli() }.getOrNull() }
}
