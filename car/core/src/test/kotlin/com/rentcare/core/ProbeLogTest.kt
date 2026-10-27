package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProbeLogTest {
    @Test fun `log line keeps the API name and outcome only`() {
        assertEquals("OK | NaviHelper.getWaypointInfo", ProbeLog.line("NaviHelper.getWaypointInfo", ok = true))
        assertEquals("FAIL | Tire.getTirePressure", ProbeLog.line("Tire.getTirePressure", ok = false))
    }

    @Test fun `log line never carries a value even if the name has one appended`() {
        // The value parameter does not exist: callers cannot pass coordinates, addresses or input.
        val line = ProbeLog.line("NaviHelper.getCurrentLocationInfo", ok = true)
        assertFalse(line.contains("lat"))
        assertFalse(line.contains("="))
    }
}
