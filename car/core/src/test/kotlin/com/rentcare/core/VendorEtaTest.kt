package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VendorEtaTest {
    private val now = IsoTime.millis("2026-10-23T05:00:00Z")!!

    @Test fun `shown only while the vendor is handling it`() {
        val at = "2026-10-23T05:45:00Z"; val set = "2026-10-23T04:50:00Z"
        assertEquals(VendorEta.Upcoming(IsoTime.millis(at)!!, IsoTime.millis(set), 45), VendorEta.of(IncidentStatus.IN_PROGRESS, at, set, now))
        for (s in listOf(IncidentStatus.RECEIVED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.RESOLVED)) {
            assertEquals(VendorEta.Hidden, VendorEta.of(s, at, set, now), s.name)
        }
    }

    @Test fun `passed estimate says so instead of a negative countdown`() {
        assertEquals(VendorEta.Passed(IsoTime.millis("2026-10-23T04:30:00Z")!!), VendorEta.of(IncidentStatus.IN_PROGRESS, "2026-10-23T04:30:00Z", null, now))
    }

    @Test fun `missing or broken value is hidden, never guessed`() {
        assertEquals(VendorEta.Hidden, VendorEta.of(IncidentStatus.IN_PROGRESS, null, null, now))
        assertEquals(VendorEta.Hidden, VendorEta.of(IncidentStatus.IN_PROGRESS, "soon", null, now))
    }

    @Test fun `minutes left round up so 'in 1 min' never shows 0`() {
        val e = VendorEta.of(IncidentStatus.IN_PROGRESS, "2026-10-23T05:00:20Z", null, now)
        assertEquals(1, (e as VendorEta.Upcoming).minutesLeft)
    }
}
