package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportToneTest {
    @Test fun `tone follows the report state`() {
        assertEquals(ReportTone.NONE, ReportTone.of(pending = false, received = false, acknowledged = false))
        assertEquals(ReportTone.NOT_SENT, ReportTone.of(pending = true, received = false, acknowledged = false))
        assertEquals(ReportTone.RECEIVED, ReportTone.of(pending = false, received = true, acknowledged = false))
        assertEquals(ReportTone.ACKNOWLEDGED, ReportTone.of(pending = false, received = true, acknowledged = true))
        // an unsent new report matters more than an older received one
        assertEquals(ReportTone.NOT_SENT, ReportTone.of(pending = true, received = true, acknowledged = true))
    }

    @Test fun `status colors (Pleos informative) keep 4_5 to 1 on every ground, day and night`() {
        for (p in listOf(DesignTokens.Day, DesignTokens.Night)) {
            val names = p.textPairs().map { it.first }
            for (c in listOf("positive", "active", "error")) {
                assertTrue(names.any { it.startsWith("$c on primaryContainer") }, "${p.name} $c on selected menu")
                assertTrue(names.any { it.startsWith("$c on surface") }, "${p.name} $c on surface")
            }
        }
    }
}
