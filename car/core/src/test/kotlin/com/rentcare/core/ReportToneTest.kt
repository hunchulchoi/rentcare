package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportToneTest {
    @Test fun `tone follows the report state`() {
        assertEquals(ReportTone.NONE, ReportTone.of(pending = false, status = null))
        assertEquals(ReportTone.NOT_SENT, ReportTone.of(pending = true, status = null))
        assertEquals(ReportTone.RECEIVED, ReportTone.of(pending = false, status = IncidentStatus.RECEIVED))
        assertEquals(ReportTone.ACKNOWLEDGED, ReportTone.of(pending = false, status = IncidentStatus.ACKNOWLEDGED))
        assertEquals(ReportTone.IN_PROGRESS, ReportTone.of(pending = false, status = IncidentStatus.IN_PROGRESS))
        assertEquals(ReportTone.RESOLVED, ReportTone.of(pending = false, status = IncidentStatus.RESOLVED))
        // an unsent new report matters more than an older received one
        assertEquals(ReportTone.NOT_SENT, ReportTone.of(pending = true, status = IncidentStatus.RESOLVED))
    }

    @Test fun `status colors (Pleos informative) keep 4_5 to 1 on every ground, day and night`() {
        for (p in listOf(DesignTokens.Day, DesignTokens.Night)) {
            val names = p.textPairs().map { it.first }
            for (c in listOf("positive", "active", "error", "info", "progress")) {
                assertTrue(names.any { it.startsWith("$c on primaryContainer") }, "${p.name} $c on selected menu")
                assertTrue(names.any { it.startsWith("$c on surface") }, "${p.name} $c on surface")
            }
        }
    }
}
