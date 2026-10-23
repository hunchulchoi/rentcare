package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaypointCoordsTest {
    @Test fun `uses route coordinates when present`() {
        assertEquals(37.40 to 127.10, WaypointCoords.pick(37.40, 127.10, 37.41, 127.11))
    }

    @Test fun `falls back to display coordinates when route ones are zero`() {
        assertEquals(37.41 to 127.11, WaypointCoords.pick(0.0, 0.0, 37.41, 127.11))
    }

    @Test fun `no usable coordinates gives null, never 0,0`() {
        assertNull(WaypointCoords.pick(0.0, 0.0, null, null))
        assertNull(WaypointCoords.pick(0.0, 0.0, 0.0, 0.0))
        assertNull(WaypointCoords.pick(Double.NaN, 1.0, null, null))
        assertNull(WaypointCoords.pick(95.0, 1.0, null, null))
    }
}
