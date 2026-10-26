package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LampSignalTest {
    private val now = 1_800_000_000_000L

    @Test fun `12V low lamp on is a low signal (no prediction, no guarantee)`() {
        assertEquals(LampSignal.ON, LampSignal.of(listOf("LOW_VOLT_BATTERY", "TPMS"), readAtMillis = now, code = "LOW_VOLT_BATTERY", now = now))
    }

    @Test fun `lamp off means only 'no signal'`() {
        assertEquals(LampSignal.OFF, LampSignal.of(listOf("TPMS"), readAtMillis = now, code = "LOW_VOLT_BATTERY", now = now))
    }

    @Test fun `could not read, or older than 10 minutes, is unknown — never treated as normal`() {
        assertEquals(LampSignal.UNKNOWN, LampSignal.of(null, readAtMillis = null, code = "LOW_VOLT_BATTERY", now = now))
        assertEquals(LampSignal.UNKNOWN, LampSignal.of(emptyList(), readAtMillis = now - 11 * 60_000, code = "LOW_VOLT_BATTERY", now = now))
    }
}
